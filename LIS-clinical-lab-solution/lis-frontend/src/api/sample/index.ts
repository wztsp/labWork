import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

// ========== 检验申请 ==========
export interface OrderItemVO {
  id: number
  orderId: number
  testItemId: number
  testItemCode: string
  testItemName: string
  status: number
  fee?: number
  createTime?: string
}

export interface OrderVO {
  id: number | string
  orderNo: string
  visitId: number | string
  visitNo?: string
  patientId: number | string
  patientName?: string
  patientGender?: string
  patientAge?: string
  visitType?: string
  orderType?: string
  status: number
  applyDoctor?: string
  applyDept?: string
  clinicalDiag?: string
  /** 患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等 */
  patientCondition?: string
  /** 采集方式ID (关联 lis_collection_method.id) */
  collectionMethodId?: number | string
  /** 采集方式名称 (冗余, 便于直接展示) */
  collectionMethodName?: string
  applyTime?: string
  createTime?: string
  items?: OrderItemVO[]
  /** 该申请已生成的标本数 */
  sampleCount?: number
}

export interface OrderQuery {
  visitNo?: string
  patientName?: string
  orderNo?: string
  status?: number
  current?: number
  size?: number
}

export function getOrderPage(params: OrderQuery) {
  return request.get<PageResult<OrderVO>>('/sample/orders/page', params)
}

export function receiveOrder(data: any) {
  return request.post<SampleVO[]>('/sample/orders/receive', data)
}

// ========== 创建检验申请 ==========
export interface OrderItemDTO {
  testItemId: number | string
  testItemCode: string
  testItemName: string
  fee?: number
}

export interface OrderCreateDTO {
  patientName: string
  patientGender?: string
  patientAge?: string
  orderType: string // 门诊/住院/急诊
  applyDoctor: string
  applyDept: string
  clinicalDiag?: string
  patientCondition?: string
  collectionMethodId?: number | string
  collectionMethodName?: string
  items: OrderItemDTO[]
}

/**
 * 创建检验申请 (调用后端 receive 接口)
 */
export function createOrder(data: OrderCreateDTO) {
  return request.post<any[]>('/sample/orders/receive', data)
}

/**
 * 更新申请的采集方式
 * @param orderId 申请ID
 * @param collectionMethodId 采集方式ID (传 null/undefined 清空)
 * @param collectionMethodName 采集方式名称(冗余)
 */
export function updateOrderCollectionMethod(
  orderId: number | string,
  collectionMethodId: number | string | null,
  collectionMethodName?: string
) {
  return request.put<void>(`/sample/orders/${orderId}/collection-method`, {
    collectionMethodId: collectionMethodId ?? null,
    collectionMethodName: collectionMethodName ?? null
  })
}

export interface SampleVO {
  id: number
  sampleBarcode: string
  sampleNo?: string
  instrumentId?: number
  instrumentName?: string
  orderId: number
  patientId: number
  patientName: string
  visitId: number
  /** 就诊号 (从 lis_visit 冗余) */
  visitNo?: string
  sampleType: string
  containerType: string
  status: number
  statusText: string
  isUrgent: number
  collectTime: string
  /** 送检时间 */
  deliverTime?: string
  /** 送检人 */
  deliverPerson?: string
  /** 签收时间 */
  signOffTime?: string
  /** 签收人 */
  signOffPerson?: string
  /** 拒收时间 */
  rejectTime?: string
  /** 拒收人 */
  rejectPerson?: string
  /** 不合格类型 */
  rejectType?: string
  /** 拒收原因 */
  rejectReason?: string
  /** 拒收次数 */
  rejectCount?: number
  receiveTime: string
  testStartTime: string
  reportTime: string
  operatorId: number
  collectorId: number
  collectorName?: string
  department: string
  remark: string
  createTime: string
  updateTime: string
  trackNodes?: any[]
  /** 该标本对应的检验项目列表 (一个条码可对应多个检验项目) */
  orderItems?: OrderItemVO[]
}

