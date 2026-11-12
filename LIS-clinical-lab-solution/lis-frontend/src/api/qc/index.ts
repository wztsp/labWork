import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// 质控品批号
export interface QcLot {
  id: number
  lotNo: string
  testItemId: number
  instrumentId?: number
  targetValue: number
  sd: number
  cv?: number
  level: number // 1低 2中 3高
  startDate?: string
  endDate?: string
  status: number
  createTime?: string
  testItemName?: string
  instrumentName?: string
}

// 质控记录
export interface QcRecord {
  id: number
  qcLotId: number
  testItemId: number
  instrumentId?: number
  measureValue: number
  measureTime: string
  operatorId?: number
  operatorName?: string
  zScore?: number
  ruleViolation?: string
  qcResult: number // 0失控 1在控
  createTime?: string
  testItemName?: string
  instrumentName?: string
  lotNo?: string
}

// Westgard 规则 (前端硬编码, 后端为策略模式实现)
export interface QcRule {
  id: number
  ruleCode: string // 1-2s, 1-3s, 2-2s, R-4s, 4-1s, 10x
  ruleName: string
  ruleType: number // 1警告 2拒绝
  enabled: number // 0禁用 1启用
  description?: string
  params?: string
}

// 质控判定结果 (后端 QCResult)
export interface QcJudgeResult {
  qcResult: number // 0失控 1在控 (对应后端 accepted: true->1, false->0)
  ruleViolation?: string
  zScore?: number
  message?: string
}

// 质控图数据点
export interface QcChartPoint {
  measureTime: string
  measureValue: number
  zScore: number | null
  qcResult: number
  ruleViolation?: string
}

// 质控图数据
export interface QcChartData {
  lot: QcLot
  points: QcChartPoint[]
  mean: number
  sd: number
}

// 查询参数
export interface QcRecordQuery extends PageQuery {
  qcLotId?: number
  lotId?: number
  testItemId?: number
  instrumentId?: number
  measureDate?: string
  beginDate?: string
  endDate?: string
}

// 质控品批号列表 (后端 /api/qc/lots/list 返回全部, 无分页)
export function getQcLotList(params?: PageQuery) {
  // 后端返回全部批号, 前端如有分页参数则忽略
  return request.get<QcLot[]>('/qc/lots/list').then((list) => {
    // 包装成 PageResult 兼容旧调用
    return {
      records: list || [],
      total: list?.length || 0,
      current: params?.pageNum || 1,
      size: params?.pageSize || list?.length || 10
    } as PageResult<QcLot>
  })
}

// 全部启用的质控品批号(下拉用)
export function getAllQcLots() {
  return request.get<QcLot[]>('/qc/lots/enabled')
}

// 新增质控品批号
export function createQcLot(data: Omit<QcLot, 'id' | 'createTime'>) {
  return request.post<number>('/qc/lots', data)
}

// 修改质控品批号
export function updateQcLot(data: QcLot) {
  return request.put<boolean>('/qc/lots', data)
}

// 删除质控品批号
export function deleteQcLot(id: number) {
  return request.delete<boolean>(`/qc/lots/${id}`)
}

/**
 * 重估靶值与SD: 基于该批号下历史在控(ACCEPT)质控记录,
 * 使用样本均值与Bessel修正SD重算, 写回 lis_qc_lot.target_value / sd / cv
 * @param params.id lotId  必填
 * @param params.minSamples 最小样本数, 默认20
 */
export function reestimateLot(params: { id: number; minSamples?: number }) {
  const query: Record<string, any> = {}
  if (params.minSamples) query.minSamples = params.minSamples
  return request.post<any>(`/qc/lots/${params.id}/reestimate`, null, { params: query }).then((d) => ({
    lotId: Number(d?.lotId ?? 0),
    lotNo: d?.lotNo,
    oldTarget: d?.oldTarget != null ? Number(d.oldTarget) : null,
    oldSd: d?.oldSd != null ? Number(d.oldSd) : null,
    newTarget: d?.newTarget != null ? Number(d.newTarget) : null,
    newSd: d?.newSd != null ? Number(d.newSd) : null,
    newCv: d?.newCv != null ? Number(d.newCv) : null,
    sampleCount: Number(d?.sampleCount ?? 0),
    operator: d?.operator,
    estimatedAt: d?.estimatedAt
  }))
}

