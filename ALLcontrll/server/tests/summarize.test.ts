/**
 * `summarize` 函数单元测试。
 *
 * 覆盖点：
 *   - enabled=false 计入 paused（本期新增功能）
 *   - enabled=true 按 status 计入 online/offline/unknown
 *   - health_rate = online / total * 100（保留一位小数）
 *   - 空数组与边界值
 */

import { describe, expect, it } from 'vitest';
import { summarize } from '../src/routes/services';
import type { Service } from '../src/types';

/** 构造最小化测试输入（仅需 status + enabled 字段）。 */
function item(status: Service['status'], enabled: boolean): Pick<Service, 'status' | 'enabled'> {
  return { status, enabled };
}

describe('summarize —— 基本计数', () => {
  it('全部启用且在线时，online=total，其余为 0', () => {
    const result = summarize([item('online', true), item('online', true)]);
    expect(result).toEqual({ total: 2, online: 2, offline: 0, unknown: 0, paused: 0, health_rate: 100 });
  });

  it('全部启用且有 online/offline/unknown 时应分别计数', () => {
    const result = summarize([
      item('online', true),
      item('offline', true),
      item('unknown', true),
    ]);
    expect(result).toEqual({ total: 3, online: 1, offline: 1, unknown: 1, paused: 0, health_rate: 33.3 });
  });

  it('空数组应返回全零汇总', () => {
    const result = summarize([]);
    expect(result).toEqual({ total: 0, online: 0, offline: 0, unknown: 0, paused: 0, health_rate: 0 });
  });
});

describe('summarize —— paused 计数（本期新增）', () => {
  it('enabled=false 的服务应计入 paused，不计入 online/offline/unknown', () => {
    const result = summarize([
      item('online', true),
      item('offline', false),
      item('unknown', false),
    ]);
    expect(result.paused).toBe(2);
    expect(result.offline).toBe(0);
    expect(result.unknown).toBe(0);
    expect(result.online).toBe(1);
    expect(result.total).toBe(3);
  });

  it('全部暂停时 paused=total，health_rate=0', () => {
    const result = summarize([item('online', false), item('offline', false)]);
    expect(result).toEqual({ total: 2, online: 0, offline: 0, unknown: 0, paused: 2, health_rate: 0 });
  });

  it('paused 服务不影响 health_rate 分子（仅 online 计入）', () => {
    const result = summarize([
      item('online', true),
      item('online', true),
      item('offline', false),
      item('unknown', false),
    ]);
    // health_rate = 2 / 4 * 100 = 50.0
    expect(result.health_rate).toBe(50);
    expect(result.paused).toBe(2);
  });
});

describe('summarize —— health_rate 计算', () => {
  it('应保留一位小数', () => {
    // 1 / 3 * 100 = 33.333... → 33.3
    expect(summarize([item('online', true), item('offline', true), item('unknown', true)]).health_rate).toBe(33.3);
    // 2 / 3 * 100 = 66.666... → 66.7
    expect(
      summarize([item('online', true), item('online', true), item('offline', true)]).health_rate,
    ).toBe(66.7);
  });

  it('无在线服务时 health_rate=0', () => {
    expect(summarize([item('offline', true), item('unknown', true)]).health_rate).toBe(0);
  });

  it('全部在线时 health_rate=100', () => {
    expect(summarize([item('online', true), item('online', true), item('online', true)]).health_rate).toBe(
      100,
    );
  });

  it('五五开时 health_rate=50', () => {
    expect(
      summarize([item('online', true), item('offline', true)]).health_rate,
    ).toBe(50);
  });
});
