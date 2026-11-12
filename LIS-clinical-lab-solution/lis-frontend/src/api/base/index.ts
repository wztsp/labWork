import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// 检验项目
export interface TestItem {
  id: number | string
  itemCode: string
  itemName: string
  itemNameEn?: string
  shortName?: string
  category?: string
  specimenType?: string
  unit?: string
  method?: string
  isPanel?: number
  parentId?: number | string
  price?: number
  tubeId?: number | string // 绑定的检验试管ID
  collectionMethodId?: number | string // 采集方式ID (关联 lis_collection_method)
  reportTemplateId?: number | string // 报告模板ID (关联 lis_report_template)
  status: number // 0停用 1启用
  createTime?: string
}

// 参考范围
export interface ReferenceRange {
  id: number
  testItemId: number
  testItemName?: string
  gender?: number // null全部 1男 2女
  ageMin?: number
  ageMax?: number
  refLow?: string
  refHigh?: string
  refText?: string
  criticalLow?: string
  criticalHigh?: string
  unit?: string
  specimenType?: string // 标本类型 (空表示全部适用)
  instrumentId?: number // 仪器ID (空表示全部适用)
  conditionRemark?: string // 条件备注: 生理周期/体位/采血时间/饮食状态/孕周等
}

// 仪器字典(同 instrument 简化)
export interface InstrumentDict {
  id: number
  instrumentCode: string
  instrumentName: string
  brand?: string
  model?: string
  protocolType?: string
  connectType?: string
  serialPort?: string
  baudRate?: number
  ipAddress?: string
  port?: number
  status: number
  lastOnlineTime?: string
  createTime?: string
}

// 仪器项目映射
export interface InstrumentItemMap {
  id?: number
  instrumentId?: number
  instrumentName?: string
  testItemId: number
  testItemName?: string
  instrumentItemCode: string
  instrumentItemName: string
  channelCode?: string // 仪器通道码(用于仪器对接)
  specimenType?: string // 适用标本类型 (空表示全部)
  operatorId?: number // 操作人员ID
  operatorName?: string // 操作人员姓名
}

export interface TestItemQuery extends PageQuery {
  keyword?: string
  itemCode?: string
  itemName?: string
  category?: string
  status?: number
  isPanel?: number // 0普通指标 1检验项目
  specimenType?: string
}

export interface RefRangeQuery extends PageQuery {
  testItemId?: number
  testItemName?: string
  specimenType?: string
  instrumentId?: number
}

// 标本类型
export interface SpecimenType {
  id: number
  typeCode: string
  typeName: string
  description?: string
  status: number
  sortOrder?: number
  createTime?: string
}

export interface SpecimenTypeQuery extends PageQuery {
  keyword?: string
  status?: number
}

export interface InstrumentDictQuery extends PageQuery {
  keyword?: string
  instrumentCode?: string
  instrumentName?: string
  protocolType?: string
  status?: number
}

// 检验指标分页列表 (后端 /api/base/items, 参数 current/size/keyword/category/isPanel/status/specimenType)
export function getTestItemList(params: TestItemQuery) {
  const { pageNum = 1, pageSize = 10, keyword, itemCode, itemName, category, status, isPanel, specimenType } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  // 后端只有一个 keyword 字段同时匹配 itemCode/itemName
  if (keyword) query.keyword = keyword
  else if (itemCode) query.keyword = itemCode
  else if (itemName) query.keyword = itemName
  if (category) query.category = category
  if (status !== undefined) query.status = status
  if (isPanel !== undefined) query.isPanel = isPanel
  if (specimenType) query.specimenType = specimenType
  return request.get<PageResult<TestItem>>('/base/items', query)
}

// 全部启用的检验项目(下拉/字典用, 后端 /api/base/items/dict)
export function getAllTestItems() {
  return request.get<TestItem[]>('/base/items/dict')
}

export function saveTestItem(data: TestItem) {
  if (data.id) {
    return request.put<boolean>('/base/items', data)
  }
  return request.post<boolean>('/base/items', data)
}

export function deleteTestItem(id: number) {
  return request.delete<boolean>(`/base/items/${id}`)
}

// 参考范围分页列表 (后端 /api/base/ref-ranges, 参数 current/size/testItemId/specimenType/instrumentId)
export function getRefRangeList(params: RefRangeQuery) {
  const { pageNum = 1, pageSize = 10, testItemId, specimenType, instrumentId } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (testItemId) query.testItemId = testItemId
  if (specimenType) query.specimenType = specimenType
  if (instrumentId) query.instrumentId = instrumentId
  return request.get<PageResult<ReferenceRange>>('/base/ref-ranges', query)
}

export function saveRefRange(data: ReferenceRange) {
  if (data.id) {
    return request.put<boolean>('/base/ref-ranges', data)
  }
  return request.post<boolean>('/base/ref-ranges', data)
}

export function deleteRefRange(id: number) {
  return request.delete<boolean>(`/base/ref-ranges/${id}`)
}