// 质控记录列表 (后端 /api/qc/records/page, 参数 current/size/lotId/testItemId)
// 后端 qcResult 是字符串 ACCEPT/REJECT, 前端期望数字 1/0
export function getQcRecordList(params: QcRecordQuery) {
  const { pageNum = 1, pageSize = 10, qcLotId, lotId, testItemId, instrumentId, measureDate, beginDate, endDate } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  // 后端使用 lotId, 前端可能传 qcLotId
  if (lotId) query.lotId = lotId
  else if (qcLotId) query.lotId = qcLotId
  if (testItemId) query.testItemId = testItemId
  if (instrumentId) query.instrumentId = instrumentId
  if (measureDate) query.measureDate = measureDate
  if (beginDate) query.beginDate = beginDate
  if (endDate) query.endDate = endDate
  return request.get<PageResult<QcRecord>>('/qc/records/page', query).then((res) => {
    // 转换 qcResult: ACCEPT->1, REJECT->0
    const records = (res.records || []).map((r: any) => ({
      ...r,
      qcResult: r.qcResult === 'ACCEPT' || r.qcResult === 1 ? 1 : 0,
      zScore: r.zScore !== null && r.zScore !== undefined ? Number(r.zScore) : undefined,
      measureValue: r.measureValue !== null && r.measureValue !== undefined ? Number(r.measureValue) : 0
    })) as QcRecord[]
    return {
      records,
      total: Number(res.total) || 0,
      current: Number(res.current) || 1,
      size: Number(res.size) || 10
    } as PageResult<QcRecord>
  })
}

// 录入质控数据 (后端 POST /api/qc/records, body 为 QCRecordDTO)
// 后端返回 QCResult {accepted, ruleName, suggestion, zScore}
export function submitQcRecord(data: {
  qcLotId: number
  measureValue: number
  measureTime?: string
}) {
  return request.post<any>('/qc/records', data).then((res: any) => {
    // 转换后端 QCResult 为前端 QcJudgeResult
    return {
      qcResult: res.accepted ? 1 : 0,
      ruleViolation: res.accepted ? undefined : res.ruleName,
      zScore: res.zScore !== null && res.zScore !== undefined ? Number(res.zScore) : undefined,
      message: res.suggestion
    } as QcJudgeResult
  })
}

// 更新质控记录 (后端 PUT /api/qc/records/{id})
export function updateQcRecord(id: number, data: {
  measureValue?: number
  measureTime?: string
}) {
  return request.put<any>(`/qc/records/${id}`, data).then((res: any) => {
    return {
      qcResult: res.accepted ? 1 : 0,
      ruleViolation: res.accepted ? undefined : res.ruleName,
      zScore: res.zScore !== null && res.zScore !== undefined ? Number(res.zScore) : undefined,
      message: res.suggestion
    } as QcJudgeResult
  })
}

// 批量录入质控数据 (后端无批量接口, 前端循环调用)
export function batchSubmitQcRecords(records: Array<{
  qcLotId: number
  measureValue: number
  measureTime?: string
}>) {
  return Promise.all(records.map(r => submitQcRecord(r)))
}

// 批量重算失控判定 (质控图"计算失控"按钮)
// 对指定批号+时间范围内的记录按时间序逐条重跑 Westgard 规则, 更新失控状态
export function recalculateQcRecords(params: { lotId: number; beginDate?: string; endDate?: string }) {
  const query: Record<string, any> = { lotId: params.lotId }
  if (params.beginDate) query.beginDate = params.beginDate
  if (params.endDate) query.endDate = params.endDate
  return request.post<any>('/qc/records/recalculate', null, { params: query }).then((data) => ({
    total: Number(data?.total ?? 0),
    rejectCount: Number(data?.rejectCount ?? 0),
    updatedCount: Number(data?.updatedCount ?? 0)
  }))
}

