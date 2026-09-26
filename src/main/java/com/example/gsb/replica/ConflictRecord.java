package com.example.gsb.replica;

import java.util.Objects;

/**
 * 一条冲突解决记录，保留现场供人工排查：
 * 冲突的键与字段、被覆盖（落选）的值与版本、获胜的值与版本、
 * 采用的策略、冲突原因以及发生时间。
 * 不可变。
 */
public record ConflictRecord(
        String key,
        String field,
        VersionedValue winner,
        VersionedValue loser,
        String strategy,
        String reason,
        long occurredAtMillis) {

    public ConflictRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(reason, "reason");
    }

    /** 标准冲突原因：两个版本向量时钟互不支配，即并发写入同一字段。 */
    public static final String REASON_CONCURRENT_WRITES =
            "CONCURRENT_WRITES: vector clocks are incomparable (writes happened without knowledge of each other)";

    public static ConflictRecord concurrent(String key, String field,
                                            VersionedValue winner, VersionedValue loser,
                                            String strategy, long occurredAtMillis) {
        return new ConflictRecord(key, field, winner, loser, strategy,
                REASON_CONCURRENT_WRITES, occurredAtMillis);
    }
}
