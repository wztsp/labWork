/**
 * 健康检测结果合并工具。
 *
 * 从 `OverviewPage.checkAll` 中抽离的纯函数，便于单元测试覆盖
 * “检测结果回填服务列表” 这一核心逻辑。
 */

import type { CheckResult, Service } from '../types';

/**
 * 将一次全量检测的结果合并进现有服务列表。
 *
 * 匹配规则：`CheckResult.service_id` === `Service.id`。
 * 命中的服务会被覆盖 `status` / `response_ms` / `last_checked_at`，
 * 其余字段保持不变；未命中的服务原样保留（返回同一对象引用）。
 *
 * @param services 当前服务列表（不会被修改）
 * @param results 后端 `POST /api/services/check` 返回的检测结果数组
 * @param nowIso 本次检测的时间戳（ISO 8601 字符串），写入 `last_checked_at`
 * @returns 合并后的新服务列表，顺序与入参 `services` 一致
 */
export function applyCheckResults(
  services: Service[],
  results: CheckResult[],
  nowIso: string,
): Service[] {
  const resultByServiceId: Map<number, CheckResult> = new Map<number, CheckResult>(
    results.map((result: CheckResult): [number, CheckResult] => [result.service_id, result]),
  );

  return services.map((service: Service): Service => {
    const result: CheckResult | undefined = resultByServiceId.get(service.id);
    if (result === undefined) {
      return service;
    }
    return {
      ...service,
      status: result.status,
      response_ms: result.response_ms,
      last_checked_at: nowIso,
    };
  });
}

export default applyCheckResults;