export interface SampleQuery {
  sampleBarcode?: string
  /** 就诊号查询 */
  visitNo?: string
  patientName?: string
  status?: number
  isUrgent?: number
  department?: string
  /** 采集日期范围 */
  startDate?: string
  endDate?: string
  /** 签收日期范围 */
  signOffStartDate?: string
  signOffEndDate?: string
  /** 送检日期范围 */
  deliverStartDate?: string
  deliverEndDate?: string
  /** 申请单ID查询 (雪花ID, 用 string 避免精度丢失) */
  orderId?: number | string
  /** 就诊ID查询 (精确匹配) */
  visitId?: number | string
  /** 就诊ID列表查询 (当匹配多就诊时合并加载) */
  visitIds?: (number | string)[]
  /** 采集人ID查询 */
  collectorId?: number
  /** 送检人查询 */
  deliverPerson?: string
  /** 签收人查询 */
  signOffPerson?: string
  current?: number
  size?: number
}

export interface SampleCollectDTO {
  sampleBarcode?: string
  sampleId?: number
  /** 采集人ID (关联人事员工 hr_staff.id) */
  collectorId?: number
  /** 采集人姓名 (冗余) */
  collectorName?: string
  collectTime?: string
  /** 采集备注 */
  remark?: string
}

export interface SampleReceiveDTO {
  sampleBarcode: string
  instrumentId: number
  operatorId?: number
  department?: string
  remark?: string
}

/** 标本批量操作 DTO (送检/签收) */
export interface SampleBatchDTO {
  /** 标本条码列表 */
  barcodes: string[]
  /** 操作人 (送检人/签收人) 姓名 (冗余) */
  operator?: string
  /** 操作人ID (关联人事员工 hr_staff.id, 优先使用) */
  operatorId?: number
}

/** 标本不合格类型常量 */
export const SAMPLE_REJECT_TYPES = [
  '溶血',
  '脂血',
  '黄疸',
  '凝集',
  '标本量不足',
  '标本类型错误',
  '容器错误',
  '标本污染',
  '标识错误',
  '采集时间错误',
  '标本丢失',
  '标本破损',
  '患者信息不符',
  '其他'
] as const

/** 标本拒收 DTO */
export interface SampleRejectDTO {
  /** 标本条码 */
  barcode: string
  /** 不合格类型 */
  rejectType: string
  /** 拒收原因 */
  rejectReason?: string
  /** 拒收人姓名 (冗余) */
  operator?: string
  /** 拒收人ID (关联人事员工 hr_staff.id, 优先使用) */
  operatorId?: number
}

// ========== 标本基础接口 ==========
export function getSamplePage(params: SampleQuery) {
  return request.get<PageResult<SampleVO>>('/sample/page', params)
}
export function getSampleById(id: number) { return request.get<SampleVO>(`/sample/${id}`) }
export function getSampleByBarcode(barcode: string) { return request.get<SampleVO>(`/sample/barcode/${barcode}`) }
export function createSample(data: any) { return request.post<SampleVO>('/sample', data) }

// ========== 试管分组计算(后端自动合管) ==========

/** 试管分组中的检验项目信息 (含跨申请关联信息) */
export interface TubeGroupTestItem {
  testItemId: number | string
  itemCode: string
  itemName: string
  specimenType?: string
  tubeId?: number | string
  /** 申请ID (跨申请合管时标识项目所属申请) */
  orderId?: number | string
  /** 申请项目ID (lis_order_item.id) */
  orderItemId?: number | string
}

/** 试管分组 */
export interface TubeGroup {
  groupIndex: number
  tubeId?: number | string
  tubeName?: string
  tubeColor?: string
  sampleType?: string
  containerType?: string
  testItems: TubeGroupTestItem[]
  itemCount?: number
}

/** 批量生成条码的分组内项目 (含关联信息) */
export interface BarcodeGroupTestItem {
  testItemId: number | string
  orderId: number | string
  orderItemId: number | string
}

/** 批量生成条码的分组 (一个标本可关联多个申请项目) */
export interface BarcodeGroup {
  tubeId?: number | string
  tubeName?: string
  sampleType?: string
  containerType?: string
  testItems: BarcodeGroupTestItem[]
}

/** 批量生成条码DTO (按就诊维度跨申请合管) */
export interface BatchBarcodeDTO {
  visitId: number | string
  patientId: number | string
  patientName: string
  visitNo?: string
  department?: string
  groups: BarcodeGroup[]
}

