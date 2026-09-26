package com.example.gsb.replication;

import java.util.Objects;

/**
 * 单个字段的一次带版本写入：值 + 写入时的向量时钟 + 写入节点 + 物理时间戳。
 *
 * <p>物理时间戳仅供"按时间戳取新"策略使用，不参与因果判断。
 */
public final class VersionedField {

  private final String value;
  private final VectorClock clock;
  private final String nodeId;
  private final long timestamp;

  public VersionedField(String value, VectorClock clock, String nodeId, long timestamp) {
    this.value = Objects.requireNonNull(value, "value");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
    this.timestamp = timestamp;
  }

  public String value() {
    return value;
  }

  public VectorClock clock() {
    return clock;
  }

  public String nodeId() {
    return nodeId;
  }

  public long timestamp() {
    return timestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VersionedField)) {
      return false;
    }
    VersionedField that = (VersionedField) o;
    return timestamp == that.timestamp
        && value.equals(that.value)
        && clock.equals(that.clock)
        && nodeId.equals(that.nodeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, clock, nodeId, timestamp);
  }

  @Override
  public String toString() {
    return "VersionedField{value=" + value + ", node=" + nodeId
        + ", ts=" + timestamp + ", clock=" + clock + "}";
  }
}
