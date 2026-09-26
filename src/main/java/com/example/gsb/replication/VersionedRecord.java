package com.example.gsb.replication;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一个键对应的记录：字段名 → 带版本的字段值。
 *
 * <p>字段各自携带独立版本，因此同一键的不同字段可以被不同节点分别修改后合并，
 * 而不是整记录覆盖。
 */
public final class VersionedRecord {

  private final String key;
  private final Map<String, VersionedField> fields;

  public VersionedRecord(String key, Map<String, VersionedField> fields) {
    this.key = Objects.requireNonNull(key, "key");
    this.fields = Collections.unmodifiableMap(new HashMap<>(fields));
  }

  public static VersionedRecord empty(String key) {
    return new VersionedRecord(key, Map.of());
  }

  public String key() {
    return key;
  }

  public Map<String, VersionedField> fields() {
    return fields;
  }

  public VersionedField field(String name) {
    return fields.get(name);
  }

  /** 返回替换/新增一个字段后的新记录。 */
  public VersionedRecord withField(String name, VersionedField field) {
    Map<String, VersionedField> next = new HashMap<>(fields);
    next.put(name, field);
    return new VersionedRecord(key, next);
  }

  /** 记录级时钟 = 所有字段时钟的 join，用于快速判断整记录的因果关系。 */
  public VectorClock clock() {
    VectorClock clock = new VectorClock();
    for (VersionedField field : fields.values()) {
      clock = clock.merge(field.clock());
    }
    return clock;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VersionedRecord)) {
      return false;
    }
    VersionedRecord that = (VersionedRecord) o;
    return key.equals(that.key) && fields.equals(that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, fields);
  }

  @Override
  public String toString() {
    return "VersionedRecord{key=" + key + ", fields=" + fields + "}";
  }
}
