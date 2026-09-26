package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SyncStatsTest {

  @Test
  void statsTrackRoundsConflictsStrategiesAndRecords() {
    NodePriorityResolver resolver = new NodePriorityResolver(Map.of("A", 2, "B", 1));
    ReplicaNode a = new ReplicaNode("A", resolver, new AtomicLong()::incrementAndGet);
    ReplicaNode b = new ReplicaNode("B", resolver, new AtomicLong()::incrementAndGet);

    a.write("k1", "f1", "a1");
    b.write("k1", "f1", "b1");
    a.write("k2", "f2", "a2");
    b.write("k2", "f2", "b2");
    a.write("k3", "f3", "a3");
    b.write("k3", "f3", "b3");

    a.syncWith(b);
    a.syncWith(b);

    assertThat(a.stats().syncRounds()).isEqualTo(2);
    assertThat(b.stats().syncRounds()).isEqualTo(2);

    assertThat(a.stats().conflictsDetected()).isEqualTo(3);
    assertThat(a.stats().resolvedByStrategy())
        .containsExactlyEntriesOf(Map.of(NodePriorityResolver.NAME, 3L));
    assertThat(a.stats().resolvedByStrategy(NodePriorityResolver.NAME)).isEqualTo(3);
    assertThat(a.stats().resolvedByStrategy(LastWriteWinsResolver.NAME)).isZero();

    assertThat(a.stats().conflictRecordsKept()).isEqualTo(3);
    assertThat(a.conflictLog()).hasSize(3);
    assertThat(a.conflictLog())
        .allSatisfy(record -> {
          assertThat(record.strategy()).isEqualTo(NodePriorityResolver.NAME);
          assertThat(record.keptNodeId()).isEqualTo("A");
          assertThat(record.discardedNodeId()).isEqualTo("B");
          assertThat(record.reason()).isNotBlank();
        });
  }

  @Test
  void conflictFreeSyncsDoNotAffectConflictStats() {
    ReplicaNode a = new ReplicaNode("A", new LastWriteWinsResolver());
    ReplicaNode b = new ReplicaNode("B", new LastWriteWinsResolver());

    a.write("k", "f1", "v1");
    b.write("k", "f2", "v2");
    a.syncWith(b);

    assertThat(a.stats().syncRounds()).isEqualTo(1);
    assertThat(a.stats().conflictsDetected()).isZero();
    assertThat(a.stats().resolvedByStrategy()).isEmpty();
    assertThat(a.stats().conflictRecordsKept()).isZero();
  }
}
