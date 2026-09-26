package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VectorClockTest {

  @Test
  void emptyClocksAreEqual() {
    assertThat(new VectorClock().compare(new VectorClock())).isEqualTo(VersionRelation.EQUAL);
  }

  @Test
  void incrementCreatesHappenedBeforeRelation() {
    VectorClock v1 = new VectorClock().increment("A");
    VectorClock v2 = v1.increment("A");

    assertThat(v1.compare(v2)).isEqualTo(VersionRelation.BEFORE);
    assertThat(v2.compare(v1)).isEqualTo(VersionRelation.AFTER);
    assertThat(v2.compare(v2)).isEqualTo(VersionRelation.EQUAL);
  }

  @Test
  void incrementsOnDifferentNodesAreConcurrent() {
    VectorClock base = new VectorClock().increment("A");
    VectorClock fromA = base.increment("A");
    VectorClock fromB = base.increment("B");

    assertThat(fromA.compare(fromB)).isEqualTo(VersionRelation.CONCURRENT);
    assertThat(fromB.compare(fromA)).isEqualTo(VersionRelation.CONCURRENT);
  }

  @Test
  void mergeTakesComponentwiseMaxAndRestoresOrder() {
    VectorClock fromA = VectorClock.of(Map.of("A", 2L));
    VectorClock fromB = VectorClock.of(Map.of("A", 1L, "B", 1L));

    VectorClock joined = fromA.merge(fromB);

    assertThat(joined.counters()).containsExactlyInAnyOrderEntriesOf(Map.of("A", 2L, "B", 1L));
    assertThat(joined.compare(fromA)).isEqualTo(VersionRelation.AFTER);
    assertThat(joined.compare(fromB)).isEqualTo(VersionRelation.AFTER);
  }

  @Test
  void mergeIsCommutativeAndIdempotent() {
    VectorClock a = VectorClock.of(Map.of("A", 2L, "B", 1L));
    VectorClock b = VectorClock.of(Map.of("A", 1L, "C", 3L));

    assertThat(a.merge(b)).isEqualTo(b.merge(a));
    assertThat(a.merge(b).merge(b)).isEqualTo(a.merge(b));
  }

  @Test
  void missingEntriesCountAsZero() {
    VectorClock onlyA = VectorClock.of(Map.of("A", 1L));
    VectorClock empty = new VectorClock();

    assertThat(onlyA.get("B")).isZero();
    assertThat(onlyA.compare(empty)).isEqualTo(VersionRelation.AFTER);
    assertThat(empty.compare(onlyA)).isEqualTo(VersionRelation.BEFORE);
  }
}
