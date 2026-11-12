/**
 * `ProcessManager` 单元测试。
 *
 * 通过 vi.mock 隔离 node:child_process（spawn/execSync）与
 * ServiceRepository（DB 访问），专注验证进程管理逻辑：
 *   - start/stop 状态转换与错误处理
 *   - intentionalStops 标志位防止主动停止被误判为 crashed
 *   - reconcileOnStartup 状态修复
 *   - 输出环形缓冲
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';

// ---------------------------------------------------------------------------
// vi.hoisted —— 在 vi.mock 工厂执行前创建共享 mock 对象
// ---------------------------------------------------------------------------

const mocks = vi.hoisted(() => {
  /** 每次 spawn 调用创建一个独立的 mock 子进程，便于分别触发事件。 */
  const children: Array<{
    pid: number;
    on: ReturnType<typeof vi.fn>;
    stdout: { on: ReturnType<typeof vi.fn> };
    stderr: { on: ReturnType<typeof vi.fn> };
  }> = [];

  const spawnImpl = vi.fn((): unknown => {
    const child = {
      pid: 10000 + children.length,
      on: vi.fn(),
      stdout: { on: vi.fn() },
      stderr: { on: vi.fn() },
    };
    children.push(child);
    return child;
  });

  return {
    children,
    spawn: spawnImpl,
    execSync: vi.fn(),
    upsertProcessRecord: vi.fn(),
    findProcessRecord: vi.fn((): unknown => null),
    findById: vi.fn((): unknown => ({ id: 1, name: 'test-service' })),
    getRunningServiceIds: vi.fn((): number[] => []),
  };
});

// ---------------------------------------------------------------------------
// vi.mock —— 必须在 import 之前声明（vitest 自动提升）
// ---------------------------------------------------------------------------

vi.mock('node:child_process', () => ({
  spawn: mocks.spawn,
  execSync: mocks.execSync,
}));

vi.mock('../src/routes/services', () => ({
  ServiceRepository: class MockServiceRepository {
    upsertProcessRecord = mocks.upsertProcessRecord;
    findProcessRecord = mocks.findProcessRecord;
    findById = mocks.findById;
    getRunningServiceIds = mocks.getRunningServiceIds;
  },
  summarize: vi.fn(),
}));

// ---------------------------------------------------------------------------
// 导入被测模块（此时 mock 已生效）
// ---------------------------------------------------------------------------

import { ProcessManager } from '../src/services/processManager';
import type { Service } from '../src/types';
import { HttpError } from '../src/utils/response';

// ---------------------------------------------------------------------------
// 测试夹具
// ---------------------------------------------------------------------------

/** 构造服务对象。 */
function makeService(overrides: Partial<Service> = {}): Service {
  return {
    id: 1,
    name: 'test-service',
    type: 'backend',
    host: '127.0.0.1',
    port: 8080,
    health_url: null,
    enabled: true,
    sort_order: 1,
    start_command: 'java -jar app.jar',
    work_dir: null,
    created_at: '2025-01-01T00:00:00.000Z',
    updated_at: '2025-01-01T00:00:00.000Z',
    status: 'unknown',
    last_checked_at: null,
    response_ms: null,
    process_status: 'unknown',
    pid: null,
    process_started_at: null,
    ...overrides,
  };
}

