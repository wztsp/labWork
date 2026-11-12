/**
 * `src/services/healthChecker.ts` 单元测试。
 *
 * 覆盖 tcpProbe（真实本地 socket）、httpProbe（mock 全局 fetch）、
 * checkService（HTTP / TCP 策略选择）与 checkAll（并发、顺序、空数组）。
 *
 * 注意：本文件仅 import `services/healthChecker`，不触碰 routes / db，
 * 因此不会加载 better-sqlite3 原生模块。
 */

import net from 'node:net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { checkAll, checkService, httpProbe, tcpProbe } from '../src/services/healthChecker';
import type { CheckResult, ProbeResult, Service } from '../src/types';

// ---------------------------------------------------------------------------
// 测试夹具
// ---------------------------------------------------------------------------

interface TcpFixture {
  port: number;
  close: () => Promise<void>;
}

/** 在随机空闲端口启动一个本地 TCP 监听服务。 */
async function startTcpServer(): Promise<TcpFixture> {
  const server: net.Server = net.createServer();
  await new Promise<void>((resolve): void => {
    server.listen(0, '127.0.0.1', (): void => resolve());
  });
  const address: net.AddressInfo = server.address() as net.AddressInfo;
  return {
    port: address.port,
    close: (): Promise<void> =>
      new Promise<void>((resolve): void => {
        server.close((): void => resolve());
      }),
  };
}

/** 取得一个确定没有进程监听的本地端口（先占用再释放）。 */
async function getClosedPort(): Promise<number> {
  const fixture: TcpFixture = await startTcpServer();
  const port: number = fixture.port;
  await fixture.close();
  return port;
}

/** 构造服务对象。 */
function makeService(overrides: Partial<Service> = {}): Service {
  return {
    id: 1,
    name: 'lis-gateway',
    type: 'backend',
    host: '127.0.0.1',
    port: 8080,
    health_url: null,
    enabled: true,
    sort_order: 1,
    created_at: '2025-01-01T00:00:00.000Z',
    updated_at: '2025-01-01T00:00:00.000Z',
    status: 'unknown',
    last_checked_at: null,
    response_ms: null,
    ...overrides,
  };
}

/** 构造一个最小可用的 fetch Response 替身。 */
function makeResponse(status: number): Response {
  return { ok: status >= 200 && status < 300, status } as Response;
}

// ---------------------------------------------------------------------------
// tcpProbe
// ---------------------------------------------------------------------------

describe('tcpProbe', () => {
  it('连接成功时应返回 ok=true 与非负耗时', async () => {
    // Arrange
    const server: TcpFixture = await startTcpServer();

    try {
      // Act
      const result: ProbeResult = await tcpProbe('127.0.0.1', server.port, 3000);

      // Assert
      expect(result.ok).toBe(true);
      expect(result.errorMessage).toBeNull();
      expect(typeof result.responseMs).toBe('number');
      expect(result.responseMs as number).toBeGreaterThanOrEqual(0);
    } finally {
      await server.close();
    }
  });

  it('端口无人监听时应返回 ECONNREFUSED', async () => {
    // Arrange
    const port: number = await getClosedPort();

    // Act
    const result: ProbeResult = await tcpProbe('127.0.0.1', port, 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).toBe('ECONNREFUSED');
  });

  it('域名无法解析时应返回错误码而非抛异常', async () => {
    // Act
    const result: ProbeResult = await tcpProbe('nonexistent.invalid.host.test', 1234, 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).not.toBeNull();
  });

  it('探测结果永不 reject（Promise 始终 resolve）', async () => {
    // Act
    const settled: PromiseSettledResult<ProbeResult>[] = await Promise.allSettled([
      tcpProbe('127.0.0.1', await getClosedPort(), 500),
    ]);

    // Assert
    expect(settled[0].status).toBe('fulfilled');
  });
});

// ---------------------------------------------------------------------------
// httpProbe
// ---------------------------------------------------------------------------

