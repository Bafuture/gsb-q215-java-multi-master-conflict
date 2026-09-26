package com.example.gsb.replication;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个节点的同步与冲突统计。
 */
public final class SyncStats {

  private long syncRounds;
  private long conflictsDetected;
  private final Map<String, Long> resolvedByStrategy = new LinkedHashMap<>();
  private long conflictRecordsKept;

  void recordSyncRound() {
    syncRounds++;
  }

  void recordConflict(String strategy) {
    conflictsDetected++;
    resolvedByStrategy.merge(strategy, 1L, Long::sum);
  }

  void recordConflictRecordKept() {
    conflictRecordsKept++;
  }

  /** 本节点参与的同步轮次。 */
  public long syncRounds() {
    return syncRounds;
  }

  /** 检测到的并发冲突总数。 */
  public long conflictsDetected() {
    return conflictsDetected;
  }

  /** 按策略名统计的解决次数。 */
  public Map<String, Long> resolvedByStrategy() {
    return Collections.unmodifiableMap(resolvedByStrategy);
  }

  public long resolvedByStrategy(String strategy) {
    return resolvedByStrategy.getOrDefault(strategy, 0L);
  }

  /** 当前保留的冲突记录条数。 */
  public long conflictRecordsKept() {
    return conflictRecordsKept;
  }

  @Override
  public String toString() {
    return "SyncStats{syncRounds=" + syncRounds
        + ", conflictsDetected=" + conflictsDetected
        + ", resolvedByStrategy=" + resolvedByStrategy
        + ", conflictRecordsKept=" + conflictRecordsKept + "}";
  }
}
