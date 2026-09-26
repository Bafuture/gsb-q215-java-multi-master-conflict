package com.example.gsb.replica;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个字段的调和状态：一组互不支配（互为并发）的“兄弟版本”加一个当前胜出版本。
 *
 * <p>保留全部并发原始版本（而不只是赢家）是收敛性的关键：两个兄弟先调和后产生的合并时钟，
 * 可能在数值上支配第三个与它们各自并发的版本；若只留赢家会把第三个版本误判为旧值丢弃，
 * 导致最终结果依赖同步顺序。调和采用集合运算（并集 → 剪除存在因果祖先的版本 → 策略选优），
 * 满足结合律、交换律与幂等律，因此任意同步顺序都收敛到同一状态。
 */
final class FieldEntry {

    private final List<VersionedValue> origins;
    private final VersionedValue current;
    private final VersionedValue winnerOrigin;

    FieldEntry(VersionedValue sole) {
        this(List.of(sole), sole, sole);
    }

    private FieldEntry(List<VersionedValue> origins, VersionedValue winnerOrigin, VersionedValue current) {
        this.origins = List.copyOf(origins);
        this.current = current;
        this.winnerOrigin = winnerOrigin;
    }

    List<VersionedValue> origins() {
        return origins;
    }

    VersionedValue current() {
        return current;
    }

    /** 当前胜出值所对应的原始写入版本（未携带合并时钟），用于冲突记录识别被覆盖方。 */
    VersionedValue winnerOrigin() {
        return winnerOrigin;
    }

    /**
     * 把对端同一字段的兄弟版本集合并进来。
     *
     * @return 调和结果；{@code addedConcurrent} 为新发现的、与现有版本并发的原始版本
     *         （每个都构成一次冲突），{@code changed} 表示字段状态是否发生变化
     */
    Reconciliation reconcile(List<VersionedValue> incoming, ConflictResolutionStrategy strategy) {
        List<VersionedValue> merged = new ArrayList<>(origins);
        List<VersionedValue> addedConcurrent = new ArrayList<>();
        boolean changed = false;

        for (VersionedValue candidate : incoming) {
            if (merged.contains(candidate)) {
                continue; // 同一原始写入的重复同步，幂等。
            }
            boolean dominatedByExisting = false;
            List<VersionedValue> dominatedByCandidate = new ArrayList<>();
            boolean concurrentWithAny = false;
            for (VersionedValue existing : merged) {
                VectorClock.Relation relation = candidate.version().relationTo(existing.version());
                if (relation == VectorClock.Relation.BEFORE || relation == VectorClock.Relation.EQUAL) {
                    dominatedByExisting = true;
                    break;
                }
                if (relation == VectorClock.Relation.AFTER) {
                    dominatedByCandidate.add(existing);
                } else {
                    concurrentWithAny = true;
                }
            }
            if (dominatedByExisting) {
                continue; // 候选是因果上的旧版本，忽略。
            }
            // 与至少一个存活兄弟并发即为一次新冲突（它也可能同时是另一些兄弟的因果后继）。
            if (concurrentWithAny) {
                addedConcurrent.add(candidate);
            }
            merged.removeAll(dominatedByCandidate);
            merged.add(candidate);
            changed = true;
        }

        if (!changed) {
            return new Reconciliation(this, List.of(), false);
        }

        VersionedValue winner = merged.get(0);
        VectorClock mergedClock = winner.version().vectorClock();
        for (int i = 1; i < merged.size(); i++) {
            VersionedValue candidate = merged.get(i);
            winner = pickWinner(winner, candidate, strategy);
            mergedClock = mergedClock.merge(candidate.version().vectorClock());
        }
        Version currentVersion = new Version(
                mergedClock, winner.version().nodeId(), winner.version().wallTimestampMillis());
        FieldEntry next = new FieldEntry(merged, winner, winner.withVersion(currentVersion));
        return new Reconciliation(next, List.copyOf(addedConcurrent), true);
    }

    private static VersionedValue pickWinner(VersionedValue currentWinner, VersionedValue candidate,
                                             ConflictResolutionStrategy strategy) {
        Version chosen = strategy.resolve(currentWinner.version(), candidate.version());
        return chosen == currentWinner.version() ? currentWinner : candidate;
    }

    record Reconciliation(FieldEntry entry, List<VersionedValue> addedConcurrent, boolean changed) {
    }
}
