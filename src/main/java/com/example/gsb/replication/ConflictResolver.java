package com.example.gsb.replication;

/**
 * 冲突解决策略：当同一字段出现两个并发（互不因果包含）的写入时，决定保留哪一个。
 *
 * <p>实现必须满足确定性：对同一对输入，任何节点在任何时刻都必须选出同一个胜者，
 * 否则无法保证收敛。
 */
public interface ConflictResolver {

  /** 策略名称，用于统计与冲突记录。 */
  String name();

  /**
   * 在两个并发版本之间选出胜者。
   *
   * @param key    键
   * @param field  发生冲突的字段名
   * @param local  本地版本
   * @param remote 对端版本
   * @return 被保留的版本（必须是 {@code local} 或 {@code remote} 之一）
   */
  VersionedField resolve(String key, String field, VersionedField local, VersionedField remote);
}