describe('httpProbe', () => {
  const fetchMock = vi.fn();

  beforeEach((): void => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach((): void => {
    vi.unstubAllGlobals();
  });

  it('响应 2xx 时应判定为在线', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(200));

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:8080/actuator/health', 3000);

    // Assert
    expect(result.ok).toBe(true);
    expect(result.errorMessage).toBeNull();
    expect(result.responseMs as number).toBeGreaterThanOrEqual(0);
  });

  it('应以 GET 方法请求并携带 AbortSignal', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(204));

    // Act
    await httpProbe('http://127.0.0.1:8080/health', 3000);

    // Assert
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://127.0.0.1:8080/health');
    expect(init.method).toBe('GET');
    expect(init.signal).toBeDefined();
  });

  it.each([
    [404, 'HTTP 404'],
    [500, 'HTTP 500'],
    [301, 'HTTP 301'],
  ])('响应 %i（非 2xx）时应判定为离线并回报状态码', async (status: number, expected: string) => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(status));

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:8080/health', 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.errorMessage).toBe(expected);
    // 非 2xx 仍有真实往返耗时，应保留。
    expect(result.responseMs).not.toBeNull();
  });

  it('超时应中断请求并返回 TIMEOUT', async () => {
    // Arrange：模拟一个永不响应、仅在 abort 时 reject 的请求。
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit): Promise<Response> =>
        new Promise<Response>((_resolve, reject): void => {
          init.signal?.addEventListener('abort', (): void => {
            const err: Error = new Error('This operation was aborted');
            err.name = 'AbortError';
            reject(err);
          });
        }),
    );

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:8080/health', 50);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).toBe('TIMEOUT (>50ms)');
  });

  it('网络层错误应提取 cause.code 作为错误信息', async () => {
    // Arrange
    const err: Error & { cause?: unknown } = new TypeError('fetch failed');
    err.cause = { code: 'ECONNREFUSED' };
    fetchMock.mockRejectedValue(err);

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:9/health', 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).toBe('ECONNREFUSED');
  });

  it('无 cause.code 的错误应回退为 message', async () => {
    // Arrange
    fetchMock.mockRejectedValue(new Error('Invalid URL'));

    // Act
    const result: ProbeResult = await httpProbe('not-a-url', 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.errorMessage).toBe('Invalid URL');
  });

  it('cause 为非对象时应回退为 message', async () => {
    // Arrange
    const err: Error & { cause?: unknown } = new TypeError('fetch failed');
    err.cause = 'some string cause';
    fetchMock.mockRejectedValue(err);

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:9/health', 3000);

    // Assert
    expect(result.errorMessage).toBe('fetch failed');
  });

  it('cause 对象缺少 code 字段时应回退为 message', async () => {
    // Arrange
    const err: Error & { cause?: unknown } = new TypeError('fetch failed');
    err.cause = { reason: 'unknown' };
    fetchMock.mockRejectedValue(err);

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:9/health', 3000);

    // Assert
    expect(result.errorMessage).toBe('fetch failed');
  });

  it('抛出非 Error 值时应回退为字符串化结果', async () => {
    // Arrange
    fetchMock.mockRejectedValue('raw string failure');

    // Act
    const result: ProbeResult = await httpProbe('http://127.0.0.1:9/health', 3000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.errorMessage).toBe('raw string failure');
  });
});

// ---------------------------------------------------------------------------
// checkService
// ---------------------------------------------------------------------------

