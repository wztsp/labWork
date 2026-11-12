import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== TAT 规则 ====================
export interface TatRule {
  id?: number | string
  specimenType?: string
  testItemId?: number | string
  testItemName?: string
  urgentTimeout?: number   // 急查超时时间(分钟)
  normalTimeout?: number   // 普通超时时间(分钟)
  enabled?: number         // 0停用 1启用
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface TatRuleQuery extends PageQuery {
  specimenType?: string
  keyword?: string
}

export function getTatRulePage(params: TatRuleQuery) {
  const { pageNum = 1, pageSize = 10, specimenType, keyword } = params
  const query: Record<string, any> = { page: pageNum, size: pageSize }
  if (specimenType) query.specimenType = specimenType
  if (keyword) query.keyword = keyword
  return request.get<PageResult<TatRule>>('/tat/rule/page', query)
}

export function getTatRuleList() {
  return request.get<TatRule[]>('/tat/rule/list')
}

export function saveTatRule(data: TatRule) {
  if (data.id) return request.put<TatRule>('/tat/rule', data)
  return request.post<TatRule>('/tat/rule', data)
}

export function deleteTatRule(id: number | string) {
  return request.delete<boolean>(`/tat/rule/${id}`)
}

/** 是否启用 TAT 规则 */
export function getTatEnabled() {
  return request.get<boolean>('/tat/rule/enabled')
}

export function setTatEnabled(enabled: boolean) {
  return request.put<boolean>(`/tat/rule/enabled?enabled=${enabled}`)
}

// ==================== TAT 业务监控 ====================
export interface TatMonitor {
  id?: number | string
  sampleId?: number | string
  sampleBarcode?: string
  patientName?: string
  testItemId?: number | string
  testItemName?: string
  specimenType?: string
  isUrgent?: number        // 0否 1是
  signOffTime?: string
  timeoutMinutes?: number
  elapsedMinutes?: number
  isOverdue?: number       // 0未超时 1已超时
  operatorId?: number | string
  operatorName?: string
  department?: string
  notified?: number        // 0未通知 1已通知
  createTime?: string
}

export interface TatMonitorQuery extends PageQuery {
  isOverdue?: number
  keyword?: string
  /** 开始日期 yyyy-MM-dd */
  startDate?: string
  /** 结束日期 yyyy-MM-dd */
  endDate?: string
}

export function getTatMonitorPage(params: TatMonitorQuery) {
  const { pageNum = 1, pageSize = 10, isOverdue, keyword, startDate, endDate } = params
  const query: Record<string, any> = { page: pageNum, size: pageSize }
  if (isOverdue !== undefined) query.isOverdue = isOverdue
  if (keyword) query.keyword = keyword
  if (startDate) query.startDate = startDate
  if (endDate) query.endDate = endDate
  return request.get<PageResult<TatMonitor>>('/tat/monitor/page', query)
}

export function getTatMonitorStats(params?: { startDate?: string; endDate?: string }) {
  const query: Record<string, any> = {}
  if (params?.startDate) query.startDate = params.startDate
  if (params?.endDate) query.endDate = params.endDate
  return request.get<Record<string, any>>('/tat/monitor/stats', query)
}

export function refreshTatMonitor() {
  return request.post<number>('/tat/monitor/refresh')
}

// ==================== 传染病项目配置 ====================
export interface InfectiousItem {
  id?: number | string
  testItemId?: number | string
  testItemName?: string
  positiveText?: string
  enabled?: number
  remark?: string
  createTime?: string
}

export interface InfectiousQuery extends PageQuery {
  keyword?: string
}

export function getInfectiousPage(params: InfectiousQuery) {
  const { pageNum = 1, pageSize = 10, keyword } = params
  const query: Record<string, any> = { page: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  return request.get<PageResult<InfectiousItem>>('/infectious/page', query)
}

export function saveInfectious(data: InfectiousItem) {
  if (data.id) return request.put<InfectiousItem>('/infectious', data)
  return request.post<InfectiousItem>('/infectious', data)
}

export function deleteInfectious(id: number | string) {
  return request.delete<boolean>(`/infectious/${id}`)
}