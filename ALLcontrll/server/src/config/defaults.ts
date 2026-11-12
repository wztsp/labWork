/**
 * 全局配置常量与默认服务注册表种子数据。
 *
 * 所有可调参数集中在此，支持通过环境变量覆盖（见 `.env.example`）。
 */

import path from 'node:path';
import type { ServiceType } from '../types';

/** server/ 目录的绝对路径（本文件位于 server/src/config 或 server/dist/config）。 */
export const SERVER_ROOT: string = path.resolve(__dirname, '../..');

/** 解析可能为相对路径的配置项，相对路径以 server/ 为基准。 */
function resolveFromServerRoot(target: string): string {
  return path.isAbsolute(target) ? target : path.resolve(SERVER_ROOT, target);
}

/** 读取整数型环境变量，非法或缺失时回退默认值。 */
function readIntEnv(key: string, fallback: number): number {
  const raw: string | undefined = process.env[key];
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  const parsed: number = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

/** HTTP 服务监听端口。 */
export const PORT: number = readIntEnv('PORT', 8900);

/** SQLite 数据库文件绝对路径。 */
export const DB_PATH: string = resolveFromServerRoot(process.env.DB_PATH ?? 'data/lis-admin.db');

/** 单次健康探测超时（毫秒）。 */
export const HEALTH_CHECK_TIMEOUT_MS: number = readIntEnv('HEALTH_CHECK_TIMEOUT_MS', 3000);

/** 日志上传大小上限（字节），默认 50MB。 */
export const MAX_UPLOAD_SIZE_BYTES: number = readIntEnv('MAX_UPLOAD_SIZE_MB', 50) * 1024 * 1024;

/** 生产模式下托管的前端静态目录绝对路径。 */
export const STATIC_DIR: string = resolveFromServerRoot(process.env.STATIC_DIR ?? '../web/dist');

/** 允许上传的日志文件后缀（空字符串代表无后缀的崩溃日志）。 */
export const ALLOWED_LOG_EXTENSIONS: readonly string[] = ['.log', '.txt', '.out', '.err', ''];

/** 每个服务保留的检测历史条数上限（超出后自动裁剪，防止无限膨胀）。 */
export const MAX_HISTORY_PER_SERVICE: number = 500;

/** 进程输出环形缓冲行数上限（超出后移除最早行）。admin 重启后丢失，不落库。 */
export const PROCESS_OUTPUT_BUFFER_LINES: number = 500;

/** 日志条目查询默认每页条数。 */
export const DEFAULT_PAGE_SIZE: number = 50;

/** 日志条目查询每页条数上限。 */
export const MAX_PAGE_SIZE: number = 500;

/** 默认服务注册表的单条定义。 */
export interface DefaultServiceSeed {
  name: string;
  type: ServiceType;
  host: string;
  port: number;
  health_url: string | null;
  enabled: number;
  sort_order: number;
  /** 启动命令，null 表示不支持进程管控。 */
  start_command?: string | null;
  /** 工作目录，null 表示使用默认目录。 */
  work_dir?: string | null;
}

/**
 * 首次启动写入的默认服务注册表（15 条）。
 *
 * 端口取自 LIS 现有部署约定，host 统一 127.0.0.1，
 * health_url 默认 NULL 表示使用 TCP 端口探测。
 */
export const DEFAULT_SERVICES: readonly DefaultServiceSeed[] = [
  { name: 'lis-gateway', type: 'backend', host: '127.0.0.1', port: 8080, health_url: null, enabled: 1, sort_order: 1 },
  { name: 'lis-auth', type: 'backend', host: '127.0.0.1', port: 8090, health_url: null, enabled: 1, sort_order: 2 },
  { name: 'lis-service-system', type: 'backend', host: '127.0.0.1', port: 8087, health_url: null, enabled: 1, sort_order: 3 },
  { name: 'lis-service-base', type: 'backend', host: '127.0.0.1', port: 8085, health_url: null, enabled: 1, sort_order: 4 },
  { name: 'lis-service-sample', type: 'backend', host: '127.0.0.1', port: 8081, health_url: null, enabled: 1, sort_order: 5 },
  { name: 'lis-service-report', type: 'backend', host: '127.0.0.1', port: 8082, health_url: null, enabled: 1, sort_order: 6 },
  { name: 'lis-service-qc', type: 'backend', host: '127.0.0.1', port: 8083, health_url: null, enabled: 1, sort_order: 7 },
  { name: 'lis-service-instrument', type: 'backend', host: '127.0.0.1', port: 8084, health_url: null, enabled: 1, sort_order: 8 },
  { name: 'lis-service-instrument-mgmt', type: 'backend', host: '127.0.0.1', port: 8091, health_url: null, enabled: 1, sort_order: 9 },
  { name: 'lis-service-integration', type: 'backend', host: '127.0.0.1', port: 8086, health_url: null, enabled: 1, sort_order: 10 },
  { name: 'lis-service-reagent', type: 'backend', host: '127.0.0.1', port: 8092, health_url: null, enabled: 1, sort_order: 11 },
  { name: 'lis-service-micro', type: 'backend', host: '127.0.0.1', port: 8093, health_url: null, enabled: 1, sort_order: 12 },
  { name: 'lis-service-hr', type: 'backend', host: '127.0.0.1', port: 8094, health_url: null, enabled: 1, sort_order: 13 },
  { name: 'lis-service-stats', type: 'backend', host: '127.0.0.1', port: 8088, health_url: null, enabled: 1, sort_order: 14 },
  { name: 'lis-frontend', type: 'frontend', host: '127.0.0.1', port: 3000, health_url: null, enabled: 1, sort_order: 15 },
];
