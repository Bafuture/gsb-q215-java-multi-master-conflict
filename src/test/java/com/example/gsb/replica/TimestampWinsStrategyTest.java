package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimestampWinsStrategyTest {

    private final TimestampWinsStrategy strategy = new TimestampWinsStrategy();

    private Version version(String node, long wallMillis, VectorClock clock) {
        return new Version(clock, node, wallMillis);
    }

    @Test
    void newerWallTimestampWinsRegardlessOfDirection() {
        Version older = version("A", 100, VectorClock.of("A", 1));
        Version newer = version("B", 200, VectorClock.of("B", 1));

        assertThat(strategy.resolve(older, newer)).isEqualTo(newer);
        assertThat(strategy.resolve(newer, older)).isEqualTo(newer);
        assertThat(strategy.name()).isEqualTo(TimestampWinsStrategy.NAME);
    }

    @Test
    void equalTimestampsBreakTieByNodeIdDeterministically() {
        Version a = version("A", 100, VectorClock.of("A", 1));
        Version b = version("B", 100, VectorClock.of("B", 1));

        assertThat(strategy.resolve(a, b)).isEqualTo(a);
        assertThat(strategy.resolve(b, a)).isEqualTo(a);
    }
}
