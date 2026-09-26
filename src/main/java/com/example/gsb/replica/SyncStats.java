package com.example.gsb.replica;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步统计（线程安全）：
 * <ul>
 *   <li>同步轮次：发起一次对端合并计一轮；</li>
 *   <li>冲突次数：经向量时钟判定为并发的字段写入对数；</li>
 *   <li>按策略解决次数：按策略名分别计数（每个并发冲突恰被一个策略解决一次）；</li>
 *   <li>无冲突字段合并次数：同键不同字段被合并、未发生覆盖的次数；</li>
 *   <li>保留的冲突记录数：直接取冲突日志当前大小。</li>
 * </ul>
 */
public final class SyncStats {

    private long syncRounds;
    private long conflicts;
    private long fieldMerges;
    private final Map<String, Long> resolvedByStrategy = new LinkedHashMap<>();

    public synchronized void recordSyncRound() {
        syncRounds++;
    }

    public synchronized void recordConflict(String strategyName) {
        conflicts++;
        resolvedByStrategy.merge(strategyName, 1L, Long::sum);
    }

    public synchronized void recordFieldMerge() {
        fieldMerges++;
    }

    public synchronized long syncRounds() {
        return syncRounds;
    }

    public synchronized long conflicts() {
        return conflicts;
    }

    public synchronized long fieldMerges() {
        return fieldMerges;
    }

    public synchronized long resolvedByStrategy(String strategyName) {
        return resolvedByStrategy.getOrDefault(strategyName, 0L);
    }

    public synchronized Map<String, Long> resolvedByStrategy() {
        return Map.copyOf(resolvedByStrategy);
    }

    /** 不可变快照。{@code retainedConflictRecords} 由调用方传入（冲突日志当前大小）。 */
    public synchronized StatsSnapshot snapshot(int retainedConflictRecords) {
        return new StatsSnapshot(syncRounds, conflicts, fieldMerges,
                Map.copyOf(resolvedByStrategy), retainedConflictRecords);
    }

    /** 统计快照。 */
    public record StatsSnapshot(
            long syncRounds,
            long conflicts,
            long fieldMerges,
            Map<String, Long> resolvedByStrategy,
            int retainedConflictRecords) {
    }
}