// 仪器字典分页列表 (后端 /api/base/instruments, 参数 current/size/keyword)
export function getInstrumentDictList(params: InstrumentDictQuery) {
  const { pageNum = 1, pageSize = 10, keyword, instrumentCode, instrumentName, protocolType, status } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  // 后端只有一个 keyword 字段
  if (keyword) query.keyword = keyword
  else if (instrumentCode) query.keyword = instrumentCode
  else if (instrumentName) query.keyword = instrumentName
  if (protocolType) query.protocolType = protocolType
  if (status !== undefined) query.status = status
  return request.get<PageResult<InstrumentDict>>('/base/instruments', query)
}

export function saveInstrumentDict(data: InstrumentDict) {
  if (data.id) {
    return request.put<boolean>('/base/instruments', data)
  }
  return request.post<boolean>('/base/instruments', data)
}

export function deleteInstrumentDict(id: number) {
  return request.delete<boolean>(`/base/instruments/${id}`)
}

/** 获取全部启用的仪器字典(下拉用) */
export function getAllInstrumentDicts() {
  return request.get<InstrumentDict[]>('/base/instruments/all')
}

/** 获取当前登录员工可操作的启用仪器（权限范围） */
export function getMyInstrumentDicts() {
  return request.get<InstrumentDict[]>('/base/instruments/mine')
}

// 仪器-操作人员关联 (后端 /api/base/instruments/{id}/staff)
export interface InstrumentStaffMap {
  id?: number
  instrumentId?: number
  staffId: number
  staffName?: string
}

export function getInstrumentStaffMaps(instrumentId: number) {
  return request.get<InstrumentStaffMap[]>(`/base/instruments/${instrumentId}/staff`)
}

export function setInstrumentStaffMaps(instrumentId: number, maps: InstrumentStaffMap[]) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/staff`, maps)
}

// 仪器-标本类型关联 (后端 /api/base/instruments/{id}/specimens)
export interface InstrumentSpecimenMap {
  id?: number
  instrumentId?: number
  specimenTypeId?: number
  specimenTypeName?: string
  specimenTypeCode?: string
}

export function getInstrumentSpecimenMaps(instrumentId: number) {
  return request.get<InstrumentSpecimenMap[]>(`/base/instruments/${instrumentId}/specimens`)
}

export function setInstrumentSpecimenMaps(instrumentId: number, maps: InstrumentSpecimenMap[]) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/specimens`, maps)
}

// 仪器项目映射 (后端 /api/base/instruments/{id}/items)
export function getInstrumentItemMaps(instrumentId: number) {
  return request.get<InstrumentItemMap[]>(`/base/instruments/${instrumentId}/items`)
}

/** 批量设置仪器项目映射 (覆盖式) */
export function setInstrumentItemMaps(instrumentId: number, maps: InstrumentItemMap[]) {
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, maps)
}

export function saveInstrumentItemMap(instrumentId: number, data: InstrumentItemMap) {
  // 后端是批量保存: POST /api/base/instruments/{id}/items, body 是 list
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, [data])
}

export function deleteInstrumentItemMap(instrumentId: number, id: number) {
  // 后端没有单独删除接口, 这里用批量覆盖方式
  // 此函数保留但实际页面应使用 setInstrumentItemMaps 批量保存
  return request.post<boolean>(`/base/instruments/${instrumentId}/items`, [])
}

// 检验项目分类下拉 (后端无此接口, 返回前端预设)
export function getTestItemCategories() {
  // 后端无 /categories 接口, 返回预设分类
  return Promise.resolve(['血常规', '生化', '免疫', '凝血', '微生物', '尿常规', '其他'])
}

export const GENDER_MAP: Record<number, string> = {
  0: '不限',
  1: '男',
  2: '女'
}

// ==================== 标本类型管理 ====================

export function getSpecimenTypeList(params: SpecimenTypeQuery) {
  const { pageNum = 1, pageSize = 10, keyword, status } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (keyword) query.keyword = keyword
  if (status !== undefined) query.status = status
  return request.get<PageResult<SpecimenType>>('/base/specimen-types', query)
}

/** 获取全部启用的标本类型 (下拉选择用) */
export function getAllSpecimenTypes() {
  return request.get<SpecimenType[]>('/base/specimen-types/all')
}

export function saveSpecimenType(data: SpecimenType) {
  if (data.id) {
    return request.put<SpecimenType>('/base/specimen-types', data)
  }
  return request.post<SpecimenType>('/base/specimen-types', data)
}

export function deleteSpecimenType(id: number) {
  return request.delete<boolean>(`/base/specimen-types/${id}`)
}

// ==================== 采集方式管理 ====================

/** 采集方式字典 */
export interface CollectionMethod {
  id: number | string
  methodCode: string
  methodName: string
  description?: string
  status: number // 0停用 1启用
  sortOrder?: number
  createTime?: string
}

export interface CollectionMethodQuery extends PageQuery {
  keyword?: string
  status?: number
}

export function getCollectionMethodList(params: CollectionMethodQuery) {
  const { pageNum = 1, pageSize = 10, keyword, status } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (keyword) query.keyword = keyword
  if (status !== undefined) query.status = status
  return request.get<PageResult<CollectionMethod>>('/base/collection-methods', query)
}

