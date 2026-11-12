import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// 仪器(通讯状态)
export interface InstrumentStatus {
  id: number
  instrumentCode: string
  instrumentName: string
  brand?: string
  model?: string
  protocolType: string // ASTM/HL7/LIS01
  connectType: string // SERIAL/TCP
  channelType?: string // TCP / TCP_SERIAL(双通道热备)
  serialPort?: string
  baudRate?: number
  ipAddress?: string
  port?: number
  serialNo?: string
  installDate?: string
  warrantyExpiry?: string
  department?: string
  status: number // 0停用 1启用
  workStatus?: number // 工作状态: 1正常 2维护中 3故障中
  online: boolean // 在线状态
  lastOnlineTime?: string
  lastCommTime?: string
  createTime?: string
}

// 仪器配置(字典)
export interface Instrument {
  id: number
  instrumentCode: string
  instrumentName: string
  brand?: string
  model?: string
  protocolType: string
  connectType: string
  channelType?: string
  serialPort?: string
  baudRate?: number
  ipAddress?: string
  port?: number
  serialNo?: string
  installDate?: string
  warrantyExpiry?: string
  department?: string
  workStatus?: number
  status: number
  lastOnlineTime?: string
  createTime?: string
  // 文件采集 (connectType=FILE)
  filePath?: string
  fileFormat?: string
  filePattern?: string
  processedDir?: string
  pollInterval?: number
  // 数据库采集 (connectType=DB)
  dbType?: string
  dbHost?: string
  dbPort?: number
  dbName?: string
  dbUser?: string
  dbPassword?: string
  dbDriver?: string
  dbTable?: string
  dbColumns?: string
  dbWhere?: string
  dbWatermarkCol?: string
  lastPolledTime?: string
}

// 仪器项目映射
export interface InstrumentItemMap {
  id: number
  instrumentId: number
  testItemId: number
  testItemName?: string
  instrumentItemCode: string
  instrumentItemName: string
}

// 通讯日志 (与后端 lis_instrument_comm_log 及 CommLogService.queryPage 返回一致)
export interface CommLog {
  id: number
  instrumentId: number
  instrumentName?: string
  direction: string // RECEIVE 接收 / SEND 发送
  rawMessage?: string
  protocolType?: string // ASTM / HL7
  parseStatus?: string // SUCCESS / FAIL
  parseResult?: string
  messageTime?: string
  createTime?: string
}

// 查询参数
export interface InstrumentQuery extends PageQuery {
  keyword?: string
  instrumentCode?: string
  instrumentName?: string
  protocolType?: string
  status?: number
}

export interface CommLogQuery extends PageQuery {
  instrumentId?: number
  direction?: number
  beginTime?: string
  endTime?: string
}

// 仪器状态列表 (后端 instrument 服务 /api/instrument/status)
export function getInstrumentStatusList() {
  return request.get<InstrumentStatus[]>('/instrument/status')
}

// 连接仪器 (后端 POST /api/instrument/{id}/connect, 参数拼接 query；后端缺省时从 DB 读取仪器配置)
export function connectInstrument(id: number, params?: {
  instrumentCode?: string
  connectType?: string
  host?: string
  port?: number
  portName?: string
  baudRate?: number
}) {
  return request.post<boolean>(`/instrument/${id}/connect`, undefined, { params })
}

// 断开仪器
export function disconnectInstrument(id: number) {
  return request.post<boolean>(`/instrument/${id}/disconnect`)
}

// 查询单台仪器连接状态 (TCP 在线 / IP/端口等)
export function getInstrumentSingleStatus(id: number) {
  return request.get<{
    id: number
    instrumentId: number
    instrumentCode?: string
    instrumentName?: string
    brand?: string
    connectType?: string // TCP / SERIAL / FILE / DB
    ipAddress?: string
    port?: number
    serialPort?: string
    baudRate?: number
    online: boolean
    endpoint?: string
    lastCommTime?: string
    connectionSource?: 'REAL' | 'SIM' | 'NONE'
    connectTime?: string
  }>(`/instrument/${id}/status`)
}

