package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VectorClockComparisonTest {

    private final VectorClock clockA1 = VectorClock.of("A", 1);
    private final VectorClock clockA2 = VectorClock.of("A", 2);
    private final VectorClock clockA1B1 = VectorClock.of("A", 1).increment("B");

    @Test
    void localIncrementsAreOrdered() {
        assertThat(clockA1.happensBefore(clockA2)).isTrue();
        assertThat(clockA1.relationTo(clockA2)).isEqualTo(VectorClock.Relation.BEFORE);
        assertThat(clockA2.relationTo(clockA1)).isEqualTo(VectorClock.Relation.AFTER);
        assertThat(clockA1.concurrentWith(clockA2)).isFalse();
    }

    @Test
    void mergedClockDominatesItsParents() {
        assertThat(clockA1.happensBefore(clockA1B1)).isTrue();
        assertThat(VectorClock.of("B", 1).happensBefore(clockA1B1)).isTrue();
    }

    @Test
    void writesFromDifferentNodesWithoutSyncAreConcurrent() {
        VectorClock b1 = VectorClock.of("B", 1);
        assertThat(clockA1.relationTo(b1)).isEqualTo(VectorClock.Relation.CONCURRENT);
        assertThat(clockA1.concurrentWith(b1)).isTrue();
        assertThat(clockA1.happensBefore(b1)).isFalse();
        assertThat(b1.happensBefore(clockA1)).isFalse();
    }

    @Test
    void concurrentWritesBecomeOrderedAfterPropagation() {
        // 复制后的典型场景：[A=2,B=1] 与 [A=1,B=1] 构成先后关系，不再是并发。
        VectorClock propagated = VectorClock.of("A", 2).increment("B");
        assertThat(clockA1B1.relationTo(propagated)).isEqualTo(VectorClock.Relation.BEFORE);
        assertThat(propagated.concurrentWith(clockA1B1)).isFalse();
    }

    @Test
    void equalClocksCompareEqual() {
        assertThat(new VectorClock().relationTo(new VectorClock())).isEqualTo(VectorClock.Relation.EQUAL);
        assertThat(clockA1.relationTo(VectorClock.of("A", 1))).isEqualTo(VectorClock.Relation.EQUAL);
        assertThat(clockA1).isEqualTo(VectorClock.of("A", 1));
    }

    @Test
    void mergeTakesComponentwiseMax() {
        VectorClock merged = VectorClock.of("A", 3).merge(VectorClock.of("B", 2).increment("A"));
        assertThat(merged.tickOf("A")).isEqualTo(3L);
        assertThat(merged.tickOf("B")).isEqualTo(2L);
    }
}
