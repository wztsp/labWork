import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

/** 微生物报告视图对象 */
export interface MicroReport {
  id: number
  reportNo: string
  sampleId?: number
  patientId?: number
  patientName?: string
  specimenType?: string
  cultureSite?: string
  // 标本信息 (详情接口补充)
  sampleBarcode?: string
  sampleNo?: string
  plateBarcodes?: string
  applyItems?: string
  collectTime?: string
  collectorName?: string
  receiveTime?: string
  department?: string
  reportStatus: number // 1初步 2鉴定 3最终
  reportStatusText?: string
  auditorId?: number
  auditTime?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
  // 革兰氏染色
  gramStainResult?: string
  gramStainRemark?: string
  gramStainTime?: string
  gramStainOperator?: string
  // 阴性报告
  isNegative?: number // 0否 1是
  negativeRemark?: string
  negativeOperator?: string
  negativeTime?: string
  // 血培养专用
  bottleNo?: string
  bottleType?: string
  volume?: string
  inoculateTime?: string
  alarmTime?: string
  ttpHours?: number
  bloodCultureId?: number
  bloodCultureResult?: string
  // 关联数据
  organisms?: Organism[]
  sensitivities?: Sensitivity[]
  subcultures?: Subculture[]
}

export interface Subculture {
  id?: number
  microReportId?: number
  subcultureTime?: string
  medium?: string
  incubatorNo?: string
  result?: string
  operator?: string
  remark?: string
  createTime?: string
}

export interface Organism {
  id?: number
  microReportId?: number
  organismName: string
  organismCode?: string
  quantity?: string
  colonsMethod?: string
  createTime?: string
  sensitivities?: Sensitivity[]
}

export interface Sensitivity {
  id?: number
  microReportId?: number
  organismId?: number
  antibioticName: string
  antibioticCode?: string
  micValue?: string
  result: string // S/I/R
  breakpoint?: string
  createTime?: string
}

export interface MicroReportQuery {
  pageNum?: number
  pageSize?: number
  reportNo?: string
  patientName?: string
  status?: number
  beginDate?: string
  endDate?: string
  keyword?: string
  sampleNo?: string
  sampleBarcode?: string
  visitNo?: string
  patientId?: number
}

