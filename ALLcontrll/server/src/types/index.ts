/**
 * LIS 运维后台管理系统 —— 后端类型定义。
 *
 * 本文件是后端所有模块共享的类型契约，前端 `web/src/types/index.ts` 与之保持镜像。
 * 命名约定：数据库行/API 传输字段统一使用 snake_case，保持与 SQLite 列名一致。
 */

/** 服务类型：后端微服务 / 前端应用。 */
export type ServiceType = 'backend' | 'frontend';

/** 服务运行状态。`unknown` 表示尚未检测过。 */
export type ServiceStatus = 'online' | 'offline' | 'unknown';

/** 进程运行状态。`unknown` = 未配置启动命令或从未启动。 */
export type ProcessStatus = 'running' | 'stopped' | 'crashed' | 'unknown';

/** 日志级别。 */
export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

/** 统一 API 响应结构。 */
export interface ApiResponse<T> {
  /** 0 = 成功，非 0 = 错误。 */
  code: number;
  /** 'ok' 或错误描述。 */
  message: string;
  /** 业务数据。 */
  data: T;
}

// ---------------------------------------------------------------------------
// 服务注册表
// ---------------------------------------------------------------------------

/** `services` 表原始行结构（enabled 为 0/1 整数）。 */
export interface ServiceRow {
  id: number;
  name: string;
  type: ServiceType;
  host: string;
  port: number;
  health_url: string | null;
  enabled: number;
  sort_order: number;
  start_command: string | null;
  work_dir: string | null;
  created_at: string;
  updated_at: string;
}

/** 服务注册表行 + 最近一次检测状态 + 进程状态（JOIN 结果）。 */
export interface ServiceRowWithStatus extends ServiceRow {
  status: ServiceStatus | null;
  last_checked_at: string | null;
  response_ms: number | null;
  process_status: ProcessStatus | null;
  pid: number | null;
  process_started_at: string | null;
}

/** 对外暴露的服务对象（enabled 转为布尔，status 兜底为 'unknown'）。 */
export interface Service {
  id: number;
  name: string;
  type: ServiceType;
  host: string;
  port: number;
  health_url: string | null;
  enabled: boolean;
  sort_order: number;
  start_command: string | null;
  work_dir: string | null;
  created_at: string;
  updated_at: string;
  status: ServiceStatus;
  last_checked_at: string | null;
  response_ms: number | null;
  process_status: ProcessStatus;
  pid: number | null;
  process_started_at: string | null;
}

/** 新增 / 更新服务的入参（更新时字段均可选）。 */
export interface ServiceInput {
  name?: string;
  type?: ServiceType;
  host?: string;
  port?: number;
  health_url?: string | null;
  enabled?: boolean;
  sort_order?: number;
  start_command?: string | null;
  work_dir?: string | null;
}

/** 服务状态汇总。 */
export interface StatusSummary {
  total: number;
  online: number;
  offline: number;
  unknown: number;
  /** 已暂停监控（enabled=false）的服务数量。 */
  paused: number;
  /** 健康率百分比，保留一位小数，如 86.7。 */
  health_rate: number;
}

/** `GET /api/services` 响应数据。 */
export interface ServiceListData {
  services: Service[];
  summary: StatusSummary;
}

/** 单次探测的底层结果。 */
export interface ProbeResult {
  ok: boolean;
  /** 成功时的耗时（毫秒），失败为 null。 */
  responseMs: number | null;
  /** 失败原因，成功为 null。 */
  errorMessage: string | null;
}

/** 单个服务的健康检查结果。 */
export interface CheckResult {
  service_id: number;
  name: string;
  status: ServiceStatus;
  response_ms: number | null;
  error_message: string | null;
  /** ISO 8601 时间戳。 */
  checked_at: string;
}

/** `POST /api/services/check` 响应数据。 */
export interface CheckAllData {
  results: CheckResult[];
  summary: StatusSummary;
  duration_ms: number;
}

/** `service_check_history` 表行结构。 */
export interface CheckHistory {
  id: number;
  service_id: number;
  status: ServiceStatus;
  response_ms: number | null;
  error_message: string | null;
  checked_at: string;
}

/** `GET /api/services/:id/history` 响应数据。 */
export interface CheckHistoryData {
  history: CheckHistory[];
}

// ---------------------------------------------------------------------------
// 进程管理
// ---------------------------------------------------------------------------

/** `service_processes` 表行结构 / 进程信息。 */
export interface ProcessInfo {
  service_id: number;
  pid: number | null;
  process_status: ProcessStatus;
  started_at: string | null;
  stopped_at: string | null;
  exit_code: number | null;
}

/** `POST /api/services/:id/start` 响应数据。 */
export interface StartResult {
  service_id: number;
  pid: number;
  process_status: 'running';
  started_at: string;
}

/** `POST /api/services/:id/stop` 响应数据。 */
export interface StopResult {
  service_id: number;
  process_status: 'stopped';
  message: string;
}

// ---------------------------------------------------------------------------
// 启动命令自动检测
// ---------------------------------------------------------------------------

/** 自动检测请求体。 */
export interface AutoDetectRequest {
  base_dir: string;
}

/** 自动检测结果项。 */
export interface DetectResultItem {
  service_id: number;
  service_name: string;
  detected_command: string;
  detected_work_dir: string | null;
  matched_file: string;
  match_type: 'exact' | 'prefix' | 'contains';
  confidence: 'high' | 'medium' | 'low';
}