// 手工验证专用：模拟连接到 LIS 系统（不拨号实体仪器，仅在系统内存标记为在线）
export function simulateInstrumentConnect(id: number) {
  return request.post<{
    online: boolean
    connectType?: string
    endpoint?: string
    connectTime?: string
  }>(`/instrument/${id}/simulate-connect`)
}

// 模拟断开
export function simulateInstrumentDisconnect(id: number) {
  return request.post<boolean>(`/instrument/${id}/simulate-disconnect`)
}

// 通过已有连接向仪器发送报文 (模拟连接在线时走「模拟入站解析」链路；真实连接时走socket)
export function sendInstrumentRawData(id: number, rawData: string) {
  return request.post<boolean>(`/instrument/${id}/send`, rawData, {
    headers: { 'Content-Type': 'text/plain' }
  })
}

// 仪器列表(分页) - 使用 base 服务的 /api/base/instruments
export function getInstrumentList(params: InstrumentQuery) {
  const { pageNum = 1, pageSize = 10, keyword, instrumentCode, instrumentName, protocolType, status } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (keyword) query.keyword = keyword
  else if (instrumentCode) query.keyword = instrumentCode
  else if (instrumentName) query.keyword = instrumentName
  if (protocolType) query.protocolType = protocolType
  if (status !== undefined) query.status = status
  return request.get<PageResult<Instrument>>('/base/instruments', query)
}

// 全部仪器(下拉用) - 使用 base 服务分页接口取大页
export function getAllInstruments() {
  return request.get<PageResult<Instrument>>('/base/instruments', { current: 1, size: 1000 }).then((res) => {
    return res.records || []
  })
}

/** 当前登录员工可操作的仪器（权限范围）；若未配置人员则退化为全部仪器（保证可用） */
export async function getMyInstruments() {
  try {
    const mine = await request.get<Instrument[]>('/base/instruments/mine')
    if (Array.isArray(mine) && mine.length > 0) {
      return mine
    }
  } catch (_) {
    // 接口不可用(如下游未部署或表尚未创建)则回退到全部
  }
  return request.get<PageResult<Instrument>>('/base/instruments', { current: 1, size: 1000 }).then((res) => {
    return res.records || []
  })
}

// 新增/编辑仪器 - 使用 base 服务
export function saveInstrument(data: Instrument) {
  if (data.id) {
    return request.put<boolean>('/base/instruments', data)
  }
  return request.post<boolean>('/base/instruments', data)
}

// 删除仪器
export function deleteInstrument(id: number) {
  return request.delete<boolean>(`/base/instruments/${id}`)
}

// 仪器项目映射列表 - 使用 base 服务 /api/base/instruments/{id}/items
export function getInstrumentItemMaps(instrumentId: number) {
  return request.get<InstrumentItemMap[]>(`/base/instruments/${instrumentId}/items`)
}

// 保存仪器项目映射 (后端是批量保存)
export function saveInstrumentItemMap(instrumentId: number, data: InstrumentItemMap) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, [data])
}

// 批量保存仪器项目映射
export function saveInstrumentItemMaps(instrumentId: number, items: InstrumentItemMap[]) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, items)
}

// 删除仪器项目映射 (后端无单独删除, 需批量覆盖)
export function deleteInstrumentItemMap(instrumentId: number, id: number) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, [])
}

// 通讯日志列表 (后端 instrument 服务 /api/instrument/{id}/logs, 参数 page/size; 不传 id 查全部)
export function getCommLogList(params: CommLogQuery) {
  const { pageNum = 1, pageSize = 20, instrumentId } = params
  const query: Record<string, any> = {
    page: pageNum,
    size: pageSize
  }
  if (instrumentId) {
    return request.get<PageResult<CommLog>>(`/instrument/${instrumentId}/logs`, query)
  }
  return request.get<PageResult<CommLog>>('/instrument/logs', query)
}