// 质控图数据 (后端 /api/qc/charts/lj, 参数 lotId/limit/beginDate/endDate)
// 后端返回 {targetValue, sd, cv, level, actualMean, actualSd, points, ...}
// 前端期望 {lot, points, mean, sd} - mean/sd 使用实际计算值用于Z分数和控制线
export function getQcChartData(params: { lotId: number; beginDate?: string; endDate?: string }) {
  const query: Record<string, any> = { lotId: params.lotId }
  if (params.beginDate) query.beginDate = params.beginDate
  if (params.endDate) query.endDate = params.endDate
  return request.get<any>('/qc/charts/lj', query).then((data) => {
    // 转换后端数据结构为前端期望的 QcChartData
    const targetValue = Number(data.targetValue ?? 0)
    const sd = Number(data.sd ?? 0)
    const cv = data.cv !== null && data.cv !== undefined ? Number(data.cv) : undefined
    // 实际均值和实际SD (后端基于测量数据计算)
    const actualMean = data.actualMean !== null && data.actualMean !== undefined
      ? Number(data.actualMean) : targetValue
    const actualSd = data.actualSd !== null && data.actualSd !== undefined
      ? Number(data.actualSd) : sd
    return {
      lot: {
        id: params.lotId,
        lotNo: data.lotNo || '',
        testItemId: 0,
        targetValue,
        sd,
        cv,
        level: data.level || 1,
        status: 1
      } as QcLot,
      points: (data.points || []).map((p: any) => ({
        measureTime: p.measureTime,
        measureValue: Number(p.measureValue ?? 0),
        zScore: p.zScore !== null && p.zScore !== undefined ? Number(p.zScore) : null,
        qcResult: p.qcResult === 'ACCEPT' || p.qcResult === 1 ? 1 : 0,
        ruleViolation: p.ruleViolation
      })) as QcChartPoint[],
      // mean 和 sd 使用实际计算值, 用于对比显示
      mean: actualMean,
      sd: actualSd
    } as QcChartData
  })
}

// Z-分数图数据
export function getZScoreChartData(params: { lotId: number; limit?: number; beginDate?: string; endDate?: string }) {
  const query: Record<string, any> = { lotId: params.lotId }
  if (params.limit) query.limit = params.limit
  if (params.beginDate) query.beginDate = params.beginDate
  if (params.endDate) query.endDate = params.endDate
  return request.get<any>('/qc/charts/z-score', query)
}

// Westgard 规则列表 (后端 /api/qc/rules)
// 后端 ruleType 为字符串 WARNING/REJECT, 前端转换为数字 1=警告 2=拒绝
export function getQcRuleList() {
  return request.get<any[]>('/qc/rules').then((list) => {
    return (list || []).map((r: any) => ({
      id: r.id,
      ruleCode: r.ruleCode,
      ruleName: r.ruleName,
      ruleType: r.ruleType === 'WARNING' ? 1 : 2,
      enabled: Number(r.enabled ?? 1),
      description: r.description
    })) as QcRule[]
  })
}

// 启停规则 (后端 PUT /api/qc/rules/{id}/enabled?enabled=1/0)
export function updateQcRule(data: QcRule) {
  return request.put<boolean>(`/qc/rules/${data.id}/enabled?enabled=${data.enabled}`)
}

// 规则类型字典
export const QC_RULE_TYPE_MAP: Record<number, { label: string; type: 'warning' | 'danger' }> = {
  1: { label: '警告', type: 'warning' },
  2: { label: '拒绝', type: 'danger' }
}

// ==================== 质控偏差趋势预警 ====================

export interface QcAlert {
  id: number
  alertType?: string
  lotId?: number
  testItemId?: number
  instrumentId?: number
  lastMeasureValue?: number
  lastZScore?: number
  message?: string
  status?: string // PENDING / READ
  alertTime?: string
  createTime?: string
  testItemName?: string
  instrumentName?: string
  lotNo?: string
}

