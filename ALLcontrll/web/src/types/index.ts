// 前端类型定义，与后端 server/src/types/index.ts 镜像对齐

export type ServiceType = 'backend' | 'frontend';
export type ServiceStatus = 'online' | 'offline' | 'unknown';

/** 进程运行状态，与后端 `ProcessStatus` 镜像。 */
export type ProcessStatus = 'running' | 'stopped' | 'crashed' | 'unknown';

/** 日志级别，与后端 `LogLevel` 镜像。 */
export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

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

export interface ServiceSummary {
  total: number;
  online: number;
  offline: number;
  unknown: number;
  paused: number;
  health_rate: number;
}

/** `POST /api/services/:id/start` 响应数据，与后端 `StartResult` 镜像。 */
export interface StartResult {
  service_id: number;
  pid: number;
  process_status: 'running';
  started_at: string;
}

/** `POST /api/services/:id/stop` 响应数据，与后端 `StopResult` 镜像。 */
export interface StopResult {
  service_id: number;
  process_status: 'stopped';
  message: string;
}

/** 自动检测结果项，与后端 `DetectResultItem` 镜像。 */
export interface DetectResultItem {
  service_id: number;
  service_name: string;
  detected_command: string;
  detected_work_dir: string | null;
  matched_file: string;
  match_type: 'exact' | 'prefix' | 'contains';
  confidence: 'high' | 'medium' | 'low';
}

/** 自动检测响应数据，与后端 `AutoDetectData` 镜像。 */
export interface AutoDetectData {
  results: DetectResultItem[];
  scanned_files: number;
  matched_count: number;
}

/** 批量应用配置项，与后端 `ApplyConfigItem` 镜像。 */
export interface ApplyConfigItem {
  id: number;
  start_command: string;
  work_dir: string | null;
}

/** 批量应用响应数据，与后端 `ApplyDetectedData` 镜像。 */
export interface ApplyDetectedData {
  updated: Service[];
}

/**
 * 单个服务的健康检查结果，与后端 `CheckResult`（server/src/types/index.ts）严格镜像。
 *
 * 注意：字段名为 `service_id`（而非 `id`），它指向 `Service.id`。
 */
export interface CheckResult {
  service_id: number;
  name: string;
  status: ServiceStatus;
  response_ms: number | null;
  error_message: string | null;
  /** ISO 8601 时间戳。 */
  checked_at: string;
}

export interface ServiceCheckResponse {
  results: CheckResult[];
  summary: ServiceSummary;
  duration_ms: number;
}

export interface ServiceHistoryItem {
  id: number;
  service_id: number;
  status: ServiceStatus;
  response_ms: number | null;
  error_message: string | null;
  checked_at: string;
}

export interface LogFile {
  id: number;
  filename: string;
  service_name: string | null;
  file_size: number;
  entry_count: number;
  uploaded_at: string;
}

export interface LogEntry {
  id: number;
  file_id: number;
  line_no: number;
  timestamp: string | null;
  level: string | null;
  pid: string | null;
  thread: string | null;
  logger: string | null;
  message: string;
  stack_trace: string | null;
  has_stack: boolean;
  raw_line: string | null;
}

/**
 * 详情视图的日志条目（含完整堆栈与原始行），与后端 `LogEntryDetail` 严格镜像。
 * 包含 `pid` 字段，`LogEntry` 列表类型亦同构；`getLogEntry` 返回此类型。
 */
export interface LogEntryDetail {
  id: number;
  file_id: number;
  line_no: number;
  timestamp: string | null;
  /** 日志级别，可能为 null（解析失败时）。 */
  level: LogLevel | null;
  pid: string | null;
  thread: string | null;
  logger: string | null;
  message: string;
  stack_trace: string | null;
  has_stack: boolean;
  raw_line: string | null;
}

export interface LogQueryResponse {
  entries: LogEntry[];
  total: number;
  page: number;
  page_size: number;
  level_counts: Record<string, number>;
}

/** `POST /api/logs/upload` 响应，与后端 `UploadLogData` / `ParseSummary` 镜像。 */
export interface LogUploadResponse {
  file: LogFile;
  parse_summary: {
    total_lines: number;
    parsed_entries: number;
    error_count: number;
    warn_count: number;
  };
}

export interface DashboardSummary {
  service_status: ServiceSummary;
  log_summary: {
    total_files: number;
    total_entries: number;
    error_count: number;
    warn_count: number;
  };
  service_health_pie: Array<{ name: string; value: number }>;
  service_error_counts: Array<{ service_name: string; error_count: number }>;
}

// 统一响应结构 { code, message, data }
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}
