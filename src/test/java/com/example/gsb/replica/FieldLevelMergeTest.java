package com.example.gsb.replica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldLevelMergeTest {

    private ReplicaNode node(String id) {
        // 固定墙上时间，避免本测试受真实时钟影响。
        return new ReplicaNode(id, new TimestampWinsStrategy(), () -> 1_000L, 100);
    }

    @Test
    void concurrentWritesToDifferentFieldsOfSameKeyMergeInsteadOfOverwriting() {
        ReplicaNode a = node("A");
        ReplicaNode b = node("B");

        a.write("user:1", Map.of("name", "alice", "city", "BJ"));
        b.syncFrom(a);

        // 分区期间两边改同一键的不同字段。
        a.writeField("user:1", "city", "SH");
        b.writeField("user:1", "email", "a@x.com");

        ReplicaNode.SyncResult result = a.syncFrom(b);

        assertThat(a.getKey("user:1")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "name", "alice",
                "city", "SH",
                "email", "a@x.com"));
        assertThat(result.conflictsResolved()).isZero();
        assertThat(result.fieldsMerged()).isEqualTo(1);
        assertThat(a.conflictLog().records()).isEmpty();
    }

    @Test
    void causallyOrderedWriteToSameFieldTakesNewerWithoutConflict() {
        ReplicaNode a = node("A");
        ReplicaNode b = node("B");

        a.writeField("k", "f", "v1");
        b.syncFrom(a);
        b.writeField("k", "f", "v2");
        ReplicaNode.SyncResult result = a.syncFrom(b);

        assertThat(a.get("k", "f")).isEqualTo("v2");
        assertThat(result.conflictsResolved()).isZero();
        assertThat(a.conflictLog().records()).isEmpty();
    }

    @Test
    void bothDirectionsEndWithMergedKey() {
        ReplicaNode a = node("A");
        ReplicaNode b = node("B");

        a.writeField("user:1", "name", "alice");
        b.writeField("user:1", "email", "a@x.com");

        a.syncWith(b);

        assertThat(a.getKey("user:1")).containsExactlyInAnyOrderEntriesOf(
                Map.of("name", "alice", "email", "a@x.com"));
        assertThat(b.getKey("user:1")).containsExactlyInAnyOrderEntriesOf(
                Map.of("name", "alice", "email", "a@x.com"));
    }
}