// ==================== 无主标本（instrument 服务） ====================

export interface InstrumentUnmatchedResult {
  id: number
  instrumentId?: number
  instrumentName?: string
  sampleNo?: string
  testCode?: string
  testName?: string
  resultValue?: string
  unit?: string
  refRange?: string
  abnormalFlag?: string
  isCritical?: number
  status?: number // 0待处理 1已匹配 2已作废
  matchedSampleId?: number
  remark?: string
  testTime?: string
  createTime?: string
}

export interface UnmatchedQuery extends PageQuery {
  status?: number
  instrumentId?: number
}

export function getUnmatchedPage(params: UnmatchedQuery) {
  const { pageNum = 1, pageSize = 10, status, instrumentId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (status !== undefined) query.status = status
  if (instrumentId !== undefined) query.instrumentId = instrumentId
  return request.get<PageResult<InstrumentUnmatchedResult>>('/instrument/unmatched/page', query)
}

export function getUnmatchedPendingCount() {
  return request.get<number>('/instrument/unmatched/pending-count')
}

export function updateUnmatchedResult(id: number, data: Partial<InstrumentUnmatchedResult>) {
  return request.put<InstrumentUnmatchedResult>(`/instrument/unmatched/${id}`, data)
}

export function matchUnmatchedResult(id: number, sampleBarcode: string) {
  return request.post<number>(`/instrument/unmatched/${id}/match`, { sampleBarcode })
}

export function discardUnmatchedResult(id: number) {
  return request.post<boolean>(`/instrument/unmatched/${id}/discard`)
}

/** 批量认领：将一组无主标本结果（同一张无主卡片内）匹配到同一业务标本条码 */
export function batchMatchUnmatchedResults(ids: number[], sampleBarcode: string) {
  return request.post<number>('/instrument/unmatched/batch-match', { ids, sampleBarcode })
}

/** 批量作废：作废一组无主标本结果（同一张无主卡片内） */
export function batchDiscardUnmatchedResults(ids: number[]) {
  return request.post<boolean>('/instrument/unmatched/batch-discard', { ids })
}

/** 无主标本批量转质控 —— 结果值作为质控测量值写入 lis_qc_record，并执行 Westgard 判定 */
export function batchToQcUnmatchedResults(params: {
  ids: number[]
  qcLotId: number
  operatorId?: number
}) {
  return request.post<{
    successCount?: number
    failCount?: number
    generatedQcCount?: number
    failDetails?: Array<{ id?: number; reason?: string }>
  }>('/instrument/unmatched/batch-to-qc', params)
}

// ==================== 质控专用 ====================

/** 查询某仪器下可用的质控品批号候选列表（供质控模式下拉） */
export function getInstrumentQcLots(instrumentId: number) {
  return request.get<Array<{
    id: number
    lotNo?: string
    levels?: string
    expireDate?: string
    testItemId?: number
    itemName?: string
    itemCode?: string
  }>>(`/instrument/${instrumentId}/qc-lots`)
}

/** 质控专用报文发送（手工验证区质控模式） */
export function sendInstrumentQcData(params: {
  instrumentId: number
  rawData?: string
  qcLotId?: number
  forceParseMap?: Array<{
    sampleNo?: string
    testCode: string
    value: string
    unit?: string
    refRange?: string
    abnormalFlag?: string
  }>
}) {
  const { instrumentId, ...body } = params
  return request.post<{
    instrumentId: number
    qcMode: boolean
    parseStatus: string
    parseMsg?: string
    qtyInput?: number
    qcOk?: number
    qcFail?: number
    qcLotId?: number
    serverTime?: string
  }>(`/instrument/${instrumentId}/send-qc`, body)
}

/** 微生物鉴定/药敏专用回传（手工验证区微生物药敏模式，模拟通信程序回传） */
export function sendInstrumentMicroData(params: {
  instrumentId: number
  plateBarcode?: string
  sampleBarcode?: string
  organisms: Array<{
    organismCode?: string
    organismName: string
    quantity?: string
    colonsMethod?: string
    coloniesMethod?: string
    remark?: string
    sensitivities?: Array<{
      antibioticCode?: string
      antibioticName: string
      micValue?: string
      result?: 'S' | 'I' | 'R' | string
      breakpoint?: string
      method?: string
      zoneSize?: number
      remark?: string
    }>
  }>
}) {
  const { instrumentId, ...body } = params
  return request.post<{
    instrumentId: number
    microMode: boolean
    parseStatus: string
    parseMsg?: string
    organismCount?: number
    sensitivityCount?: number
    report?: any
    serverTime?: string
  }>(`/instrument/${instrumentId}/send-micro`, body)
}

// ==================== 数据手动接收 / 通讯验证 (instrument 服务) ====================

// 可用仪器品牌 (后台 InstrumentHandler 提供)
export function getInstrumentBrands() {
  return request.get<string[]>('/instrument/data/brands')
}

// 手动发送仪器报文 (POST text/plain, 用于前台手工验证通讯链路)
export function sendInstrumentTestData(params: {
  instrumentId: number
  brand: string
  rawData: string
}) {
  return request.post<boolean>(
    '/instrument/data/receive',
    params.rawData,
    {
      params: { instrumentId: params.instrumentId, brand: params.brand },
      headers: { 'Content-Type': 'text/plain' }
    }
  )
}

export const PROTOCOL_TYPE_MAP: Record<string, string> = {
  ASTM: 'ASTM',
  HL7: 'HL7',
  LIS01: 'LIS01'
}

export const CONNECT_TYPE_MAP: Record<string, string> = {
  SERIAL: '串口',
  TCP: 'TCP',
  FILE: '本地文件',
  DB: '数据库'
}

export const FILE_FORMAT_MAP: Record<string, string> = {
  CSV: 'CSV/TXT(通用)',
  MINDRAY: '迈瑞文件',
  ROCHE: '罗氏文件',
  ABX: 'ABX文件'
}

export const DB_TYPE_MAP: Record<string, string> = {
  POSTGRESQL: 'PostgreSQL',
  MYSQL: 'MySQL',
  SQLSERVER: 'SQL Server',
  ORACLE: 'Oracle'
}

// 通道类型
export const CHANNEL_TYPE_MAP: Record<string, string> = {
  TCP: 'TCP单通道',
  TCP_SERIAL: 'TCP+串口双通道热备'
}

// 工作状态: 1正常 2维护中 3故障中
export const WORK_STATUS_MAP: Record<number, string> = {
  1: '正常',
  2: '维护中',
  3: '故障中'
}

// 工作状态对应的 el-tag type
export const WORK_STATUS_TAG_TYPE: Record<number, string> = {
  1: 'success',
  2: 'warning',
  3: 'danger'
}

// ==================== 仪器维护记录 (instrument-mgmt 服务) ====================

// 维护记录
export interface MaintenanceLog {
  id?: number
  instrumentId: number
  templateId?: number
  maintenanceDate?: string
  maintenanceType?: string
  operatorId?: number
  operatorName?: string
  reviewerId?: number
  reviewerName?: string
  result?: number // 0异常 1正常 2未执行
  remark?: string
  createTime?: string
}

export interface MaintenanceLogQuery extends PageQuery {
  instrumentId?: number
  maintenanceType?: string
  startDate?: string
  endDate?: string
}

export function getMaintenanceLogPage(params: MaintenanceLogQuery) {
  const { pageNum = 1, pageSize = 10, instrumentId, maintenanceType, startDate, endDate } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (instrumentId) query.instrumentId = instrumentId
  if (maintenanceType) query.maintenanceType = maintenanceType
  if (startDate) query.startDate = startDate
  if (endDate) query.endDate = endDate
  return request.get<PageResult<MaintenanceLog>>('/instrument-mgmt/logs', query)
}

export function saveMaintenanceLog(data: MaintenanceLog) {
  if (data.id && data.id !== 0) {
    return request.put<MaintenanceLog>('/instrument-mgmt/logs', data)
  }
  return request.post<MaintenanceLog>('/instrument-mgmt/logs', data)
}

export function deleteMaintenanceLog(id: number) {
  return request.delete<void>(`/instrument-mgmt/logs/${id}`)
}

// 维护模板
export interface MaintenanceTemplate {
  id?: number
  templateCode: string
  templateName: string
  instrumentType?: string
  cycleType?: string
  status?: number
  createTime?: string
}

export function getMaintenanceTemplatePage(params: { pageNum?: number; pageSize?: number; keyword?: string; cycleType?: string }) {
  const { pageNum = 1, pageSize = 100, keyword, cycleType } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (cycleType) query.cycleType = cycleType
  return request.get<PageResult<MaintenanceTemplate>>('/instrument-mgmt/templates', query)
}

// ==================== 保养计划与任务 (instrument-mgmt 服务) ====================

export interface MaintenancePlan {
  id?: number
  instrumentId: number
  planType?: string // PM/REPAIR/CALIBRATION
  cycleType?: string // DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
  firstDate?: string
  nextDate?: string
  status?: number
  remark?: string
  createTime?: string
}

export interface MaintenanceTask {
  id?: number
  planId?: number
  instrumentId?: number
  planType?: string
  scheduledDate?: string
  finishDate?: string
  operatorId?: number
  status?: number // 0待执行 1已完成 2逾期
  cost?: number
  content?: string
}

export function getMaintenancePlansByInstrument(instrumentId: number) {
  return request.get<MaintenancePlan[]>(`/instrument-mgmt/plans/instrument/${instrumentId}`)
}

export function getDueMaintenancePlans() {
  return request.get<MaintenancePlan[]>('/instrument-mgmt/plans/due')
}

export function saveMaintenancePlan(data: MaintenancePlan) {
  if (data.id && data.id !== 0) {
    return request.put<MaintenancePlan>('/instrument-mgmt/plans', data)
  }
  return request.post<MaintenancePlan>('/instrument-mgmt/plans', data)
}

export function deleteMaintenancePlan(id: number) {
  return request.delete<void>(`/instrument-mgmt/plans/${id}`)
}

export function getMaintenanceTasks(instrumentId?: number, status?: number) {
  const query: Record<string, any> = {}
  if (instrumentId) query.instrumentId = instrumentId
  if (status !== undefined) query.status = status
  return request.get<MaintenanceTask[]>('/instrument-mgmt/plans/tasks', query)
}

export function finishMaintenanceTask(taskId: number, params: { operatorId?: number; content?: string; cost?: number }) {
  const query: Record<string, any> = {}
  if (params.operatorId !== undefined) query.operatorId = params.operatorId
  if (params.content !== undefined) query.content = params.content
  if (params.cost !== undefined) query.cost = params.cost
  return request.post<MaintenanceTask>(`/instrument-mgmt/plans/tasks/${taskId}/finish`, undefined, { params: query })
}

// 保养计划/任务类型字典
export const PLAN_TYPE_MAP: Record<string, string> = {
  PM: '日常保养',
  REPAIR: '维修',
  CALIBRATION: '校准'
}

export const CYCLE_TYPE_MAP: Record<string, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  QUARTERLY: '每季',
  YEARLY: '每年'
}

// 维护记录结果: 0异常 1正常 2未执行
export const MAINT_RESULT_MAP: Record<number, string> = {
  0: '异常',
  1: '正常',
  2: '未执行'
}

export const MAINT_RESULT_TAG_TYPE: Record<number, string> = {
  0: 'danger',
  1: 'success',
  2: 'info'
}