/** 获取全部启用的采集方式 (下拉选择用) */
export function getAllCollectionMethods() {
  return request.get<CollectionMethod[]>('/base/collection-methods/all')
}

export function saveCollectionMethod(data: CollectionMethod) {
  if (data.id) {
    return request.put<CollectionMethod>('/base/collection-methods', data)
  }
  return request.post<CollectionMethod>('/base/collection-methods', data)
}

export function deleteCollectionMethod(id: number) {
  return request.delete<boolean>(`/base/collection-methods/${id}`)
}

// ==================== 检验项目管理 ====================

/** 查询检验项目下的子项目(指标) */
export function getPanelItems(panelId: number) {
  return request.get<TestItem[]>(`/base/items/${panelId}/panel-items`)
}

/** 管理检验项目: 设置子项目 */
export function managePanel(panelId: number, childIds: number[]) {
  return request.post<boolean>(`/base/items/${panelId}/panel`, childIds)
}

// ==================== 检验试管管理 ====================

/** 检验试管 */
export interface TestTube {
  id: number
  tubeCode: string
  tubeName: string
  tubeColor: string // 颜色: 红/紫/蓝/绿/黑/黄/灰
  tubeType: string // 管类型: 采血管/尿杯/粪盒/培养管
  volume?: string // 容量
  additive?: string // 抗凝剂/添加剂
  sortOrder?: number
  status: number
  remark?: string
  createTime?: string
}

export interface TestTubeQuery extends PageQuery {
  keyword?: string
  tubeType?: string
  status?: number
}

export function getTestTubeList(params: TestTubeQuery) {
  const { pageNum = 1, pageSize = 10, keyword, tubeType, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (tubeType) query.tubeType = tubeType
  if (status !== undefined) query.status = status
  return request.get<PageResult<TestTube>>('/base/test-tubes', query)
}

/** 获取全部启用的试管(下拉用) */
export function getAllTestTubes() {
  return request.get<TestTube[]>('/base/test-tubes/all')
}

export function saveTestTube(data: TestTube) {
  if (data.id) return request.put<boolean>('/base/test-tubes', data)
  return request.post<boolean>('/base/test-tubes', data)
}

export function deleteTestTube(id: number) {
  return request.delete<boolean>(`/base/test-tubes/${id}`)
}

// ==================== 条码规则管理 ====================

/** 条码生成规则 */
export interface BarcodeRule {
  id: number | string
  ruleCode: string
  ruleName: string
  prefix: string
  /** 日期格式: none/yyyyMMdd/yyMMdd/yyyyMM/yyyy */
  datePattern: string
  /** 序列号长度 (1-10) */
  seqLength: number
  /** 序列号策略: daily 每日重置 / global 全局递增 */
  seqStrategy: string
  /** 启用: 1 / 停用: 0 */
  enabled: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 日期格式下拉选项 */
export const DATE_PATTERN_OPTIONS = [
  { value: 'none', label: '无日期 (纯数字/字母+数字)' },
  { value: 'yyyyMMdd', label: '完整年月日 (20260731)' },
  { value: 'yyMMdd', label: '2位年+月日 (260731)' },
  { value: 'yyyyMM', label: '年月 (202607)' },
  { value: 'yyyy', label: '年份 (2026)' }
] as const

/** 序列号策略下拉选项 */
export const SEQ_STRATEGY_OPTIONS = [
  { value: 'daily', label: '每日重置 (从1开始)' },
  { value: 'global', label: '全局递增 (不重置)' }
] as const

/** 日期格式 → 示例展示用的日期部分 */
export function buildSampleDatePart(pattern: string): string {
  const now = new Date()
  const y = now.getFullYear()
  const yy = String(y).slice(-2)
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  switch (pattern) {
    case 'yyyyMMdd': return `${y}${mm}${dd}`
    case 'yyMMdd': return `${yy}${mm}${dd}`
    case 'yyyyMM': return `${y}${mm}`
    case 'yyyy': return String(y)
    default: return ''
  }
}

/** 按规则生成条码示例 (用于页面预览, 实际调用后端) */
export function buildBarcodePreview(rule: BarcodeRule, sampleSeq = 1): string {
  const datePart = buildSampleDatePart(rule.datePattern)
  const seqStr = String(sampleSeq).padStart(rule.seqLength || 6, '0')
  return `${rule.prefix || ''}${datePart}${seqStr}`
}

/** 查询所有条码规则 */
export function getBarcodeRuleList() {
  return request.get<BarcodeRule[]>('/sample/barcode/rules')
}

/** 查询单个启用的规则 */
export function getEnabledBarcodeRule(ruleCode: string) {
  return request.get<BarcodeRule>(`/sample/barcode/rules/${ruleCode}`)
}

/** 更新条码规则 (PUT body: BarcodeRule) */
export function updateBarcodeRule(data: BarcodeRule) {
  return request.put<void>('/sample/barcode/rules', data)
}

/** 按规则编码立即生成一条条码 (测试用) */
export function testGenerateBarcode(ruleCode: string) {
  return request.post<string>(`/sample/barcode/generate/${ruleCode}`)
}
