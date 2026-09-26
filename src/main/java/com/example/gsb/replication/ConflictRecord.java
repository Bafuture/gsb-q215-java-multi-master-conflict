package com.example.gsb.replication;

import java.util.Objects;

/**
 * 一条冲突记录：冲突发生时保留了哪个值、覆盖了哪个值、由哪条策略因何原因裁决，
 * 供事后人工排查。
 */
public final class ConflictRecord {

  private final String key;
  private final String field;
  private final String keptValue;
  private final String keptNodeId;
  private final String discardedValue;
  private final String discardedNodeId;
  private final String strategy;
  private final String reason;

  public ConflictRecord(
      String key,
      String field,
      VersionedField kept,
      VersionedField discarded,
      String strategy,
      String reason) {
    this.key = Objects.requireNonNull(key, "key");
    this.field = Objects.requireNonNull(field, "field");
    this.keptValue = Objects.requireNonNull(kept, "kept").value();
    this.keptNodeId = kept.nodeId();
    this.discardedValue = Objects.requireNonNull(discarded, "discarded").value();
    this.discardedNodeId = discarded.nodeId();
    this.strategy = Objects.requireNonNull(strategy, "strategy");
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public String key() {
    return key;
  }

  public String field() {
    return field;
  }

  /** 被保留的值。 */
  public String keptValue() {
    return keptValue;
  }

  public String keptNodeId() {
    return keptNodeId;
  }

  /** 被覆盖（丢弃）的值。 */
  public String discardedValue() {
    return discardedValue;
  }

  public String discardedNodeId() {
    return discardedNodeId;
  }

  /** 裁决所用策略名。 */
  public String strategy() {
    return strategy;
  }

  /** 冲突原因描述。 */
  public String reason() {
    return reason;
  }

  @Override
  public String toString() {
    return "ConflictRecord{key=" + key + ", field=" + field
        + ", kept=" + keptValue + " (from " + keptNodeId + ")"
        + ", discarded=" + discardedValue + " (from " + discardedNodeId + ")"
        + ", strategy=" + strategy + ", reason=" + reason + "}";
  }
}
