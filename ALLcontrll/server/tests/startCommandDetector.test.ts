/**
 * `startCommandDetector` 单元测试。
 *
 * 覆盖点：
 *   1. 匹配逻辑（精确 > 前缀 > 包含，同优先级取最短文件名，无匹配返回空）
 *   2. 命令生成（jar / package.json / docker-compose / exe / bat）
 *   3. 目录扫描（最大深度 3 层，跳过 node_modules / .git，scanned_files 计数）
 *   4. applyDetectedConfigs（正确调用 repository.update，传入正确字段）
 *
 * 测试使用临时目录与文件，完成后自动清理。
 */

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, type Dir } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import {
  detectStartCommands,
  applyDetectedConfigs,
  type ScanStats,
} from '../src/services/startCommandDetector';
import type { Service, ServiceInput } from '../src/types';
import type { ServiceRepository } from '../src/routes/services';

// ---------------------------------------------------------------------------
// 测试辅助
// ---------------------------------------------------------------------------

/** 创建一个最小化 Service 对象（仅填充必要字段）。 */
function makeService(id: number, name: string): Service {
  return {
    id,
    name,
    type: 'backend',
    host: '127.0.0.1',
    port: 8080,
    health_url: null,
    enabled: true,
    sort_order: id,
    start_command: null,
    work_dir: null,
    created_at: '2024-01-01T00:00:00.000Z',
    updated_at: '2024-01-01T00:00:00.000Z',
    status: 'unknown',
    last_checked_at: null,
    response_ms: null,
    process_status: 'unknown',
    pid: null,
    process_started_at: null,
  };
}

/** 创建临时目录，返回路径。 */
function makeTempDir(): string {
  return mkdtempSync(path.join(tmpdir(), 'qa-scd-'));
}

/** 递归创建目录（已存在则跳过）。 */
function ensureDir(dir: string): void {
  mkdirSync(dir, { recursive: true });
}

/** 在目录下写入文件（自动创建父目录）。 */
function writeFile(dir: string, fileName: string, content: string = ''): string {
  ensureDir(dir);
  const filePath: string = path.join(dir, fileName);
  writeFileSync(filePath, content, 'utf-8');
  return filePath;
}

// ---------------------------------------------------------------------------
// 1. 匹配逻辑
// ---------------------------------------------------------------------------

