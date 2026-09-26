package com.example.gsb.replication;

/**
 * 两个版本之间的因果关系。
 */
public enum VersionRelation {
  /** 本版本严格先于对方（对方是本版本的后继）。 */
  BEFORE,
  /** 本版本严格晚于对方（本版本是对方的后继）。 */
  AFTER,
  /** 两个版本完全相同。 */
  EQUAL,
  /** 两个版本互不包含，属于并发写入，即冲突。 */
  CONCURRENT
}
