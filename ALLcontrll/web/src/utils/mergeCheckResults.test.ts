/**
 * `applyCheckResults` 单元测试。
 *
 * 回归背景：修复前 `OverviewPage.checkAll` 误用 `result.id` 建立索引，
 * 而后端 `CheckResult` 的服务标识字段实为 `service_id`，
 * 导致点击“立即检测”后服务列表的状态徽章 / 耗时 / 最后检测时间不刷新。
 * 本文件的核心断言即：**必须按 `service_id` 匹配 `Service.id`**。
 */

import { describe, expect, it } from 'vitest';
import { applyCheckResults } from './mergeCheckResults';
import type { CheckResult, Service, ServiceStatus } from '../types';

const NOW_ISO = '2025-01-01T10:00:00.000Z';

/** 构造服务对象，默认处于“从未检测”状态。 */
function makeService(id: number, overrides: Partial<Service> = {}): Service {
  return {
    id,
    name: `svc-${id}`,
    type: 'backend',
    host: '127.0.0.1',
    port: 8000 + id,
    health_url: null,
    enabled: true,
    sort_order: id,
    start_command: null,
    work_dir: null,
    created_at: NOW_ISO,
    updated_at: NOW_ISO,
    status: 'unknown',
    last_checked_at: null,
    response_ms: null,
    process_status: 'unknown',
    pid: null,
    process_started_at: null,
    ...overrides,
  };
}

/** 构造检测结果对象。 */
function makeResult(
  serviceId: number,
  status: ServiceStatus,
  responseMs: number | null,
  overrides: Partial<CheckResult> = {},
): CheckResult {
  return {
    service_id: serviceId,
    name: `svc-${serviceId}`,
    status,
    response_ms: responseMs,
    error_message: status === 'online' ? null : 'ECONNREFUSED',
    checked_at: NOW_ISO,
    ...overrides,
  };
}

describe('applyCheckResults —— 按 service_id 回填（Bug 修复回归核心）', () => {
  it('命中项应按 service_id 覆盖 status / response_ms / last_checked_at', () => {
    // Arrange
    const services: Service[] = [makeService(1), makeService(2)];
    const results: CheckResult[] = [
      makeResult(1, 'online', 12),
      makeResult(2, 'offline', null),
    ];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert
    expect(merged[0].status).toBe('online');
    expect(merged[0].response_ms).toBe(12);
    expect(merged[0].last_checked_at).toBe(NOW_ISO);
    expect(merged[1].status).toBe('offline');
    expect(merged[1].response_ms).toBeNull();
    expect(merged[1].last_checked_at).toBe(NOW_ISO);
  });

  it('必须使用 service_id 而非 id 字段匹配（旧 Bug 的直接回归用例）', () => {
    // Arrange：结果对象额外携带一个指向“错误服务”的 id 字段。
    // 若实现回退为按 r.id 建索引，服务 1 将被误判为 offline、服务 2 不刷新。
    const services: Service[] = [makeService(1), makeService(2)];
    const misleading = [
      { ...makeResult(2, 'online', 33), id: 1 },
      { ...makeResult(1, 'offline', null), id: 2 },
    ] as unknown as CheckResult[];

    // Act
    const merged: Service[] = applyCheckResults(services, misleading, NOW_ISO);

    // Assert：服务 1 取 service_id === 1 的结果（offline），服务 2 取 online。
    expect(merged[0].id).toBe(1);
    expect(merged[0].status).toBe('offline');
    expect(merged[0].response_ms).toBeNull();
    expect(merged[1].id).toBe(2);
    expect(merged[1].status).toBe('online');
    expect(merged[1].response_ms).toBe(33);
  });

  it('“立即检测”后列表应从 unknown 刷新为真实状态（端到端语义验证）', () => {
    // Arrange：初始列表全部 unknown / 无耗时 / 无检测时间。
    const services: Service[] = [makeService(1), makeService(2), makeService(3)];
    expect(services.every((s: Service): boolean => s.status === 'unknown')).toBe(true);

    const results: CheckResult[] = [
      makeResult(1, 'online', 8),
      makeResult(2, 'offline', null),
      makeResult(3, 'online', 21),
    ];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert：不再有 unknown，且全部带上检测时间。
    expect(merged.map((s: Service): ServiceStatus => s.status)).toEqual([
      'online',
      'offline',
      'online',
    ]);
    expect(merged.every((s: Service): boolean => s.last_checked_at === NOW_ISO)).toBe(true);
  });
});

