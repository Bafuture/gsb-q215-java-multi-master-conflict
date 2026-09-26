package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConflictRecordAndStatsTest {

    private ReplicaNode node(String id, long baseTime) {
        long[] clock = {baseTime};
        return new ReplicaNode(id, new TimestampWinsStrategy(), () -> clock[0] += 10L, 100);
    }

    @Test
    void conflictRecordKeepsOverwrittenValueVersionsReasonAndStrategy() {
        ReplicaNode a = node("A", 1_000);
        ReplicaNode b = node("B", 2_000);

        a.writeField("k", "f", "old");
        b.syncFrom(a);
        a.writeField("k", "f", "from-A");
        b.writeField("k", "f", "from-B"); // B 时间戳更大，按时间戳策略应胜出

        ReplicaNode.SyncResult result = a.syncFrom(b);

        assertThat(a.get("k", "f")).isEqualTo("from-B");
        assertThat(result.conflictsResolved()).isEqualTo(1);
        assertThat(a.conflictLog().size()).isEqualTo(1);

        ConflictRecord record = a.conflictLog().records().get(0);
        assertThat(record.key()).isEqualTo("k");
        assertThat(record.field()).isEqualTo("f");
        assertThat(record.winner().value()).isEqualTo("from-B");
        assertThat(record.loser().value()).isEqualTo("from-A");
        assertThat(record.strategy()).isEqualTo(TimestampWinsStrategy.NAME);
        assertThat(record.reason()).isEqualTo(ConflictRecord.REASON_CONCURRENT_WRITES);
        assertThat(record.occurredAtMillis()).isPositive();
        // 落选值的节点标识与版本元数据被完整保留，可供人工排查。
        assertThat(record.loser().version().nodeId()).isEqualTo("A");
    }

    @Test
    void repeatedSyncDoesNotDuplicateConflictAndStatsReflectRounds() {
        ReplicaNode a = node("A", 1_000);
        ReplicaNode b = node("B", 2_000);

        a.writeField("k", "f", "from-A");
        b.writeField("k", "f", "from-B");

        a.syncFrom(b);
        long firstRoundConflicts = a.stats().conflicts();
        a.syncFrom(b);
        b.syncFrom(a);

        assertThat(firstRoundConflicts).isEqualTo(1);
        assertThat(a.stats().conflicts()).isEqualTo(1);
        // B 在反向同步时首次见到 A 的并发版本，同样独立解决并记录一次。
        assertThat(b.stats().conflicts()).isEqualTo(1);
        assertThat(a.conflictLog().size()).isEqualTo(1);
        assertThat(b.conflictLog().size()).isEqualTo(1);

        // 再重复同步：同步轮次增加，但双方都不应再产生新冲突（幂等）。
        a.syncFrom(b);
        b.syncFrom(a);
        assertThat(a.stats().syncRounds()).isEqualTo(3);
        assertThat(b.stats().syncRounds()).isEqualTo(2);
        assertThat(a.stats().conflicts()).isEqualTo(1);
        assertThat(b.stats().conflicts()).isEqualTo(1);

        SyncStats.StatsSnapshot snapshot = a.stats().snapshot(a.conflictLog().size());
        assertThat(snapshot.syncRounds()).isEqualTo(3);
        assertThat(snapshot.conflicts()).isEqualTo(1);
        assertThat(snapshot.resolvedByStrategy()).containsEntry(TimestampWinsStrategy.NAME, 1L);
        assertThat(snapshot.retainedConflictRecords()).isEqualTo(1);
    }

    @Test
    void statsTrackFieldMergesAndPerStrategyResolutionCounts() {
        ReplicaNode a = node("A", 1_000);
        ReplicaNode b = node("B", 2_000);

        a.write("doc", Map.of("title", "t", "body", "x"));
        b.syncFrom(a);
        a.writeField("doc", "body", "xa");
        b.writeField("doc", "body", "xb");
        b.writeField("doc", "tag", "zz");

        ReplicaNode.SyncResult result = a.syncFrom(b);

        assertThat(result.conflictsResolved()).isEqualTo(1);
        assertThat(result.fieldsMerged()).isEqualTo(1);
        assertThat(a.stats().conflicts()).isEqualTo(1);
        assertThat(a.stats().resolvedByStrategy(TimestampWinsStrategy.NAME)).isEqualTo(1);
        assertThat(a.stats().fieldMerges()).isEqualTo(1);
        assertThat(a.conflictLog().size()).isEqualTo(1);
    }

    @Test
    void nodePriorityStrategyProducesDifferentWinnerAndItsOwnStatBucket() {
        NodePriorityStrategy strategy = new NodePriorityStrategy(Map.of("low", 1, "high", 9));
        ReplicaNode low = new ReplicaNode("low", strategy, () -> 5_000L, 10);
        ReplicaNode high = new ReplicaNode("high", strategy, () -> 1_000L, 10);

        low.writeField("k", "f", "low-value");   // 时间戳更大但优先级低
        high.writeField("k", "f", "high-value"); // 时间戳更小但优先级高

        low.syncFrom(high);

        assertThat(low.get("k", "f")).isEqualTo("high-value");
        assertThat(low.stats().resolvedByStrategy(NodePriorityStrategy.NAME)).isEqualTo(1);
        assertThat(low.conflictLog().records().get(0).strategy())
                .isEqualTo(NodePriorityStrategy.NAME);
    }

    @Test
    void conflictLogRespectsCapacityByDroppingOldest() {
        ReplicaNode a = node("A", 1_000);
        ReplicaNode b = node("B", 2_000);
        a.writeField("seed", "f", "s");
        b.syncFrom(a);

        ReplicaNode capped = new ReplicaNode("C", new TimestampWinsStrategy(), () -> 9L, 2);
        capped.syncFrom(a);
        for (int i = 0; i < 4; i++) {
            a.writeField("k" + i, "f", "a" + i);
            b.writeField("k" + i, "f", "b" + i);
            capped.syncFrom(a);
            capped.syncFrom(b);
        }
        assertThat(capped.conflictLog().size()).isEqualTo(2);
    }
}