export function getQcAlertPage(params: { pageNum?: number; pageSize?: number; status?: string }) {
  const { pageNum = 1, pageSize = 10, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (status) query.status = status
  return request.get<PageResult<QcAlert>>('/qc/alerts/page', query)
}

export function markQcAlertRead(id: number) {
  return request.put<boolean>(`/qc/alerts/${id}/read`)
}

export const QC_RESULT_MAP: Record<number, { label: string; type: 'success' | 'danger' }> = {
  0: { label: '失控', type: 'danger' },
  1: { label: '在控', type: 'success' }
}

export const QC_LEVEL_MAP: Record<number, string> = {
  1: '低浓度',
  2: '中浓度',
  3: '高浓度'
}

// 质控月报
export interface QcMonthlyReport {
  year: number
  month: number
  totalRecords: number
  outOfControlCount: number
  outOfControlRate: number
  itemReports: QcItemReport[]
  details: QcReportDetail[]
}

export interface QcItemReport {
  testItemId: number
  itemName: string
  instrumentId?: number
  instrumentName: string
  totalCount: number
  outCount: number
  cv: number
  passRate: number
}

export interface QcReportDetail {
  id: number
  testItemId: number
  itemName: string
  instrumentName: string
  measureValue: number
  measureTime: string
  zScore?: number
  ruleViolation?: string
  qcResult: string
}

export function getQcMonthlyReport(year: number, month: number) {
  return request.get<any>('/qc/report/monthly', { year, month }).then((data) => {
    return {
      year: data.year,
      month: data.month,
      totalRecords: data.totalRecords || 0,
      outOfControlCount: data.outOfControlCount || 0,
      outOfControlRate: data.outOfControlRate || 0,
      itemReports: (data.itemReports || []).map((r: any) => ({
        ...r,
        cv: r.cv ? Number(r.cv) : 0,
        measureValue: r.measureValue !== undefined ? Number(r.measureValue) : undefined
      })),
      details: (data.details || []).map((d: any) => ({
        ...d,
        measureValue: d.measureValue !== undefined ? Number(d.measureValue) : 0,
        zScore: d.zScore !== null && d.zScore !== undefined ? Number(d.zScore) : undefined
      }))
    } as QcMonthlyReport
  })
}

// ==================== 技师工作站: 手工标本转质控 ====================

export interface SampleQcMapping {
  testItemId: number
  qcLotId: number
}

/** 手工标本转质控标本: 检验结果 -> 质控记录 */
export function sampleToQc(data: {
  sampleId: number
  operatorId?: number
  mappings: SampleQcMapping[]
}) {
  return request.post<any[]>('/qc/records/from-sample', data).then((list) => {
    return (list || []).map((r: any) => ({
      id: r.id,
      qcLotId: r.qcLotId,
      measureValue: Number(r.measureValue ?? 0),
      qcResult: r.qcResult === 'ACCEPT' || r.qcResult === 1 ? 1 : 0,
      ruleViolation: r.ruleViolation,
      zScore: r.zScore !== null && r.zScore !== undefined ? Number(r.zScore) : undefined
    }))
  })
}

/** 取消质控: 删除指定标本生成的全部质控记录 */
export function cancelQcBySample(sampleId: number) {
  return request.delete<number>(`/qc/records/by-sample/${sampleId}`)
}

// ==================== 室内质控管理 (IQC, iqc-module-design.md) ====================

export interface IqcProject {
  lotId: number
  lotNo?: string
  itemId?: number
  itemCode?: string
  itemName?: string
  group?: string
  method?: string
  unit?: string
  instrumentId?: number | string   // Long → String 序列化, 需前端 toNumber
  instrumentName?: string
  brand?: string
  model?: string
  level?: number
  target?: number
  sd?: number
  cv?: number
  lastValue?: number
  lastZ?: number
  lastResult?: string
  status?: string // 在控/警告/失控
}

export interface IqcProjectDetail {
  lot: any
  rules: QcRule[]
  points: { measureTime: string; measureValue: number; zScore: number | null; qcResult: number; ruleViolation?: string }[]
  target?: number
  sd?: number
  warning?: boolean
}

export function getIqcSummary() {
  return request.get<{ total: number; inControl: number; warning: number; outOfControl: number }>('/qc/iqc/summary')
}

export function getIqcProjects(params?: { instrumentId?: number; group?: string; status?: string; keyword?: string }) {
  return request.get<IqcProject[]>('/qc/iqc/projects', params || {})
}

export function getIqcProjectDetail(lotId: number) {
  return request.get<IqcProjectDetail>(`/qc/iqc/projects/${lotId}`)
}

// ==================== 失控报告管理 (OCC, occ-module-design.md) ====================

export interface OccCheckItem { text: string; done: boolean }

export interface OccStepVO {
  id?: number
  stepNo?: number
  name?: string
  status?: string // PENDING/CURRENT/DONE
  checklist?: OccCheckItem[]
  operator?: string
  operatedAt?: string
  conclusion?: string
}

export interface OccApprovalVO {
  id?: number
  levelNo?: number
  levelName?: string
  status?: string // PENDING/APPROVED/REJECTED
  approver?: string
  comment?: string
  approvedAt?: string
}

export interface OccEvent {
  id?: number
  occNo?: string
  lotId?: number
  projectCode?: string
  projectName?: string
  groupName?: string
  triggerRules?: string
  foundAt?: string
  foundBy?: string
  status?: string // PENDING/INVESTIGATING/CORRECTED/CLOSED
  errorType?: string
  qcValues?: string
  targetValue?: number
  sd?: number
  rootCauseCategory?: string
  rootCauseDesc?: string
  rootCauseBy?: string
  rootCauseAt?: string
  resultImpact?: string
  prevention?: string
  materialSnapshot?: string
  currentStep?: number
  createTime?: string
  steps?: OccStepVO[]
  approvals?: OccApprovalVO[]
}

export function getOccList(params: { pageNum?: number; pageSize?: number; status?: string; keyword?: string; range?: string }) {
  const { pageNum = 1, pageSize = 10, status, keyword, range } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (status) query.status = status
  if (keyword) query.keyword = keyword
  if (range) query.range = range
  return request.get<PageResult<OccEvent>>('/qc/occ/list', query).then((res) => ({
    ...res,
    records: (res.records || []).map(normalizeOcc)
  }))
}

export function getOccDetail(id: number) {
  return request.get<OccEvent>(`/qc/occ/${id}`).then(normalizeOcc)
}

export function createOcc(data: Partial<OccEvent>) {
  return request.post<OccEvent>('/qc/occ/create', data).then(normalizeOcc)
}

export function updateOccStep(id: number, stepNo: number, payload: { checklist?: OccCheckItem[]; conclusion?: string }) {
  return request.put<OccEvent>(`/qc/occ/${id}/steps/${stepNo}`, payload).then(normalizeOcc)
}

export function confirmOccRootCause(id: number, payload: { category?: string; description?: string }) {
  return request.post<OccEvent>(`/qc/occ/${id}/root-cause/confirm`, payload).then(normalizeOcc)
}

export function approveOcc(id: number, params: { levelNo: number; approved: boolean; comment?: string }) {
  return request.post<OccEvent>(`/qc/occ/${id}/approve`, null, { params: { levelNo: params.levelNo, approved: params.approved, comment: params.comment || '' } }).then(normalizeOcc)
}

/** OCC 阶梯 checklist 为 JSON 字符串, 统一解析成数组避免前端 .every/.done 报错 */
function normalizeOcc(e: OccEvent): OccEvent {
  if (!e || !Array.isArray(e.steps)) return e
  return {
    ...e,
    steps: e.steps.map((st) => {
      let c: any = st.checklist
      if (typeof c === 'string') {
        try { c = JSON.parse(c || '[]') } catch (_) { c = [] }
      }
      return { ...st, checklist: Array.isArray(c) ? c : [] }
    })
  }
}
