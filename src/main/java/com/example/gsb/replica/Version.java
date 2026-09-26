package com.example.gsb.replica;

import java.util.Objects;

/**
 * 一次写入的版本元数据：
 * <ul>
 *   <li>{@code vectorClock} —— 逻辑时钟，用于因果判断（版本比较的唯一权威依据）；</li>
 *   <li>{@code nodeId} —— 发起写入的节点标识；</li>
 *   <li>{@code wallTimestampMillis} —— 物理墙上时间（UTC 毫秒），仅供“按时间戳取新”策略参考，
 *       不参与因果判断（物理时钟在多节点间不保证单调、一致）。</li>
 * </ul>
 * 不可变值对象。
 */
public record Version(VectorClock vectorClock, String nodeId, long wallTimestampMillis) {

    public Version {
        Objects.requireNonNull(vectorClock, "vectorClock");
        Objects.requireNonNull(nodeId, "nodeId");
    }

    /** 只比较逻辑时钟：返回两个写入是先后关系还是并发。 */
    public VectorClock.Relation relationTo(Version other) {
        return vectorClock.relationTo(other.vectorClock);
    }

    @Override
    public String toString() {
        return nodeId + "@" + wallTimestampMillis + "[" + vectorClock + "]";
    }
}
