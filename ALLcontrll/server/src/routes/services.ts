/**
 * 服务管理路由：注册表 CRUD + 健康检测 + 检测历史 + 进程启停。
 *
 * 路由前缀由 `app.ts` 挂载为 `/api/services`。
 * 数据访问逻辑内聚在本文件的 `ServiceRepository` 中（见架构文档 T03 说明）。
 */

import { Router, type Request, type Response } from 'express';
import { existsSync, statSync } from 'node:fs';
import { getDb, type Db } from '../db/connection';
import { MAX_HISTORY_PER_SERVICE } from '../config/defaults';
import { processManager } from '../services/processManager';
import { detectStartCommands, applyDetectedConfigs, type ScanStats } from '../services/startCommandDetector';
import { checkAll, checkService } from '../services/healthChecker';
import type {
  AutoDetectData,
  ApplyConfigItem,
  ApplyDetectedData,
  CheckAllData,
  CheckHistory,
  CheckResult,
  DeletedData,
  DetectResultItem,
  ProcessInfo,
  Service,
  ServiceInput,
  ServiceListData,
  ServiceRowWithStatus,
  ServiceType,
  StatusSummary,
} from '../types';
import { asyncHandler, HttpError, nowIso, sendSuccess } from '../utils/response';

/** 查询服务列表（含最近一次检测状态 + 进程状态）的 SQL。 */
const SELECT_SERVICES_WITH_STATUS: string = `
  SELECT
    s.*,
    h.status         AS status,
    h.checked_at     AS last_checked_at,
    h.response_ms    AS response_ms,
    sp.process_status AS process_status,
    sp.pid           AS pid,
    sp.started_at    AS process_started_at
  FROM services s
  LEFT JOIN (
    SELECT c.service_id, c.status, c.checked_at, c.response_ms
    FROM service_check_history c
    JOIN (
      SELECT service_id, MAX(id) AS max_id
      FROM service_check_history
      GROUP BY service_id
    ) m ON m.max_id = c.id
  ) h ON h.service_id = s.id
  LEFT JOIN service_processes sp ON sp.service_id = s.id
`;

/** 将数据库行转换为对外 Service 对象。 */
function toService(row: ServiceRowWithStatus): Service {
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    host: row.host,
    port: row.port,
    health_url: row.health_url,
    enabled: row.enabled === 1,
    sort_order: row.sort_order,
    start_command: row.start_command ?? null,
    work_dir: row.work_dir ?? null,
    created_at: row.created_at,
    updated_at: row.updated_at,
    status: row.status ?? 'unknown',
    last_checked_at: row.last_checked_at,
    response_ms: row.response_ms,
    process_status: row.process_status ?? 'unknown',
    pid: row.pid ?? null,
    process_started_at: row.process_started_at ?? null,
  };
}

/** 服务注册表数据访问层。 */
export class ServiceRepository {
  private readonly db: Db;

  constructor(db: Db = getDb()) {
    this.db = db;
  }

  /** 查询全部服务（按 sort_order 升序）。 */
  public findAll(): Service[] {
    const rows = this.db
      .prepare(`${SELECT_SERVICES_WITH_STATUS} ORDER BY s.sort_order ASC, s.id ASC`)
      .all() as ServiceRowWithStatus[];
    return rows.map(toService);
  }

  /** 查询启用检测的服务。 */
  public findEnabled(): Service[] {
    return this.findAll().filter((service: Service): boolean => service.enabled);
  }

  /** 按 id 查询单个服务，不存在返回 null。 */
  public findById(id: number): Service | null {
    const row = this.db
      .prepare(`${SELECT_SERVICES_WITH_STATUS} WHERE s.id = ?`)
      .get(id) as ServiceRowWithStatus | undefined;
    return row === undefined ? null : toService(row);
  }

  /** 按名称查询单个服务，不存在返回 null。 */
  public findByName(name: string): Service | null {
    const row = this.db
      .prepare(`${SELECT_SERVICES_WITH_STATUS} WHERE s.name = ?`)
      .get(name) as ServiceRowWithStatus | undefined;
    return row === undefined ? null : toService(row);
  }

