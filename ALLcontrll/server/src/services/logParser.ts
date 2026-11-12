/**
 * Spring Boot 日志解析器。
 *
 * 解析策略（见架构文档 8.2）：
 *   1. 逐行匹配标准日志行正则，命中则开启一条新的主日志条目；
 *   2. 未命中且属于堆栈行 / 缩进续行的，聚合到上一条主日志的 `stack_trace`；
 *   3. 既非主日志行、也无上文可归属的行，退化为一条无级别条目，保证内容不丢失；
 *   4. JVM 崩溃日志（`hs_err_pid*.log`）跳过行解析，整文件作为单条 raw_text 条目。
 */

import type { LogLevel, ParseResult, ParsedLogEntry } from '../types';

/**
 * Spring Boot 3.x 标准行（带应用名 + 线程名两个方括号）。
 * 捕获组：[1]timestamp [2]level [3]pid [4]appName [5]thread [6]logger [7]message
 */
const LINE_REGEX: RegExp =
  /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}(?:[+-]\d{2}:\d{2})?)\s+(INFO|DEBUG|WARN|ERROR|TRACE)\s+(\d+)\s+---\s+\[([^\]]+)\]\s+\[([^\]]+)\]\s+(\S+)\s*:\s*(.*)$/;

/**
 * 兼容行式一：Spring Boot 默认 pattern（仅一个方括号包裹线程名），
 * 时间戳允许 `T` 或空格分隔、允许无时区后缀。
 * 捕获组：[1]timestamp [2]level [3]pid [4]thread [5]logger [6]message
 */
const LINE_REGEX_SINGLE_BRACKET: RegExp =
  /^(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}[.,]\d{3}(?:[+-]\d{2}:?\d{2})?)\s+(INFO|DEBUG|WARN|ERROR|TRACE)\s+(\d+)\s+---\s+\[([^\]]+)\]\s+(\S+)\s*:\s*(.*)$/;

/**
 * 兼容行式二：无 PID / 无 `---` 分隔的精简 pattern（部分服务自定义 logback 输出）。
 * 捕获组：[1]timestamp [2]level [3]thread(可选) [4]logger(可选) [5]message
 */
const LINE_REGEX_LOOSE: RegExp =
  /^(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d{1,3})?(?:[+-]\d{2}:?\d{2})?)\s+\[?(INFO|DEBUG|WARN|ERROR|TRACE)\]?\s+(?:\[([^\]]+)\]\s+)?(?:([\w$.]+(?:\.[\w$]+)+)\s*[:-]\s*)?(.*)$/;

/** 堆栈行判定正则。 */
const STACK_LINE_REGEX: RegExp = /^(Exception|Caused by:|at\s+|\.\.\.\s+\d+\s+more|\s+at\s)/;

/** 异常类名行（如 `java.lang.NullPointerException: xxx`）。 */
const EXCEPTION_HEAD_REGEX: RegExp = /^(?:[\w$]+\.)+[\w$]*(?:Exception|Error|Throwable)(?::|$)/;

/** JVM 崩溃日志文件名。 */
export const HS_ERR_FILENAME: RegExp = /^hs_err_pid\d+\.log$/i;

/** 单条堆栈聚合上限行数，防止异常文件撑爆内存。 */
const MAX_STACK_LINES: number = 500;

/**
 * 判断某行是否属于堆栈 / 续行。
 *
 * @param line 原始行文本
 * @returns true 表示应聚合到上一条主日志
 */
export function isStackLine(line: string): boolean {
  if (line.trim() === '') {
    return false;
  }
  if (STACK_LINE_REGEX.test(line)) {
    return true;
  }
  if (EXCEPTION_HEAD_REGEX.test(line.trim())) {
    return true;
  }
  // 缩进续行（制表符或空格开头）同样归属上一条主日志。
  return /^[\t ]/.test(line);
}

/** 归一化时间戳：统一使用 `T` 分隔、`.` 作为毫秒分隔符，便于字符串比较与筛选。 */
function normalizeTimestamp(raw: string): string {
  return raw.replace(' ', 'T').replace(',', '.');
}

/** 从正则捕获结果构造主日志条目。 */
function buildEntry(
  lineNo: number,
  rawLine: string,
  timestamp: string,
  level: string,
  pid: string | null,
  thread: string | null,
  logger: string | null,
  message: string,
): ParsedLogEntry {
  return {
    line_no: lineNo,
    timestamp: normalizeTimestamp(timestamp),
    level: level as LogLevel,
    pid,
    thread,
    logger,
    message,
    stack_trace: null,
    has_stack: false,
    raw_line: rawLine,
  };
}

