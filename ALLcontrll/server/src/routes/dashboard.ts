/**
 * 仪表盘路由：服务状态 + 日志维度的汇总统计。
 *
 * 路由前缀由 `app.ts` 挂载为 `/api/dashboard`。
 */

import { Router, type Request, type Response } from 'express';
import { getDb, type Db } from '../db/connection';
import { ServiceRepository, summarize } from './services';
import type {
  DashboardSummary,
  LogSummary,
  PieDatum,
  Service,
  ServiceErrorCount,
  StatusSummary,
} from '../types';
import { sendSuccess } from '../utils/response';

/** 各服务异常计数的展示条数上限。 */
const TOP_ERROR_SERVICES: number = 10;

/** 仪表盘聚合服务。 */
export class DashboardService {
  private readonly db: Db;
  private readonly serviceRepository: ServiceRepository;

  constructor(db: Db = getDb(), serviceRepository: ServiceRepository = new ServiceRepository()) {
    this.db = db;
    this.serviceRepository = serviceRepository;
  }

  /** 统计服务在线/离线/未知/已暂停数量。 */
  public countServiceStatus(): StatusSummary {
    const services: Service[] = this.serviceRepository.findAll();
    return summarize(services);
  }

  /** 统计日志文件与条目维度的汇总数据。 */
  public countLogs(): LogSummary {
    const fileRow = this.db.prepare('SELECT COUNT(*) AS total FROM log_files').get() as
      | { total: number }
      | undefined;
    const entryRow = this.db
      .prepare(
        `SELECT
           COUNT(*) AS total,
           SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END) AS error_count,
           SUM(CASE WHEN level = 'WARN' THEN 1 ELSE 0 END) AS warn_count
         FROM log_entries`,
      )
      .get() as { total: number; error_count: number | null; warn_count: number | null } | undefined;

    return {
      total_files: fileRow?.total ?? 0,
      total_entries: entryRow?.total ?? 0,
      error_count: entryRow?.error_count ?? 0,
      warn_count: entryRow?.warn_count ?? 0,
    };
  }

  /** 按服务（无关联服务名时回退为文件名）统计 ERROR 条目数。 */
  public getServiceErrorCounts(): ServiceErrorCount[] {
    return this.db
      .prepare(
        `SELECT
           COALESCE(NULLIF(f.service_name, ''), f.filename) AS service_name,
           COUNT(e.id) AS error_count
         FROM log_files f
         JOIN log_entries e ON e.file_id = f.id AND e.level = 'ERROR'
         GROUP BY COALESCE(NULLIF(f.service_name, ''), f.filename)
         HAVING error_count > 0
         ORDER BY error_count DESC
         LIMIT ?`,
      )
      .all(TOP_ERROR_SERVICES) as ServiceErrorCount[];
  }

  /** 构造健康率饼图数据（数量为 0 的分片不展示）。 */
  public buildHealthPie(status: StatusSummary): PieDatum[] {
    const candidates: PieDatum[] = [
      { name: '在线', value: status.online },
      { name: '离线', value: status.offline },
      { name: '已暂停', value: status.paused },
      { name: '未知', value: status.unknown },
    ];
    return candidates.filter((item: PieDatum): boolean => item.value > 0);
  }

  /** 汇总仪表盘全部数据。 */
  public getSummary(): DashboardSummary {
    const serviceStatus: StatusSummary = this.countServiceStatus();
    return {
      service_status: serviceStatus,
      log_summary: this.countLogs(),
      service_health_pie: this.buildHealthPie(serviceStatus),
      service_error_counts: this.getServiceErrorCounts(),
    };
  }
}

/** 创建仪表盘路由。 */
export function createDashboardRouter(service: DashboardService = new DashboardService()): Router {
  const router: Router = Router();

  // GET /api/dashboard/summary —— 仪表盘汇总
  router.get('/summary', (_req: Request, res: Response): void => {
    sendSuccess(res, service.getSummary());
  });

  return router;
}
