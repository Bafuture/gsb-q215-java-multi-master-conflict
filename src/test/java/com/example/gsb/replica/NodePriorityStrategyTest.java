package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NodePriorityStrategyTest {

    private final NodePriorityStrategy strategy =
            new NodePriorityStrategy(Map.of("A", 1, "B", 5, "C", 5));

    private Version version(String node) {
        return new Version(VectorClock.of(node, 1), node, 1_000L);
    }

    @Test
    void higherPriorityNodeWinsRegardlessOfDirection() {
        Version a = version("A");
        Version b = version("B");

        assertThat(strategy.resolve(a, b)).isEqualTo(b);
        assertThat(strategy.resolve(b, a)).isEqualTo(b);
        assertThat(strategy.name()).isEqualTo(NodePriorityStrategy.NAME);
    }

    @Test
    void equalPriorityBreaksTieByNodeIdDeterministically() {
        Version b = version("B");
        Version c = version("C");

        assertThat(strategy.resolve(b, c)).isEqualTo(b);
        assertThat(strategy.resolve(c, b)).isEqualTo(b);
    }

    @Test
    void unknownNodeHasPriorityZero() {
        NodePriorityStrategy onlyA = new NodePriorityStrategy(Map.of("A", 1));
        Version a = version("A");
        Version z = version("Z");
        assertThat(onlyA.resolve(z, a)).isEqualTo(a);
    }
}
