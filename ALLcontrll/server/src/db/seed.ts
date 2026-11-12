/**
 * 默认服务注册表种子数据写入。
 *
 * 仅在 `services` 表为空（首次启动）时写入 15 条默认记录，
 * 避免覆盖用户后续的自定义配置。
 *
 * 注：种子数据的 start_command / work_dir 留空（null），由用户在 UI 自行配置。
 */

import { getDb, type Db, type Stmt } from './connection';
import { DEFAULT_SERVICES, type DefaultServiceSeed } from '../config/defaults';
import { nowIso } from '../utils/response';

/** 统计行数的查询结果。 */
interface CountRow {
  count: number;
}

/**
 * 首次启动时写入默认服务注册表。
 *
 * @returns 实际插入的记录数（表非空时返回 0）
 */
export function seedDatabase(): number {
  const db: Db = getDb();
  const countRow = db.prepare('SELECT COUNT(*) AS count FROM services').get() as CountRow | undefined;
  const existing: number = countRow?.count ?? 0;
  if (existing > 0) {
    return 0;
  }

  const timestamp: string = nowIso();
  const insert: Stmt = db.prepare(`
    INSERT INTO services (name, type, host, port, health_url, enabled, sort_order, start_command, work_dir, created_at, updated_at)
    VALUES (@name, @type, @host, @port, @health_url, @enabled, @sort_order, @start_command, @work_dir, @created_at, @updated_at)
  `);

  const insertMany = db.transaction((rows: readonly DefaultServiceSeed[]): void => {
    for (const row of rows) {
      insert.run({
        ...row,
        start_command: row.start_command ?? null,
        work_dir: row.work_dir ?? null,
        created_at: timestamp,
        updated_at: timestamp,
      });
    }
  });

  insertMany(DEFAULT_SERVICES);
  return DEFAULT_SERVICES.length;
}
