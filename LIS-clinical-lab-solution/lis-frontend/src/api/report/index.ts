import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

export interface ResultVO {
  id: number; sampleId: number; testItemId: number; testItemCode: string; testItemName: string;
  resultValue: string; resultText: string; unit: string; refRange: string; abnormalFlag: string;
  abnormalText: string; isCritical: number; instrumentId: number; instrumentName: string;
  operatorId: number; operatorName: string; testTime: string; auditStatus: number; auditStatusText: string;
  auditorId: number; auditTime: string; createTime: string;
  panelId?: number; panelName?: string; englishName?: string; category?: string; interpretation?: string;
  lastResult?: string; lastFlag?: string; lastTestTime?: string;
  /** 英文缩写 (来自检验指标 english_code 或 testItemCode, 无则显示'-') */
  englishCode?: string;
  /** 检验方法 (来自仪器 method 或项目配置, 无则兜底 '-' ) */
  testMethod?: string;
  /** 广东省检验结果互认标识: 1=带*号 / 0=不带 */
  mutuallyRecognized?: number;
  /** 组标识: 'urine-chem' 尿液化学 / 'urine-sediment' 尿沉渣, 用于尿常规左右双栏分组 */
  groupKey?: string;
}
export interface ReportVO {
  id: number; reportNo: string; sampleId: number; patientId: number; patientName: string;
  reportType: number; status: number; statusText: string; auditorId: number; auditTime: string;
  publisherId: number; publishTime: string; reportDoctor: string; checker: string; pdfPath: string;
  hisSyncStatus: number; hisSyncTime: string; createTime: string; results: ResultVO[];
  patientGender?: number; patientAge?: number; hospitalName?: string;
  department?: string; doctor?: string; clinicalDiag?: string;
  sampleNo?: string; sampleBarcode?: string; receiveTime?: string;
  visitType?: number; source?: string; testItems?: string;
  visitNo?: string;
  auditor?: string;
  /** 住院号 (无则回退到就诊号 visitNo) */
  inpatientNo?: string;
  /** 床号 */
  bedNo?: string;
  /** 标本编号 */
  specimenNo?: string;
  /** 标本类型 (冗余字段, 便于打印) */
  specimenType?: string;
  /** 申请项目名称 (多个项目时顿号拼接) */
  applyItemNames?: string;
  /** 开嘱医生 (无则使用 doctor / applyDoctor) */
  orderDoctor?: string;
  /** 开嘱时间 (无则使用 applyTime) */
  orderTime?: string;
  /** 申请时间 */
  applyTime?: string;
  /** 采样人 (姓名) */
  collectorName?: string;
  /** 采样时间 */
  collectTime?: string;
  /** 送检时间 */
  deliverTime?: string;
  /** 送检人 */
  deliverPerson?: string;
  /** 接收人 (核收人) */
  receiverName?: string;
  /** 检测医师 (检验者, 无则使用 checker) */
  testerName?: string;
  /** 审核者 (无则使用 auditor / reportDoctor 兜底) */
  auditorName?: string;
  /** 报告人 (无则使用 reportDoctor) */
  reporterName?: string;
  /** 医院Logo URL (可选, 有则在标题区左侧展示) */
  hospitalLogo?: string;
  /** 报告副标题/分院名称, 如 "连平县人民医院" */
  hospitalSubName?: string;
  /** 温馨提示文本 (多条时换行拼接) */
  warmTips?: string[];
}
export function getReportPage(params: any) { return request.get<PageResult<ReportVO>>('/report/page', params) }
/** 患者某检验项目历史趋势 (后端返回时间升序最近20次) */
export interface TrendPoint {
  resultId: number; sampleId: number; testItemId: number;
  resultValue: string; resultText: string; unit: string;
  abnormalFlag: string; isCritical: number; testTime: string;
}
export function getResultTrend(patientId: number, testItemId: number) {
  return request.get<TrendPoint[]>('/report/results/trend', { patientId, testItemId })
}
export function getReportById(id: number) { return request.get<ReportVO>(`/report/${id}`) }
export function getReportBySampleId(sampleId: number) { return request.get<ReportVO>(`/report/sample/${sampleId}`) }
export function generateReport(sampleId: number) { return request.post<ReportVO>(`/report/generate/${sampleId}`, undefined) }
export function auditReport(data: { reportId: number; pass: boolean; reason?: string }) { return request.put<void>('/report/audit', data) }
export function publishReport(reportId: number) {
  return request.put<void>('/report/publish', null, { params: { reportId, publisherId: localStorage.getItem('userId') } })
}
export function getCriticalList(params?: { startDate?: string; endDate?: string }) { return request.get<unknown[]>('/report/critical/list', params) }
export function alertCritical(resultId: number) { return request.post<void>(`/report/critical/${resultId}/alert`, undefined) }
export function notifyCritical(resultId: number, data: {
  notifier?: string; notifyTime?: string; sender?: string; sendTime?: string; remark?: string
}) { return request.post<void>(`/report/critical/${resultId}/notify`, data) }
export function cancelNotifyCritical(resultId: number) { return request.post<void>(`/report/critical/${resultId}/cancel-notify`, undefined) }
export function updateCriticalRecord(resultId: number, data: {
  notifier?: string; notifyTime?: string; sender?: string; sendTime?: string; remark?: string
}) { return request.put<void>(`/report/critical/${resultId}/record`, data) }
export function confirmCritical(resultId: number) {
  return request.put<void>('/report/critical/confirm', null, { params: { resultId, confirmerId: localStorage.getItem('userId') } })
}
export function getResultsBySampleId(sampleId: number) { return request.get<ResultVO[]>(`/report/results/sample/${sampleId}`) }

/** 修改报告 (留痕): 按 resultId 修改结果值并填写修改原因 */
export function modifyReport(data: {
  reportId: number
  results: { resultId: number; resultValue: string; modifyReason: string }[]
}) { return request.put<void>('/report/modify', data) }

// ========== 标本合并 ==========
/**
 * 标本合并: 将多个源标本合并到目标标本 (结果/项目归并)
 * 后端 POST /api/report/merge, body: { targetSampleId, sourceSampleIds }
 */
export function mergeReports(targetSampleId: number, sourceSampleIds: number[]) {
  return request.post<void>('/report/merge', { targetSampleId, sourceSampleIds })
}