/**
 * 计算试管分组: 按就诊维度查询所有申请的检验项目, 按相同试管+相同标本类型跨申请合管
 * @param visitId 就诊ID (雪花ID用string传输避免精度丢失)
 */
export function getTubeGroups(visitId: number | string) {
  return request.get<TubeGroup[]>('/sample/tube-groups', { visitId })
}

/**
 * 批量生成条码: 按试管分组批量创建标本, 每组一个条码, 写入标本-申请项目关联
 * 已生成条码的申请项目不可重复生成
 */
export function batchCreateBarcodes(data: BatchBarcodeDTO) {
  return request.post<SampleVO[]>('/sample/batch', data)
}

/**
 * 取消条码: 删除标本及中间表关联, 允许重新生成条码
 * 仅允许未采集的标本取消条码, 已采集/送检/签收/核收的标本不可取消
 */
export function cancelBarcode(barcode: string) {
  return request.delete<void>(`/sample/barcode/${barcode}`)
}

export function receiveSample(data: SampleReceiveDTO) { return request.put<SampleVO>('/sample/receive', data) }
/** 核收前预检: 返回不可检测的项目名称列表(空数组表示通过) */
export function precheckReceive(barcode: string, instrumentId: number) {
  return request.get<string[]>('/sample/receive/precheck', { barcode, instrumentId })
}

/** 按就诊/门诊/住院标识查询可核收标本(状态=已送检2/已签收3), 供技师工作站多标本选择
 *  type: visitNo就诊号 / outpatient门诊号 / inpatient住院号 */
export interface ReceivableSample {
  id: number | string
  sampleBarcode: string
  sampleNo?: string
  patientName?: string
  sampleType?: string
  containerType?: string
  status?: number
  isUrgent?: number
  department?: string
  visitNo?: string
  collectTime?: string
  visitType?: number
  testItems?: string
}
export function lookupReceivableSamples(type: string, keyword: string) {
  return request.get<ReceivableSample[]>('/sample/receive/lookup', { type, keyword })
}
export function collectSample(data: SampleCollectDTO) { return request.put<SampleVO>('/sample/collect', data) }
/**
 * 标本取消采集: 批量取消采集, 状态回退到已申请, 清除采集时间/采集人
 * 仅允许已采集(status=1)且未送检的标本取消采集
 */
export function cancelCollectSamples(data: SampleBatchDTO) {
  return request.put<SampleVO[]>('/sample/cancel-collect', data)
}
export function updateSampleStatus(id: number, status: number) { return request.put<void>(`/sample/${id}/status`, { status }) }
export function trackSample(id: number) { return request.get<SampleVO>(`/sample/${id}/track`) }

// ========== 标本退费 / 作废 ==========
/**
 * 标本退费: 后端 PUT /api/sample/{id}/refund?reason=...
 */
export function refundSample(id: number, reason = '标本退费') {
  return request.put<SampleVO>(`/sample/${id}/refund?reason=${encodeURIComponent(reason)}`)
}
/**
 * 标本作废: 后端 PUT /api/sample/{id}/void?reason=...
 */
export function voidSample(id: number, reason = '标本作废') {
  return request.put<SampleVO>(`/sample/${id}/void?reason=${encodeURIComponent(reason)}`)
}

// ========== 标本送检 ==========
/**
 * 标本送检: 批量送检, 校验已采集才能送检
 * 请求体: { barcodes: ["BC001","BC002"], operator: "送检人姓名" }
 * 返回送检后的标本列表
 */
export function deliverSamples(data: SampleBatchDTO) {
  return request.put<SampleVO[]>('/sample/deliver', data)
}

/**
 * 标本取消送检: 批量取消送检, 清除送检时间/送检人
 * 仅允许已送检但未签收、未核收、未报告的标本取消送检
 */
export function cancelDeliverSamples(data: SampleBatchDTO) {
  return request.put<SampleVO[]>('/sample/cancel-deliver', data)
}

// ========== 标本签收 ==========
/**
 * 标本签收: 批量签收, 校验已送检才能签收
 * 请求体: { barcodes: ["BC001","BC002"], operator: "签收人姓名" }
 * 返回签收后的标本列表
 */