describe('checkService', () => {
  const fetchMock = vi.fn();

  beforeEach((): void => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach((): void => {
    vi.unstubAllGlobals();
  });

  it('health_url 非空时应走 HTTP 探测', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(200));
    const service: Service = makeService({
      id: 42,
      name: 'lis-auth',
      health_url: 'http://127.0.0.1:8090/actuator/health',
    });

    // Act
    const result: CheckResult = await checkService(service, 3000);

    // Assert
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('http://127.0.0.1:8090/actuator/health');
    expect(result.service_id).toBe(42);
    expect(result.name).toBe('lis-auth');
    expect(result.status).toBe('online');
    expect(result.error_message).toBeNull();
    expect(typeof result.checked_at).toBe('string');
  });

  it('health_url 前后带空白时应 trim 后再请求', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(200));

    // Act
    await checkService(makeService({ health_url: '  http://127.0.0.1:8090/health  ' }), 3000);

    // Assert
    expect(fetchMock.mock.calls[0][0]).toBe('http://127.0.0.1:8090/health');
  });

  it.each([[null], ['']])('health_url 为 %o 时应回退到 TCP 探测', async (healthUrl: string | null) => {
    // Arrange
    const server: TcpFixture = await startTcpServer();

    try {
      // Act
      const result: CheckResult = await checkService(
        makeService({ health_url: healthUrl, host: '127.0.0.1', port: server.port }),
        3000,
      );

      // Assert：未发起任何 HTTP 请求，且 TCP 连通判定为在线。
      expect(fetchMock).not.toHaveBeenCalled();
      expect(result.status).toBe('online');
      expect(result.response_ms).not.toBeNull();
    } finally {
      await server.close();
    }
  });

  it('health_url 为纯空白字符串时同样回退到 TCP 探测', async () => {
    // Arrange
    const port: number = await getClosedPort();

    // Act
    const result: CheckResult = await checkService(
      makeService({ health_url: '   ', host: '127.0.0.1', port }),
      3000,
    );

    // Assert
    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.status).toBe('offline');
    expect(result.error_message).toBe('ECONNREFUSED');
  });

  it('探测失败时 status 应为 offline 且保留错误信息', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(503));

    // Act
    const result: CheckResult = await checkService(
      makeService({ id: 7, health_url: 'http://127.0.0.1:8080/health' }),
      3000,
    );

    // Assert
    expect(result.service_id).toBe(7);
    expect(result.status).toBe('offline');
    expect(result.error_message).toBe('HTTP 503');
  });

  it('checked_at 应为合法 ISO 8601 时间戳', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(200));

    // Act
    const result: CheckResult = await checkService(
      makeService({ health_url: 'http://127.0.0.1:8080/health' }),
      3000,
    );

    // Assert
    expect(result.checked_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    expect(Number.isNaN(Date.parse(result.checked_at))).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// checkAll
// ---------------------------------------------------------------------------

describe('checkAll', () => {
  const fetchMock = vi.fn();

  beforeEach((): void => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach((): void => {
    vi.unstubAllGlobals();
  });

  it('空数组应安全返回空结果', async () => {
    // Act
    const results: CheckResult[] = await checkAll([], 3000);

    // Assert
    expect(results).toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('结果顺序应与入参 services 顺序严格一致', async () => {
    // Arrange：让靠前的服务响应更慢，验证结果不会被完成顺序打乱。
    const delays: Record<string, number> = {
      'http://svc/1': 60,
      'http://svc/2': 10,
      'http://svc/3': 30,
    };
    fetchMock.mockImplementation(
      (url: string): Promise<Response> =>
        new Promise<Response>((resolve): void => {
          globalThis.setTimeout((): void => resolve(makeResponse(200)), delays[url] ?? 0);
        }),
    );
    const services: Service[] = [1, 2, 3].map(
      (n: number): Service =>
        makeService({ id: n, name: `svc-${n}`, health_url: `http://svc/${n}` }),
    );

    // Act
    const results: CheckResult[] = await checkAll(services, 3000);

    // Assert
    expect(results.map((r: CheckResult): number => r.service_id)).toEqual([1, 2, 3]);
    expect(results.map((r: CheckResult): string => r.name)).toEqual(['svc-1', 'svc-2', 'svc-3']);
  });

  it('应并发执行而非串行（总耗时远小于累加耗时）', async () => {
    // Arrange：6 个服务，每个固定 60ms；串行需 ~360ms，并发约 60ms。
    const perCallMs: number = 60;
    const count: number = 6;
    fetchMock.mockImplementation(
      (): Promise<Response> =>
        new Promise<Response>((resolve): void => {
          globalThis.setTimeout((): void => resolve(makeResponse(200)), perCallMs);
        }),
    );
    const services: Service[] = Array.from(
      { length: count },
      (_v: unknown, i: number): Service =>
        makeService({ id: i + 1, name: `svc-${i + 1}`, health_url: `http://svc/${i + 1}` }),
    );

    // Act
    const startedAt: number = Date.now();
    const results: CheckResult[] = await checkAll(services, 3000);
    const elapsed: number = Date.now() - startedAt;

    // Assert
    expect(results).toHaveLength(count);
    expect(fetchMock).toHaveBeenCalledTimes(count);
    expect(elapsed).toBeLessThan(perCallMs * count * 0.6);
  });

  it('单个服务失败不应影响其它服务的结果', async () => {
    // Arrange
    fetchMock.mockImplementation((url: string): Promise<Response> => {
      if (url === 'http://svc/2') {
        const err: Error & { cause?: unknown } = new TypeError('fetch failed');
        err.cause = { code: 'ECONNREFUSED' };
        return Promise.reject(err);
      }
      return Promise.resolve(makeResponse(200));
    });
    const services: Service[] = [1, 2, 3].map(
      (n: number): Service => makeService({ id: n, health_url: `http://svc/${n}` }),
    );

    // Act
    const results: CheckResult[] = await checkAll(services, 3000);

    // Assert
    expect(results.map((r: CheckResult): string => r.status)).toEqual([
      'online',
      'offline',
      'online',
    ]);
    expect(results[1].error_message).toBe('ECONNREFUSED');
  });

  it('混合 HTTP / TCP 服务应各自采用对应策略', async () => {
    // Arrange
    fetchMock.mockResolvedValue(makeResponse(200));
    const server: TcpFixture = await startTcpServer();

    try {
      const services: Service[] = [
        makeService({ id: 1, health_url: 'http://svc/http-one' }),
        makeService({ id: 2, health_url: null, host: '127.0.0.1', port: server.port }),
      ];

      // Act
      const results: CheckResult[] = await checkAll(services, 3000);

      // Assert
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(results.map((r: CheckResult): string => r.status)).toEqual(['online', 'online']);
    } finally {
      await server.close();
    }
  });
});
