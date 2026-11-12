/**
 * 数据库表结构初始化（DDL + 索引 + 幂等迁移）。
 *
 * 幂等：所有建表语句均使用 `IF NOT EXISTS`，可在每次启动时安全执行。
 * 列迁移通过 `addColumnIfNotExists` 检测后 ALTER，兼容已有数据库。
 */

import { getDb, type Db } from './connection';

/** 建表与索引的全部 DDL 语句。 */
const SCHEMA_SQL: string = `
CREATE TABLE IF NOT EXISTS services (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL UNIQUE,
  type          TEXT NOT NULL DEFAULT 'backend',
  host          TEXT NOT NULL DEFAULT '127.0.0.1',
  port          INTEGER NOT NULL,
  health_url    TEXT,
  enabled       INTEGER NOT NULL DEFAULT 1,
  sort_order    INTEGER NOT NULL DEFAULT 0,
  created_at    TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_services_type ON services(type);
CREATE INDEX IF NOT EXISTS idx_services_enabled ON services(enabled);

CREATE TABLE IF NOT EXISTS service_check_history (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  service_id      INTEGER NOT NULL,
  status          TEXT NOT NULL,
  response_ms     INTEGER,
  error_message   TEXT,
  checked_at      TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_history_service_time ON service_check_history(service_id, checked_at);

CREATE TABLE IF NOT EXISTS service_processes (
  service_id     INTEGER PRIMARY KEY,
  pid            INTEGER,
  process_status TEXT NOT NULL DEFAULT 'unknown',
  started_at     TEXT,
  stopped_at     TEXT,
  exit_code      INTEGER,
  updated_at     TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_process_status ON service_processes(process_status);

CREATE TABLE IF NOT EXISTS log_files (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  filename      TEXT NOT NULL,
  service_name  TEXT,
  file_size     INTEGER NOT NULL,
  entry_count   INTEGER NOT NULL DEFAULT 0,
  raw_content   TEXT,
  uploaded_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_log_files_service ON log_files(service_name);

CREATE TABLE IF NOT EXISTS log_entries (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id       INTEGER NOT NULL,
  line_no       INTEGER NOT NULL,
  timestamp     TEXT,
  level         TEXT,
  pid           TEXT,
  thread        TEXT,
  logger        TEXT,
  message       TEXT NOT NULL,
  stack_trace   TEXT,
  has_stack     INTEGER NOT NULL DEFAULT 0,
  raw_line      TEXT,
  FOREIGN KEY (file_id) REFERENCES log_files(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_entries_file_level ON log_entries(file_id, level);
CREATE INDEX IF NOT EXISTS idx_entries_file_time ON log_entries(file_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_entries_level ON log_entries(level);
`;

/**
 * 幂等地为指定表添加列。
 *
 * SQLite 不支持 `ADD COLUMN IF NOT EXISTS`，通过 `PRAGMA table_info` 检测列是否存在。
 *
 * @param db 数据库实例
 * @param table 表名
 * @param column 列名
 * @param definition 列定义（如 'TEXT'、'INTEGER NOT NULL DEFAULT 0'）
 */
export function addColumnIfNotExists(db: Db, table: string, column: string, definition: string): void {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name: string }>;
  const exists: boolean = columns.some((col: { name: string }): boolean => col.name === column);
  if (!exists) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  }
}

/**
 * 执行建表与索引创建，并执行幂等列迁移与进程记录回填。
 *
 * @returns 已初始化的数据库实例
 */
export function initDatabase(): Db {
  const db: Db = getDb();
  db.exec(SCHEMA_SQL);

  // 幂等列迁移：为 services 表添加进程管控字段
  addColumnIfNotExists(db, 'services', 'start_command', 'TEXT');
  addColumnIfNotExists(db, 'services', 'work_dir', 'TEXT');

  // 回填：为已有服务补充进程记录（status=unknown），INSERT OR IGNORE 保证幂等
  db.exec(
    `INSERT OR IGNORE INTO service_processes (service_id, process_status)
     SELECT id, 'unknown' FROM services`,
  );

  return db;
}