describe('detectStartCommands —— 匹配逻辑', () => {
  let tmpDir: string;

  beforeEach(() => {
    tmpDir = makeTempDir();
  });

  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('精确匹配：文件名去扩展名 == 服务名', () => {
    writeFile(tmpDir, 'lis-gateway.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].service_name).toBe('lis-gateway');
    expect(results[0].match_type).toBe('exact');
    expect(results[0].confidence).toBe('high');
  });

  it('前缀匹配：文件名以服务名开头（如 lis-gateway-1.0.0.jar → lis-gateway）', () => {
    writeFile(tmpDir, 'lis-gateway-1.0.0.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('prefix');
    expect(results[0].confidence).toBe('medium');
  });

  it('包含匹配：文件名包含服务名关键词', () => {
    // 服务名 lis-service-instrument-mgmt，文件 instrument.jar
    // extractKeywords 过滤掉 lis / service，保留 instrument / mgmt
    writeFile(tmpDir, 'instrument.jar');
    const services: Service[] = [makeService(1, 'lis-service-instrument-mgmt')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('contains');
    expect(results[0].confidence).toBe('low');
  });

  it('包含匹配：文件名包含完整服务名', () => {
    // candidateName "my-lis-gateway-tool" 包含 "lis-gateway"
    writeFile(tmpDir, 'my-lis-gateway-tool.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('contains');
    expect(results[0].confidence).toBe('low');
  });

  it('优先级：精确 > 前缀 > 包含', () => {
    // 同时存在精确匹配和前缀匹配的文件
    writeFile(tmpDir, 'lis-gateway.jar');        // exact
    writeFile(tmpDir, 'lis-gateway-1.0.0.jar');   // prefix
    writeFile(tmpDir, 'my-lis-gateway-tool.jar'); // contains
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('exact');
    expect(results[0].confidence).toBe('high');
    expect(results[0].matched_file).toContain('lis-gateway.jar');
  });

  it('同优先级取最短文件名（前缀匹配场景）', () => {
    // 两个前缀匹配，文件名长度不同
    writeFile(tmpDir, 'lis-gateway-v1.jar');       // prefix, fileName length = 18
    writeFile(tmpDir, 'lis-gateway-1.0.0.jar');    // prefix, fileName length = 22
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('prefix');
    // 最短文件名 lis-gateway-v1.jar 应当选中
    expect(results[0].matched_file).toContain('lis-gateway-v1.jar');
  });

  it('无匹配时返回空数组', () => {
    writeFile(tmpDir, 'totally-unrelated.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(0);
  });

  it('多个服务分别匹配各自文件', () => {
    writeFile(tmpDir, 'lis-gateway.jar');
    writeFile(tmpDir, 'lis-auth.jar');
    writeFile(tmpDir, 'lis-monitor.jar');
    const services: Service[] = [
      makeService(1, 'lis-gateway'),
      makeService(2, 'lis-auth'),
      makeService(3, 'lis-monitor'),
    ];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(3);
    const names: string[] = results.map((r) => r.service_name).sort();
    expect(names).toEqual(['lis-auth', 'lis-gateway', 'lis-monitor']);
  });

  it('空服务列表应返回空数组', () => {
    writeFile(tmpDir, 'lis-gateway.jar');
    const results = detectStartCommands(tmpDir, []);
    expect(results).toHaveLength(0);
  });

  it('匹配不区分大小写', () => {
    writeFile(tmpDir, 'LIS-Gateway.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].match_type).toBe('exact');
  });
});

// ---------------------------------------------------------------------------
// 2. 命令生成
// ---------------------------------------------------------------------------

describe('detectStartCommands —— 命令生成', () => {
  let tmpDir: string;

  beforeEach(() => {
    tmpDir = makeTempDir();
  });

  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('jar → java -jar "<path>"', () => {
    const filePath: string = writeFile(tmpDir, 'lis-gateway.jar');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe(`java -jar "${filePath}"`);
  });

  it('package.json（含 start 脚本）→ npm start', () => {
    const pkgDir: string = path.join(tmpDir, 'lis-gateway-web');
    writeFile(pkgDir, 'package.json', JSON.stringify({ scripts: { start: 'node server.js' } }));
    const services: Service[] = [makeService(1, 'lis-gateway-web')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe('npm start');
    expect(results[0].detected_work_dir).toBe(pkgDir);
  });

  it('package.json（无 start 脚本）→ 跳过', () => {
    const pkgDir: string = path.join(tmpDir, 'lis-gateway-web');
    writeFile(pkgDir, 'package.json', JSON.stringify({ scripts: { build: 'tsc' } }));
    const services: Service[] = [makeService(1, 'lis-gateway-web')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(0);
  });

  it('package.json（无 scripts 字段）→ 跳过', () => {
    const pkgDir: string = path.join(tmpDir, 'lis-gateway-web');
    writeFile(pkgDir, 'package.json', JSON.stringify({ name: 'test' }));
    const services: Service[] = [makeService(1, 'lis-gateway-web')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(0);
  });

  it('package.json（JSON 解析失败）→ 跳过', () => {
    const pkgDir: string = path.join(tmpDir, 'lis-gateway-web');
    writeFile(pkgDir, 'package.json', '{ invalid json');
    const services: Service[] = [makeService(1, 'lis-gateway-web')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(0);
  });

  it('docker-compose.yml → docker-compose up -d', () => {
    const composeDir: string = path.join(tmpDir, 'lis-gateway-stack');
    writeFile(composeDir, 'docker-compose.yml', 'version: "3"\nservices:\n  web:\n    image: nginx');
    const services: Service[] = [makeService(1, 'lis-gateway-stack')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe('docker-compose up -d');
    expect(results[0].detected_work_dir).toBe(composeDir);
  });

  it('docker-compose.yaml（.yaml 扩展名）→ docker-compose up -d', () => {
    const composeDir: string = path.join(tmpDir, 'lis-gateway-stack');
    writeFile(composeDir, 'docker-compose.yaml', 'version: "3"');
    const services: Service[] = [makeService(1, 'lis-gateway-stack')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe('docker-compose up -d');
  });

  it('exe → "<path>"', () => {
    const filePath: string = writeFile(tmpDir, 'lis-gateway.exe');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe(`"${filePath}"`);
  });

  it('bat → "<path>"', () => {
    const filePath: string = writeFile(tmpDir, 'lis-gateway.bat');
    const services: Service[] = [makeService(1, 'lis-gateway')];
    const results = detectStartCommands(tmpDir, services);
    expect(results).toHaveLength(1);
    expect(results[0].detected_command).toBe(`"${filePath}"`);
  });
});

// ---------------------------------------------------------------------------
// 3. 目录扫描
// ---------------------------------------------------------------------------

describe('detectStartCommands —— 目录扫描', () => {
  let tmpDir: string;

  beforeEach(() => {
    tmpDir = makeTempDir();
  });

  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('最大深度 3 层（depth 0-3 被扫描，depth 4 不扫描）', () => {
    // depth 0: 根目录
    writeFile(tmpDir, 'root.jar');
    // depth 1
    const d1: string = path.join(tmpDir, 'level1');
    writeFile(d1, 'l1.jar');
    // depth 2
    const d2: string = path.join(d1, 'level2');
    writeFile(d2, 'l2.jar');
    // depth 3
    const d3: string = path.join(d2, 'level3');
    writeFile(d3, 'l3.jar');
    // depth 4 — 不应被扫描
    const d4: string = path.join(d3, 'level4');
    writeFile(d4, 'l4.jar');

    const stats: ScanStats = { scanned_files: 0 };
    // 用一个不会匹配任何文件的服务名，确保只验证 scanned_files
    detectStartCommands(tmpDir, [], stats);

    // 4 个 .jar 文件被扫描（root, l1, l2, l3），l4 在 depth 4 不扫描
    expect(stats.scanned_files).toBe(4);
  });

  it('跳过 node_modules 目录', () => {
    writeFile(tmpDir, 'visible.jar');
    const nmDir: string = path.join(tmpDir, 'node_modules');
    writeFile(nmDir, 'hidden1.jar');
    const nmSub: string = path.join(nmDir, 'pkg', 'lib');
    writeFile(nmSub, 'hidden2.jar');

    const stats: ScanStats = { scanned_files: 0 };
    detectStartCommands(tmpDir, [], stats);

    // 只有 visible.jar 被扫描
    expect(stats.scanned_files).toBe(1);
  });

  it('跳过 .git 目录', () => {
    writeFile(tmpDir, 'visible.jar');
    const gitDir: string = path.join(tmpDir, '.git');
    writeFile(gitDir, 'hidden.jar');

    const stats: ScanStats = { scanned_files: 0 };
    detectStartCommands(tmpDir, [], stats);

    expect(stats.scanned_files).toBe(1);
  });

  it('跳过其他噪声目录（dist / build / target 等）', () => {
    writeFile(tmpDir, 'visible.jar');
    writeFile(path.join(tmpDir, 'dist'), 'hidden1.jar');
    writeFile(path.join(tmpDir, 'build'), 'hidden2.jar');
    writeFile(path.join(tmpDir, 'target'), 'hidden3.jar');
    writeFile(path.join(tmpDir, '.idea'), 'hidden4.jar');

    const stats: ScanStats = { scanned_files: 0 };
    detectStartCommands(tmpDir, [], stats);

    expect(stats.scanned_files).toBe(1);
  });

  it('scanned_files 计数包含非候选文件', () => {
    // .jar 是候选，.txt 不是候选，但都应被计入 scanned_files
    writeFile(tmpDir, 'lis-gateway.jar');
    writeFile(tmpDir, 'readme.txt');
    writeFile(tmpDir, 'config.json');

    const stats: ScanStats = { scanned_files: 0 };
    detectStartCommands(tmpDir, [], stats);

    expect(stats.scanned_files).toBe(3);
  });

  it('不传 stats 参数时不报错', () => {
    writeFile(tmpDir, 'lis-gateway.jar');
    // 不传第三个参数
    const results = detectStartCommands(tmpDir, [makeService(1, 'lis-gateway')]);
    expect(results).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// 4. applyDetectedConfigs
// ---------------------------------------------------------------------------

describe('applyDetectedConfigs —— 批量应用配置', () => {
  /** 创建一个模拟的 ServiceRepository，仅实现 update 方法。 */
  function makeMockRepo(): { repo: ServiceRepository; updateSpy: ReturnType<typeof vi.fn> } {
    const updateSpy = vi.fn((id: number, data: ServiceInput): Service => {
      return {
        ...makeService(id, `service-${id}`),
        start_command: data.start_command ?? null,
        work_dir: data.work_dir ?? null,
        updated_at: new Date().toISOString(),
      };
    });
    const repo = { update: updateSpy } as unknown as ServiceRepository;
    return { repo, updateSpy };
  }

  it('正确调用 repository.update 并传入 start_command 和 work_dir', () => {
    const { repo, updateSpy } = makeMockRepo();
    const configs = [
      { id: 1, start_command: 'java -jar "/app/gateway.jar"', work_dir: '/app' },
      { id: 2, start_command: 'npm start', work_dir: '/web' },
    ];

    const result = applyDetectedConfigs(configs, repo);

    expect(result).toHaveLength(2);
    expect(updateSpy).toHaveBeenCalledTimes(2);

    // 第一次调用：id=1
    expect(updateSpy).toHaveBeenNthCalledWith(1, 1, {
      start_command: 'java -jar "/app/gateway.jar"',
      work_dir: '/app',
    });

    // 第二次调用：id=2
    expect(updateSpy).toHaveBeenNthCalledWith(2, 2, {
      start_command: 'npm start',
      work_dir: '/web',
    });

    // 返回值应包含更新后的命令
    expect(result[0].start_command).toBe('java -jar "/app/gateway.jar"');
    expect(result[0].work_dir).toBe('/app');
    expect(result[1].start_command).toBe('npm start');
    expect(result[1].work_dir).toBe('/web');
  });

  it('work_dir 为 null 时正确传递', () => {
    const { repo, updateSpy } = makeMockRepo();
    const configs = [
      { id: 5, start_command: 'docker-compose up -d', work_dir: null },
    ];

    applyDetectedConfigs(configs, repo);

    expect(updateSpy).toHaveBeenCalledWith(5, {
      start_command: 'docker-compose up -d',
      work_dir: null,
    });
  });

  it('空 configs 数组返回空数组且不调用 update', () => {
    const { repo, updateSpy } = makeMockRepo();
    const result = applyDetectedConfigs([], repo);
    expect(result).toEqual([]);
    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('返回数组顺序与 configs 一致', () => {
    const { repo } = makeMockRepo();
    const configs = [
      { id: 10, start_command: 'cmd-a', work_dir: '/a' },
      { id: 20, start_command: 'cmd-b', work_dir: '/b' },
      { id: 30, start_command: 'cmd-c', work_dir: '/c' },
    ];

    const result = applyDetectedConfigs(configs, repo);
    expect(result).toHaveLength(3);
    expect(result[0].id).toBe(10);
    expect(result[1].id).toBe(20);
    expect(result[2].id).toBe(30);
  });

  it('repository.update 抛出异常时应向上传播', () => {
    const error = new Error('服务不存在: id=999');
    const updateSpy = vi.fn().mockImplementation(() => {
      throw error;
    });
    const repo = { update: updateSpy } as unknown as ServiceRepository;

    expect(() => applyDetectedConfigs([{ id: 999, start_command: 'cmd', work_dir: null }], repo)).toThrow(
      '服务不存在: id=999',
    );
  });
});
