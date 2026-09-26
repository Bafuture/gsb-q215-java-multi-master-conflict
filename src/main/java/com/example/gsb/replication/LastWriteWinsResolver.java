package com.example.gsb.replication;

/**
 * 按物理时间戳取新（Last-Write-Wins）：时间戳较大的写入获胜；
 * 时间戳相同则按节点标识字典序取大，保证确定性。
 *
 * <p><b>风险提示</b>：该策略依赖各节点的物理时钟。多主环境下机器时钟存在
 * 漂移（clock skew），NTP 同步也只能把误差控制在有限范围内。当两次并发写入
 * 的时间间隔小于时钟误差时，"较新"的时间戳未必对应真实发生顺序，可能静默
 * 丢弃用户眼中更新的数据。对数据丢失敏感的场景应优先考虑
 * {@link NodePriorityResolver} 或应用层合并。
 */
public final class LastWriteWinsResolver implements ConflictResolver {

  public static final String NAME = "last-write-wins";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public VersionedField resolve(String key, String field, VersionedField local, VersionedField remote) {
    if (local.timestamp() != remote.timestamp()) {
      return local.timestamp() > remote.timestamp() ? local : remote;
    }
    int byNode = local.nodeId().compareTo(remote.nodeId());
    if (byNode != 0) {
      return byNode > 0 ? local : remote;
    }
    return local.value().compareTo(remote.value()) >= 0 ? local : remote;
  }
}
