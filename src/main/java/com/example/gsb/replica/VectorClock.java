package com.example.gsb.replica;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 逻辑时钟（向量时钟）：为每个节点维护一个单调递增的计数器。
 *
 * <p>向量时钟可以判定两个事件（写入）之间的因果关系：
 * <ul>
 *   <li>{@code a} 的每个分量都不大于 {@code b} 且至少一个严格更小：a <b>先发生于</b>（happens-before）b；</li>
 *   <li>反之 b 先发生于 a；</li>
 *   <li>两边互有大小：两个写入<b>并发</b>，即多主场景下的冲突。</li>
 * </ul>
 * 该类不可变，所有“修改”操作都返回新实例。
 */
public final class VectorClock {

    private final Map<String, Long> ticks;

    public VectorClock() {
        this(Map.of());
    }

    public VectorClock(Map<String, Long> ticks) {
        Map<String, Long> copy = new HashMap<>();
        for (Map.Entry<String, Long> e : ticks.entrySet()) {
            if (e.getValue() > 0) {
                copy.put(e.getKey(), e.getValue());
            }
        }
        this.ticks = Collections.unmodifiableMap(copy);
    }

    /** 单个节点在第 {@code count} 次本地写入时的时钟：{nodeId: count}。 */
    public static VectorClock of(String nodeId, long count) {
        return new VectorClock(Map.of(nodeId, count));
    }

    /** 某节点本地写入一次：该节点分量加一，返回新时钟。 */
    public VectorClock increment(String nodeId) {
        Map<String, Long> next = new HashMap<>(ticks);
        next.merge(nodeId, 1L, Long::sum);
        return new VectorClock(next);
    }

    /** 合并两个时钟：逐分量取最大值（同步时合并因果历史）。 */
    public VectorClock merge(VectorClock other) {
        Map<String, Long> next = new HashMap<>(ticks);
        for (Map.Entry<String, Long> e : other.ticks.entrySet()) {
            next.merge(e.getKey(), e.getValue(), Math::max);
        }
        return new VectorClock(next);
    }

    public long tickOf(String nodeId) {
        return ticks.getOrDefault(nodeId, 0L);
    }

    public Map<String, Long> ticks() {
        return ticks;
    }

    /** 是否先发生于 {@code other}：所有分量都不大于 other，且至少一个分量严格小于。 */
    public boolean happensBefore(VectorClock other) {
        return relationTo(other) == Relation.BEFORE;
    }

    public boolean concurrentWith(VectorClock other) {
        return relationTo(other) == Relation.CONCURRENT;
    }

    /** 判定两个时钟的因果关系。 */
    public Relation relationTo(VectorClock other) {
        boolean selfLess = false;
        boolean selfGreater = false;
        for (String node : unionKeys(other)) {
            long x = tickOf(node);
            long y = other.tickOf(node);
            if (x < y) {
                selfLess = true;
            } else if (x > y) {
                selfGreater = true;
            }
        }
        if (selfLess && !selfGreater) {
            return Relation.BEFORE;
        }
        if (selfGreater && !selfLess) {
            return Relation.AFTER;
        }
        if (!selfLess) {
            return Relation.EQUAL;
        }
        return Relation.CONCURRENT;
    }

    private Set<String> unionKeys(VectorClock other) {
        Set<String> keys = new TreeSet<>(ticks.keySet());
        keys.addAll(other.ticks.keySet());
        return keys;
    }

    private String canonicalString() {
        StringBuilder sb = new StringBuilder();
        for (String node : new TreeSet<>(ticks.keySet())) {
            sb.append(node).append('=').append(ticks.get(node)).append(';');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VectorClock)) {
            return false;
        }
        VectorClock other = (VectorClock) o;
        return ticks.equals(other.ticks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticks);
    }

    @Override
    public String toString() {
        return canonicalString();
    }

    /** 两个版本（写入）之间的因果关系。 */
    public enum Relation {
        /** 当前版本先发生（对方更新）。 */
        BEFORE,
        /** 当前版本后发生（当前更新）。 */
        AFTER,
        /** 同一逻辑时钟。 */
        EQUAL,
        /** 并发：多主场景下需要按策略解决的冲突。 */
        CONCURRENT
    }
}