  /** 新增服务。 */
  public create(data: Required<Pick<ServiceInput, 'name' | 'port'>> & ServiceInput): Service {
    const timestamp: string = nowIso();
    const maxRow = this.db.prepare('SELECT COALESCE(MAX(sort_order), 0) AS max_order FROM services').get() as
      | { max_order: number }
      | undefined;
    const nextOrder: number = (maxRow?.max_order ?? 0) + 1;

    const info = this.db
      .prepare(
        `INSERT INTO services (name, type, host, port, health_url, enabled, sort_order, start_command, work_dir, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        data.name,
        data.type ?? 'backend',
        data.host ?? '127.0.0.1',
        data.port,
        data.health_url ?? null,
        data.enabled === false ? 0 : 1,
        data.sort_order ?? nextOrder,
        data.start_command ?? null,
        data.work_dir ?? null,
        timestamp,
        timestamp,
      );

    const created: Service | null = this.findById(Number(info.lastInsertRowid));
    if (created === null) {
      throw new HttpError(500, '服务创建后读取失败');
    }
    return created;
  }

  /** 更新服务配置（仅更新传入的字段）。 */
  public update(id: number, data: ServiceInput): Service {
    const existing: Service | null = this.findById(id);
    if (existing === null) {
      throw new HttpError(404, `服务不存在: id=${id}`);
    }

    const merged = {
      name: data.name ?? existing.name,
      type: data.type ?? existing.type,
      host: data.host ?? existing.host,
      port: data.port ?? existing.port,
      health_url: data.health_url === undefined ? existing.health_url : data.health_url,
      enabled: (data.enabled === undefined ? existing.enabled : data.enabled) ? 1 : 0,
      sort_order: data.sort_order ?? existing.sort_order,
      start_command: data.start_command === undefined ? existing.start_command : data.start_command,
      work_dir: data.work_dir === undefined ? existing.work_dir : data.work_dir,
      updated_at: nowIso(),
      id,
    };

    this.db
      .prepare(
        `UPDATE services
         SET name = @name, type = @type, host = @host, port = @port, health_url = @health_url,
             enabled = @enabled, sort_order = @sort_order, start_command = @start_command,
             work_dir = @work_dir, updated_at = @updated_at
         WHERE id = @id`,
      )
      .run(merged);

    const updated: Service | null = this.findById(id);
    if (updated === null) {
      throw new HttpError(500, '服务更新后读取失败');
    }
    return updated;
  }

  /** 删除服务（级联删除检测历史与进程记录）。 */
  public delete(id: number): void {
    const info = this.db.prepare('DELETE FROM services WHERE id = ?').run(id);
    if (info.changes === 0) {
      throw new HttpError(404, `服务不存在: id=${id}`);
    }
  }

  /** 查询某服务的检测历史（按时间倒序）。 */
  public findHistory(serviceId: number, limit: number): CheckHistory[] {
    return this.db
      .prepare(
        `SELECT id, service_id, status, response_ms, error_message, checked_at
         FROM service_check_history
         WHERE service_id = ?
         ORDER BY id DESC
         LIMIT ?`,
      )
      .all(serviceId, limit) as CheckHistory[];
  }

  /** 批量写入检测历史，并裁剪超出上限的旧记录。 */
  public saveHistory(results: readonly CheckResult[]): void {
    if (results.length === 0) {
      return;
    }
    const insert = this.db.prepare(
      `INSERT INTO service_check_history (service_id, status, response_ms, error_message, checked_at)
       VALUES (?, ?, ?, ?, ?)`,
    );
    const prune = this.db.prepare(
      `DELETE FROM service_check_history
       WHERE service_id = ?
         AND id NOT IN (
           SELECT id FROM service_check_history WHERE service_id = ? ORDER BY id DESC LIMIT ?
         )`,
    );

    const run = this.db.transaction((records: readonly CheckResult[]): void => {
      for (const record of records) {
        insert.run(
          record.service_id,
          record.status,
          record.response_ms,
          record.error_message,
          record.checked_at,
        );
        prune.run(record.service_id, record.service_id, MAX_HISTORY_PER_SERVICE);
      }
    });

    run(results);
  }

  /** 查询某服务的进程记录，不存在返回 null。 */
  public findProcessRecord(serviceId: number): ProcessInfo | null {
    const row = this.db
      .prepare(
        `SELECT service_id, pid, process_status, started_at, stopped_at, exit_code
         FROM service_processes
         WHERE service_id = ?`,
      )
      .get(serviceId) as ProcessInfo | undefined;
    return row === undefined ? null : row;
  }

  /**
   * 插入或更新进程记录（UPSERT）。
   *
   * started_at 使用 COALESCE：调用方传 null 时保留已有值（如 crashed 场景需保留启动时间）。
   *
   * @param info 进程信息
   */
  public upsertProcessRecord(info: ProcessInfo): void {
    this.db
      .prepare(
        `INSERT INTO service_processes (service_id, pid, process_status, started_at, stopped_at, exit_code)
         VALUES (@service_id, @pid, @process_status, @started_at, @stopped_at, @exit_code)
         ON CONFLICT(service_id) DO UPDATE SET
           pid = excluded.pid,
           process_status = excluded.process_status,
           started_at = COALESCE(excluded.started_at, started_at),
           stopped_at = excluded.stopped_at,
           exit_code = excluded.exit_code,
           updated_at = datetime('now')`,
      )
      .run(info);
  }

  /** 查询所有进程状态为 running 的服务 ID（用于 admin 重启后状态修复）。 */
  public getRunningServiceIds(): number[] {
    const rows = this.db
      .prepare(`SELECT service_id FROM service_processes WHERE process_status = 'running'`)
      .all() as Array<{ service_id: number }>;
    return rows.map((row: { service_id: number }): number => row.service_id);
  }
}

/**
 * 计算服务状态汇总。
 *
 * 遍历时先判断 enabled：false 计入 paused，true 按 status 计入 online/offline/unknown。
 * health_rate = online / total * 100（保留一位小数）。
 *
 * @param services 服务列表（或仅含 status + enabled 的子集，用于检测结果汇总）
 * @returns 汇总对象
 */
export function summarize(services: readonly Pick<Service, 'status' | 'enabled'>[]): StatusSummary {
  const total: number = services.length;
  let online: number = 0;
  let offline: number = 0;
  let unknown: number = 0;
  let paused: number = 0;

  for (const service of services) {
    if (!service.enabled) {
      paused += 1;
      continue;
    }
    if (service.status === 'online') {
      online += 1;
    } else if (service.status === 'offline') {
      offline += 1;
    } else {
      unknown += 1;
    }
  }

  const healthRate: number = total === 0 ? 0 : Math.round((online / total) * 1000) / 10;
  return { total, online, offline, unknown, paused, health_rate: healthRate };
}

/** 解析并校验路径参数中的数字 id。 */
function parseId(raw: string, label: string = 'id'): number {
  const value: number = Number.parseInt(raw, 10);
  if (!Number.isInteger(value) || value <= 0) {
    throw new HttpError(400, `非法的${label}: ${raw}`);
  }
  return value;
}

/** 校验并归一化服务写入参数。 */
function normalizeServiceInput(body: unknown, requireRequired: boolean): ServiceInput {
  if (body === null || typeof body !== 'object') {
    throw new HttpError(400, '请求体必须是 JSON 对象');
  }
  const raw = body as Record<string, unknown>;
  const result: ServiceInput = {};

  if (raw.name !== undefined) {
    if (typeof raw.name !== 'string' || raw.name.trim() === '') {
      throw new HttpError(400, 'name 必须是非空字符串');
    }
    result.name = raw.name.trim();
  } else if (requireRequired) {
    throw new HttpError(400, '缺少必填字段: name');
  }

  if (raw.port !== undefined) {
    const port: number = Number(raw.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      throw new HttpError(400, 'port 必须是 1-65535 之间的整数');
    }
    result.port = port;
  } else if (requireRequired) {
    throw new HttpError(400, '缺少必填字段: port');
  }

  if (raw.type !== undefined) {
    if (raw.type !== 'backend' && raw.type !== 'frontend') {
      throw new HttpError(400, "type 只能是 'backend' 或 'frontend'");
    }
    result.type = raw.type as ServiceType;
  }

  if (raw.host !== undefined) {
    if (typeof raw.host !== 'string' || raw.host.trim() === '') {
      throw new HttpError(400, 'host 必须是非空字符串');
    }
    result.host = raw.host.trim();
  }

  if (raw.health_url !== undefined) {
    if (raw.health_url === null || (typeof raw.health_url === 'string' && raw.health_url.trim() === '')) {
      result.health_url = null;
    } else if (typeof raw.health_url === 'string') {
      result.health_url = raw.health_url.trim();
    } else {
      throw new HttpError(400, 'health_url 必须是字符串或 null');
    }
  }

  if (raw.enabled !== undefined) {
    result.enabled = raw.enabled === true || raw.enabled === 1 || raw.enabled === '1';
  }

  if (raw.sort_order !== undefined) {
    const order: number = Number(raw.sort_order);
    if (!Number.isInteger(order)) {
      throw new HttpError(400, 'sort_order 必须是整数');
    }
    result.sort_order = order;
  }

  if (raw.start_command !== undefined) {
    if (raw.start_command === null || (typeof raw.start_command === 'string' && raw.start_command.trim() === '')) {
      result.start_command = null;
    } else if (typeof raw.start_command === 'string') {
      result.start_command = raw.start_command;
    } else {
      throw new HttpError(400, 'start_command 必须是字符串或 null');
    }
  }

  if (raw.work_dir !== undefined) {
    if (raw.work_dir === null || (typeof raw.work_dir === 'string' && raw.work_dir.trim() === '')) {
      result.work_dir = null;
    } else if (typeof raw.work_dir === 'string') {
      result.work_dir = raw.work_dir;
    } else {
      throw new HttpError(400, 'work_dir 必须是字符串或 null');
    }
  }

  return result;
}

/** 创建服务管理路由。 */
export function createServicesRouter(repository: ServiceRepository = new ServiceRepository()): Router {
  const router: Router = Router();

  // GET /api/services —— 获取全部服务（含最新状态 + 进程状态）
  router.get('/', (_req: Request, res: Response): void => {
    const services: Service[] = repository.findAll();
    const data: ServiceListData = {
      services,
      summary: summarize(services),
    };
    sendSuccess(res, data);
  });

  // POST /api/services/check —— 手动检测全部服务（须置于 /:id 之前注册）
  router.post(
    '/check',
    asyncHandler(async (_req: Request, res: Response): Promise<void> => {
      const startedAt: number = Date.now();
      const services: Service[] = repository.findEnabled();
      const results: CheckResult[] = await checkAll(services);
      repository.saveHistory(results);

      // 检测结果均为 enabled 服务，map 时设 enabled:true，paused 恒为 0
      const data: CheckAllData = {
        results,
        summary: summarize(
          results.map((result: CheckResult): Pick<Service, 'status' | 'enabled'> => ({
            status: result.status,
            enabled: true,
          })),
        ),
        duration_ms: Date.now() - startedAt,
      };
      sendSuccess(res, data);
    }),
  );

  // POST /api/services —— 新增服务
  router.post('/', (req: Request, res: Response): void => {
    const input: ServiceInput = normalizeServiceInput(req.body, true);
    if (repository.findByName(input.name as string) !== null) {
      throw new HttpError(400, `服务名已存在: ${input.name}`);
    }
    const created: Service = repository.create(
      input as Required<Pick<ServiceInput, 'name' | 'port'>> & ServiceInput,
    );
    sendSuccess(res, created);
  });

  // POST /api/services/auto-detect —— 自动检测启动命令（须置于 /:id 路由之前）
  router.post('/auto-detect', (req: Request, res: Response): void => {
    const baseDir: string = String(req.body?.base_dir ?? '').trim();
    if (baseDir === '') {
      throw new HttpError(400, 'base_dir 不能为空');
    }
    if (!existsSync(baseDir)) {
      throw new HttpError(400, `目录不存在: ${baseDir}`);
    }
    const stat = statSync(baseDir);
    if (!stat.isDirectory()) {
      throw new HttpError(400, `路径不是目录: ${baseDir}`);
    }

    const services: Service[] = repository.findAll();
    const stats: ScanStats = { scanned_files: 0 };
    const results = detectStartCommands(baseDir, services, stats);
    const data: AutoDetectData = {
      results: results as DetectResultItem[],
      scanned_files: stats.scanned_files,
      matched_count: results.length,
    };
    sendSuccess(res, data);
  });

  // POST /api/services/apply-detected —— 批量应用检测配置（须置于 /:id 路由之前）
  router.post('/apply-detected', (req: Request, res: Response): void => {
    const rawConfigs: unknown = req.body?.configs;
    if (!Array.isArray(rawConfigs) || rawConfigs.length === 0) {
      throw new HttpError(400, 'configs 必须是非空数组');
    }

    const configs: ApplyConfigItem[] = rawConfigs.map(
      (item: unknown, index: number): ApplyConfigItem => {
        if (item === null || typeof item !== 'object') {
          throw new HttpError(400, `configs[${index}] 必须是 JSON 对象`);
        }
        const raw = item as Record<string, unknown>;
        const id: number = Number(raw.id);
        if (!Number.isInteger(id) || id <= 0) {
          throw new HttpError(400, `configs[${index}].id 必须是正整数`);
        }
        if (typeof raw.start_command !== 'string' || raw.start_command.trim() === '') {
          throw new HttpError(400, `configs[${index}].start_command 必须是非空字符串`);
        }
        const workDir: unknown = raw.work_dir;
        if (workDir !== null && typeof workDir !== 'string') {
          throw new HttpError(400, `configs[${index}].work_dir 必须是字符串或 null`);
        }
        return {
          id,
          start_command: raw.start_command,
          work_dir: typeof workDir === 'string' ? workDir : null,
        };
      },
    );

    const updated: Service[] = applyDetectedConfigs(configs, repository);
    const data: ApplyDetectedData = { updated };
    sendSuccess(res, data);
  });

  // PUT /api/services/:id —— 更新服务
  router.put('/:id', (req: Request, res: Response): void => {
    const id: number = parseId(req.params.id);
    const input: ServiceInput = normalizeServiceInput(req.body, false);
    if (input.name !== undefined) {
      const conflict: Service | null = repository.findByName(input.name);
      if (conflict !== null && conflict.id !== id) {
        throw new HttpError(400, `服务名已存在: ${input.name}`);
      }
    }
    sendSuccess(res, repository.update(id, input));
  });

  // DELETE /api/services/:id —— 删除服务
  router.delete('/:id', (req: Request, res: Response): void => {
    const id: number = parseId(req.params.id);
    repository.delete(id);
    const data: DeletedData = { deleted: true };
    sendSuccess(res, data);
  });

  // POST /api/services/:id/start —— 启动服务进程
  router.post('/:id/start', (req: Request, res: Response): void => {
    const id: number = parseId(req.params.id);
    const service: Service | null = repository.findById(id);
    if (service === null) {
      throw new HttpError(404, `服务不存在: id=${id}`);
    }
    const result = processManager.start(service);
    sendSuccess(res, result);
  });

  // POST /api/services/:id/stop —— 停止服务进程
  router.post('/:id/stop', (req: Request, res: Response): void => {
    const id: number = parseId(req.params.id);
    const service: Service | null = repository.findById(id);
    if (service === null) {
      throw new HttpError(404, `服务不存在: id=${id}`);
    }
    const result = processManager.stop(id);
    sendSuccess(res, result);
  });

  // POST /api/services/:id/check —— 检测单个服务
  router.post(
    '/:id/check',
    asyncHandler(async (req: Request, res: Response): Promise<void> => {
      const id: number = parseId(req.params.id);
      const service: Service | null = repository.findById(id);
      if (service === null) {
        throw new HttpError(404, `服务不存在: id=${id}`);
      }
      const result: CheckResult = await checkService(service);
      repository.saveHistory([result]);
      sendSuccess(res, result);
    }),
  );

  // GET /api/services/:id/history —— 检测历史
  router.get('/:id/history', (req: Request, res: Response): void => {
    const id: number = parseId(req.params.id);
    const rawLimit: number = Number.parseInt(String(req.query.limit ?? '50'), 10);
    const limit: number = Number.isInteger(rawLimit) && rawLimit > 0 ? Math.min(rawLimit, 500) : 50;
    if (repository.findById(id) === null) {
      throw new HttpError(404, `服务不存在: id=${id}`);
    }
    sendSuccess(res, { history: repository.findHistory(id, limit) });
  });

  return router;
}
