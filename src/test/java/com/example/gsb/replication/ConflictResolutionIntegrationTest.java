package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ConflictResolutionIntegrationTest {

  @Test
  void lastWriteWinsKeepsNewerTimestampAndRecordsConflict() {
    AtomicLong clockA = new AtomicLong(100);
    AtomicLong clockB = new AtomicLong(200);
    ReplicaNode a = new ReplicaNode("A", new LastWriteWinsResolver(), clockA::incrementAndGet);
    ReplicaNode b = new ReplicaNode("B", new LastWriteWinsResolver(), clockB::incrementAndGet);

    a.write("user:1", "name", "from-a");
    b.write("user:1", "name", "from-b");
    a.syncWith(b);

    assertThat(a.value("user:1", "name")).isEqualTo("from-b");
    assertThat(b.value("user:1", "name")).isEqualTo("from-b");

    assertThat(a.conflictLog()).hasSize(1);
    ConflictRecord record = a.conflictLog().get(0);
    assertThat(record.key()).isEqualTo("user:1");
    assertThat(record.field()).isEqualTo("name");
    assertThat(record.keptValue()).isEqualTo("from-b");
    assertThat(record.keptNodeId()).isEqualTo("B");
    assertThat(record.discardedValue()).isEqualTo("from-a");
    assertThat(record.discardedNodeId()).isEqualTo("A");
    assertThat(record.strategy()).isEqualTo(LastWriteWinsResolver.NAME);
    assertThat(record.reason()).contains("user:1").contains("name").contains(LastWriteWinsResolver.NAME);
  }

  @Test
  void nodePriorityKeepsHigherPriorityNodeEvenWithOlderTimestamp() {
    NodePriorityResolver resolver = new NodePriorityResolver(Map.of("A", 10, "B", 1));
    AtomicLong clockA = new AtomicLong(100);
    AtomicLong clockB = new AtomicLong(200);
    ReplicaNode a = new ReplicaNode("A", resolver, clockA::incrementAndGet);
    ReplicaNode b = new ReplicaNode("B", resolver, clockB::incrementAndGet);

    a.write("user:1", "name", "from-a");
    b.write("user:1", "name", "from-b");
    a.syncWith(b);

    assertThat(a.value("user:1", "name")).isEqualTo("from-a");
    assertThat(b.value("user:1", "name")).isEqualTo("from-a");
    assertThat(a.conflictLog()).hasSize(1);
    assertThat(a.conflictLog().get(0).discardedValue()).isEqualTo("from-b");
  }

  @Test
  void resolvedConflictDoesNotReappearOnSubsequentSyncs() {
    ReplicaNode a = new ReplicaNode("A", new LastWriteWinsResolver(), new AtomicLong()::incrementAndGet);
    ReplicaNode b = new ReplicaNode("B", new LastWriteWinsResolver(), new AtomicLong()::incrementAndGet);

    a.write("k", "f", "v1");
    b.write("k", "f", "v2");
    a.syncWith(b);
    long conflictsAfterFirstRound = a.stats().conflictsDetected();

    a.syncWith(b);
    a.syncWith(b);

    assertThat(conflictsAfterFirstRound).isEqualTo(1);
    assertThat(a.stats().conflictsDetected()).isEqualTo(1);
  }
}
