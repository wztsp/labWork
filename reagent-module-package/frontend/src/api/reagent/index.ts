import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// 试剂字典
export interface Reagent {
  id: number
  reagentCode: string // 试剂编码
  reagentName: string // 试剂名称
  specification?: string // 规格
  manufacturer?: string // 厂家
  supplier?: string // 供应商
  unit?: string // 单位
  price?: number // 单价
  storageCondition?: string // 存储条件
  expiryDays?: number // 有效期天数
  status: number // 0停用 1启用
}

// 试剂库存
export interface ReagentStock {
  id: number
  reagentId: number
  reagentName?: string
  batchNo?: string // 批号 (别名，等价于 lotNo)
  lotNo?: string // 批号 (后端字段，与 batchNo 同义)
  barcode?: string // 入库验收生成的批次条码
  quantity?: number // 库存数量
  unit?: string
  expiryDate?: string // 到期日期
  location?: string // 存放位置
  warningValue?: number // 预警阈值
  status?: number // 0不足 1正常
}

// 出入库记录
export interface ReagentStockRecord {
  id: number
  reagentId: number
  reagentName?: string
  batchNo?: string
  recordType?: string // 入库/出库
  quantity?: number
  operatorName?: string // 操作人
  recordTime?: string // 操作时间
  remark?: string
}

// 试剂查询参数
export interface ReagentQuery extends PageQuery {
  reagentName?: string
  reagentCode?: string
  status?: number
  categoryId?: number // 分类筛选（dict 增强）
  hazardFlag?: number // 危险品标志筛选（dict 增强）
  // T-19 / R-14 库存维度组合筛选
  instrumentId?: number
  testItemId?: number
  minQuantity?: number // 库存量下限
  maxQuantity?: number // 库存量上限
  expiryStart?: string // 效期起
  expiryEnd?: string // 效期止
  warehouseId?: number // 仓库/冰箱节点
  location?: string // 存放位置
}

// 库存查询参数
export interface ReagentStockQuery extends PageQuery {
  reagentName?: string
  batchNo?: string
  barcode?: string
  status?: number
}

// 出入库记录查询参数
export interface ReagentRecordQuery extends PageQuery {
  reagentName?: string
  recordType?: number
  beginTime?: string
  endTime?: string
}

// ==================== 试剂字典 ====================

// 分页查询试剂 (后端 /api/reagent/dict, 参数 current/size/reagentName)
export function getReagentPage(params: ReagentQuery) {
  const {
    pageNum = 1,
    pageSize = 10,
    reagentName,
    reagentCode,
    status,
    categoryId,
    hazardFlag,
    instrumentId,
    testItemId,
    minQuantity,
    maxQuantity,
    expiryStart,
    expiryEnd,
    warehouseId,
    location
  } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (reagentName) query.reagentName = reagentName
  if (reagentCode) query.reagentCode = reagentCode
  if (status !== undefined) query.status = status
  if (categoryId !== undefined) query.categoryId = categoryId
  if (hazardFlag !== undefined) query.hazardFlag = hazardFlag
  if (instrumentId !== undefined) query.instrumentId = instrumentId
  if (testItemId !== undefined) query.testItemId = testItemId
  if (minQuantity !== undefined) query.minQuantity = minQuantity
  if (maxQuantity !== undefined) query.maxQuantity = maxQuantity
  if (expiryStart) query.expiryStart = expiryStart
  if (expiryEnd) query.expiryEnd = expiryEnd
  if (warehouseId !== undefined) query.warehouseId = warehouseId
  if (location) query.location = location
  return request.get<PageResult<Reagent>>('/reagent/dict', query)
}

/** T-19 / R-14 试剂基础信息 Excel 导出（UTF-8 逗号分隔 CSV，可直接导入） */
export async function exportReagentDict(): Promise<void> {
  await downloadFile('/api/reagent/dict/export', {}, 'reagent-dict.csv')
}

/** T-19 / R-14 试剂基础信息批量导入（返回 成功数/失败数/行级错误明细） */
export function importReagentDict(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  // 注意：不要手动设置 multipart/form-data 的 Content-Type，
  // 否则浏览器不会自动补全 boundary 分隔符，后端将无法解析 file 参数。
  return request.post<{ success: number; failed: number; errors: Array<{ row: number; reason: string }> }>(
    '/reagent/dict/import',
    formData
  )
}

/**
 * 通用文件下载（fetch + 携带 token，绕过 axios 统一 JSON 拦截器）。
 * 后端返回 Blob 流，按 Content-Disposition 或兜底文件名落盘。
 */
async function downloadFile(url: string, params: Record<string, any>, fallbackName: string): Promise<void> {
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  const qs = new URLSearchParams()
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
  })
  const sep = url.includes('?') ? '&' : '?'
  const fullUrl = `${url}${qs.toString() ? sep + qs.toString() : ''}`
  const resp = await fetch(fullUrl, {
    headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}
  })
  if (!resp.ok) {
    throw new Error(`下载失败: ${resp.status}`)
  }
  const blob = await resp.blob()
  let fileName = fallbackName
  const cd = resp.headers.get('Content-Disposition')
  const m = cd && cd.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  if (m && m[1]) fileName = decodeURIComponent(m[1])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 延迟回收 objectURL，避免 click 后立即 revoke 导致下载异步失败（竞态）
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

// 新增/编辑试剂
export function saveReagent(data: Reagent) {
  if (data.id && data.id !== 0) {
    return request.put<boolean>('/reagent/dict', data)
  }
  return request.post<boolean>('/reagent/dict', data)
}

