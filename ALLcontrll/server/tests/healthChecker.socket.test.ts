/**
 * `tcpProbe` 的超时 / 异常分支测试。
 *
 * 这些分支难以用真实网络稳定复现（依赖不可路由地址、防火墙行为），
 * 因此以一个可控的假 Socket 替换 `node:net`，做确定性验证。
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';

const hoisted = vi.hoisted(() => {
  type Behavior = 'connect' | 'timeout' | 'error' | 'throwOnConnect';

  const state = {
    behavior: 'connect' as Behavior,
    errorCode: undefined as string | undefined,
    errorMessage: 'socket exploded',
    lastTimeoutMs: -1,
    connectArgs: null as { port: number; host: string } | null,
    destroyCount: 0,
    removeAllListenersCount: 0,
  };

  /** 仅实现 healthChecker 用到的 Socket 接口子集。 */
  class FakeSocket {
    private readonly handlers: Map<string, (arg?: unknown) => void> = new Map();

    public setTimeout(ms: number): this {
      state.lastTimeoutMs = ms;
      return this;
    }

    public once(event: string, handler: (arg?: unknown) => void): this {
      this.handlers.set(event, handler);
      return this;
    }

    public removeAllListeners(): this {
      state.removeAllListenersCount += 1;
      this.handlers.clear();
      return this;
    }

    public destroy(): this {
      state.destroyCount += 1;
      return this;
    }

    public connect(port: number, host: string): this {
      state.connectArgs = { port, host };
      if (state.behavior === 'throwOnConnect') {
        throw new Error(state.errorMessage);
      }
      globalThis.setTimeout((): void => {
        if (state.behavior === 'connect') {
          this.handlers.get('connect')?.();
          return;
        }
        if (state.behavior === 'timeout') {
          this.handlers.get('timeout')?.();
          return;
        }
        const err: Error & { code?: string } = new Error(state.errorMessage);
        if (state.errorCode !== undefined) {
          err.code = state.errorCode;
        }
        this.handlers.get('error')?.(err);
      }, 0);
      return this;
    }
  }

  return { state, FakeSocket };
});

vi.mock('node:net', () => ({
  default: { Socket: hoisted.FakeSocket },
  Socket: hoisted.FakeSocket,
}));

const { tcpProbe } = await import('../src/services/healthChecker');
const state = hoisted.state;

beforeEach((): void => {
  state.behavior = 'connect';
  state.errorCode = undefined;
  state.errorMessage = 'socket exploded';
  state.lastTimeoutMs = -1;
  state.connectArgs = null;
  state.destroyCount = 0;
  state.removeAllListenersCount = 0;
});

describe('tcpProbe —— 超时与异常分支', () => {
  it('socket 触发 timeout 时应返回 TIMEOUT 并携带阈值', async () => {
    // Arrange
    state.behavior = 'timeout';

    // Act
    const result = await tcpProbe('10.0.0.1', 8080, 1500);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).toBe('TIMEOUT (>1500ms)');
  });

  it('应把 timeoutMs 透传给 socket.setTimeout', async () => {
    // Arrange
    state.behavior = 'timeout';

    // Act
    await tcpProbe('10.0.0.1', 8080, 777);

    // Assert
    expect(state.lastTimeoutMs).toBe(777);
  });

  it('应以 (port, host) 的顺序发起连接', async () => {
    // Act
    await tcpProbe('192.168.1.5', 9001, 1000);

    // Assert
    expect(state.connectArgs).toEqual({ port: 9001, host: '192.168.1.5' });
  });

  it('error 事件带 code 时应回报 code', async () => {
    // Arrange
    state.behavior = 'error';
    state.errorCode = 'EHOSTUNREACH';

    // Act
    const result = await tcpProbe('10.0.0.1', 8080, 1000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.errorMessage).toBe('EHOSTUNREACH');
  });

  it('error 事件无 code 时应回退为 message', async () => {
    // Arrange
    state.behavior = 'error';
    state.errorCode = undefined;
    state.errorMessage = 'unexpected socket failure';

    // Act
    const result = await tcpProbe('10.0.0.1', 8080, 1000);

    // Assert
    expect(result.errorMessage).toBe('unexpected socket failure');
  });

  it('connect 同步抛异常时应被捕获并转为 offline 结果', async () => {
    // Arrange
    state.behavior = 'throwOnConnect';
    state.errorMessage = 'invalid port';

    // Act
    const result = await tcpProbe('10.0.0.1', -1, 1000);

    // Assert
    expect(result.ok).toBe(false);
    expect(result.responseMs).toBeNull();
    expect(result.errorMessage).toBe('invalid port');
  });

  it('无论成功或失败都应释放 socket 资源', async () => {
    // Act
    await tcpProbe('127.0.0.1', 8080, 1000);

    // Assert
    expect(state.removeAllListenersCount).toBe(1);
    expect(state.destroyCount).toBe(1);
  });

  it('连接成功时应返回 ok=true 且不带错误信息', async () => {
    // Act
    const result = await tcpProbe('127.0.0.1', 8080, 1000);

    // Assert
    expect(result.ok).toBe(true);
    expect(result.errorMessage).toBeNull();
    expect(result.responseMs).toBeGreaterThanOrEqual(0);
  });
});
