package com.example.gsb.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * 收敛性验证：合并操作是 join-semilattice（交换、结合、幂等），
 * 因此任意顺序的同步之后所有节点必须达到相同的最终状态。
 */
class ConvergenceTest {

  private static List<ReplicaNode> createCluster(Supplier<ConflictResolver> resolvers, int size) {
    List<ReplicaNode> nodes = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      AtomicLong clock = new AtomicLong();
      nodes.add(new ReplicaNode("N" + i, resolvers.get(), clock::incrementAndGet));
    }
    return nodes;
  }

  /** 模拟并发写入：所有节点在互不知晓的情况下写同一批键。 */
  private static void concurrentWrites(List<ReplicaNode> nodes) {
    for (int i = 0; i < nodes.size(); i++) {
      ReplicaNode node = nodes.get(i);
      node.write("user:1", "name", "name-by-" + node.id());
      node.write("user:1", "field-" + node.id(), "only-" + node.id());
      node.write("key-" + (i % 2), "shared", "v-" + node.id());
    }
  }

  private static void syncUntilConverged(List<ReplicaNode> nodes, Random random) {
    for (int round = 0; round < nodes.size(); round++) {
      List<ReplicaNode> shuffled = new ArrayList<>(nodes);
      for (int i = 0; i < shuffled.size(); i++) {
        for (int j = i + 1; j < shuffled.size(); j++) {
          if (random.nextBoolean()) {
            shuffled.get(i).syncWith(shuffled.get(j));
          } else {
            shuffled.get(j).syncWith(shuffled.get(i));
          }
        }
      }
    }
  }

  private static void assertAllConverged(List<ReplicaNode> nodes) {
    Map<String, VersionedRecord> expected = nodes.get(0).store();
    for (ReplicaNode node : nodes) {
      assertThat(node.store()).as("store of %s", node.id()).isEqualTo(expected);
    }
  }

  @Test
  void clusterConvergesAfterRandomSyncOrderWithLastWriteWins() {
    for (long seed = 0; seed < 20; seed++) {
      List<ReplicaNode> nodes = createCluster(LastWriteWinsResolver::new, 4);
      concurrentWrites(nodes);
      syncUntilConverged(nodes, new Random(seed));
      assertAllConverged(nodes);
    }
  }

  @Test
  void clusterConvergesAfterRandomSyncOrderWithNodePriority() {
    Supplier<ConflictResolver> resolver =
        () -> new NodePriorityResolver(Map.of("N0", 4, "N1", 3, "N2", 2, "N3", 1));
    for (long seed = 0; seed < 20; seed++) {
      List<ReplicaNode> nodes = createCluster(resolver, 4);
      concurrentWrites(nodes);
      syncUntilConverged(nodes, new Random(seed));
      assertAllConverged(nodes);
    }
  }

  @Test
  void differentSyncOrdersLeadToIdenticalFinalState() {
    List<Map<String, VersionedRecord>> finalStates = new ArrayList<>();
    for (long seed = 100; seed < 110; seed++) {
      List<ReplicaNode> nodes = createCluster(LastWriteWinsResolver::new, 3);
      concurrentWrites(nodes);
      syncUntilConverged(nodes, new Random(seed));
      finalStates.add(nodes.get(0).store());
    }
    for (Map<String, VersionedRecord> state : finalStates) {
      assertThat(state).isEqualTo(finalStates.get(0));
    }
  }

  @Test
  void convergencePreservesMergedFieldsAndResolvedWinners() {
    List<ReplicaNode> nodes =
        createCluster(() -> new NodePriorityResolver(Map.of("N0", 2, "N1", 1, "N2", 0)), 3);
    concurrentWrites(nodes);
    syncUntilConverged(nodes, new Random(42));

    for (ReplicaNode node : nodes) {
      assertThat(node.value("user:1", "name")).isEqualTo("name-by-N0");
      assertThat(node.value("user:1", "field-N0")).isEqualTo("only-N0");
      assertThat(node.value("user:1", "field-N1")).isEqualTo("only-N1");
      assertThat(node.value("user:1", "field-N2")).isEqualTo("only-N2");
    }
  }
}
