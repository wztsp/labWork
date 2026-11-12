/**
 * 进程管理核心 —— ProcessManager 单例。
 *
 * 职责：
 *   - spawn 子进程并追踪 PID（内存 Map）
 *   - 监听 exit 事件，区分主动停止（stopped）与意外退出（crashed）
 *   - Windows tasklist/taskkill 封装
 *   - admin 重启后通过 reconcileOnStartup 修正 PID 状态一致性
 *   - 内存环形缓冲子进程 stdout/stderr 输出（最近 N 行，不落库）
 *
 * 设计约定（见架构文档 7.4）：
 *   - 模块级单例，整个 admin 生命周期共享
 *   - intentionalStops 标志位防止主动停止被误判为 crashed
 *   - shell:true 执行自由文本命令，支持路径含空格、管道等
 */

import { execSync, spawn } from 'node:child_process';
import type { ChildProcess } from 'node:child_process';
import { PROCESS_OUTPUT_BUFFER_LINES } from '../config/defaults';
import { ServiceRepository } from '../routes/services';
import type { ProcessInfo, ProcessStatus, Service, StartResult, StopResult } from '../types';
import { HttpError, nowIso } from '../utils/response';

/**
 * 进程管理器单例。
 *
 * 通过 `processManager` 导出实例，全应用共享。数据库操作经由 `ServiceRepository` 完成，
 * 因 processManager.ts 与 routes/services.ts 存在循环引用，repository 采用惰性创建
 * 以确保模块加载顺序安全（运行时 ServiceRepository 已完成定义）。
 */
export class ProcessManager {
  /** 内存中持有的子进程引用，key=serviceId。 */
  private readonly processes: Map<number, ChildProcess> = new Map<number, ChildProcess>();

  /** 子进程输出环形缓冲，key=serviceId，value=最近 N 行。 */
  private readonly outputBuffers: Map<number, string[]> = new Map<number, string[]>();

  /** 主动停止标记集合，防止 exit 事件将主动停止误判为 crashed。 */
  private readonly intentionalStops: Set<number> = new Set<number>();

  /** 惰性创建的 ServiceRepository 实例（避免循环引用的加载时问题）。 */
  private repository: ServiceRepository | null = null;

  /** 获取 ServiceRepository 实例（惰性初始化）。 */
  private get repo(): ServiceRepository {
    if (this.repository === null) {
      this.repository = new ServiceRepository();
    }
    return this.repository;
  }

