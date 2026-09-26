package com.example.gsb.replica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongSupplier;

/**
 * 一个多主复制节点（副本）。
 *
 * <p>存储模型：{@code key -> field -> FieldEntry}。同一个键的不同字段各自携带版本，
 * 因此两个节点分别修改同一键的不同字段时可以<b>字段级无冲突合并</b>；
 * 只有两边并发修改了<b>同一个字段</b>才构成冲突，交由 {@link ConflictResolutionStrategy} 裁决。
 *
 * <p>每个字段保留一组互不支配的兄弟版本（见 {@link FieldEntry}）。同步时对字段做集合调和：
 * 并集 → 剪除存在因果后继的版本 → 剩余版本互为并发，按策略确定性选优。
 * 该操作满足结合律、交换律与幂等律，因此任意同步顺序、任意同步方向最终都收敛到同一状态。
 *
 * <p>本类面向单线程编排的同步流程；统计与日志内部线程安全。
 */
public final class ReplicaNode {

    private final String nodeId;
    private final ConflictResolutionStrategy strategy;
    private final LongSupplier wallClock;
    private final ConflictLog conflictLog;
    private final SyncStats stats = new SyncStats();

    private VectorClock clock = new VectorClock();
    private final Map<String, Map<String, FieldEntry>> store = new HashMap<>();

    public ReplicaNode(String nodeId, ConflictResolutionStrategy strategy) {
        this(nodeId, strategy, System::currentTimeMillis, Integer.MAX_VALUE);
    }

    public ReplicaNode(String nodeId,
                       ConflictResolutionStrategy strategy,
                       LongSupplier wallClock,
                       int conflictLogCapacity) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
        this.conflictLog = new ConflictLog(conflictLogCapacity);
    }

    public String nodeId() {
        return nodeId;
    }

    public ConflictResolutionStrategy strategy() {
        return strategy;
    }

    public ConflictLog conflictLog() {
        return conflictLog;
    }

    public SyncStats stats() {
        return stats;
    }

    /** 整键写入：一次逻辑时钟滴答，批量写入/覆盖多个字段（未提及的字段保留）。 */
    public void write(String key, Map<String, String> fields) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fields, "fields");
        if (fields.isEmpty()) {
            return;
        }
        clock = clock.increment(nodeId);
        Version version = new Version(clock, nodeId, wallClock.getAsLong());
        Map<String, FieldEntry> keyed = store.computeIfAbsent(key, k -> new LinkedHashMap<>());
        for (Map.Entry<String, String> e : fields.entrySet()) {
            keyed.put(e.getKey(), new FieldEntry(new VersionedValue(e.getValue(), version)));
        }
    }

    /** 单字段写入，等价于只含一个字段的整键写入。 */
    public void writeField(String key, String field, String value) {
        write(key, Map.of(field, value));
    }

    /** 读取某键某字段的当前值；不存在返回 {@code null}。 */
    public String get(String key, String field) {
        Map<String, FieldEntry> keyed = store.get(key);
        if (keyed == null) {
            return null;
        }
        FieldEntry entry = keyed.get(field);
        return entry == null ? null : entry.current().value();
    }

    /** 读取某键全部字段的不可变快照；键不存在返回空 Map。 */
    public Map<String, String> getKey(String key) {
        Map<String, FieldEntry> keyed = store.get(key);
        if (keyed == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, FieldEntry> e : keyed.entrySet()) {
            result.put(e.getKey(), e.getValue().current().value());
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean containsKey(String key) {
        return store.containsKey(key);
    }

    /** 整个节点数据的不可变慢照（{@code key -> field -> value}，键、字段有序便于比较）。 */
    public Map<String, Map<String, String>> snapshot() {
        Map<String, Map<String, String>> result = new TreeMap<>();
        for (String key : new TreeSet<>(store.keySet())) {
            result.put(key, Collections.unmodifiableMap(new TreeMap<>(getKey(key))));
        }
        return Collections.unmodifiableMap(result);
    }

    public int keyCount() {
        return store.size();
    }

    /**
     * 单向同步：把 {@code peer} 的全部数据合并进本节点。
     * 有因果关系的写入自动取新；并发同字段写入按策略解决；不同字段自动合并。
     *
     * @return 本轮同步的变化汇总
     */
    public SyncResult syncFrom(ReplicaNode peer) {
        Objects.requireNonNull(peer, "peer");
        stats.recordSyncRound();
        int conflictsResolved = 0;
        int fieldsMerged = 0;
        List<ConflictRecord> newRecords = new ArrayList<>();

        synchronized (peer) {
            for (Map.Entry<String, Map<String, FieldEntry>> peerEntry : peer.store.entrySet()) {
                String key = peerEntry.getKey();
                Map<String, FieldEntry> localFields = store.computeIfAbsent(key, k -> new LinkedHashMap<>());
                boolean keyExistedLocally = !localFields.isEmpty();

                for (Map.Entry<String, FieldEntry> fieldEntry : peerEntry.getValue().entrySet()) {
                    String field = fieldEntry.getKey();
                    FieldEntry remote = fieldEntry.getValue();
                    FieldEntry local = localFields.get(field);

                    if (local == null) {
                        // 本节点没有该字段：纯复制；若键已存在，则属于不同字段的无冲突合并。
                        localFields.put(field, remote);
                        if (keyExistedLocally) {
                            fieldsMerged++;
                            stats.recordFieldMerge();
                        }
                        continue;
                    }

                    VersionedValue previous = local.current();
                    FieldEntry.Reconciliation outcome = local.reconcile(remote.origins(), strategy);
                    if (!outcome.changed()) {
                        continue;
                    }
                    localFields.put(field, outcome.entry());
                    for (VersionedValue concurrent : outcome.addedConcurrent()) {
                        // 每个新发现的并发原始写入记一次冲突；被覆盖的值与版本完整保留。
                        VersionedValue winner = outcome.entry().current();
                        // 新并发版本赢了，则本地旧值被覆盖；否则该新并发版本本身被覆盖。
                        VersionedValue loser = concurrent == outcome.entry().winnerOrigin()
                                ? previous
                                : concurrent;
                        ConflictRecord record = ConflictRecord.concurrent(
                                key, field, winner, loser, strategy.name(), wallClock.getAsLong());
                        conflictLog.append(record);
                        newRecords.add(record);
                        stats.recordConflict(strategy.name());
                    }
                    conflictsResolved += outcome.addedConcurrent().size();
                }
            }
            clock = clock.merge(peer.clock);
        }
        return new SyncResult(nodeId, peer.nodeId, conflictsResolved, fieldsMerged, List.copyOf(newRecords));
    }

    /**
     * 双向同步：本节点先吸收对端，对端再吸收本节点。
     * 一轮之后两边对该对拥有相同数据；冲突在两边按各自（应当相同的）策略裁决。
     */
    public SyncResult syncWith(ReplicaNode peer) {
        SyncResult incoming = syncFrom(peer);
        peer.syncFrom(this);
        return incoming;
    }

    /** 单轮同步的结果汇总。 */
    public record SyncResult(
            String targetNode,
            String sourceNode,
            int conflictsResolved,
            int fieldsMerged,
            List<ConflictRecord> conflicts) {
    }
}
