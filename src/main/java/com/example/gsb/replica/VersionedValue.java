package com.example.gsb.replica;

import java.util.Objects;

/**
 * 带版本的值。键下的每个字段各持有一个 {@code VersionedValue}，
 * 这使得“同一键、不同字段被分别修改”可以按字段合并而非整体覆盖。
 * 不可变。
 */
public record VersionedValue(String value, Version version) {

    public VersionedValue {
        Objects.requireNonNull(version, "version");
    }

    /** 冲突解决后保留胜出版本的值、但携带合并后的时钟（包含双方因果历史）。 */
    public VersionedValue withVersion(Version newVersion) {
        return new VersionedValue(value, newVersion);
    }
}
