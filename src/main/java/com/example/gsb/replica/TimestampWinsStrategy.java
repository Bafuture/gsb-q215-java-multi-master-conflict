package com.example.gsb.replica;

import java.util.Objects;

/**
 * “按时间戳取新”（last-write-wins by wall clock）：物理时间戳更大的版本胜出。
 *
 * <p><b>时钟不可靠风险（必须知悉）：</b>
 * 墙上时钟来自各节点本地系统时钟，而分布式系统中物理时钟
 * <ul>
 *   <li>可能存在偏差/漂移（clock skew），节点间时钟没有全局同步保证；</li>
 *   <li>可能因 NTP 校时、人工修改、闰秒等发生<b>回拨</b>，不保证单调；</li>
 *   <li>因此“时间戳更大”并不代表逻辑上更新，可能把语义上更新的写入判输，
 *       造成已确认的数据被旧值静默覆盖（lost update）。</li>
 * </ul>
 * 本策略只把时间戳用于<b>并发</b>版本之间的裁决（因果先后仍由向量时钟保证），
 * 并在时间戳相等时以节点标识字典序兜底，保证结果确定、可收敛。
 * 对正确性敏感的场景应使用带版本向量的字段合并，或部署单调时钟/混合逻辑时钟（HLC）。
 */
public final class TimestampWinsStrategy implements ConflictResolutionStrategy {

    public static final String NAME = "TIMESTAMP_WINS";

    @Override
    public Version resolve(Version local, Version remote) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        if (local.wallTimestampMillis() > remote.wallTimestampMillis()) {
            return local;
        }
        if (remote.wallTimestampMillis() > local.wallTimestampMillis()) {
            return remote;
        }
        // 时间戳相同：节点标识字典序兜底，避免结果依赖同步方向。
        return local.nodeId().compareTo(remote.nodeId()) <= 0 ? local : remote;
    }

    @Override
    public String name() {
        return NAME;
    }
}
