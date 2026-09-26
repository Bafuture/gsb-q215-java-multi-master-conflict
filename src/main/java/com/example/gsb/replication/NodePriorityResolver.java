package com.example.gsb.replication;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按节点优先级取高：优先级数值大的节点写入获胜，与物理时钟无关。
 *
 * <p>未配置优先级的节点视为 0。优先级相同则按节点标识字典序取大，
 * 保证所有节点得到一致的确定性结果。
 */
public final class NodePriorityResolver implements ConflictResolver {

  public static final String NAME = "node-priority";

  private final Map<String, Integer> priorities;

  public NodePriorityResolver(Map<String, Integer> priorities) {
    this.priorities = Collections.unmodifiableMap(new HashMap<>(priorities));
  }

  public int priorityOf(String nodeId) {
    return priorities.getOrDefault(nodeId, 0);
  }

  public Map<String, Integer> priorities() {
    return priorities;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public VersionedField resolve(String key, String field, VersionedField local, VersionedField remote) {
    int localPriority = priorityOf(local.nodeId());
    int remotePriority = priorityOf(remote.nodeId());
    if (localPriority != remotePriority) {
      return localPriority > remotePriority ? local : remote;
    }
    int byNode = local.nodeId().compareTo(remote.nodeId());
    if (byNode != 0) {
      return byNode > 0 ? local : remote;
    }
    return local.value().compareTo(remote.value()) >= 0 ? local : remote;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NodePriorityResolver)) {
      return false;
    }
    NodePriorityResolver that = (NodePriorityResolver) o;
    return priorities.equals(that.priorities);
  }

  @Override
  public int hashCode() {
    return Objects.hash(priorities);
  }
}
