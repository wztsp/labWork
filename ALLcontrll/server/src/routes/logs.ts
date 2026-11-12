/**
 * 日志管理路由：上传解析 + 文件管理 + 条目查询/详情。
 *
 * 路由前缀由 `app.ts` 挂载为 `/api/logs`。
 * 数据访问逻辑内聚在本文件的 `LogRepository` 中（见架构文档 T03 说明）。
 */

import path from 'node:path';
import { Router, type Request, type Response } from 'express';
import multer, { type FileFilterCallback, type Multer } from 'multer';
import {
  ALLOWED_LOG_EXTENSIONS,
  DEFAULT_PAGE_SIZE,
  MAX_PAGE_SIZE,
  MAX_UPLOAD_SIZE_BYTES,
} from '../config/defaults';
import { getDb, type Db } from '../db/connection';
import { parse } from '../services/logParser';
import type {
  DeletedData,
  EntryFilter,
  LevelCounts,
  LogEntryDetail,
  LogEntryDetailData,
  LogEntryRow,
  LogEntrySummary,
  LogFileInput,
  LogFileListData,
  LogFileSummary,
  LogLevel,
  PagedEntries,
  ParseResult,
  ParsedLogEntry,
  UploadLogData,
} from '../types';
import { asyncHandler, HttpError, nowIso, sendSuccess } from '../utils/response';

/** 合法日志级别集合。 */
const VALID_LEVELS: readonly string[] = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

/** 日志数据访问层。 */
export class LogRepository {
  private readonly db: Db;

  constructor(db: Db = getDb()) {
    this.db = db;
  }

  /** 保存日志文件记录，返回文件摘要。 */
  public saveFile(file: LogFileInput): LogFileSummary {
    const uploadedAt: string = nowIso();
    const info = this.db
      .prepare(
        `INSERT INTO log_files (filename, service_name, file_size, entry_count, raw_content, uploaded_at)
         VALUES (?, ?, ?, ?, ?, ?)`,
      )
      .run(file.filename, file.service_name, file.file_size, file.entry_count, file.raw_content, uploadedAt);

    return {
      id: Number(info.lastInsertRowid),
      filename: file.filename,
      service_name: file.service_name,
      file_size: file.file_size,
      entry_count: file.entry_count,
      uploaded_at: uploadedAt,
    };
  }