export function signOffSamples(data: SampleBatchDTO) {
  return request.put<SampleVO[]>('/sample/signoff', data)
}

/**
 * 标本取消签收: 批量取消签收, 状态回退到已送检, 清除签收时间/签收人
 * 仅允许已签收但未核收、未报告的标本取消签收
 */
export function cancelSignOffSamples(data: SampleBatchDTO) {
  return request.put<SampleVO[]>('/sample/cancel-signoff', data)
}

// ========== 标本拒收 ==========
/**
 * 标本拒收: 采集/签收环节均可拒收, 需选择不合格类型
 * 请求体: { barcode, rejectType, rejectReason, operator }
 */
export function rejectSample(data: SampleRejectDTO) {
  return request.put<SampleVO>('/sample/reject', data)
}

// ========== 技师工作站 ==========
export interface TechnicianSample {
  id: number
  sampleBarcode: string
  sampleNo?: string
  patientName: string
  patientGender?: string
  patientAge?: string
  visitNo?: string
  visitType?: string
  testItems: string
  department?: string
  doctor?: string
  collectTime?: string
  receiveTime?: string
  testTime?: string
  reportTime?: string
  status: number
  isUrgent: number
  abnormalCount?: number
  criticalCount?: number
  instrumentId?: number
  // ===== 无主标本扩展（isUnmatched=true 时有效） =====
  isUnmatched?: boolean
  unmatchedSampleNo?: string
  unmatchedInstrumentId?: number
  unmatchedInstrumentName?: string
  /** 该卡片内包含的无主结果指标条数 */
  unmatchedResultCount?: number
  /** 摘要文本（如：ALT=35 | AST=28 | ...），用于卡片 hover 或直接显示 */
  unmatchedResultSummary?: string
  /** 无主标本结果ID集合，用于批量认领/作废 */
  unmatchedResultIds?: number[]
}

export interface TechnicianResult {
  id?: number | null
  testItemId: number
  testItemCode: string
  testItemName: string
  itemNameEn?: string
  resultValue?: string | null
  unit?: string
  refRange?: string
  abnormalFlag?: string
  isCritical?: boolean
  instrumentId?: number
  instrumentName?: string
  category?: string
  panelId?: number
  panelName?: string
  appliedToReport?: boolean
  imageUrl?: string
  /** NO. (序号, 1-based) */
  itemNo?: number
  /** 上次结果值 */
  lastResultValue?: string | null
  /** 上次结果时间 */
  lastResultTime?: string | null
  /** 上次结果异常标志 */
  lastAbnormalFlag?: string | null
  /** OD值 (光密度) */
  odValue?: number | string | null
  /** CUTOFF值 */
  cutoffValue?: number | string | null
  /** S/CO值 */
  scRatio?: number | string | null
  /** 自动审核: 0未做 1通过 2异常 */
  autoAudit?: number
  /** 自动审核异常信息 */
  autoAuditMessage?: string | null
  /** 项目知识 / 临床提示 */
  itemKnowledge?: string | null
}

export interface TechnicianPatientsInfo {
  patientId: number
  patientName: string
  gender: string
  age: string
  visitNo: string
  visitType: string
  department: string
  doctor: string
  diagnosis?: string
  idCard?: string
  phone?: string
  address?: string
  sampleType?: string
  collectTime?: string
  receiveTime?: string
  testTime?: string
  reportTime?: string
  specimenNo?: string
  sampleNo?: string
  specimenSerialNo?: string
  feeReceivable?: number
  feeActual?: number
  collector?: string
  receiver?: string
  inspector?: string
  reporter?: string
  publisher?: string
  applyTime?: string
}

/** 标本级图像 (一个标本对应多个图像, 血常规/尿常规等形态图像) */
export interface TechnicianSampleImage {
  id: number
  sampleId: number
  imageUrl: string
  imageName?: string
  imageType?: string
  sortNo?: number
}

/** 技师工作站标本详情 */
export interface TechnicianDetail {
  patient: TechnicianPatientsInfo
  results: TechnicianResult[]
  images: TechnicianSampleImage[]
}