/** 构造 running 状态的进程记录。 */
function runningRecord(serviceId: number = 1, pid: number = 10000): {
  service_id: number;
  pid: number;
  process_status: 'running';
  started_at: string;
  stopped_at: null;
  exit_code: null;
} {
  return {
    service_id: serviceId,
    pid,
    process_status: 'running',
    started_at: '2025-01-01T00:00:00.000Z',
    stopped_at: null,
    exit_code: null,
  };
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

describe('ProcessManager', () => {
  let pm: ProcessManager;

  beforeEach((): void => {
    // 清除所有 mock 调用记录（保留实现）
    vi.clearAllMocks();
    // 清空子进程数组
    mocks.children.length = 0;
    // 重置默认返回值
    mocks.findProcessRecord.mockReturnValue(null);
    mocks.findById.mockReturnValue({ id: 1, name: 'test-service' });
    mocks.getRunningServiceIds.mockReturnValue([]);
    // execSync 默认返回空字符串（tasklist/taskkill 成功）
    mocks.execSync.mockReturnValue('');
    // 创建新的 ProcessManager 实例（避免单例状态污染）
    pm = new ProcessManager();
  });

  // -------------------------------------------------------------------------
  // start
  // -------------------------------------------------------------------------

  describe('start', () => {
    it('未配置启动命令时应抛出 400', () => {
      expect(() => pm.start(makeService({ start_command: '' }))).toThrow(HttpError);
      expect(() => pm.start(makeService({ start_command: '' }))).toThrow('未配置启动命令');
    });

    it('start_command 为 null 时应抛出 400', () => {
      expect(() => pm.start(makeService({ start_command: null }))).toThrow(HttpError);
    });

    it('start_command 为纯空白时应抛出 400', () => {
      expect(() => pm.start(makeService({ start_command: '   ' }))).toThrow(HttpError);
    });

    it('进程已在运行时应抛出 409', () => {
      pm.start(makeService());
      expect(() => pm.start(makeService())).toThrow(HttpError);
      expect(() => pm.start(makeService())).toThrow('进程已在运行');
    });

    it('启动成功时应返回 StartResult', () => {
      const result = pm.start(makeService());
      expect(result.service_id).toBe(1);
      expect(result.pid).toBe(10000);
      expect(result.process_status).toBe('running');
      expect(typeof result.started_at).toBe('string');
    });

    it('启动后应调用 upsertProcessRecord 写入 running 状态', () => {
      pm.start(makeService());
      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          pid: 10000,
          process_status: 'running',
        }),
      );
    });

    it('启动后应注册 exit / error / stdout / stderr 事件', () => {
      pm.start(makeService());
      const child = mocks.children[0];
      expect(child.on).toHaveBeenCalledWith('exit', expect.any(Function));
      expect(child.on).toHaveBeenCalledWith('error', expect.any(Function));
      expect(child.stdout.on).toHaveBeenCalledWith('data', expect.any(Function));
      expect(child.stderr.on).toHaveBeenCalledWith('data', expect.any(Function));
    });
  });

  // -------------------------------------------------------------------------
  // stop
  // -------------------------------------------------------------------------

  describe('stop', () => {
    it('进程未运行时应抛出 400', () => {
      mocks.findProcessRecord.mockReturnValue(null);
      expect(() => pm.stop(1)).toThrow(HttpError);
      expect(() => pm.stop(1)).toThrow('进程未在运行');
    });

    it('进程状态非 running 时应抛出 400', () => {
      mocks.findProcessRecord.mockReturnValue({
        service_id: 1,
        pid: 10000,
        process_status: 'stopped',
        started_at: null,
        stopped_at: '2025-01-01T00:00:00.000Z',
        exit_code: null,
      });
      expect(() => pm.stop(1)).toThrow(HttpError);
    });

    it('停止成功时应返回 StopResult', () => {
      pm.start(makeService());
      mocks.findProcessRecord.mockReturnValue(runningRecord());
      const result = pm.stop(1);
      expect(result.service_id).toBe(1);
      expect(result.process_status).toBe('stopped');
      expect(result.message).toBe('进程已停止');
    });

    it('停止后应调用 upsertProcessRecord 写入 stopped 状态', () => {
      pm.start(makeService());
      mocks.findProcessRecord.mockReturnValue(runningRecord());
      pm.stop(1);
      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          pid: null,
          process_status: 'stopped',
        }),
      );
    });

    it('停止时应调用 taskkill 强制终止进程', () => {
      pm.start(makeService());
      mocks.findProcessRecord.mockReturnValue(runningRecord(1, 10000));
      pm.stop(1);
      const killCall = mocks.execSync.mock.calls.find(
        (call: unknown[]): boolean => typeof call[0] === 'string' && (call[0] as string).includes('taskkill'),
      );
      expect(killCall).toBeDefined();
      expect((killCall![0] as string)).toContain('/F');
      expect((killCall![0] as string)).toContain('/PID 10000');
    });

    it('taskkill 失败时应抛出 500', () => {
      pm.start(makeService());
      mocks.findProcessRecord.mockReturnValue(runningRecord(1, 10000));
      mocks.execSync.mockImplementationOnce((): never => {
        throw new Error('taskkill failed');
      });

      // 只调用一次 stop（mockImplementationOnce 仅生效一次）
      let error: unknown;
      try {
        pm.stop(1);
      } catch (e) {
        error = e;
      }
      expect(error).toBeInstanceOf(HttpError);
      expect((error as Error).message).toContain('停止进程失败');
    });
  });

  // -------------------------------------------------------------------------
  // getProcessStatus
  // -------------------------------------------------------------------------

  describe('getProcessStatus', () => {
    it('无进程记录时应返回 unknown', () => {
      mocks.findProcessRecord.mockReturnValue(null);
      expect(pm.getProcessStatus(1)).toBe('unknown');
    });

    it('有进程记录时应返回对应状态', () => {
      mocks.findProcessRecord.mockReturnValue({
        service_id: 1,
        pid: 10000,
        process_status: 'running',
        started_at: '2025-01-01T00:00:00.000Z',
        stopped_at: null,
        exit_code: null,
      });
      expect(pm.getProcessStatus(1)).toBe('running');
    });
  });

  // -------------------------------------------------------------------------
  // getOutputBuffer
  // -------------------------------------------------------------------------

  describe('getOutputBuffer', () => {
    it('未启动的进程应返回空数组', () => {
      expect(pm.getOutputBuffer(1)).toEqual([]);
    });

    it('应缓冲子进程 stdout 输出', () => {
      pm.start(makeService());
      const child = mocks.children[0];
      // 获取 stdout 'data' 回调并触发
      const stdoutDataCall = child.stdout.on.mock.calls.find(
        (call: unknown[]): boolean => call[0] === 'data',
      );
      const stdoutHandler = stdoutDataCall?.[1] as (data: Buffer) => void;
      stdoutHandler(Buffer.from('Hello World\n'));
      expect(pm.getOutputBuffer(1)).toEqual(['Hello World']);
    });
  });

  // -------------------------------------------------------------------------
  // reconcileOnStartup
  // -------------------------------------------------------------------------

  describe('reconcileOnStartup', () => {
    it('无 running 记录时应安全空转', () => {
      mocks.getRunningServiceIds.mockReturnValue([]);
      pm.reconcileOnStartup();
      expect(mocks.upsertProcessRecord).not.toHaveBeenCalled();
    });

    it('PID 已不存活时应修正为 stopped', () => {
      mocks.getRunningServiceIds.mockReturnValue([1]);
      mocks.findProcessRecord.mockReturnValue(runningRecord(1, 99999));
      // tasklist 返回不含 PID → isPidAlive=false
      mocks.execSync.mockReturnValue('信息: 没有运行的任务匹配指定标准。');
      pm.reconcileOnStartup();
      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          process_status: 'stopped',
          pid: null,
        }),
      );
    });

    it('PID 仍存活时应维持 running（不调用 upsert）', () => {
      mocks.getRunningServiceIds.mockReturnValue([1]);
      mocks.findProcessRecord.mockReturnValue(runningRecord(1, 10000));
      // tasklist 返回包含 PID → isPidAlive=true
      mocks.execSync.mockReturnValue('"10000","cmd.exe"');
      pm.reconcileOnStartup();
      expect(mocks.upsertProcessRecord).not.toHaveBeenCalled();
    });

    it('findProcessRecord 返回 null 时应跳过', () => {
      mocks.getRunningServiceIds.mockReturnValue([1]);
      mocks.findProcessRecord.mockReturnValue(null);
      pm.reconcileOnStartup();
      expect(mocks.upsertProcessRecord).not.toHaveBeenCalled();
    });

    it('pid 为 null 时应跳过', () => {
      mocks.getRunningServiceIds.mockReturnValue([1]);
      mocks.findProcessRecord.mockReturnValue({
        service_id: 1,
        pid: null,
        process_status: 'running',
        started_at: '2025-01-01T00:00:00.000Z',
        stopped_at: null,
        exit_code: null,
      });
      pm.reconcileOnStartup();
      expect(mocks.upsertProcessRecord).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // exit 事件处理
  // -------------------------------------------------------------------------

  describe('exit 事件 —— 意外退出', () => {
    it('子进程意外退出时应标记为 crashed', () => {
      pm.start(makeService());
      const child = mocks.children[0];
      // 获取 exit 回调
      const exitCall = child.on.mock.calls.find(
        (call: unknown[]): boolean => call[0] === 'exit',
      );
      const exitHandler = exitCall?.[1] as (code: number | null, signal: string | null) => void;

      mocks.upsertProcessRecord.mockClear();
      exitHandler(1, null);

      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          process_status: 'crashed',
          exit_code: 1,
        }),
      );
    });

    it('error 事件应触发 onProcessExit（防止进程残留）', () => {
      pm.start(makeService());
      const child = mocks.children[0];
      const errorCall = child.on.mock.calls.find(
        (call: unknown[]): boolean => call[0] === 'error',
      );
      const errorHandler = errorCall?.[1] as (err: Error) => void;

      mocks.upsertProcessRecord.mockClear();
      errorHandler(new Error('spawn ENOENT'));

      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          process_status: 'crashed',
        }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // [BUG] intentionalStops 泄漏 —— stop→start→crash 序列
  // -------------------------------------------------------------------------

  describe('intentionalStops 泄漏 Bug（stop→start→crash）', () => {
    it('stop 后再 start，新进程崩溃时应正确标记为 crashed', () => {
      const service = makeService();

      // Step 1: 首次启动
      pm.start(service);
      expect(mocks.children).toHaveLength(1);

      // Step 2: 停止 —— intentionalStops 被设置，processes 被清除
      mocks.findProcessRecord.mockReturnValue(runningRecord(1, 10000));
      pm.stop(1);

      // Step 3: 再次启动 —— 新子进程
      pm.start(service);
      expect(mocks.children).toHaveLength(2);

      // Step 4: 模拟新进程意外退出（崩溃）
      const secondChild = mocks.children[1];
      const exitCall = secondChild.on.mock.calls.find(
        (call: unknown[]): boolean => call[0] === 'exit',
      );
      const exitHandler = exitCall?.[1] as (code: number | null, signal: string | null) => void;

      mocks.upsertProcessRecord.mockClear();
      exitHandler(137, null); // SIGKILL

      // 期望：崩溃应被记录为 crashed
      // 如果 intentionalStops 泄漏（stop 后未清理），此处 onProcessExit 会误判为主动停止，
      // 跳过 crashed 记录，upsertProcessRecord 不会被调用。
      expect(mocks.upsertProcessRecord).toHaveBeenCalledWith(
        expect.objectContaining({
          service_id: 1,
          process_status: 'crashed',
          exit_code: 137,
        }),
      );
    });
  });
});