/**
 * 解析单行为主日志条目。
 *
 * @param line 原始行文本
 * @param lineNo 行号（从 1 开始）
 * @returns 主日志条目；非标准行返回 null
 */
export function parseLine(line: string, lineNo: number): ParsedLogEntry | null {
  const strict: RegExpMatchArray | null = line.match(LINE_REGEX);
  if (strict !== null) {
    // strict[4] 为应用名，strict[5] 为线程名。
    return buildEntry(lineNo, line, strict[1], strict[2], strict[3], strict[5], strict[6], strict[7]);
  }

  const single: RegExpMatchArray | null = line.match(LINE_REGEX_SINGLE_BRACKET);
  if (single !== null) {
    return buildEntry(lineNo, line, single[1], single[2], single[3], single[4], single[5], single[6]);
  }

  const loose: RegExpMatchArray | null = line.match(LINE_REGEX_LOOSE);
  if (loose !== null) {
    return buildEntry(
      lineNo,
      line,
      loose[1],
      loose[2],
      null,
      loose[3] ?? null,
      loose[4] ?? null,
      loose[5] ?? '',
    );
  }

  return null;
}

/** 将聚合的堆栈行写回条目。 */
function attachStack(entry: ParsedLogEntry, stackLines: string[]): void {
  if (stackLines.length === 0) {
    return;
  }
  entry.stack_trace = stackLines.join('\n');
  entry.has_stack = true;
}

/** 构造 JVM 崩溃日志的单条条目。 */
function buildCrashEntry(content: string): ParsedLogEntry {
  const lines: string[] = content.split(/\r?\n/);
  const firstMeaningful: string =
    lines.find((line: string): boolean => line.trim() !== '' && !line.trim().startsWith('#=')) ?? 'JVM crash log';
  return {
    line_no: 1,
    timestamp: null,
    level: 'ERROR',
    pid: null,
    thread: null,
    logger: 'JVM',
    message: firstMeaningful.trim().slice(0, 500),
    stack_trace: content,
    has_stack: true,
    raw_line: firstMeaningful,
  };
}

/**
 * 解析整个日志文件内容。
 *
 * @param content 文件全文
 * @param filename 原始文件名（用于识别 JVM 崩溃日志）
 * @returns 解析结果（条目列表 + 统计信息）
 */
export function parse(content: string, filename: string): ParseResult {
  const allLines: string[] = content.split(/\r?\n/);
  const totalLines: number = allLines.length;

  if (HS_ERR_FILENAME.test(filename)) {
    const crashEntry: ParsedLogEntry = buildCrashEntry(content);
    return {
      entries: [crashEntry],
      total_lines: totalLines,
      parsed_entries: 1,
      error_count: 1,
      warn_count: 0,
    };
  }

  const entries: ParsedLogEntry[] = [];
  let current: ParsedLogEntry | null = null;
  let stackLines: string[] = [];

  const flush = (): void => {
    if (current !== null) {
      attachStack(current, stackLines);
      entries.push(current);
    }
    current = null;
    stackLines = [];
  };

  for (let index = 0; index < allLines.length; index += 1) {
    const line: string = allLines[index];
    const lineNo: number = index + 1;

    const parsed: ParsedLogEntry | null = parseLine(line, lineNo);
    if (parsed !== null) {
      flush();
      current = parsed;
      continue;
    }

    if (line.trim() === '') {
      // 空行：若正处于堆栈聚合中则保留为分隔，否则忽略。
      if (current !== null && stackLines.length > 0 && stackLines.length < MAX_STACK_LINES) {
        stackLines.push('');
      }
      continue;
    }

    if (current !== null && isStackLine(line)) {
      if (stackLines.length < MAX_STACK_LINES) {
        stackLines.push(line);
      }
      continue;
    }

    // 无法归属的非标准行：单独成条，保证原始内容可见。
    flush();
    entries.push({
      line_no: lineNo,
      timestamp: null,
      level: null,
      pid: null,
      thread: null,
      logger: null,
      message: line.trim(),
      stack_trace: null,
      has_stack: false,
      raw_line: line,
    });
  }

  flush();

  let errorCount: number = 0;
  let warnCount: number = 0;
  for (const entry of entries) {
    if (entry.level === 'ERROR') {
      errorCount += 1;
    } else if (entry.level === 'WARN') {
      warnCount += 1;
    }
  }

  return {
    entries,
    total_lines: totalLines,
    parsed_entries: entries.length,
    error_count: errorCount,
    warn_count: warnCount,
  };
}
