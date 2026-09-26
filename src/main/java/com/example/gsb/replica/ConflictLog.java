package com.example.gsb.replica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 冲突日志：保留每个被策略裁决覆盖的版本现场。
 * 默认不限容量；可通过构造参数限制保留条数（超出后丢弃最旧记录）。
 */
public final class ConflictLog {

    private final int capacity;
    private final List<ConflictRecord> records = new ArrayList<>();

    public ConflictLog() {
        this(Integer.MAX_VALUE);
    }

    /** @param capacity 最多保留多少条记录，必须为正数；超出后丢弃最旧的。 */
    public ConflictLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    public synchronized void append(ConflictRecord record) {
        records.add(record);
        while (records.size() > capacity) {
            records.remove(0);
        }
    }

    /** 返回当前保留的冲突记录（不可变快照，按发生先后排列）。 */
    public synchronized List<ConflictRecord> records() {
        return List.copyOf(records);
    }

    public synchronized int size() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
    }
}