// 删除试剂
export function deleteReagent(id: number) {
  return request.delete<boolean>(`/reagent/dict/${id}`)
}

// ==================== 试剂库存 ====================

// 分页查询库存 (后端 /api/reagent/stock, 参数 current/size/reagentName/batchNo/barcode/status)
export function getStockPage(params: ReagentStockQuery) {
  const { pageNum = 1, pageSize = 10, reagentName, batchNo, barcode, status } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (reagentName) query.reagentName = reagentName
  if (batchNo) query.batchNo = batchNo
  if (barcode) query.barcode = barcode
  if (status !== undefined) query.status = status
  return request.get<PageResult<ReagentStock>>('/reagent/stock', query)
}

// 【缺陷 O 已修复】saveStock / deleteStock 已删除。
// 原因：后端 POST/PUT/DELETE /api/reagent/stock 三个裸端点可绕过流水直接改
// lis_reagent_stock.quantity，摧毁 R-05/R-07 的核心验收断言
// 「SUM(流水.quantity) + 初始值 = 当前 quantity」。后端端点已随 T-06 删除，
// 前端函数同步下线（保留会得到 404/405）。
// 库存变动请改走：入库 postStockIn、出库 postStockOutFifo / postStockOut、
// 盘点调账 submitStockCheckItems —— 三者均在同一事务内写出流水。

// ==================== 出入库记录 ====================

// 分页查询出入库记录 (后端 /api/reagent/records, 参数 current/size)
export function getRecordPage(params: ReagentRecordQuery) {
  const { pageNum = 1, pageSize = 10, reagentName, recordType, beginTime, endTime } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (reagentName) query.reagentName = reagentName
  if (recordType) query.recordType = recordType
  if (beginTime) query.beginTime = beginTime
  if (endTime) query.endTime = endTime
  return request.get<PageResult<ReagentStockRecord>>('/reagent/records', query)
}

// 【缺陷 O 已修复】saveRecord / deleteRecord 已删除。
// 原因：lis_reagent_stock_record 是库存变动的审计流水，只能由业务动作在同一事务内
// 追加写入。原后端 POST/PUT/DELETE /api/reagent/records 可手工增改删流水而不动库存，
// 使流水与库存脱钩。后端端点已随 T-06 删除，前端函数同步下线。
// 流水一律只读，仅保留 getRecordPage 查询。

// ==================== 入库验收 ====================

export interface ReagentInboundItem {
  id?: number
  inboundId?: number
  reagentId: number
  reagentName?: string
  lotNo: string
  barcode?: string
  quantity: number
  unit?: string
  purchasePrice?: number
  amount?: number
  expiryDate: string
  location?: string
  remark?: string
}

export interface ReagentInbound {
  id?: number
  inboundNo?: string
  supplier?: string
  totalAmount?: number
  verifyStatus?: number // 0待验收 1已验收 2拒收
  verifierId?: number
  verifierName?: string
  verifyTime?: string
  remark?: string
  createTime?: string
  items?: ReagentInboundItem[]
}

export interface ReagentInboundQuery extends PageQuery {
  inboundNo?: string
  verifyStatus?: number
}

export function getInboundPage(params: ReagentInboundQuery) {
  const { pageNum = 1, pageSize = 10, inboundNo, verifyStatus } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (inboundNo) query.inboundNo = inboundNo
  if (verifyStatus !== undefined) query.verifyStatus = verifyStatus
  return request.get<PageResult<ReagentInbound>>('/reagent/inbound', query)
}

export function getInboundDetail(id: number) {
  return request.get<ReagentInbound>(`/reagent/inbound/${id}`)
}

export function createInbound(data: ReagentInbound) {
  return request.post<ReagentInbound>('/reagent/inbound', data)
}

/** 下载入库验收明细导入模板（CSV） */
export async function downloadInboundTemplate(): Promise<void> {
  await downloadFile('/api/reagent/inbound/template', {}, 'reagent-inbound-template.csv')
}

/** 解析入库验收明细导入文件，返回可填充到明细表格的行及行级错误 */
export function importInboundItems(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{
    items: ReagentInboundItem[]
    success: number
    failed: number
    errors: Array<{ row: number; reason: string }>
  }>('/reagent/inbound/import', formData)
}

export function verifyInbound(id: number, verifierId: number, verifierName: string) {
  return request.post<boolean>(`/reagent/inbound/${id}/verify`, null, { params: { verifierId, verifierName } })
}

export function rejectInbound(id: number, verifierId: number, verifierName: string, reason: string) {
  return request.post<boolean>(`/reagent/inbound/${id}/reject`, null, { params: { verifierId, verifierName, reason } })
}

// ==================== 消耗登记 ====================

export interface ReagentConsumption {
  id?: number
  consumptionNo?: string
  reagentId: number
  reagentName?: string
  stockId?: number
  lotNo?: string
  quantity: number
  unit?: string
  consumeType?: number // 1手工 2自动 3质控 4校准 5维护
  relatedNo?: string
  operatorId?: number
  operatorName?: string
  remark?: string
  createTime?: string
}

export interface ReagentConsumptionQuery extends PageQuery {
  reagentId?: number
  consumeType?: number
  relatedNo?: string
}

export function getConsumptionPage(params: ReagentConsumptionQuery) {
  const { pageNum = 1, pageSize = 10, reagentId, consumeType, relatedNo } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (reagentId) query.reagentId = reagentId
  if (consumeType !== undefined) query.consumeType = consumeType
  if (relatedNo) query.relatedNo = relatedNo
  return request.get<PageResult<ReagentConsumption>>('/reagent/consumption', query)
}