/** 自动检测响应数据。 */
export interface AutoDetectData {
  results: DetectResultItem[];
  scanned_files: number;
  matched_count: number;
}

/** 批量应用配置项。 */
export interface ApplyConfigItem {
  id: number;
  start_command: string;
  work_dir: string | null;
}

/** 批量应用请求体。 */
export interface ApplyDetectedRequest {
  configs: ApplyConfigItem[];
}

/** 批量应用响应数据。 */
export interface ApplyDetectedData {
  updated: Service[];
}

// ---------------------------------------------------------------------------
// 日志
// ---------------------------------------------------------------------------

/** `log_files` 表行结构。 */
export interface LogFileRow {
  id: number;
  filename: string;
  service_name: string | null;
  file_size: number;
  entry_count: number;
  raw_content: string | null;
  uploaded_at: string;
}

/** 日志文件摘要（不含 raw_content）。 */
export interface LogFileSummary {
  id: number;
  filename: string;
  service_name: string | null;
  file_size: number;
  entry_count: number;
  uploaded_at: string;
}

/** 保存日志文件的入参。 */
export interface LogFileInput {
  filename: string;
  service_name: string | null;
  file_size: number;
  entry_count: number;
  raw_content: string;
}

/** 解析产出的日志条目（尚未落库，无 id / file_id）。 */
export interface ParsedLogEntry {
  line_no: number;
  timestamp: string | null;
  level: LogLevel | null;
  pid: string | null;
  thread: string | null;
  logger: string | null;
  message: string;
  stack_trace: string | null;
  has_stack: boolean;
  raw_line: string;
}

/** `log_entries` 表行结构（has_stack 为 0/1 整数）。 */
export interface LogEntryRow {
  id: number;
  file_id: number;
  line_no: number;
  timestamp: string | null;
  level: LogLevel | null;
  pid: string | null;
  thread: string | null;
  logger: string | null;
  message: string;
  stack_trace: string | null;
  has_stack: number;
  raw_line: string | null;
}

/** 列表视图的日志条目（不含完整堆栈）。 */
export interface LogEntrySummary {
  id: number;
  line_no: number;
  timestamp: string | null;
  level: LogLevel | null;
  thread: string | null;
  logger: string | null;
  message: string;
  has_stack: boolean;
}

/** 详情视图的日志条目（含完整堆栈与原始行）。 */
export interface LogEntryDetail {
  id: number;
  file_id: number;
  line_no: number;
  timestamp: string | null;
  level: LogLevel | null;
  pid: string | null;
  thread: string | null;
  logger: string | null;
  message: string;
  stack_trace: string | null;
  has_stack: boolean;
  raw_line: string | null;
}

/** 日志解析结果。 */
export interface ParseResult {
  entries: ParsedLogEntry[];
  total_lines: number;
  parsed_entries: number;
  error_count: number;
  warn_count: number;
}

/** 日志上传响应中的解析摘要。 */
export interface ParseSummary {
  total_lines: number;
  parsed_entries: number;
  error_count: number;
  warn_count: number;
}

/** `POST /api/logs/upload` 响应数据。 */
export interface UploadLogData {
  file: LogFileSummary;
  parse_summary: ParseSummary;
}

/** `GET /api/logs/files` 响应数据。 */
export interface LogFileListData {
  files: LogFileSummary[];
}

/** 日志条目查询过滤条件。 */
export interface EntryFilter {
  /** 级别集合，空数组表示不限。 */
  level: LogLevel[];
  /** 关键字（匹配 message / logger / stack_trace）。 */
  keyword: string;
  /** 起始时间（含），字符串比较。 */
  start_time: string;
  /** 结束时间（含），字符串比较。 */
  end_time: string;
  /** true = 仅含堆栈的条目；null = 不限。 */
  has_stack: boolean | null;
  page: number;
  page_size: number;
}

/** 各级别条目计数（文件维度，不受筛选影响）。 */
export type LevelCounts = Record<string, number>;

/** `GET /api/logs/files/:fileId/entries` 响应数据。 */
export interface PagedEntries {
  entries: LogEntrySummary[];
  total: number;
  page: number;
  page_size: number;
  level_counts: LevelCounts;
}

/** `GET /api/logs/files/:fileId/entries/:entryId` 响应数据。 */
export interface LogEntryDetailData {
  entry: LogEntryDetail;
}

/** 删除类接口的响应数据。 */
export interface DeletedData {
  deleted: boolean;
}

// ---------------------------------------------------------------------------
// 仪表盘
// ---------------------------------------------------------------------------

/** 日志维度汇总。 */
export interface LogSummary {
  total_files: number;
  total_entries: number;
  error_count: number;
  warn_count: number;
}

/** 饼图数据项。 */
export interface PieDatum {
  name: string;
  value: number;
}

/** 各服务异常计数。 */
export interface ServiceErrorCount {
  service_name: string;
  error_count: number;
}

/** `GET /api/dashboard/summary` 响应数据。 */
export interface DashboardSummary {
  service_status: StatusSummary;
  log_summary: LogSummary;
  service_health_pie: PieDatum[];
  service_error_counts: ServiceErrorCount[];
}
