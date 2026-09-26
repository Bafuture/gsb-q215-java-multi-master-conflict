package com.example.gsb.replica;

/**
 * 字段级冲突解决策略。仅当两个版本经向量时钟判定为<b>并发</b>时才会被调用：
 * 有明确先后关系的写入永远是“新者胜”，无需策略参与。
 *
 * <p>实现必须是确定性的：相同的两个并发版本，无论在哪个节点、以什么同步顺序执行，
 * 都必须选出同一个赢家——这是多主复制收敛性（最终所有节点状态一致）的前提。
 */
@FunctionalInterface
public interface ConflictResolutionStrategy {

    /**
     * 在两个并发版本之间选出赢家。
     *
     * @param local  本地当前版本
     * @param remote 同步对端带来的版本
     * @return 获胜的版本（必须是入参之一）
     */
    Version resolve(Version local, Version remote);

    /** 策略名称，用于统计“按策略解决次数”。 */
    default String name() {
        return getClass().getSimpleName();
    }
}