describe('applyCheckResults —— 字段透传与不可变性', () => {
  it('命中项的其余字段应原样透传', () => {
    // Arrange
    const services: Service[] = [
      makeService(7, {
        name: 'lis-gateway',
        type: 'frontend',
        host: '10.0.0.9',
        port: 9999,
        health_url: 'http://10.0.0.9:9999/actuator/health',
        enabled: false,
      }),
    ];
    const results: CheckResult[] = [makeResult(7, 'online', 5, { name: '后端返回的其它名字' })];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert：name 等字段不应被 CheckResult 覆盖。
    expect(merged[0].name).toBe('lis-gateway');
    expect(merged[0].type).toBe('frontend');
    expect(merged[0].host).toBe('10.0.0.9');
    expect(merged[0].port).toBe(9999);
    expect(merged[0].health_url).toBe('http://10.0.0.9:9999/actuator/health');
    expect(merged[0].enabled).toBe(false);
  });

  it('未命中项应返回同一对象引用（避免无谓重渲染）', () => {
    // Arrange
    const hit: Service = makeService(1);
    const miss: Service = makeService(2);
    const services: Service[] = [hit, miss];

    // Act
    const merged: Service[] = applyCheckResults(services, [makeResult(1, 'online', 3)], NOW_ISO);

    // Assert
    expect(merged[1]).toBe(miss);
    expect(merged[0]).not.toBe(hit);
  });

  it('纯函数：不得修改入参 services / results', () => {
    // Arrange
    const services: Service[] = [makeService(1), makeService(2)];
    const results: CheckResult[] = [makeResult(1, 'online', 42)];
    const servicesSnapshot: string = JSON.stringify(services);
    const resultsSnapshot: string = JSON.stringify(results);

    // Act
    applyCheckResults(services, results, NOW_ISO);

    // Assert
    expect(JSON.stringify(services)).toBe(servicesSnapshot);
    expect(JSON.stringify(results)).toBe(resultsSnapshot);
    expect(services).toHaveLength(2);
  });

  it('应返回新数组而非原数组引用', () => {
    // Arrange
    const services: Service[] = [makeService(1)];

    // Act
    const merged: Service[] = applyCheckResults(services, [], NOW_ISO);

    // Assert
    expect(merged).not.toBe(services);
  });
});

describe('applyCheckResults —— 顺序与边界', () => {
  it('输出顺序应与 services 一致，即使 results 顺序被打乱', () => {
    // Arrange
    const services: Service[] = [makeService(3), makeService(1), makeService(2)];
    const results: CheckResult[] = [
      makeResult(2, 'offline', null),
      makeResult(3, 'online', 30),
      makeResult(1, 'online', 10),
    ];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert
    expect(merged.map((s: Service): number => s.id)).toEqual([3, 1, 2]);
    expect(merged.map((s: Service): number | null => s.response_ms)).toEqual([30, 10, null]);
  });

  it('results 为空时应等价于原列表（元素引用不变）', () => {
    // Arrange
    const services: Service[] = [makeService(1), makeService(2)];

    // Act
    const merged: Service[] = applyCheckResults(services, [], NOW_ISO);

    // Assert
    expect(merged).toEqual(services);
    expect(merged[0]).toBe(services[0]);
    expect(merged[1]).toBe(services[1]);
  });

  it('services 为空时应返回空数组', () => {
    expect(applyCheckResults([], [makeResult(1, 'online', 1)], NOW_ISO)).toEqual([]);
  });

  it('results 中不存在于列表的 service_id 应被忽略，不新增行', () => {
    // Arrange
    const services: Service[] = [makeService(1)];
    const results: CheckResult[] = [makeResult(1, 'online', 4), makeResult(99, 'offline', null)];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert
    expect(merged).toHaveLength(1);
    expect(merged[0].id).toBe(1);
    expect(merged[0].status).toBe('online');
  });

  it('同一 service_id 出现多次时以最后一条为准', () => {
    // Arrange
    const services: Service[] = [makeService(1)];
    const results: CheckResult[] = [makeResult(1, 'online', 10), makeResult(1, 'offline', null)];

    // Act
    const merged: Service[] = applyCheckResults(services, results, NOW_ISO);

    // Assert
    expect(merged[0].status).toBe('offline');
    expect(merged[0].response_ms).toBeNull();
  });

  it('由 online 变为 offline 时应清空 response_ms', () => {
    // Arrange
    const services: Service[] = [
      makeService(1, { status: 'online', response_ms: 15, last_checked_at: '2024-12-31T00:00:00.000Z' }),
    ];

    // Act
    const merged: Service[] = applyCheckResults(services, [makeResult(1, 'offline', null)], NOW_ISO);

    // Assert
    expect(merged[0].status).toBe('offline');
    expect(merged[0].response_ms).toBeNull();
    expect(merged[0].last_checked_at).toBe(NOW_ISO);
  });

  it('response_ms 为 0 时应正确写入而非被当作缺省值丢弃', () => {
    // Arrange
    const services: Service[] = [makeService(1)];

    // Act
    const merged: Service[] = applyCheckResults(services, [makeResult(1, 'online', 0)], NOW_ISO);

    // Assert
    expect(merged[0].response_ms).toBe(0);
  });
});