  /** 批量写入日志条目（单事务）。 */
  public saveEntries(fileId: number, entries: readonly ParsedLogEntry[]): void {
    if (entries.length === 0) {
      return;
    }
    const insert = this.db.prepare(
      `INSERT INTO log_entries
         (file_id, line_no, timestamp, level, pid, thread, logger, message, stack_trace, has_stack, raw_line)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );
    const run = this.db.transaction((rows: readonly ParsedLogEntry[]): void => {
      for (const row of rows) {
        insert.run(
          fileId,
          row.line_no,
          row.timestamp,
          row.level,
          row.pid,
          row.thread,
          row.logger,
          row.message,
          row.stack_trace,
          row.has_stack ? 1 : 0,
          row.raw_line,
        );
      }
    });
    run(entries);
  }

  /** 查询全部日志文件摘要（按上传时间倒序）。 */
  public findAllFiles(): LogFileSummary[] {
    return this.db
      .prepare(
        `SELECT id, filename, service_name, file_size, entry_count, uploaded_at
         FROM log_files
         ORDER BY id DESC`,
      )
      .all() as LogFileSummary[];
  }

  /** 判断文件是否存在。 */
  public fileExists(fileId: number): boolean {
    const row = this.db.prepare('SELECT id FROM log_files WHERE id = ?').get(fileId) as
      | { id: number }
      | undefined;
    return row !== undefined;
  }

  /** 统计文件内各级别条目数（不受筛选影响）。 */
  public countByLevel(fileId: number): LevelCounts {
    const rows = this.db
      .prepare(
        `SELECT COALESCE(level, 'UNKNOWN') AS level, COUNT(*) AS count
         FROM log_entries
         WHERE file_id = ?
         GROUP BY COALESCE(level, 'UNKNOWN')`,
      )
      .all(fileId) as Array<{ level: string; count: number }>;

    const counts: LevelCounts = {};
    for (const row of rows) {
      counts[row.level] = row.count;
    }
    return counts;
  }

  /** 按筛选条件分页查询日志条目。 */
  public findEntries(fileId: number, filter: EntryFilter): PagedEntries {
    const conditions: string[] = ['file_id = ?'];
    const params: unknown[] = [fileId];

    if (filter.level.length > 0) {
      conditions.push(`level IN (${filter.level.map((): string => '?').join(', ')})`);
      params.push(...filter.level);
    }
    if (filter.keyword !== '') {
      conditions.push('(message LIKE ? OR COALESCE(logger, "") LIKE ? OR COALESCE(stack_trace, "") LIKE ?)');
      const pattern: string = `%${filter.keyword}%`;
      params.push(pattern, pattern, pattern);
    }
    if (filter.start_time !== '') {
      conditions.push('timestamp IS NOT NULL AND timestamp >= ?');
      params.push(filter.start_time);
    }
    if (filter.end_time !== '') {
      conditions.push('timestamp IS NOT NULL AND timestamp <= ?');
      params.push(filter.end_time);
    }
    if (filter.has_stack === true) {
      conditions.push('has_stack = 1');
    } else if (filter.has_stack === false) {
      conditions.push('has_stack = 0');
    }

    const where: string = conditions.join(' AND ');
    const totalRow = this.db
      .prepare(`SELECT COUNT(*) AS total FROM log_entries WHERE ${where}`)
      .get(...params) as { total: number } | undefined;
    const total: number = totalRow?.total ?? 0;

    const offset: number = (filter.page - 1) * filter.page_size;
    const rows = this.db
      .prepare(
        `SELECT id, line_no, timestamp, level, thread, logger, message, has_stack
         FROM log_entries
         WHERE ${where}
         ORDER BY line_no ASC
         LIMIT ? OFFSET ?`,
      )
      .all(...params, filter.page_size, offset) as Array<Omit<LogEntrySummary, 'has_stack'> & { has_stack: number }>;

    const entries: LogEntrySummary[] = rows.map((row): LogEntrySummary => ({
      id: row.id,
      line_no: row.line_no,
      timestamp: row.timestamp,
      level: row.level,
      thread: row.thread,
      logger: row.logger,
      message: row.message,
      has_stack: row.has_stack === 1,
    }));

    return {
      entries,
      total,
      page: filter.page,
      page_size: filter.page_size,
      level_counts: this.countByLevel(fileId),
    };
  }

  /** 查询单条日志详情（含完整堆栈）。 */
  public findEntryDetail(fileId: number, entryId: number): LogEntryDetail | null {
    const row = this.db
      .prepare('SELECT * FROM log_entries WHERE file_id = ? AND id = ?')
      .get(fileId, entryId) as LogEntryRow | undefined;
    if (row === undefined) {
      return null;
    }
    return {
      id: row.id,
      file_id: row.file_id,
      line_no: row.line_no,
      timestamp: row.timestamp,
      level: row.level,
      pid: row.pid,
      thread: row.thread,
      logger: row.logger,
      message: row.message,
      stack_trace: row.stack_trace,
      has_stack: row.has_stack === 1,
      raw_line: row.raw_line,
    };
  }

  /** 删除日志文件及其全部条目。 */
  public deleteFile(fileId: number): void {
    const run = this.db.transaction((id: number): number => {
      this.db.prepare('DELETE FROM log_entries WHERE file_id = ?').run(id);
      return this.db.prepare('DELETE FROM log_files WHERE id = ?').run(id).changes;
    });
    const changes: number = run(fileId);
    if (changes === 0) {
      throw new HttpError(404, `日志文件不存在: id=${fileId}`);
    }
  }
}

/** 校验上传文件后缀。 */
function fileFilter(_req: Request, file: Express.Multer.File, callback: FileFilterCallback): void {
  const ext: string = path.extname(file.originalname).toLowerCase();
  if (ALLOWED_LOG_EXTENSIONS.includes(ext)) {
    callback(null, true);
    return;
  }
  callback(new HttpError(400, `不支持的文件类型: ${ext || '(无后缀)'}，仅支持 .log/.txt/.out/.err`));
}

/** multer 上传中间件（内存存储，限制 50MB）。 */
const upload: Multer = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_UPLOAD_SIZE_BYTES, files: 1 },
  fileFilter,
});

/** 解析并校验路径参数中的数字 id。 */
function parseId(raw: string, label: string): number {
  const value: number = Number.parseInt(raw, 10);
  if (!Number.isInteger(value) || value <= 0) {
    throw new HttpError(400, `非法的${label}: ${raw}`);
  }
  return value;
}

/** 从查询参数构造筛选条件。 */
function buildFilter(query: Request['query']): EntryFilter {
  const rawLevel: string = typeof query.level === 'string' ? query.level : '';
  const level: LogLevel[] = rawLevel
    .split(',')
    .map((item: string): string => item.trim().toUpperCase())
    .filter((item: string): boolean => VALID_LEVELS.includes(item)) as LogLevel[];

  const rawPage: number = Number.parseInt(String(query.page ?? '1'), 10);
  const page: number = Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 1;

  const rawPageSize: number = Number.parseInt(String(query.page_size ?? String(DEFAULT_PAGE_SIZE)), 10);
  const pageSize: number =
    Number.isInteger(rawPageSize) && rawPageSize > 0 ? Math.min(rawPageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

  let hasStack: boolean | null = null;
  if (query.has_stack === 'true' || query.has_stack === '1') {
    hasStack = true;
  } else if (query.has_stack === 'false' || query.has_stack === '0') {
    hasStack = false;
  }

  return {
    level,
    keyword: typeof query.keyword === 'string' ? query.keyword.trim() : '',
    start_time: typeof query.start_time === 'string' ? query.start_time.trim() : '',
    end_time: typeof query.end_time === 'string' ? query.end_time.trim() : '',
    has_stack: hasStack,
    page,
    page_size: pageSize,
  };
}

/** 创建日志管理路由。 */
export function createLogsRouter(repository: LogRepository = new LogRepository()): Router {
  const router: Router = Router();

  // POST /api/logs/upload —— 上传并解析日志文件
  router.post(
    '/upload',
    upload.single('file'),
    asyncHandler(async (req: Request, res: Response): Promise<void> => {
      const file: Express.Multer.File | undefined = req.file;
      if (file === undefined) {
        throw new HttpError(400, '缺少上传文件字段: file');
      }

      const content: string = file.buffer.toString('utf-8');
      const rawServiceName: unknown = (req.body as Record<string, unknown>)?.serviceName;
      const serviceName: string | null =
        typeof rawServiceName === 'string' && rawServiceName.trim() !== '' ? rawServiceName.trim() : null;

      const parsed: ParseResult = parse(content, file.originalname);
      const saved: LogFileSummary = repository.saveFile({
        filename: file.originalname,
        service_name: serviceName,
        file_size: file.size,
        entry_count: parsed.parsed_entries,
        raw_content: content,
      });
      repository.saveEntries(saved.id, parsed.entries);

      const data: UploadLogData = {
        file: saved,
        parse_summary: {
          total_lines: parsed.total_lines,
          parsed_entries: parsed.parsed_entries,
          error_count: parsed.error_count,
          warn_count: parsed.warn_count,
        },
      };
      sendSuccess(res, data);
    }),
  );

  // GET /api/logs/files —— 已上传文件列表
  router.get('/files', (_req: Request, res: Response): void => {
    const data: LogFileListData = { files: repository.findAllFiles() };
    sendSuccess(res, data);
  });

  // GET /api/logs/files/:fileId/entries —— 条目查询（筛选 + 分页）
  router.get('/files/:fileId/entries', (req: Request, res: Response): void => {
    const fileId: number = parseId(req.params.fileId, 'fileId');
    if (!repository.fileExists(fileId)) {
      throw new HttpError(404, `日志文件不存在: id=${fileId}`);
    }
    sendSuccess(res, repository.findEntries(fileId, buildFilter(req.query)));
  });

  // GET /api/logs/files/:fileId/entries/:entryId —— 条目详情
  router.get('/files/:fileId/entries/:entryId', (req: Request, res: Response): void => {
    const fileId: number = parseId(req.params.fileId, 'fileId');
    const entryId: number = parseId(req.params.entryId, 'entryId');
    const entry: LogEntryDetail | null = repository.findEntryDetail(fileId, entryId);
    if (entry === null) {
      throw new HttpError(404, `日志条目不存在: fileId=${fileId}, entryId=${entryId}`);
    }
    const data: LogEntryDetailData = { entry };
    sendSuccess(res, data);
  });

  // DELETE /api/logs/files/:fileId —— 删除文件（级联删除条目）
  router.delete('/files/:fileId', (req: Request, res: Response): void => {
    const fileId: number = parseId(req.params.fileId, 'fileId');
    repository.deleteFile(fileId);
    const data: DeletedData = { deleted: true };
    sendSuccess(res, data);
  });

  return router;
}
