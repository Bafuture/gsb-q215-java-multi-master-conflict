package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 收敛性验证：相同的分区写入历史，无论按什么顺序/方向同步，所有节点最终状态必须一致。
 */
class ConvergenceTest {

    /** 同步编排方式。 */
    enum Ordering {
        RING,
        RANDOM,
        STAR
    }

    static List<Object[]> scenarios() {
        // 每种策略 × 每种同步顺序都必须收敛。
        List<Object[]> rows = new ArrayList<>();
        for (ConflictResolutionStrategy strategy : List.of(
                new TimestampWinsStrategy(),
                new NodePriorityStrategy(Map.of("A", 1, "B", 2, "C", 3, "D", 4)))) {
            for (Ordering ordering : Ordering.values()) {
                rows.add(new Object[] {strategy, ordering});
            }
        }
        return rows;
    }

    @ParameterizedTest(name = "[{index}] strategy={0}, ordering={1}")
    @MethodSource("scenarios")
    void allNodesConvergeRegardlessOfSyncOrder(ConflictResolutionStrategy strategy, Ordering ordering) {
        // 每个节点独立的确定性“墙上时钟”：起点错开，且每次取用时递增，模拟有偏差的物理时钟。
        long[] clocks = {1_000, 2_000, 3_000, 4_000};
        List<ReplicaNode> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int idx = i;
            LongSupplier wallClock = () -> clocks[idx] += 7L;
            nodes.add(new ReplicaNode(String.valueOf((char) ('A' + i)), strategy, wallClock, 10_000));
        }
        ReplicaNode a = nodes.get(0);
        ReplicaNode b = nodes.get(1);
        ReplicaNode c = nodes.get(2);
        ReplicaNode d = nodes.get(3);

        // 1) 共同祖先：所有节点先同步到同一条基线写入。
        a.write("user:1", Map.of("name", "alice", "city", "BJ"));
        b.syncFrom(a);
        c.syncFrom(b);
        d.syncFrom(c);

        // 2) 一条因果链：D 基于已知状态修改 -> C -> B（之后 A 与它们分区）。
        d.writeField("user:1", "name", "alice-d");
        c.syncFrom(d);
        c.writeField("user:1", "city", "SH");
        b.syncFrom(c);

        // 3) 分区期间，四个节点分别并发修改同一字段 city（含三方/四方并发最坏情况）。
        a.writeField("user:1", "city", "city-A");
        b.writeField("user:1", "city", "city-B");
        c.writeField("user:1", "city", "city-C");
        d.writeField("user:1", "city", "city-D2");

        // 4) 同一键不同字段的并发修改，必须无冲突合并进最终状态。
        a.writeField("user:1", "email", "a@x.com");
        b.writeField("user:1", "phone", "110");
        c.writeField("k2", "f", "vC");
        d.writeField("k2", "g", "vD");

        // 5) 按指定顺序/方向完成同步。
        reconcile(nodes, ordering);

        // 6) 收敛断言：所有节点快照完全一致。
        Map<String, Map<String, String>> expected = a.snapshot();
        for (ReplicaNode node : nodes) {
            assertThat(node.snapshot())
                    .as("节点 %s 应收敛到统一状态", node.nodeId())
                    .isEqualTo(expected);
        }
        // 不同字段合并生效；并发同字段按策略只留一个确定值。
        assertThat(expected.get("user:1"))
                .containsEntry("name", "alice-d")
                .containsEntry("email", "a@x.com")
                .containsEntry("phone", "110")
                .hasSize(4);
        assertThat(expected.get("k2"))
                .containsEntry("f", "vC")
                .containsEntry("g", "vD");

        // 7) 幂等性：再来一轮任意同步，状态不再变化，也不再产生新冲突。
        long conflictsBefore = totalConflicts(nodes);
        reconcile(nodes, Ordering.RANDOM);
        for (ReplicaNode node : nodes) {
            assertThat(node.snapshot()).isEqualTo(expected);
        }
        assertThat(totalConflicts(nodes)).as("重复同步不应重复计冲突").isEqualTo(conflictsBefore);
    }

    private static void reconcile(List<ReplicaNode> nodes, Ordering ordering) {
        switch (ordering) {
            case RING -> {
                for (int pass = 0; pass < 3; pass++) {
                    for (int i = 0; i < nodes.size(); i++) {
                        nodes.get(i).syncWith(nodes.get((i + 1) % nodes.size()));
                    }
                }
            }
            case STAR -> {
                ReplicaNode hub = nodes.get(0);
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 1; i < nodes.size(); i++) {
                        hub.syncWith(nodes.get(i));
                    }
                }
            }
            case RANDOM -> {
                Random rnd = new Random(20260926L);
                for (int round = 0; round < 60; round++) {
                    int x = rnd.nextInt(nodes.size());
                    int y = rnd.nextInt(nodes.size());
                    if (x != y) {
                        ReplicaNode source = nodes.get(y);
                        ReplicaNode target = nodes.get(x);
                        if (rnd.nextBoolean()) {
                            target.syncFrom(source);
                        } else {
                            target.syncWith(source);
                        }
                    }
                }
            }
        }
    }

    private static long totalConflicts(List<ReplicaNode> nodes) {
        long total = 0;
        for (ReplicaNode node : nodes) {
            total += node.stats().conflicts();
        }
        return total;
    }
}