  /**
   * 启动服务进程。
   *
   * @param service 服务配置（含 start_command / work_dir）
   * @returns 启动结果（pid + started_at）
   * @throws {HttpError} 400 未配置启动命令；409 进程已在运行；500 spawn 失败
   */
  public start(service: Service): StartResult {
    if (!service.start_command || service.start_command.trim() === '') {
      throw new HttpError(400, `服务 ${service.name} 未配置启动命令`);
    }
    if (this.processes.has(service.id)) {
      const existing: ChildProcess | undefined = this.processes.get(service.id);
      const pid: number | undefined = existing?.pid;
      throw new HttpError(409, `服务 ${service.name} 进程已在运行 (PID=${pid ?? 'unknown'})`);
    }

    let child: ChildProcess;
    try {
      child = spawn(service.start_command, [], {
        shell: true,
        cwd: service.work_dir ?? undefined,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (err) {
      const message: string = err instanceof Error ? err.message : String(err);
      throw new HttpError(500, `启动进程失败: ${message}`);
    }

    const pid: number | undefined = child.pid;
    if (pid === undefined) {
      throw new HttpError(500, `启动进程失败: 无法获取进程 PID`);
    }

    // 注册 exit 事件：区分主动停止与意外崩溃
    child.on('exit', (code: number | null, signal: string | null): void => {
      this.onProcessExit(service.id, code, signal);
    });

    // 注册 error 事件：spawn 后异步错误（如 shell 不可用）
    child.on('error', (err: Error): void => {
      // eslint-disable-next-line no-console
      console.error(`[processManager] 进程错误 (service_id=${service.id}):`, err.message);
      this.onProcessExit(service.id, null, null);
    });

    // 注册 stdout/stderr 监听 → 环形缓冲
    if (child.stdout !== null) {
      child.stdout.on('data', (data: Buffer): void => {
        this.appendOutput(service.id, data.toString('utf-8'));
      });
    }
    if (child.stderr !== null) {
      child.stderr.on('data', (data: Buffer): void => {
        this.appendOutput(service.id, data.toString('utf-8'));
      });
    }

    this.processes.set(service.id, child);
    const startedAt: string = nowIso();

    this.repo.upsertProcessRecord({
      service_id: service.id,
      pid,
      process_status: 'running',
      started_at: startedAt,
      stopped_at: null,
      exit_code: null,
    });

    return {
      service_id: service.id,
      pid,
      process_status: 'running',
      started_at: startedAt,
    };
  }

  /**
   * 停止服务进程（Windows taskkill /F 强制终止）。
   *
   * @param serviceId 服务 ID
   * @returns 停止结果
   * @throws {HttpError} 400 进程未在运行
   */
  public stop(serviceId: number): StopResult {
    const record: ProcessInfo | null = this.repo.findProcessRecord(serviceId);
    if (record === null || record.process_status !== 'running' || record.pid === null) {
      const service: Service | null = this.repo.findById(serviceId);
      const name: string = service?.name ?? String(serviceId);
      throw new HttpError(400, `服务 ${name} 进程未在运行`);
    }

    // 标记主动停止，防止 exit 事件覆盖为 crashed
    this.intentionalStops.add(serviceId);

    try {
      this.killPid(record.pid);
    } catch (err) {
      // taskkill 失败时清除标记，继续更新状态
      this.intentionalStops.delete(serviceId);
      const message: string = err instanceof Error ? err.message : String(err);
      throw new HttpError(500, `停止进程失败: ${message}`);
    }

    this.processes.delete(serviceId);
    // 主动停止完成后清理标记，防止后续 start→crash 被误判为主动停止
    this.intentionalStops.delete(serviceId);
    this.repo.upsertProcessRecord({
      service_id: serviceId,
      pid: null,
      process_status: 'stopped',
      started_at: null,
      stopped_at: nowIso(),
      exit_code: null,
    });

    return {
      service_id: serviceId,
      process_status: 'stopped',
      message: '进程已停止',
    };
  }

  /**
   * 查询某服务当前的进程状态。
   *
   * @param serviceId 服务 ID
   * @returns 进程状态，无记录时返回 'unknown'
   */
  public getProcessStatus(serviceId: number): ProcessStatus {
    const record: ProcessInfo | null = this.repo.findProcessRecord(serviceId);
    return record?.process_status ?? 'unknown';
  }

  /**
   * 获取子进程输出缓冲副本（P2 功能预留，本期不暴露 API）。
   *
   * @param serviceId 服务 ID
   * @returns 输出行数组副本
   */
  public getOutputBuffer(serviceId: number): string[] {
    const buffer: string[] | undefined = this.outputBuffers.get(serviceId);
    return buffer === undefined ? [] : [...buffer];
  }

  /**
   * admin 重启后状态一致性修复。
   *
   * 遍历 DB 中 process_status='running' 的记录，用 tasklist 检测 PID 是否存活：
   * 存活 → 维持 running；不存活 → 修正为 stopped。
   */
  public reconcileOnStartup(): void {
    const serviceIds: number[] = this.repo.getRunningServiceIds();
    for (const serviceId of serviceIds) {
      const record: ProcessInfo | null = this.repo.findProcessRecord(serviceId);
      if (record === null || record.pid === null) {
        continue;
      }
      if (!this.isPidAlive(record.pid)) {
        this.repo.upsertProcessRecord({
          service_id: serviceId,
          pid: null,
          process_status: 'stopped',
          started_at: null,
          stopped_at: nowIso(),
          exit_code: null,
        });
      }
    }
  }

  /**
   * 子进程 exit 事件处理。
   *
   * 通过 intentionalStops 区分主动停止（跳过，stop 已更新状态）与意外退出（标记 crashed）。
   *
   * @param serviceId 服务 ID
   * @param code 退出码（正常退出）或 null（被信号终止）
   * @param _signal 终止信号名称（未使用）
   */
  private onProcessExit(serviceId: number, code: number | null, _signal: string | null): void {
    // 防止 error + exit 双重触发
    if (!this.processes.has(serviceId)) {
      // 防御性清理：stop() 已删除 processes 并清理标记，
      // 此处兜底确保遗留的 intentionalStops 不泄漏
      this.intentionalStops.delete(serviceId);
      return;
    }
    this.processes.delete(serviceId);

    if (this.intentionalStops.has(serviceId)) {
      this.intentionalStops.delete(serviceId);
      return;
    }

    // 意外退出 → 标记 crashed
    this.repo.upsertProcessRecord({
      service_id: serviceId,
      pid: null,
      process_status: 'crashed',
      started_at: null,
      stopped_at: nowIso(),
      exit_code: code,
    });
  }

  /**
   * 检测 PID 是否存活（Windows tasklist）。
   *
   * @param pid 进程 PID
   * @returns true=存活，false=不存在或检测失败
   */
  private isPidAlive(pid: number): boolean {
    try {
      const output: string = execSync(`tasklist /FI "PID eq ${pid}" /NH /FO CSV`, {
        encoding: 'utf-8',
        stdio: ['ignore', 'pipe', 'ignore'],
      });
      return output.includes(String(pid));
    } catch {
      return false;
    }
  }

  /**
   * 强制终止进程（Windows taskkill /F）。
   *
   * @param pid 进程 PID
   */
  private killPid(pid: number): void {
    execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' });
  }

  /**
   * 追加输出到环形缓冲（超长时移除最早行）。
   *
   * @param serviceId 服务 ID
   * @param chunk 输出文本块
   */
  private appendOutput(serviceId: number, chunk: string): void {
    const buffer: string[] = this.outputBuffers.get(serviceId) ?? [];
    buffer.push(chunk.trimEnd());
    while (buffer.length > PROCESS_OUTPUT_BUFFER_LINES) {
      buffer.shift();
    }
    this.outputBuffers.set(serviceId, buffer);
  }
}

/** 进程管理器模块级单例。 */
export const processManager: ProcessManager = new ProcessManager();
