package com.example.gsb.replica;

import java.util.Map;
import java.util.Objects;

/**
 * “按节点优先级取高”：发起写入的节点优先级数字更大的版本胜出。
 *
 * <p>优先级表必须是集群范围一致、静态的配置（所有节点使用同一张表），
 * 否则不同节点会选出不同赢家，破坏收敛性。优先级相同时以节点标识字典序兜底，
 * 保证裁决确定、与同步顺序/方向无关。
 */
public final class NodePriorityStrategy implements ConflictResolutionStrategy {

    public static final String NAME = "NODE_PRIORITY";

    private final Map<String, Integer> priorities;

    public NodePriorityStrategy(Map<String, Integer> priorities) {
        this.priorities = Map.copyOf(Objects.requireNonNull(priorities, "priorities"));
    }

    @Override
    public Version resolve(Version local, Version remote) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        int pl = priorities.getOrDefault(local.nodeId(), 0);
        int pr = priorities.getOrDefault(remote.nodeId(), 0);
        if (pl != pr) {
            return pl > pr ? local : remote;
        }
        return local.nodeId().compareTo(remote.nodeId()) <= 0 ? local : remote;
    }

    @Override
    public String name() {
        return NAME;
    }
}
