/**
 * `src/services/logParser.ts` 单元测试。
 *
 * 覆盖：标准 Spring Boot 行解析、多种 pattern 兼容、多级别统计、
 * 堆栈聚合、JVM 崩溃日志整文件单条、空输入与其它边界。
 */

import { describe, expect, it } from 'vitest';
import { HS_ERR_FILENAME, isStackLine, parse, parseLine } from '../src/services/logParser';
import type { ParseResult, ParsedLogEntry } from '../src/types';

/** Spring Boot 3.x 标准行（应用名 + 线程名双方括号）。 */
const STRICT_LINE =
  '2024-05-20T10:15:30.123+08:00  INFO 12345 --- [lis-gateway] [main] c.l.g.GatewayApplication : Started GatewayApplication in 3.2 seconds';

/** Spring Boot 默认 pattern（单方括号线程名，空格分隔时间戳）。 */
const SINGLE_BRACKET_LINE =
  '2024-05-20 10:15:31.456  WARN 12345 --- [http-nio-8080-exec-1] c.l.s.SampleService : Slow query detected: 1200ms';

// ---------------------------------------------------------------------------
// parseLine
// ---------------------------------------------------------------------------

describe('parseLine', () => {
  it('应解析 Spring Boot 3.x 双方括号标准行', () => {
    // Act
    const entry: ParsedLogEntry | null = parseLine(STRICT_LINE, 1);

    // Assert
    expect(entry).not.toBeNull();
    expect(entry?.line_no).toBe(1);
    expect(entry?.timestamp).toBe('2024-05-20T10:15:30.123+08:00');
    expect(entry?.level).toBe('INFO');
    expect(entry?.pid).toBe('12345');
    expect(entry?.thread).toBe('main');
    expect(entry?.logger).toBe('c.l.g.GatewayApplication');
    expect(entry?.message).toBe('Started GatewayApplication in 3.2 seconds');
    expect(entry?.has_stack).toBe(false);
    expect(entry?.stack_trace).toBeNull();
    expect(entry?.raw_line).toBe(STRICT_LINE);
  });

  it('应解析单方括号 pattern 并归一化时间戳分隔符', () => {
    // Act
    const entry: ParsedLogEntry | null = parseLine(SINGLE_BRACKET_LINE, 9);

    // Assert
    expect(entry?.timestamp).toBe('2024-05-20T10:15:31.456');
    expect(entry?.level).toBe('WARN');
    expect(entry?.pid).toBe('12345');
    expect(entry?.thread).toBe('http-nio-8080-exec-1');
    expect(entry?.logger).toBe('c.l.s.SampleService');
    expect(entry?.message).toBe('Slow query detected: 1200ms');
    expect(entry?.line_no).toBe(9);
  });

  it('应把逗号毫秒分隔符归一化为点号', () => {
    // Act
    const entry: ParsedLogEntry | null = parseLine(
      '2024-05-20 10:15:32,789 ERROR 999 --- [main] c.l.Foo : boom',
      1,
    );

    // Assert
    expect(entry?.timestamp).toBe('2024-05-20T10:15:32.789');
    expect(entry?.level).toBe('ERROR');
  });

  it.each([['TRACE'], ['DEBUG'], ['INFO'], ['WARN'], ['ERROR']])(
    '应识别日志级别 %s',
    (level: string) => {
      // Act
      const entry: ParsedLogEntry | null = parseLine(
        `2024-05-20T10:15:30.123+08:00 ${level} 1 --- [app] [main] c.l.Foo : hello`,
        1,
      );

      // Assert
      expect(entry?.level).toBe(level);
    },
  );

  it('应通过 loose pattern 解析无 PID 的精简格式', () => {
    // Act
    const entry: ParsedLogEntry | null = parseLine(
      '2024-05-20 10:15:33.001 [ERROR] [worker-1] com.lis.job.SyncJob - sync failed',
      3,
    );

    // Assert
    expect(entry).not.toBeNull();
    expect(entry?.level).toBe('ERROR');
    expect(entry?.pid).toBeNull();
    expect(entry?.thread).toBe('worker-1');
    expect(entry?.logger).toBe('com.lis.job.SyncJob');
    expect(entry?.message).toBe('sync failed');
  });

  it('loose pattern 缺少线程与 logger 时应回退为 null', () => {
    // Act
    const entry: ParsedLogEntry | null = parseLine('2024-05-20 10:15:33.001 ERROR disk full', 1);

    // Assert
    expect(entry).not.toBeNull();
    expect(entry?.level).toBe('ERROR');
    expect(entry?.pid).toBeNull();
    expect(entry?.thread).toBeNull();
    expect(entry?.logger).toBeNull();
    expect(entry?.message).toBe('disk full');
  });

  it.each([
    ['\tat com.lis.Foo.bar(Foo.java:1)'],
    ['plain text without timestamp'],
    [''],
  ])('非日志行 %j 应返回 null', (line: string) => {
    expect(parseLine(line, 1)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// isStackLine
// ---------------------------------------------------------------------------

describe('isStackLine', () => {
  it.each([
    ['\tat com.lis.sample.SampleService.save(SampleService.java:88)'],
    ['    at java.base/java.lang.Thread.run(Thread.java:840)'],
    ['Caused by: java.lang.IllegalStateException: db closed'],
    ['java.lang.NullPointerException: s is null'],
    ['\t... 12 more'],
    ['  continuation indented line'],
  ])('应判定 %j 为堆栈/续行', (line: string) => {
    expect(isStackLine(line)).toBe(true);
  });

  it.each([[''], ['   '], ['normal message at start of line']])(
    '不应把 %j 判定为堆栈行',
    (line: string) => {
      expect(isStackLine(line)).toBe(false);
    },
  );
});

// ---------------------------------------------------------------------------
// parse —— 常规日志
// ---------------------------------------------------------------------------

describe('parse —— 常规 Spring Boot 日志', () => {
  it('应逐条解析多级别日志并正确统计 error/warn', () => {
    // Arrange
    const content: string = [
      STRICT_LINE,
      SINGLE_BRACKET_LINE,
      '2024-05-20T10:15:32.789+08:00 ERROR 12345 --- [lis-sample] [http-nio-8081-exec-2] c.l.s.c.SampleController : Failed to save sample',
      '2024-05-20T10:15:33.000+08:00 DEBUG 12345 --- [lis-sample] [main] c.l.s.Cache : cache warmed',
      '2024-05-20T10:15:34.000+08:00 ERROR 12345 --- [lis-sample] [main] c.l.s.Db : connection lost',
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'lis-sample.log');

    // Assert
    expect(result.total_lines).toBe(5);
    expect(result.parsed_entries).toBe(5);
    expect(result.entries).toHaveLength(5);
    expect(result.error_count).toBe(2);
    expect(result.warn_count).toBe(1);
    expect(result.entries.map((e: ParsedLogEntry): number => e.line_no)).toEqual([1, 2, 3, 4, 5]);
  });

  it('应把堆栈行聚合到上一条主日志', () => {
    // Arrange
    const content: string = [
      '2024-05-20T10:15:32.789+08:00 ERROR 12345 --- [lis-sample] [exec-2] c.l.s.c.SampleController : Failed to save sample',
      'java.lang.NullPointerException: Cannot invoke "String.length()" because "s" is null',
      '\tat com.lis.sample.SampleService.save(SampleService.java:88)',
      '\tat com.lis.sample.SampleController.create(SampleController.java:42)',
      'Caused by: java.lang.IllegalStateException: db closed',
      '\t... 12 more',
      '2024-05-20T10:15:35.000+08:00  INFO 12345 --- [lis-sample] [main] c.l.s.Recovery : recovered',
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'lis-sample.log');

    // Assert：堆栈不产生独立条目，只挂在 ERROR 条目上。
    expect(result.entries).toHaveLength(2);
    expect(result.parsed_entries).toBe(2);
    expect(result.total_lines).toBe(7);

    const errorEntry: ParsedLogEntry = result.entries[0];
    expect(errorEntry.level).toBe('ERROR');
    expect(errorEntry.has_stack).toBe(true);
    expect(errorEntry.stack_trace).toContain('java.lang.NullPointerException');
    expect(errorEntry.stack_trace).toContain('at com.lis.sample.SampleService.save');
    expect(errorEntry.stack_trace).toContain('Caused by: java.lang.IllegalStateException');
    expect(errorEntry.stack_trace).toContain('... 12 more');
    expect((errorEntry.stack_trace as string).split('\n')).toHaveLength(5);

    // 后续 INFO 条目不应被污染。
    expect(result.entries[1].level).toBe('INFO');
    expect(result.entries[1].has_stack).toBe(false);
    expect(result.entries[1].stack_trace).toBeNull();
    expect(result.error_count).toBe(1);
  });

  it('末尾未闭合的堆栈也应被 flush 写入', () => {
    // Arrange
    const content: string = [
      '2024-05-20T10:15:32.789+08:00 ERROR 1 --- [app] [main] c.l.Foo : boom',
      '\tat com.lis.Foo.bar(Foo.java:10)',
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect(result.entries[0].has_stack).toBe(true);
    expect(result.entries[0].stack_trace).toBe('\tat com.lis.Foo.bar(Foo.java:10)');
  });

  it('堆栈中间的空行应作为分隔符保留', () => {
    // Arrange
    const content: string = [
      '2024-05-20T10:15:32.789+08:00 ERROR 1 --- [app] [main] c.l.Foo : boom',
      '\tat com.lis.Foo.a(Foo.java:1)',
      '',
      'Caused by: java.lang.IllegalStateException: closed',
      '\tat com.lis.Foo.b(Foo.java:2)',
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect((result.entries[0].stack_trace as string).split('\n')).toEqual([
      '\tat com.lis.Foo.a(Foo.java:1)',
      '',
      'Caused by: java.lang.IllegalStateException: closed',
      '\tat com.lis.Foo.b(Foo.java:2)',
    ]);
  });

  it('主日志行之后紧跟的空行不应产生空堆栈', () => {
    // Arrange
    const content: string = [
      '2024-05-20T10:15:32.789+08:00  INFO 1 --- [app] [main] c.l.Foo : started',
      '',
      '2024-05-20T10:15:33.000+08:00  INFO 1 --- [app] [main] c.l.Bar : ready',
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(2);
    expect(result.entries[0].has_stack).toBe(false);
    expect(result.entries[0].stack_trace).toBeNull();
  });

  it('无法归属的非标准行应单独成条，内容不丢失', () => {
    // Arrange：首行没有上文可归属。
    const content: string = [
      'Picked up JAVA_TOOL_OPTIONS: -Xmx2g',
      STRICT_LINE,
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(2);
    expect(result.entries[0].level).toBeNull();
    expect(result.entries[0].timestamp).toBeNull();
    expect(result.entries[0].message).toBe('Picked up JAVA_TOOL_OPTIONS: -Xmx2g');
    expect(result.entries[0].line_no).toBe(1);
    expect(result.entries[1].level).toBe('INFO');
  });

  it('应兼容 CRLF 换行', () => {
    // Arrange
    const content: string = `${STRICT_LINE}\r\n${SINGLE_BRACKET_LINE}\r\n`;

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(2);
    expect(result.entries[0].message).toBe('Started GatewayApplication in 3.2 seconds');
    expect(result.entries[0].raw_line).not.toContain('\r');
  });

  it('堆栈聚合应有上限，防止超大异常撑爆内存', () => {
    // Arrange：600 行堆栈，超过 500 行上限。
    const stack: string[] = Array.from(
      { length: 600 },
      (_v: unknown, i: number): string => `\tat com.lis.Deep.frame${i}(Deep.java:${i})`,
    );
    const content: string = [
      '2024-05-20T10:15:32.789+08:00 ERROR 1 --- [app] [main] c.l.Foo : deep stack',
      ...stack,
    ].join('\n');

    // Act
    const result: ParseResult = parse(content, 'app.log');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect((result.entries[0].stack_trace as string).split('\n')).toHaveLength(500);
  });
});

// ---------------------------------------------------------------------------
// parse —— 边界
// ---------------------------------------------------------------------------

describe('parse —— 边界输入', () => {
  it('空字符串应返回零条目', () => {
    // Act
    const result: ParseResult = parse('', 'app.log');

    // Assert
    expect(result.entries).toEqual([]);
    expect(result.parsed_entries).toBe(0);
    expect(result.error_count).toBe(0);
    expect(result.warn_count).toBe(0);
    expect(result.total_lines).toBe(1);
  });

  it('纯空行内容应返回零条目', () => {
    // Act
    const result: ParseResult = parse('\n\n\n', 'app.log');

    // Assert
    expect(result.entries).toEqual([]);
    expect(result.parsed_entries).toBe(0);
    expect(result.total_lines).toBe(4);
  });

  it('parsed_entries 应等于 entries 长度', () => {
    // Act
    const result: ParseResult = parse([STRICT_LINE, SINGLE_BRACKET_LINE].join('\n'), 'app.log');

    // Assert
    expect(result.parsed_entries).toBe(result.entries.length);
  });
});

// ---------------------------------------------------------------------------
// parse —— JVM 崩溃日志
// ---------------------------------------------------------------------------

describe('parse —— JVM 崩溃日志 hs_err_pid*.log', () => {
  const CRASH_CONTENT: string = [
    '#',
    '# A fatal error has been detected by the Java Runtime Environment:',
    '#',
    '#  SIGSEGV (0xb) at pc=0x00007f8c9d2b1a3c, pid=4242, tid=0x00007f8c88a1c700',
    '#',
    '# JRE version: OpenJDK Runtime Environment (17.0.9+9)',
    '',
    '---------------  T H R E A D  ---------------',
    'Current thread (0x00007f8c9c00f800):  JavaThread "main"',
  ].join('\n');

  it('应整文件作为单条 ERROR 条目，不做逐行解析', () => {
    // Act
    const result: ParseResult = parse(CRASH_CONTENT, 'hs_err_pid4242.log');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect(result.parsed_entries).toBe(1);
    expect(result.error_count).toBe(1);
    expect(result.warn_count).toBe(0);

    const entry: ParsedLogEntry = result.entries[0];
    expect(entry.level).toBe('ERROR');
    expect(entry.logger).toBe('JVM');
    expect(entry.line_no).toBe(1);
    expect(entry.timestamp).toBeNull();
    expect(entry.has_stack).toBe(true);
    expect(entry.stack_trace).toBe(CRASH_CONTENT);
    expect(entry.stack_trace).toContain('SIGSEGV');
  });

  it('total_lines 仍应反映真实行数', () => {
    // Act
    const result: ParseResult = parse(CRASH_CONTENT, 'hs_err_pid4242.log');

    // Assert
    expect(result.total_lines).toBe(9);
  });

  it('文件名匹配应忽略大小写', () => {
    // Act
    const result: ParseResult = parse(CRASH_CONTENT, 'HS_ERR_PID77.LOG');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect(result.entries[0].logger).toBe('JVM');
  });

  it('全部为分隔行时 message 应回退为占位文案', () => {
    // Act
    const result: ParseResult = parse('#=========\n\n#==', 'hs_err_pid5.log');

    // Assert
    expect(result.entries).toHaveLength(1);
    expect(result.entries[0].message).toBe('JVM crash log');
  });

  it('message 应截断至 500 字符以内', () => {
    // Arrange
    const longLine: string = `# ${'x'.repeat(900)}`;

    // Act
    const result: ParseResult = parse(longLine, 'hs_err_pid1.log');

    // Assert
    expect(result.entries[0].message.length).toBeLessThanOrEqual(500);
  });

  it.each([
    ['hs_err_pid1.log', true],
    ['hs_err_pid999999.log', true],
    ['HS_ERR_PID12.LOG', true],
    ['hs_err_pid.log', false],
    ['app-hs_err_pid1.log', false],
    ['hs_err_pid1.txt', false],
    ['application.log', false],
  ])('HS_ERR_FILENAME 对 %s 的判定应为 %s', (filename: string, expected: boolean) => {
    // 正则带 g 标志会有 lastIndex 副作用，此处确认其未开启 g。
    expect(HS_ERR_FILENAME.test(filename)).toBe(expected);
  });

  it('普通文件名不应触发崩溃日志分支', () => {
    // Act
    const result: ParseResult = parse(CRASH_CONTENT, 'application.log');

    // Assert：走常规解析，产出多条无级别条目。
    expect(result.entries.length).toBeGreaterThan(1);
    expect(result.error_count).toBe(0);
  });
});
