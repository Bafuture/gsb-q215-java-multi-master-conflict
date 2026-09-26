package com.example.gsb.replication;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 向量时钟：每个节点一个逻辑计数器，用于刻画写入之间的因果关系。
 *
 * <p>不可变对象，{@link #increment} 与 {@link #merge} 均返回新实例。
 */
public final class VectorClock {

  private final Map<String, Long> counters;

  public VectorClock() {
    this.counters = Map.of();
  }

  private VectorClock(Map<String, Long> counters) {
    this.counters = Collections.unmodifiableMap(counters);
  }

  public static VectorClock of(Map<String, Long> counters) {
    return new VectorClock(new HashMap<>(counters));
  }

  public long get(String nodeId) {
    return counters.getOrDefault(nodeId, 0L);
  }

  /** 本节点发生一次写事件，返回递增后的新时钟。 */
  public VectorClock increment(String nodeId) {
    Map<String, Long> next = new HashMap<>(counters);
    next.merge(nodeId, 1L, Long::sum);
    return new VectorClock(next);
  }

  /** 与另一时钟取逐分量最大值（join），表示"已看到对方看到的一切"。 */
  public VectorClock merge(VectorClock other) {
    Map<String, Long> next = new HashMap<>(counters);
    other.counters.forEach((node, count) -> next.merge(node, count, Math::max));
    return new VectorClock(next);
  }

  /**
   * 与另一时钟比较因果关系：
   * 所有分量都小于等于且至少一个严格小于 → {@link VersionRelation#BEFORE}；
   * 反之 → {@link VersionRelation#AFTER}；全部相等 → {@link VersionRelation#EQUAL}；
   * 存在互有胜负的分量 → {@link VersionRelation#CONCURRENT}（并发冲突）。
   */
  public VersionRelation compare(VectorClock other) {
    Set<String> nodes = new HashSet<>(counters.keySet());
    nodes.addAll(other.counters.keySet());
    boolean less = false;
    boolean greater = false;
    for (String node : nodes) {
      long mine = get(node);
      long theirs = other.get(node);
      if (mine < theirs) {
        less = true;
      } else if (mine > theirs) {
        greater = true;
      }
      if (less && greater) {
        return VersionRelation.CONCURRENT;
      }
    }
    if (less) {
      return VersionRelation.BEFORE;
    }
    if (greater) {
      return VersionRelation.AFTER;
    }
    return VersionRelation.EQUAL;
  }

  public Map<String, Long> counters() {
    return counters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VectorClock)) {
      return false;
    }
    VectorClock that = (VectorClock) o;
    return counters.equals(that.counters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(counters);
  }

  @Override
  public String toString() {
    return new TreeMap<>(counters).toString();
  }
}