export interface TechnicianSampleQuery {
  current?: number
  size?: number
  status?: number
  startDate?: string
  endDate?: string
  keyword?: string
  visitType?: string
  /** 核收仪器ID */
  instrumentId?: number
}

export function getTechnicianSamples(params: TechnicianSampleQuery) {
  const { current = 1, size = 50, status, startDate, endDate, keyword, visitType, instrumentId } = params
  const query: Record<string, any> = { current, size }
  if (status !== undefined && status !== '') query.status = status
  if (startDate) query.startDate = startDate
  if (endDate) query.endDate = endDate
  if (keyword) query.keyword = keyword
  if (visitType) query.visitType = visitType
  if (instrumentId !== undefined && instrumentId !== null) query.instrumentId = instrumentId
  return request.get<any>('/report/technician/samples', query).then((res: any) => {
    // 后端返回数组, 包装为分页格式
    const list = Array.isArray(res) ? res : (res.records || res.data || [])
    return { records: list, total: list.length, current: 1, size: list.length }
  })
}

export function getTechnicianDetail(sampleId: number | string) {
  return request.get<TechnicianDetail>(`/report/technician/${sampleId}`)
}

export function saveTechnicianResult(sampleId: number | string, results: TechnicianResult[]) {
  return request.post<any>(`/report/technician/${sampleId}/results`, results)
}

export function auditTechnicianReport(sampleId: number, data: { pass: boolean; reason?: string }) {
  return request.put<boolean>(`/report/technician/${sampleId}/audit`, data)
}

export function getTechnicianSummary(params?: { instrumentId?: number; startDate?: string; endDate?: string }) {
  return request.get<any>('/report/technician/summary', params)
}

// ========== 技师工作站批量操作接口 ==========

/** 批量审核 DTO */
export interface BatchAuditDTO {
  sampleIds: (number | string)[]
  pass: boolean
  reason?: string
}

/** 批量修改结果 DTO - 统一修改某个指标的值或偏移 */
export interface BatchModifyResultDTO {
  sampleIds: (number | string)[]
  /** 要修改的检验指标ID，不传表示修改所有指标 */
  testItemId?: number
  /** 修改模式：fixed=固定值，add=加偏移，multiply=乘系数 */
  mode: 'fixed' | 'add' | 'multiply'
  /** 固定值/偏移量/系数值 */
  value: string | number
  /** 仅修改结果为空的（避免覆盖已回传的） */
  onlyEmpty?: boolean
}

/** 批量修改检验人 DTO */
export interface BatchModifyInspectorDTO {
  sampleIds: (number | string)[]
  inspectorId: number | string
  inspectorName: string
  /** 是否覆盖已有检验人 */
  override?: boolean
}

/** 让步核收 DTO */
export interface WaiveReceiveDTO {
  sampleIds: (number | string)[]
  /** 让步原因 */
  reason: string
  /** 审批人 */
  approver?: string
  remark?: string
}

/** 标本复制 DTO */
export interface SampleCopyDTO {
  sampleIds: (number | string)[]
  /** 复制数量（每个标本复制几份） */
  copyCount?: number
  /** 是否复制检验结果 */
  copyResults?: boolean
}

/** 标本拆分 DTO */
export interface SampleSplitDTO {
  sampleId: number | string
  /** 拆分数目 */
  splitCount: number
  /** 每个子标本分配的检验项目ID列表 { subSampleIndex: 0, testItemIds: [] } */
  splits?: { index: number; testItemIds: (number | string)[] }[]
}

/** 标本销毁 DTO */
export interface SampleDestroyDTO {
  sampleIds: (number | string)[]
  /** 销毁原因 */
  reason: string
  /** 审批人 */
  approver?: string
  /** 确认密码（管理员密码） */
  confirmPassword?: string
}

/** 仪器人员签到 DTO */
export interface InstrumentSigninDTO {
  instrumentId: number
  operatorId: number
  operatorName: string
  signinTime?: string
  remark?: string
}

/** 自动审核签到 DTO */
export interface AutoAuditSigninDTO {
  operatorId: number
  operatorName: string
  /** true=签到, false=签退 */
  action: 'signin' | 'signout'
}

