package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FieldLevelMergeTest {

  private static ReplicaNode node(String id) {
    AtomicLong clock = new AtomicLong();
    return new ReplicaNode(id, new LastWriteWinsResolver(), clock::incrementAndGet);
  }

  @Test
  void concurrentWritesToDifferentFieldsMergeWithoutConflict() {
    ReplicaNode a = node("A");
    ReplicaNode b = node("B");

    a.write("user:1", "name", "alice");
    b.write("user:1", "email", "alice@example.com");

    a.syncWith(b);

    for (ReplicaNode node : new ReplicaNode[] {a, b}) {
      assertThat(node.value("user:1", "name")).isEqualTo("alice");
      assertThat(node.value("user:1", "email")).isEqualTo("alice@example.com");
      assertThat(node.conflictLog()).isEmpty();
      assertThat(node.stats().conflictsDetected()).isZero();
    }
  }

  @Test
  void causallyOrderedWritesTakeNewerVersionWithoutConflict() {
    ReplicaNode a = node("A");
    ReplicaNode b = node("B");

    a.write("user:1", "name", "alice");
    a.syncWith(b);
    b.write("user:1", "name", "alice-updated");
    a.syncWith(b);

    assertThat(a.value("user:1", "name")).isEqualTo("alice-updated");
    assertThat(a.conflictLog()).isEmpty();
    assertThat(b.conflictLog()).isEmpty();
  }

  @Test
  void mergeKeepsFieldsOnlyPresentOnOneSide() {
    ReplicaNode a = node("A");
    ReplicaNode b = node("B");

    a.write("user:1", "name", "alice");
    a.write("user:1", "age", "30");
    b.write("user:1", "email", "a@b.c");

    a.syncWith(b);

    assertThat(a.record("user:1").fields()).containsOnlyKeys("name", "age", "email");
    assertThat(b.record("user:1").fields()).containsOnlyKeys("name", "age", "email");
  }
}
