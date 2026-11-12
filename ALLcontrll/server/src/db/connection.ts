/**
 * 数据库连接单例（基于 Node 22 内置 node:sqlite）。
 *
 * 采用同步 API，无需 ORM；开启 WAL 提升并发读性能，开启外键约束以支持级联删除。
 * 通过兼容包装器提供 better-sqlite3 风格的 pragma() 和 transaction() 方法，
 * 使上层 Repository 代码无需修改。
 */

import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync, type StatementSync } from 'node:sqlite';
import { DB_PATH } from '../config/defaults';

/** 数据库实例类型（附带 pragma / transaction 兼容方法）。 */
export type Db = DatabaseSync & {
  /** better-sqlite3 兼容：执行 PRAGMA 语句。 */
  pragma(statement: string): void;
  /** better-sqlite3 兼容：将函数包装在事务中执行。 */
  transaction<T extends (...args: any[]) => any>(fn: T): T;
};

/** 预编译语句类型别名（供其它模块复用）。 */
export type Stmt = StatementSync;

/** 进程内唯一的数据库实例。 */
let instance: Db | null = null;

/** 确保数据库文件所在目录存在。 */
function ensureDirectory(filePath: string): void {
  const dir: string = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

/**
 * 为 DatabaseSync 实例添加 better-sqlite3 兼容方法。
 *
 * - pragma():  通过 exec('PRAGMA ...') 实现
 * - transaction(): 手动 BEGIN / COMMIT / ROLLBACK 包装
 *
 * @param raw  原始 DatabaseSync 实例
 * @returns     附带兼容方法的数据库实例
 */
function wrapDatabase(raw: DatabaseSync): Db {
  const db = raw as Db;

  db.pragma = (statement: string): void => {
    raw.exec(`PRAGMA ${statement}`);
  };

  db.transaction = <T extends (...args: any[]) => any>(fn: T): T => {
    return ((...args: any[]) => {
      raw.exec('BEGIN');
      try {
        const result = fn(...args);
        raw.exec('COMMIT');
        return result;
      } catch (err) {
        try {
          raw.exec('ROLLBACK');
        } catch {
          /* 忽略 ROLLBACK 本身的错误，优先抛出原始异常 */
        }
        throw err;
      }
    }) as T;
  };

  return db;
}

/**
 * 获取数据库连接单例（首次调用时创建）。
 *
 * @returns 数据库实例
 */
export function getDb(): Db {
  if (instance !== null) {
    return instance;
  }
  ensureDirectory(DB_PATH);
  const raw: DatabaseSync = new DatabaseSync(DB_PATH);
  const db: Db = wrapDatabase(raw);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA foreign_keys = ON');
  instance = db;
  return instance;
}

/** 关闭数据库连接（进程退出时调用）。 */
export function closeDb(): void {
  if (instance !== null) {
    instance.close();
    instance = null;
  }
}
