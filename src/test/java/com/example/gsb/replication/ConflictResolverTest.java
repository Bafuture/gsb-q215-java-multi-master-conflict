package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConflictResolverTest {

  private static VersionedField field(String node, long timestamp, String value) {
    VectorClock clock = new VectorClock().increment(node);
    return new VersionedField(value, clock, node, timestamp);
  }

  @Test
  void lastWriteWinsPicksNewerTimestamp() {
    LastWriteWinsResolver resolver = new LastWriteWinsResolver();
    VersionedField older = field("A", 100L, "old");
    VersionedField newer = field("B", 200L, "new");

    assertThat(resolver.resolve("k", "f", older, newer)).isSameAs(newer);
    assertThat(resolver.resolve("k", "f", newer, older)).isSameAs(newer);
    assertThat(resolver.name()).isEqualTo(LastWriteWinsResolver.NAME);
  }

  @Test
  void lastWriteWinsBreaksTimestampTiesDeterministically() {
    LastWriteWinsResolver resolver = new LastWriteWinsResolver();
    VersionedField fromA = field("A", 100L, "x");
    VersionedField fromB = field("B", 100L, "y");

    assertThat(resolver.resolve("k", "f", fromA, fromB)).isSameAs(fromB);
    assertThat(resolver.resolve("k", "f", fromB, fromA)).isSameAs(fromB);
  }

  @Test
  void nodePriorityPicksHigherPriorityRegardlessOfTimestamp() {
    NodePriorityResolver resolver = new NodePriorityResolver(Map.of("A", 10, "B", 1));
    VersionedField highPriorityButOlder = field("A", 100L, "authoritative");
    VersionedField lowPriorityButNewer = field("B", 999L, "fresh");

    assertThat(resolver.resolve("k", "f", highPriorityButOlder, lowPriorityButNewer))
        .isSameAs(highPriorityButOlder);
    assertThat(resolver.resolve("k", "f", lowPriorityButNewer, highPriorityButOlder))
        .isSameAs(highPriorityButOlder);
    assertThat(resolver.name()).isEqualTo(NodePriorityResolver.NAME);
  }

  @Test
  void nodePriorityTreatsUnknownNodesAsZeroAndBreaksTiesDeterministically() {
    NodePriorityResolver resolver = new NodePriorityResolver(Map.of());
    VersionedField fromA = field("A", 100L, "x");
    VersionedField fromB = field("B", 200L, "y");

    assertThat(resolver.priorityOf("A")).isZero();
    assertThat(resolver.resolve("k", "f", fromA, fromB)).isSameAs(fromB);
    assertThat(resolver.resolve("k", "f", fromB, fromA)).isSameAs(fromB);
  }
}
