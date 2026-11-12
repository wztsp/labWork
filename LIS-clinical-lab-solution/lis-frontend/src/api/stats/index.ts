import { request } from '@/api/request'

/** 工作量汇总 */
export interface WorkloadSummary { total?: number; today?: number; urgent?: number; reported?: number }
/** 危急值汇总 */
export interface CriticalSummary { total?: number; confirmed?: number; confirmRate?: number }

export function getWorkloadStats(params?: Record<string, unknown>) { return request.get<WorkloadSummary>('/stats/workload', params) }
export function getWorkloadByDay(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/workload/by-day', params) }
export function getWorkloadByDept(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/workload/by-dept', params) }
export function getWorkloadByInstrument(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/workload/by-instrument', params) }
export function getCriticalStats(params?: Record<string, unknown>) { return request.get<CriticalSummary>('/stats/critical', params) }
export function getCriticalByDay(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/critical/by-day', params) }
export function getCriticalByItem(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/critical/by-item', params) }
export function getCriticalByDept(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/critical/by-dept', params) }
export function getTrendData(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/trend', params) }
export function getPositiveRate(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/positive-rate', params) }
export function getPositiveRateTrend(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/positive-rate/trend', params) }
export function getChargeStats(params?: Record<string, unknown>) { return request.get<unknown>('/stats/charge', params) }
export function getChargeByDay(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/charge/by-day', params) }
export function getChargeByDept(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/charge/by-dept', params) }
export function getChargeByItem(params?: Record<string, unknown>) { return request.get<unknown[]>('/stats/charge/by-item', params) }