/** 批量操作返回结果 */
export interface BatchOpResult {
  success: number
  failed: number
  total: number
  /** 失败详情 [{ id, reason }] */
  failures?: { id: number | string; reason: string }[]
}

export function batchAuditTechnician(data: BatchAuditDTO) {
  return request.post<BatchOpResult>('/report/technician/batch-audit', data)
}
export function batchUnauditTechnician(sampleIds: (number | string)[]) {
  // 后端接口 @RequestBody List<Long> 接收 JSON 数组, 需直接传数组而非 { sampleIds }
  return request.post<BatchOpResult>('/report/technician/batch-unaudit', sampleIds)
}
export function batchModifyResult(data: BatchModifyResultDTO) {
  return request.post<BatchOpResult>('/report/technician/batch-modify-result', data)
}
export function batchModifyInspector(data: BatchModifyInspectorDTO) {
  return request.post<BatchOpResult>('/report/technician/batch-modify-inspector', data)
}
export function waiveReceive(data: WaiveReceiveDTO) {
  return request.post<BatchOpResult>('/report/technician/waive-receive', data)
}
export function copySample(data: SampleCopyDTO) {
  return request.post<BatchOpResult>('/report/technician/sample-copy', data)
}
export function splitSample(data: SampleSplitDTO) {
  return request.post<BatchOpResult>('/report/technician/sample-split', data)
}
export function unsplitSample(sampleId: number | string) {
  return request.post<BatchOpResult>('/report/technician/sample-unsplit', { sampleId })
}
export function destroySample(data: SampleDestroyDTO) {
  return request.post<BatchOpResult>('/report/technician/sample-destroy', data)
}
export function instrumentSignin(data: InstrumentSigninDTO) {
  return request.post<BatchOpResult>('/report/technician/instrument-signin', data)
}
export function autoAuditSignin(data: AutoAuditSigninDTO) {
  return request.post<BatchOpResult>('/report/technician/auto-audit-signin', data)
}

/** 批量补打条码 - 返回打印数据 */
export function batchReprintBarcode(sampleIds: (number | string)[]) {
  return request.post<any>('/report/technician/batch-reprint-barcode', { sampleIds })
}

/** 批量导入 - 返回导入结果 */
export function batchImportSamples(formData: FormData) {
  // 不要手动设置 multipart/form-data 的 Content-Type，
  // 否则浏览器不会自动补全 boundary 分隔符，后端将无法解析 file 参数。
  return request.post<BatchOpResult>('/report/technician/batch-import', formData)
}

// ========== 收藏夹接口 ==========
export interface FavoriteItem {
  id: number
  name: string
  /** 标本ID列表（快捷跳转） */
  sampleIds?: (number | string)[]
  /** 收藏类型：sample=标本,keyword=搜索条件,panel=检验项目 */
  type: 'sample' | 'keyword' | 'panel'
  payload?: any
  createTime?: string
}
export function getFavorites() { return request.get<FavoriteItem[]>('/report/technician/favorites') }
export function addFavorite(data: Partial<FavoriteItem>) { return request.post<FavoriteItem>('/report/technician/favorites', data) }
export function removeFavorite(id: number) { return request.delete<void>(`/report/technician/favorites/${id}`) }

// ========== 撤销核收 / 修改标本号 ==========
/** 撤销核收 (支持批量) */
export function cancelReceive(sampleIds: (number | string)[]) {
  return request.post<BatchOpResult>('/report/technician/cancel-receive', sampleIds)
}
/** 修改标本号 */
export function updateSampleNo(sampleId: number | string, sampleNo: string) {
  return request.put<BatchOpResult>(`/report/technician/${sampleId}/sample-no`, null, { params: { sampleNo } })
}

/** 修改标本信息 (标本号/核收时间/核收人/检验时间/采集时间, 仅核收未审核可改) */
export function updateTechnicianSampleInfo(sampleId: number | string, data: {
  sampleNo?: string
  receiveTime?: string
  testTime?: string
  collectTime?: string
  receiver?: string
  receiverId?: number
}) {
  return request.put<BatchOpResult>(`/report/technician/${sampleId}/info`, data)
}
