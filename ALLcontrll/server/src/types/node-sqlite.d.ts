/**
 * node:sqlite 类型声明（Node 22+ 实验性内置模块）。
 *
 * @types/node@20 不含此模块类型，此处提供最小声明以支持 TypeScript 编译。
 * 运行时需添加 --experimental-sqlite flag。
 */
declare module 'node:sqlite' {
  /** run() 方法返回值。 */
  export interface RunResult {
    changes: number;
    lastInsertRowid: number | bigint;
  }

  /** 预编译语句（同步 API）。 */
  export class StatementSync {
    /** 获取首行（无匹配返回 undefined）。 */
    get(...params: unknown[]): unknown;
    /** 获取全部行。 */
    all(...params: unknown[]): unknown[];
    /** 执行写操作，返回变更信息。 */
    run(...params: unknown[]): RunResult;
  }

  /** DatabaseSync 构造选项。 */
  export interface DatabaseSyncOptions {
    openAsBlob?: boolean;
    enableForeignKeyConstraints?: boolean;
    enableDoubleQuotedStringLiterals?: boolean;
    allowExtension?: boolean;
  }

  /** SQLite 数据库同步连接。 */
  export class DatabaseSync {
    constructor(path: string, options?: DatabaseSyncOptions);
    /** 执行原始 SQL（不含参数绑定）。 */
    exec(sql: string): void;
    /** 预编译 SQL 语句。 */
    prepare(sql: string): StatementSync;
    /** 关闭数据库连接。 */
    close(): void;
  }
}
