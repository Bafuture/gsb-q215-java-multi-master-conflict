package com.example.gsb.replication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 多主复制中的一个节点：可独立写入，并通过两两同步与其他节点交换状态。
 *
 * <p>写入时为每个字段生成版本元数据（向量时钟 + 节点标识 + 物理时间戳）。
 * 同步时先按记录级向量时钟判断因果关系：一方严格领先则直接采用；
 * 并发时逐字段合并——字段间有先后关系的取新，真正并发的字段交给
 * 可配置的 {@link ConflictResolver} 裁决，并生成 {@link ConflictRecord}。
 *
 * <p>合并操作满足交换律、结合律与幂等性（join-semilattice），
 * 因此任意顺序、任意次数的同步后所有节点收敛到同一状态。
 */
public final class ReplicaNode {

  private final String id;
  private final ConflictResolver resolver;
  private final LongSupplier wallClock;
  private final Map<String, VersionedRecord> store = new HashMap<>();
  private final List<ConflictRecord> conflictLog = new ArrayList<>();
  private final SyncStats stats = new SyncStats();
  private VectorClock clock = new VectorClock();

  public ReplicaNode(String id, ConflictResolver resolver) {
    this(id, resolver, System::currentTimeMillis);
  }

  /** @param wallClock 物理时钟来源，测试时可注入可控时钟。 */
  public ReplicaNode(String id, ConflictResolver resolver, LongSupplier wallClock) {
    this.id = Objects.requireNonNull(id, "id");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
  }

  public String id() {
    return id;
  }

  /** 本地写入一个字段，生成新的版本元数据。 */
  public void write(String key, String field, String value) {
    clock = clock.increment(id);
    VersionedRecord record = store.getOrDefault(key, VersionedRecord.empty(key));
    VersionedField versioned = new VersionedField(value, clock, id, wallClock.getAsLong());
    store.put(key, record.withField(field, versioned));
  }

  public VersionedRecord record(String key) {
    return store.get(key);
  }

  public String value(String key, String field) {
    VersionedRecord record = store.get(key);
    if (record == null || record.field(field) == null) {
      return null;
    }
    return record.field(field).value();
  }

  /** 当前存储的只读视图。 */
  public Map<String, VersionedRecord> store() {
    return Collections.unmodifiableMap(store);
  }

  public List<ConflictRecord> conflictLog() {
    return Collections.unmodifiableList(conflictLog);
  }

  public SyncStats stats() {
    return stats;
  }

  /**
   * 与另一节点进行一轮双向同步：双方各自把对方的状态合并进本地。
   * 一轮结束后两个节点在这两个节点范围内状态一致。
   */
  public void syncWith(ReplicaNode other) {
    Objects.requireNonNull(other, "other");
    if (other == this) {
      return;
    }
    this.stats.recordSyncRound();
    other.stats.recordSyncRound();
    this.pullFrom(other);
    other.pullFrom(this);
  }

  /** 把对端节点的全部记录合并进本地。 */
  private void pullFrom(ReplicaNode remote) {
    for (Map.Entry<String, VersionedRecord> entry : remote.store.entrySet()) {
      String key = entry.getKey();
      VersionedRecord remoteRecord = entry.getValue();
      VersionedRecord localRecord = store.get(key);
      if (localRecord == null) {
        store.put(key, remoteRecord);
      } else {
        VersionRelation relation = localRecord.clock().compare(remoteRecord.clock());
        switch (relation) {
          case EQUAL:
          case AFTER:
            break;
          case BEFORE:
            store.put(key, remoteRecord);
            break;
          case CONCURRENT:
            store.put(key, mergeRecords(key, localRecord, remoteRecord));
            break;
          default:
            throw new IllegalStateException("unknown relation: " + relation);
        }
      }
      clock = clock.merge(remoteRecord.clock());
    }
  }

  /** 并发记录做字段级合并：有先后关系的字段取新，真正并发的字段走策略裁决。 */
  private VersionedRecord mergeRecords(String key, VersionedRecord local, VersionedRecord remote) {
    Set<String> fieldNames = new HashSet<>(local.fields().keySet());
    fieldNames.addAll(remote.fields().keySet());
    Map<String, VersionedField> merged = new HashMap<>();
    for (String fieldName : fieldNames) {
      VersionedField localField = local.field(fieldName);
      VersionedField remoteField = remote.field(fieldName);
      if (localField == null) {
        merged.put(fieldName, remoteField);
      } else if (remoteField == null) {
        merged.put(fieldName, localField);
      } else {
        VersionRelation relation = localField.clock().compare(remoteField.clock());
        switch (relation) {
          case EQUAL:
          case AFTER:
            merged.put(fieldName, localField);
            break;
          case BEFORE:
            merged.put(fieldName, remoteField);
            break;
          case CONCURRENT:
            merged.put(fieldName, resolveConflict(key, fieldName, localField, remoteField));
            break;
          default:
            throw new IllegalStateException("unknown relation: " + relation);
        }
      }
    }
    return new VersionedRecord(key, merged);
  }

  private VersionedField resolveConflict(
      String key, String fieldName, VersionedField localField, VersionedField remoteField) {
    VersionedField winner = resolver.resolve(key, fieldName, localField, remoteField);
    if (winner != localField && winner != remoteField) {
      throw new IllegalStateException("resolver must return one of the two conflicting versions");
    }
    VersionedField loser = winner == localField ? remoteField : localField;
    String reason = "concurrent writes on key '" + key + "' field '" + fieldName
        + "' from nodes '" + localField.nodeId() + "' and '" + remoteField.nodeId()
        + "' resolved by strategy '" + resolver.name() + "'";
    conflictLog.add(new ConflictRecord(key, fieldName, winner, loser, resolver.name(), reason));
    stats.recordConflict(resolver.name());
    stats.recordConflictRecordKept();
    return winner;
  }
}