export function createConsumption(data: ReagentConsumption) {
  return request.post<ReagentConsumption>('/reagent/consumption', data)
}

export function autoConsume(testItemId: number, testCount: number, operatorId: number, operatorName: string, relatedNo: string) {
  return request.post<boolean>('/reagent/consumption/auto', { testItemId, testCount, operatorId, operatorName, relatedNo })
}

// ==================== 开瓶记录 ====================

export interface ReagentOpening {
  id?: number
  reagentId: number
  reagentName?: string
  stockId?: number
  lotNo?: string
  openTime?: string
  openedExpiry?: string
  status?: number // 1正常 0已过期
  operatorId?: number
  operatorName?: string
  remark?: string
  createTime?: string
}

export interface ReagentOpeningQuery extends PageQuery {
  reagentId?: number
  status?: number
}

export function getOpeningPage(params: ReagentOpeningQuery) {
  const { pageNum = 1, pageSize = 10, reagentId, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (reagentId) query.reagentId = reagentId
  if (status !== undefined) query.status = status
  return request.get<PageResult<ReagentOpening>>('/reagent/opening', query)
}

export function createOpening(data: ReagentOpening) {
  return request.post<ReagentOpening>('/reagent/opening', data)
}

// ==================== 库存看板 ====================

export interface DashboardOverview {
  totalReagents: number
  totalStockValue: number
  nearExpiryCount: number
  lowStockCount: number
  openedExpiryCount: number
}

export function getDashboardOverview() {
  return request.get<DashboardOverview>('/reagent/dashboard/overview')
}

export function getNearExpiryList(days: number = 30) {
  return request.get<any[]>('/reagent/dashboard/near-expiry', { days })
}

export function getLowStockList() {
  return request.get<any[]>('/reagent/dashboard/low-stock')
}

export function getOpenedExpiryList() {
  return request.get<any[]>('/reagent/dashboard/opened-expiry')
}

// ==================== 试剂批次追溯 ====================

// 根据批号查询使用该批次的所有检测结果
export function traceByLotNo(lotNo: string) {
  return request.get<ReagentTraceResult[]>(`/reagent/trace/${encodeURIComponent(lotNo)}`)
}

export interface ReagentTraceResult {
  id: number
  sampleId?: number
  testItemId?: number
  resultValue?: string
  unit?: string
  refRange?: string
  abnormalFlag?: string
  isCritical?: number
  instrumentId?: number
  testTime?: string
  reagentLotId?: number
  reagentLotNo?: string
}

// ==================== 试剂库存盘点 ====================

export interface ReagentStockCheck {
  id?: number
  checkNo?: string
  warehouse?: string
  checkDate?: string
  status?: number // 0进行中 1已完成 2已作废
  operatorId?: number
  operatorName?: string
  remark?: string
  createTime?: string
}

export interface ReagentStockCheckItem {
  id?: number
  checkId?: number
  stockId?: number
  reagentId?: number
  reagentName?: string
  reagentCode?: string
  lotNo?: string
  bookQty?: number
  actualQty?: number
  diffQty?: number
  location?: string
  remark?: string
  createTime?: string
}

export interface StockCheckQuery extends PageQuery {
  warehouse?: string
  status?: number
}

export function getStockCheckPage(params: StockCheckQuery) {
  const { pageNum = 1, pageSize = 10, warehouse, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (warehouse) query.warehouse = warehouse
  if (status !== undefined) query.status = status
  return request.get<PageResult<ReagentStockCheck>>('/reagent/stock-check', query)
}

export function getStockCheckDetail(id: number) {
  return request.get<ReagentStockCheck>(`/reagent/stock-check/${id}`)
}

export function getStockCheckItems(id: number) {
  return request.get<ReagentStockCheckItem[]>(`/reagent/stock-check/${id}/items`)
}

export function createStockCheck(data: { warehouse?: string; operatorId?: number; operatorName?: string; remark?: string }) {
  return request.post<ReagentStockCheck>('/reagent/stock-check', data)
}

export function submitStockCheck(
  id: number,
  items: Array<{ id: number; actualQty: number; remark?: string }>,
  ctx?: { operatorId?: number; operatorName?: string; deptId?: number; deptName?: string }
) {
  return request.post<void>(`/reagent/stock-check/${id}/submit`, items, { params: ctx })
}

// ==================== 试剂成本核算 ====================

export interface ReagentCostSummary {
  reagentId?: number
  reagentName?: string
  reagentCode?: string
  totalQty?: number
  totalCost?: number
  instrumentId?: number
  instrumentName?: string
}

export interface ReagentCostDetail {
  id: number
  consumptionNo?: string
  reagentId?: number
  reagentName?: string
  lotNo?: string
  quantity?: number
  unit?: string
  consumeType?: number
  relatedNo?: string
  operatorId?: number
  operatorName?: string
  cost?: number
  createTime?: string
}

export function getCostSummary(params: { start?: string; end?: string; dimension?: string }) {
  const query: Record<string, any> = {}
  if (params.start) query.start = params.start
  if (params.end) query.end = params.end
  if (params.dimension) query.dimension = params.dimension
  return request.get<ReagentCostSummary[]>('/reagent/cost/summary', query)
}

export function getCostDetail(params: { start?: string; end?: string }) {
  const query: Record<string, any> = {}
  if (params.start) query.start = params.start
  if (params.end) query.end = params.end
  return request.get<ReagentCostDetail[]>('/reagent/cost/detail', query)
}

// ==================== 采购申请（R-02 采购闭环） ====================

/** 采购申请状态：0草稿 1待审批 2审批中 3已批准 4已驳回 5已冻结 6已作废 */
export interface ReagentPurchaseApply {
  id?: number
  applyNo?: string
  applyDeptId?: number
  applyDeptName?: string
  applyUserId?: number
  applyUserName?: string
  budgetId?: number
  budgetYear?: number
  categoryId?: number
  totalAmount?: number
  status?: number
  currentNodeLevel?: number
  totalNodeCount?: number
  overBudgetFlag?: number // 0正常 1超预算
  excessAmount?: number
  urgentFlag?: number // 0普通 1紧急
  expectDate?: string
  submitTime?: string
  finishTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
  items?: ReagentPurchaseApplyItem[]
}

export interface ReagentPurchaseApplyItem {
  id?: number
  applyId?: number
  reagentId: number
  reagentCode?: string
  reagentName?: string
  specification?: string
  categoryId?: number
  quantity: number
  unit?: string
  estimatedPrice?: number // 预估单价
  amount?: number
  remark?: string
  createTime?: string
}

export interface ApplyItemDTO {
  id?: number
  reagentId: number
  quantity: number
  unit?: string
  estimatedPrice?: number
  remark?: string
}

export interface ApplyCreateDTO {
  id?: number
  applyDeptId?: number
  applyDeptName?: string
  applyUserId?: number
  applyUserName?: string
  budgetYear?: number
  urgentFlag?: number
  expectDate?: string
  remark?: string
  items: ApplyItemDTO[]
}

export interface ApplyQuery extends PageQuery {
  status?: number
  applyNo?: string
  deptId?: number
  categoryId?: number
}

/** 审批流水（只增不改不删） */
export interface ReagentPurchaseApproval {
  id?: number
  applyId?: number
  applyNo?: string
  nodeLevel?: number
  nodeName?: string
  action?: number // 1通过 2驳回
  approverId?: number
  approverName?: string
  opinion?: string
  approveTime?: string
  roundNo?: number
  createTime?: string
}

/** 采购申请详情（主单 + 明细 + 审批流水 + 预算快照 + 可执行动作） */
export interface ApplyDetailVO {
  apply?: ReagentPurchaseApply
  items?: ReagentPurchaseApplyItem[]
  approvals?: ReagentPurchaseApproval[]
  allowedActions?: string[]
  currentNodeName?: string
  budgetTotalAmount?: number
  budgetFrozenAmount?: number
  budgetUsedAmount?: number
  budgetAvailable?: number
  editable?: boolean
}

/** 提交/解冻结果（区分 PASS / WARN / EXCEED 三种结局） */
export interface SubmitResultVO {
  applyId?: number
  applyNo?: string
  status?: number
  statusLabel?: string
  verdict?: string // PASS / WARN / EXCEED
  overBudget?: boolean
  excessAmount?: number
  budgetId?: number
  totalAmount?: number
  totalNodeCount?: number
  currentNodeLevel?: number
  currentNodeName?: string
  message?: string
}

export interface ApprovalDTO {
  applyId?: number
  action: number // 1通过 2驳回
  approverId?: number
  approverName?: string
  opinion?: string
}

export function getPurchaseApplyPage(params: ApplyQuery) {
  const { pageNum = 1, pageSize = 10, status, applyNo, deptId, categoryId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (status !== undefined) query.status = status
  if (applyNo) query.applyNo = applyNo
  if (deptId !== undefined) query.deptId = deptId
  if (categoryId !== undefined) query.categoryId = categoryId
  return request.get<PageResult<ReagentPurchaseApply>>('/reagent/purchase-apply/page', query)
}

export function getPurchaseApplyDetail(id: number) {
  return request.get<ApplyDetailVO>(`/reagent/purchase-apply/${id}`)
}

/** 草稿保存（新建或编辑草稿 / 已驳回单） */
export function savePurchaseApply(data: ApplyCreateDTO) {
  return request.post<ReagentPurchaseApply>('/reagent/purchase-apply', data)
}

/** 下载采购申请明细导入模板（CSV） */
export async function downloadPurchaseApplyTemplate(): Promise<void> {
  await downloadFile('/api/reagent/purchase-apply/template', {}, 'purchase-apply-template.csv')
}

/** 解析采购申请明细导入文件，返回可填充到明细表格的行及行级错误 */
export function importPurchaseApplyItems(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{ items: ApplyItemDTO[]; success: number; failed: number; errors: Array<{ row: number; reason: string }> }>(
    '/reagent/purchase-apply/import',
    formData
  )
}

/** 提交（冻结预算 + 进入审批） */
export function submitPurchaseApply(id: number, operatorId?: number, operatorName?: string) {
  return request.post<SubmitResultVO>(`/reagent/purchase-apply/${id}/submit`, undefined, {
    params: { operatorId, operatorName }
  })
}

/** 审批通过 */
export function approvePurchaseApply(id: number, data: ApprovalDTO) {
  return request.post<void>(`/reagent/purchase-apply/${id}/approve`, data)
}

/** 审批驳回（自动释放预算占用） */
export function rejectPurchaseApply(id: number, data: ApprovalDTO) {
  return request.post<void>(`/reagent/purchase-apply/${id}/reject`, data)
}

/** 取消申请（释放预算占用） */
export function cancelPurchaseApply(id: number, operatorId?: number, operatorName?: string) {
  return request.post<void>(`/reagent/purchase-apply/${id}/cancel`, undefined, {
    params: { operatorId, operatorName }
  })
}

/** 解冻重判（预算调增后） */
export function unfreezePurchaseApply(id: number, operatorId?: number, operatorName?: string) {
  return request.post<SubmitResultVO>(`/reagent/purchase-apply/${id}/unfreeze`, undefined, {
    params: { operatorId, operatorName }
  })
}

// ==================== 采购订单 ====================

/** 订单状态：0待下单 1已下单 2部分到货 3已到货 4已取消 */
export interface ReagentPurchaseOrder {
  id?: number
  orderNo?: string
  applyId?: number
  applyNo?: string
  supplierId?: number
  supplierName?: string
  deptId?: number
  deptName?: string
  totalAmount?: number
  expectDate?: string
  orderDate?: string
  status?: number
  operatorId?: number
  operatorName?: string
  remark?: string
  createTime?: string
  updateTime?: string
  items?: ReagentPurchaseOrderItem[]
}

export interface ReagentPurchaseOrderItem {
  id?: number
  orderId?: number
  applyItemId?: number
  reagentId?: number
  reagentCode?: string
  reagentName?: string
  specification?: string
  quantity?: number
  arrivedQty?: number
  unit?: string
  unitPrice?: number
  amount?: number
  remark?: string
  createTime?: string
}

export interface OrderItemDTO {
  id?: number
  applyItemId?: number
  reagentId: number
  quantity: number
  unit?: string
  unitPrice: number
  remark?: string
}

export interface OrderCreateDTO {
  id?: number
  applyId: number
  supplierId: number
  supplierName?: string
  deptId?: number
  deptName?: string
  expectDate?: string
  orderDate?: string
  operatorId?: number
  operatorName?: string
  remark?: string
  items: OrderItemDTO[]
}

export interface OrderQuery extends PageQuery {
  orderNo?: string
  status?: number
  applyId?: number
  supplierId?: number
}

/** 某供应商价格行（比价结果子项） */
export interface SupplierPriceVO {
  supplierId?: number
  supplierName?: string
  latestPrice?: number
  avgPrice?: number
  minPrice?: number
  maxPrice?: number
  priceCount?: number
  lastBuyDate?: string
  score?: number
}

/** 某试剂跨供应商比价结果 */
export interface PriceCompareVO {
  reagentId?: number
  reagentCode?: string
  reagentName?: string
  specification?: string
  suppliers?: SupplierPriceVO[]
  lowestPrice?: number
  lowestSupplierId?: number
  lowestSupplierName?: string
  bestScoreSupplierId?: number
  bestScoreSupplierName?: string
}

export function getPurchaseOrderPage(params: OrderQuery) {
  const { pageNum = 1, pageSize = 10, orderNo, status, applyId, supplierId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (orderNo) query.orderNo = orderNo
  if (status !== undefined) query.status = status
  if (applyId !== undefined) query.applyId = applyId
  if (supplierId !== undefined) query.supplierId = supplierId
  return request.get<PageResult<ReagentPurchaseOrder>>('/reagent/purchase-order/page', query)
}

export function getPurchaseOrderDetail(id: number) {
  return request.get<ReagentPurchaseOrder>(`/reagent/purchase-order/${id}`)
}

/** 由已批准申请创建订单 */
export function createPurchaseOrder(data: OrderCreateDTO) {
  return request.post<ReagentPurchaseOrder>('/reagent/purchase-order', data)
}

/** 下载采购订单明细导入模板（CSV） */
export async function downloadPurchaseOrderTemplate(): Promise<void> {
  await downloadFile('/api/reagent/purchase-order/template', {}, 'purchase-order-template.csv')
}

/** 解析采购订单明细导入文件，返回可填充到明细表格的行及行级错误 */
export function importPurchaseOrderItems(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{ items: OrderItemDTO[]; success: number; failed: number; errors: Array<{ row: number; reason: string }> }>(
    '/reagent/purchase-order/import',
    formData
  )
}

/** 下单（待下单 → 已下单） */
export function placePurchaseOrder(id: number, operatorId?: number, operatorName?: string) {
  return request.post<void>(`/reagent/purchase-order/${id}/place`, undefined, {
    params: { operatorId, operatorName }
  })
}

/** 取消订单（释放预算占用） */
export function cancelPurchaseOrder(id: number, operatorId?: number, operatorName?: string) {
  return request.post<void>(`/reagent/purchase-order/${id}/cancel`, undefined, {
    params: { operatorId, operatorName }
  })
}

/** 同试剂跨供应商历史比价 */
export function comparePurchasePrice(reagentId: number) {
  return request.get<PriceCompareVO>('/reagent/purchase-order/compare', { reagentId })
}

// ==================== 请领业务单（统一出入库） ====================

/** 单据类型：1领用 2退库 3报废 4调拨 5借用 6归还 */
export interface ReagentRequisition {
  id?: number
  reqNo?: string
  reqType?: number
  deptId?: number
  deptName?: string
  applyUserId?: number
  applyUserName?: string
  /** 状态：0草稿 1待审 2已审 3已执行 4已驳回 5已作废 */
  status?: number
  approverId?: number
  approverName?: string
  approveTime?: string
  approveOpinion?: string
  executorId?: number
  executorName?: string
  executeTime?: string
  fromWarehouseId?: number
  toWarehouseId?: number
  hazardFlag?: number // 0否 1是
  expectReturnDate?: string
  sourceReqId?: number
  reason?: string
  totalAmount?: number
  remark?: string
  createTime?: string
  updateTime?: string
  items?: ReagentRequisitionItem[]
}

export interface ReagentRequisitionItem {
  id?: number
  reqId?: number
  reagentId: number
  reagentCode?: string
  reagentName?: string
  stockId?: number
  lotNo?: string
  expiryDate?: string
  quantity: number
  unit?: string
  unitPrice?: number
  amount?: number
  hazardFlag?: number
  remark?: string
  createTime?: string
}

export interface RequisitionItemDTO {
  id?: number
  reagentId: number
  stockId?: number
  lotNo?: string
  expiryDate?: string
  quantity: number
  unit?: string
  remark?: string
}

export interface RequisitionCreateDTO {
  id?: number
  reqType: number
  deptId?: number
  deptName?: string
  applyUserId?: number
  applyUserName?: string
  fromWarehouseId?: number
  toWarehouseId?: number
  expectReturnDate?: string
  sourceReqId?: number
  reason?: string
  remark?: string
  items: RequisitionItemDTO[]
}

export interface RequisitionQuery extends PageQuery {
  reqType?: number
  status?: number
  reqNo?: string
  deptId?: number
}

export function getRequisitionPage(params: RequisitionQuery) {
  const { pageNum = 1, pageSize = 10, reqType, status, reqNo, deptId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (reqType !== undefined) query.reqType = reqType
  if (status !== undefined) query.status = status
  if (reqNo) query.reqNo = reqNo
  if (deptId !== undefined) query.deptId = deptId
  return request.get<PageResult<ReagentRequisition>>('/reagent/requisition/page', query)
}

export function getRequisitionDetail(id: number) {
  return request.get<ReagentRequisition>(`/reagent/requisition/${id}`)
}

/** 创建 / 编辑草稿 */
export function createRequisition(data: RequisitionCreateDTO) {
  return request.post<ReagentRequisition>('/reagent/requisition', data)
}

/** 下载请领业务单明细导入模板（CSV） */
export async function downloadRequisitionTemplate(): Promise<void> {
  await downloadFile('/api/reagent/requisition/template', {}, 'requisition-template.csv')
}

/** 解析请领业务单明细导入文件，返回可填充到明细表格的行及行级错误 */
export function importRequisitionItems(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{ items: RequisitionItemDTO[]; success: number; failed: number; errors: Array<{ row: number; reason: string }> }>(
    '/reagent/requisition/import',
    formData
  )
}

/** 提交（普通免审 / 危险品进审批） */
export function submitRequisition(id: number, operatorId?: number, operatorName?: string) {
  return request.post<void>(`/reagent/requisition/${id}/submit`, undefined, {
    params: { operatorId, operatorName }
  })
}

/** 审批通过 */
export function approveRequisition(id: number, data: ApprovalDTO) {
  return request.post<void>(`/reagent/requisition/${id}/approve`, data)
}

/** 审批驳回 */
export function rejectRequisition(id: number, data: ApprovalDTO) {
  return request.post<void>(`/reagent/requisition/${id}/reject`, data)
}

/** 执行（扣减 / 增加库存，写流水） */
export function executeRequisition(
  id: number,
  ctx: { operatorId?: number; operatorName?: string; deptId?: number; deptName?: string }
) {
  return request.post<void>(`/reagent/requisition/${id}/execute`, undefined, { params: ctx })
}

/** 作废 */
export function cancelRequisition(id: number, operatorName?: string) {
  return request.post<void>(`/reagent/requisition/${id}/cancel`, undefined, {
    params: { operatorName }
  })
}

// ==================== 供应商主数据 ====================

/** 资质状态：0待完善 1有效 2已过期 */
export interface Supplier {
  id?: number
  supplierCode?: string
  supplierName: string
  shortName?: string
  creditCode?: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  licenseNo?: string
  licenseExpiry?: string
  medicalCertNo?: string
  medicalCertExpiry?: string
  qualStatus?: number
  score?: number
  sourceType?: number // 1手工录入 2存量迁移
  status?: number // 0停用 1启用
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SupplierQuery extends PageQuery {
  keyword?: string
  status?: number
  qualStatus?: number
  sourceType?: number
}

export function getSupplierPage(params: SupplierQuery) {
  const { pageNum = 1, pageSize = 10, keyword, status, qualStatus, sourceType } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (status !== undefined) query.status = status
  if (qualStatus !== undefined) query.qualStatus = qualStatus
  if (sourceType !== undefined) query.sourceType = sourceType
  return request.get<PageResult<Supplier>>('/reagent/supplier/page', query)
}

export function getSupplierDetail(id: number) {
  return request.get<Supplier>(`/reagent/supplier/${id}`)
}

export function createSupplier(data: Supplier) {
  return request.post<Supplier>('/reagent/supplier', data)
}

export function updateSupplier(data: Supplier) {
  return request.put<Supplier>('/reagent/supplier', data)
}

export function deleteSupplier(id: number) {
  return request.delete<void>(`/reagent/supplier/${id}`)
}

/** 采购下拉：证照有效且启用的供应商 */
export function getSelectableSuppliers() {
  return request.get<Supplier[]>('/reagent/supplier/selectable')
}

/** 证照临期筛选 */
export function getExpiringSuppliers(days: number = 30) {
  return request.get<Supplier[]>('/reagent/supplier/expiring', { days })
}

/** 同试剂跨供应商历史比价 */
export function compareSupplierPrice(reagentId: number) {
  return request.get<PriceCompareVO>('/reagent/supplier/compare', { reagentId })
}

// ==================== 试剂分类 ====================

/** hazardCategory：0普通 1危险品类 */
export interface ReagentCategory {
  id?: number
  categoryCode?: string
  categoryName: string
  parentId?: number // 0=根
  path?: string
  levelNo?: number
  hazardCategory?: number // 0普通 1危险品类
  sortOrder?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
  children?: ReagentCategory[]
}

export interface CategoryQuery extends PageQuery {
  keyword?: string
  status?: number
  hazardCategory?: number
}

export function getCategoryTree() {
  return request.get<ReagentCategory[]>('/reagent/category/tree')
}

export function getCategoryPage(params: CategoryQuery) {
  const { pageNum = 1, pageSize = 10, keyword, status, hazardCategory } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (status !== undefined) query.status = status
  if (hazardCategory !== undefined) query.hazardCategory = hazardCategory
  return request.get<PageResult<ReagentCategory>>('/reagent/category/page', query)
}

export function getCategoryDetail(id: number) {
  return request.get<ReagentCategory>(`/reagent/category/${id}`)
}

export function createCategory(data: ReagentCategory) {
  return request.post<ReagentCategory>('/reagent/category', data)
}

export function updateCategory(data: ReagentCategory) {
  return request.put<ReagentCategory>('/reagent/category', data)
}

export function deleteCategory(id: number) {
  return request.delete<void>(`/reagent/category/${id}`)
}

// ==================== 仓库管理 ====================

/** 仓库/冰箱/层架三级树；id=0 为系统内置默认库房哨兵，不可删 */
export interface ReagentWarehouse {
  id?: number
  whCode?: string
  whName: string
  parentId?: number // 0=根
  path?: string
  whType?: number // 1库房 2冰箱 3层架
  deptId?: number
  deptName?: string
  tempMin?: number
  tempMax?: number
  locationDesc?: string
  sortOrder?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
  children?: ReagentWarehouse[]
}

export interface WarehouseQuery extends PageQuery {
  keyword?: string
  status?: number
  whType?: number
}

export function getWarehouseTree() {
  return request.get<ReagentWarehouse[]>('/reagent/warehouse/tree')
}

export function getWarehousePage(params: WarehouseQuery) {
  const { pageNum = 1, pageSize = 10, keyword, status, whType } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (status !== undefined) query.status = status
  if (whType !== undefined) query.whType = whType
  return request.get<PageResult<ReagentWarehouse>>('/reagent/warehouse/page', query)
}

export function getWarehouseDetail(id: number) {
  return request.get<ReagentWarehouse>(`/reagent/warehouse/${id}`)
}

export function createWarehouse(data: ReagentWarehouse) {
  return request.post<ReagentWarehouse>('/reagent/warehouse', data)
}

export function updateWarehouse(data: ReagentWarehouse) {
  return request.put<ReagentWarehouse>('/reagent/warehouse', data)
}

export function deleteWarehouse(id: number) {
  return request.delete<void>(`/reagent/warehouse/${id}`)
}

// ==================== 预算管理 ====================

export interface ReagentBudget {
  id?: number
  budgetNo?: string
  budgetYear?: number
  deptId?: number
  deptName?: string
  categoryId?: number
  categoryName?: string
  totalAmount?: number
  frozenAmount?: number // 已占用
  usedAmount?: number // 已实扣
  warnRatio?: number // 预警阈值，默认 0.8
  version?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface BudgetQuery extends PageQuery {
  budgetYear?: number
  deptId?: number
  categoryId?: number
  status?: number
  budgetNo?: string
  onlyWarning?: boolean
}

/** 预算对账断言结果 */
export interface BudgetReconcileVO {
  budgetId?: number
  budgetNo?: string
  masterFrozen?: number
  masterUsed?: number
  recordFrozen?: number
  recordUsed?: number
  frozenDiff?: number
  usedDiff?: number
  consistent?: boolean
  recordCount?: number
  message?: string
}

/** 预算判定三态（PASS/WARN/EXCEED） */
export interface BudgetVerdictVO {
  code?: string
  label?: string
  passable?: boolean
}

export function getBudgetPage(params: BudgetQuery) {
  const { pageNum = 1, pageSize = 10, budgetYear, deptId, categoryId, status, budgetNo, onlyWarning } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (budgetYear !== undefined) query.budgetYear = budgetYear
  if (deptId !== undefined) query.deptId = deptId
  if (categoryId !== undefined) query.categoryId = categoryId
  if (status !== undefined) query.status = status
  if (budgetNo) query.budgetNo = budgetNo
  if (onlyWarning !== undefined) query.onlyWarning = onlyWarning
  return request.get<PageResult<ReagentBudget>>('/reagent/budget/page', query)
}

export function getBudgetDetail(id: number) {
  return request.get<ReagentBudget>(`/reagent/budget/${id}`)
}

/** 对账断言：流水推算值与主表余额是否一致 */
export function reconcileBudget(id: number) {
  return request.get<BudgetReconcileVO>(`/reagent/budget/${id}/reconcile`)
}

/** 按「年度+科室+类别」匹配预算 */
export function matchBudget(params: { year: number; deptId: number; categoryId?: number }) {
  const query: Record<string, any> = { year: params.year, deptId: params.deptId }
  if (params.categoryId !== undefined) query.categoryId = params.categoryId
  return request.get<ReagentBudget>('/reagent/budget/match', query)
}

/** 创建预算（财务维护） */
export function createBudget(data: ReagentBudget) {
  return request.post<ReagentBudget>('/reagent/budget', data)
}

/** 预算总额调增 / 调减（changeType：4调增 5调减） */
export function adjustBudget(
  id: number,
  changeType: number,
  amount: number,
  operatorId?: number,
  operatorName?: string
) {
  return request.post<void>(`/reagent/budget/${id}/adjust`, undefined, {
    params: { changeType, amount, operatorId, operatorName }
  })
}

/** 判定：拟占用金额是否会超预算（只读提示） */
export function judgeBudget(id: number, amount: number) {
  return request.get<BudgetVerdictVO>(`/reagent/budget/${id}/judge`, { amount })
}

// ==================== 盘点补充（作废） ====================

/** 作废盘点单 */
export function voidStockCheck(id: number) {
  return request.post<void>(`/reagent/stock-check/${id}/void`)
}

// ==================== 低库存预警 ====================

/** 低库存预警行（聚合 SQL） */
export interface LowStockAlertDTO {
  reagentId?: number
  reagentCode?: string
  reagentName?: string
  specification?: string
  unit?: string
  categoryId?: number
  categoryName?: string
  warehouseId?: number
  warehouseName?: string
  currentQty?: number
  minStock?: number
  gapQty?: number
  hazardFlag?: number
  suggestQty?: number
}

/** 低库存预警（聚合 SQL，与 dashboard 的 getLowStockList 区分） */
export function getStockLowStockList() {
  return request.get<LowStockAlertDTO[]>('/reagent/stock/low-stock')
}

// ==================== 供应商评价（T-16 / R-11） ====================

/** 供应商综合评分行 */
export interface SupplierScoreVO {
  supplierId?: number
  supplierName?: string
  orderCount?: number
  onTimeRate?: number
  rejectRate?: number
  autoScore?: number
  manualScore?: number
  compositeScore?: number
  lastOrderDate?: string
}

/** 供应商评分排名（includeManual=true 计入人工分） */
export function getSupplierEvaluateRank(includeManual = true) {
  return request.get<SupplierScoreVO[]>('/reagent/supplier-evaluate/rank', { includeManual })
}

/** 单个供应商评分明细 */
export function getSupplierEvaluate(id: number) {
  return request.get<SupplierScoreVO>(`/reagent/supplier-evaluate/${id}`)
}

/** 人工补充主观评分（0~100） */
export function saveSupplierManualScore(supplierId: number, score: number) {
  return request.post<void>(`/reagent/supplier-evaluate/${supplierId}/manual`, undefined, { params: { score } })
}

// ==================== 报表增强（T-18 / R-13） ====================

/** 采购统计（groupBy: supplier / dept / category） */
export function getReagentPurchaseStat(params: { start?: string; end?: string; groupBy?: string }) {
  return request.get<Array<Record<string, any>>>('/reagent/report/purchase-stat', params)
}

/** 领用趋势时间序列（granularity: day / week / month，空档补 0） */
export function getReagentTrend(params: { start?: string; end?: string; granularity?: string }) {
  return request.get<Array<{ period?: string; totalQty?: number }>>('/reagent/report/trend', params)
}

/** 库存结构分析 */
export function getReagentStockStructure() {
  return request.get<Array<Record<string, any>>>('/reagent/report/stock-structure')
}

/** 采购统计 Excel 导出 */
export async function exportReagentPurchaseStat(params: { start?: string; end?: string; groupBy?: string }) {
  await downloadFile('/api/reagent/report/export/purchase-stat', params, 'purchase-stat.xls')
}

// ==================== 环保处置（T-15 / R-10） ====================

/** 危险品报废环保处置记录 */
export interface ReagentScrapDisposal {
  id?: number
  reqId?: number
  reqNo?: string
  hazardFlag?: number
  hazardClass?: string
  disposalMethod?: string
  disposalOrg?: string
  disposalCert?: string
  disposalDate?: string
  safetyApproverName?: string
  remark?: string
  createTime?: string
}

/** 按报废单 ID 查询处置记录 */
export function getScrapDisposalByReq(reqId: number) {
  return request.get<ReagentScrapDisposal>(`/reagent/scrap-disposal/by-req/${reqId}`)
}

/** 保存 / 更新环保处置信息 */
export function saveScrapDisposal(data: ReagentScrapDisposal) {
  return request.post<ReagentScrapDisposal>('/reagent/scrap-disposal', data)
}

/** 删除环保处置记录 */
export function deleteScrapDisposal(id: number) {
  return request.delete<boolean>(`/reagent/scrap-disposal/${id}`)
}

/** 环保处置台账分页 */
export function getScrapDisposalPage(params: { pageNum?: number; pageSize?: number }) {
  const query: Record<string, any> = { current: params.pageNum || 1, size: params.pageSize || 10 }
  return request.get<PageResult<ReagentScrapDisposal>>('/reagent/scrap-disposal/page', query)
}

// ==================== 扫码（R-12 移动端） ====================

/** 扫码查询：按条码匹配库存批次 */
export function scanQueryBarcode(barcode: string) {
  return request.get<ReagentStock | null>('/reagent/scan/query', { barcode })
}

/** 扫码入库 */
export function scanIn(data: {
  barcode: string
  reagentId: number
  lotNo?: string
  quantity: number
  expiryDate?: string
  location?: string
  operatorId?: number
  operatorName?: string
}) {
  return request.post<ReagentStock>('/reagent/scan/in', data)
}

/** 扫码出库（优先按条码匹配批次，否则按试剂 FIFO） */
export function scanOut(data: {
  barcode: string
  reagentId?: number
  quantity: number
  operatorId?: number
  operatorName?: string
  sourceNo?: string
  remark?: string
}) {
  return request.post<{ mode: string; records: ReagentStockRecord[] }>('/reagent/scan/out', data)
}