// 分页查询 (后端 /api/report/micro/page, 参数 current/size)
export function getMicroReportPage(params: MicroReportQuery) {
  const { pageNum = 1, pageSize = 10, reportNo, patientName, status, beginDate, endDate, keyword, sampleNo, sampleBarcode, visitNo, patientId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (patientName) query.patientName = patientName
  if (status !== undefined) query.reportStatus = status
  if (beginDate) query.beginDate = beginDate
  if (endDate) query.endDate = endDate
  // 新增前端报告查询用到的过滤条件: keyword/sampleNo/sampleBarcode/visitNo/patientId/reportNo
  if (keyword) query.keyword = keyword
  if (reportNo) query.reportNo = reportNo
  if (sampleNo) query.sampleNo = sampleNo
  if (sampleBarcode) query.sampleBarcode = sampleBarcode
  if (visitNo) query.visitNo = visitNo
  if (patientId != null) query.patientId = patientId
  return request.get<PageResult<MicroReport>>('/report/micro/page', query).then((res) => {
    // 后端字段 reportNo/sampleId 不在 VO 中显式返回, 通过 reportNo 兼容
    const records = (res.records || []).map((r: any) => ({
      ...r,
      reportNo: r.reportNo || '',
      patientName: r.patientName || '',
      specimenType: r.specimenType || '',
      cultureSite: r.cultureSite || '',
      status: r.reportStatus
    })) as MicroReport[]
    return { records, total: res.total, current: res.current, size: res.size } as PageResult<MicroReport>
  })
}

export function getMicroReportById(id: number | string) {
  return request.get<MicroReport>(`/report/micro/${id}`)
}

export function createMicroReport(data: Partial<MicroReport>) {
  return request.post<MicroReport>('/report/micro', data)
}

/** 仪器导入: 一次性创建报告+细菌鉴定+药敏结果 */
export function importMicroReport(data: Partial<MicroReport>) {
  return request.post<MicroReport>('/report/micro/import', data)
}

export function addOrganism(id: number | string, data: Partial<Organism>) {
  return request.post<Organism>(`/report/micro/${id}/organisms`, data)
}

/** 修改细菌鉴定 */
export function updateOrganism(id: number | string, organismId: number | string, data: Partial<Organism>) {
  return request.put<Organism>(`/report/micro/${id}/organisms/${organismId}`, data)
}

/** 删除细菌鉴定 (同时删除关联药敏结果) */
export function deleteOrganism(id: number | string, organismId: number | string) {
  return request.delete<boolean>(`/report/micro/${id}/organisms/${organismId}`)
}

export function addSensitivity(id: number | string, data: Partial<Sensitivity>) {
  return request.post<Sensitivity>(`/report/micro/${id}/sensitivities`, data)
}

/** 修改药敏结果 */
export function updateSensitivity(id: number | string, sensitivityId: number | string, data: Partial<Sensitivity>) {
  return request.put<Sensitivity>(`/report/micro/${id}/sensitivities/${sensitivityId}`, data)
}

/** 删除药敏结果 */
export function deleteSensitivity(id: number | string, sensitivityId: number | string) {
  return request.delete<boolean>(`/report/micro/${id}/sensitivities/${sensitivityId}`)
}

/** 发布报告 (targetStatus: 1初步 2鉴定 3最终) */
export function publishMicroReport(id: number | string, targetStatus: number, auditorId: number | string = 1) {
  // 通过 URL 查询参数传给后端 @RequestParam
  return request.put<MicroReport>(`/report/micro/${id}/publish?targetStatus=${targetStatus}&auditorId=${auditorId}`)
}

/** 保存革兰氏染色结果 */
export function saveGramStain(id: number | string, data: { gramStainResult: string; gramStainRemark?: string; gramStainOperator?: string }) {
  return request.put<MicroReport>(`/report/micro/${id}/gram-stain`, data)
}

/** 保存阴性报告 */
export function saveNegativeReport(id: number | string, data: { isNegative: number; negativeRemark?: string; negativeOperator?: string }) {
  return request.put<MicroReport>(`/report/micro/${id}/negative`, data)
}

/** 保存血培养信息 */
export function saveBloodCulture(id: number | string, data: Partial<MicroReport>) {
  return request.put<MicroReport>(`/report/micro/${id}/blood-culture`, data)
}

/** 添加转种记录 */
export function addSubculture(id: number | string, data: Partial<Subculture>) {
  return request.post<Subculture>(`/report/micro/${id}/subcultures`, data)
}

/** 删除转种记录 */
export function deleteSubculture(id: number | string, subId: number | string) {
  return request.delete<boolean>(`/report/micro/${id}/subcultures/${subId}`)
}

/** 手动触发指定报告的耐药规则评估（原专家规则） */
export function evaluateExpertRules(id: number | string) {
  return request.post<MicroAlert[]>(`/report/micro/${id}/evaluate-rules`)
}

/** 计算指定报告的专家/耐药规则命中项(只读, 用于报告详情标注) */
export function getMicroRuleHits(id: number | string) {
  return request.get<MicroAlert[]>(`/report/micro/${id}/rule-hits`)
}

// ==================== 微生物特殊耐药/危急值预警 ====================

/** 微生物预警 */
export interface MicroAlert {
  id: number
  microReportId: number
  organismId?: number
  alertType: string // MRSA/VRE/CRE/ESBL/MDR/CRAB/CRITICAL_VALUE
  alertLevel: string // WARNING/CRITICAL
  organismName?: string
  message?: string
  status: string // PENDING/READ/HANDLED
  handler?: string
  handleRemark?: string
  handleTime?: string
  createTime?: string
}

export interface MicroAlertQuery {
  status?: string
  alertLevel?: string
}

/** 查询全部预警 (后端 /api/report/micro/alerts) */
export function getMicroAlertList(params: MicroAlertQuery = {}) {
  const query: Record<string, any> = {}
  if (params.status) query.status = params.status
  if (params.alertLevel) query.alertLevel = params.alertLevel
  return request.get<MicroAlert[]>('/report/micro/alerts', query)
}

/** 根据报告ID查询预警 */
export function getMicroAlertsByReport(reportId: number | string) {
  return request.get<MicroAlert[]>(`/report/micro/alerts/report/${reportId}`)
}

/** 标记已读 */
export function markAlertRead(id: number | string) {
  return request.post<void>(`/report/micro/alerts/${id}/read`)
}

/** 处理预警 */
export function handleAlert(id: number | string, handler: string, remark?: string) {
  const query: Record<string, any> = { handler }
  if (remark) query.remark = remark
  return request.post<void>(`/report/micro/alerts/${id}/handle`, undefined, { params: query })
}

// ==================== 微生物流行病学统计 ====================

/** 统计计数项 */
export interface EpiItemCount {
  name: string
  count: number
}

/** 耐药率统计项 */
export interface ResistanceRate {
  antibioticName: string
  totalCount: number
  resistantCount: number
  susceptibleCount: number
  intermediateCount: number
  resistanceRate: number
}

/** 流行病学统计结果 */
export interface MicroEpiStat {
  beginDate?: string
  endDate?: string
  totalReportCount: number
  totalOrganismCount: number
  totalSensitivityCount: number
  totalAlertCount: number
  organismDistribution: EpiItemCount[]
  specimenTypeDistribution: EpiItemCount[]
  cultureSiteDistribution: EpiItemCount[]
  resistanceRates: ResistanceRate[]
  monthlyTrend: EpiItemCount[]
  alertTypeDistribution: EpiItemCount[]
  resultDistribution: Record<string, number>
}

/** 流行病学统计 (后端 /api/report/micro/epi-stat) */
export function getMicroEpiStat(beginDate?: string, endDate?: string) {
  const query: Record<string, any> = {}
  if (beginDate) query.beginDate = beginDate
  if (endDate) query.endDate = endDate
  return request.get<MicroEpiStat>('/report/micro/epi-stat', query)
}

/** 通过培养基条码核收微生物报告 (后端 POST /api/report/micro/receive-by-plate) */
export function receiveByPlateBarcode(plateBarcode: string) {
  return request.post<MicroReport>('/report/micro/receive-by-plate', undefined, { params: { plateBarcode } })
}

/**
 * 撤销核收：仅允许「初步报告」状态
 * 删除该报告+回滚培养基条码状态；标本后续可重新核收
 */
export function cancelReceiveReport(id: number | string) {
  return request.delete(`/report/micro/${id}/cancel-receive`)
}

/**
 * 取消最终报告：将「最终报告」回退为「鉴定报告」，恢复可编辑、可重新发布
 */
export function revokeFinalReport(id: number | string) {
  return request.post<MicroReport>(`/report/micro/${id}/revoke-final`)
}
