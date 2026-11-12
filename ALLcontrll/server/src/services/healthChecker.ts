/**
 * 服务健康检查引擎。
 *
 * 探测策略（见架构文档 8.3）：
 *   - `health_url` 非空 → HTTP 探测，响应 2xx 判定为 online；
 *   - 否则 → TCP 端口探测，socket 连接成功判定为 online。
 * 单次探测超时 3000ms，全量检测使用 `Promise.all` 并发执行。
 */

import net from 'node:net';
import { HEALTH_CHECK_TIMEOUT_MS } from '../config/defaults';
import type { CheckResult, ProbeResult, Service } from '../types';
import { nowIso } from '../utils/response';

/**
 * TCP 端口探测。
 *
 * @param host 目标主机
 * @param port 目标端口
 * @param timeoutMs 超时时间（毫秒）
 * @returns 探测结果，永不 reject
 */
export function tcpProbe(host: string, port: number, timeoutMs: number): Promise<ProbeResult> {
  return new Promise<ProbeResult>((resolve): void => {
    const startedAt: number = Date.now();
    const socket: net.Socket = new net.Socket();
    let settled: boolean = false;

    const finish = (result: ProbeResult): void => {
      if (settled) {
        return;
      }
      settled = true;
      socket.removeAllListeners();
      socket.destroy();
      resolve(result);
    };

    socket.setTimeout(timeoutMs);

    socket.once('connect', (): void => {
      finish({ ok: true, responseMs: Date.now() - startedAt, errorMessage: null });
    });

    socket.once('timeout', (): void => {
      finish({ ok: false, responseMs: null, errorMessage: `TIMEOUT (>${timeoutMs}ms)` });
    });

    socket.once('error', (err: Error): void => {
      const code: string | undefined = (err as NodeJS.ErrnoException).code;
      finish({ ok: false, responseMs: null, errorMessage: code ?? err.message });
    });

    try {
      socket.connect(port, host);
    } catch (err) {
      finish({
        ok: false,
        responseMs: null,
        errorMessage: err instanceof Error ? err.message : String(err),
      });
    }
  });
}

/**
 * HTTP 探测：GET 指定 URL，2xx 判定为在线。
 *
 * @param url 健康检查地址
 * @param timeoutMs 超时时间（毫秒）
 * @returns 探测结果，永不 reject
 */
export async function httpProbe(url: string, timeoutMs: number): Promise<ProbeResult> {
  const startedAt: number = Date.now();
  const controller: AbortController = new AbortController();
  const timer: NodeJS.Timeout = setTimeout((): void => controller.abort(), timeoutMs);

  try {
    const response: Response = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      redirect: 'follow',
    });
    const elapsed: number = Date.now() - startedAt;
    if (response.ok) {
      return { ok: true, responseMs: elapsed, errorMessage: null };
    }
    return { ok: false, responseMs: elapsed, errorMessage: `HTTP ${response.status}` };
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') {
      return { ok: false, responseMs: null, errorMessage: `TIMEOUT (>${timeoutMs}ms)` };
    }
    const cause: unknown = err instanceof Error ? (err as Error & { cause?: unknown }).cause : undefined;
    const causeCode: string | undefined =
      cause !== null && typeof cause === 'object' && 'code' in (cause as Record<string, unknown>)
        ? String((cause as Record<string, unknown>).code)
        : undefined;
    return {
      ok: false,
      responseMs: null,
      errorMessage: causeCode ?? (err instanceof Error ? err.message : String(err)),
    };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 检测单个服务。
 *
 * @param service 服务配置
 * @param timeoutMs 超时时间（毫秒），默认取全局配置
 * @returns 检测结果
 */
export async function checkService(
  service: Service,
  timeoutMs: number = HEALTH_CHECK_TIMEOUT_MS,
): Promise<CheckResult> {
  const useHttp: boolean = typeof service.health_url === 'string' && service.health_url.trim() !== '';
  const probe: ProbeResult = useHttp
    ? await httpProbe((service.health_url as string).trim(), timeoutMs)
    : await tcpProbe(service.host, service.port, timeoutMs);

  return {
    service_id: service.id,
    name: service.name,
    status: probe.ok ? 'online' : 'offline',
    response_ms: probe.responseMs,
    error_message: probe.errorMessage,
    checked_at: nowIso(),
  };
}

/**
 * 并发检测全部服务。
 *
 * @param services 服务列表
 * @param timeoutMs 单服务超时时间（毫秒）
 * @returns 与入参顺序一致的检测结果数组
 */
export async function checkAll(
  services: readonly Service[],
  timeoutMs: number = HEALTH_CHECK_TIMEOUT_MS,
): Promise<CheckResult[]> {
  return Promise.all(services.map((service: Service): Promise<CheckResult> => checkService(service, timeoutMs)));
}
