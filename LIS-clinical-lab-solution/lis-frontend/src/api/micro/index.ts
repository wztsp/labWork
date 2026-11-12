import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== 血培养报告 ====================

/** 血培养记录 (匹配后端 lis_blood_culture 表) */
export interface BloodCulture {
  id?: number
  sampleId?: number // 标本ID
  bottleNo?: string // 培养瓶号
  mediumType?: string // 培养基类型
  instrumentId?: number // 仪器ID
  inoculationTime?: string // 接种时间
  alarmTime?: string // 报警时间
  finalResult?: string // 最终结果: POSITIVE/NEGATIVE/CONTAMINATED
  finalTime?: string // 最终报告时间
  ttpHours?: number // TTP(小时)
  operatorId?: number // 操作人ID
  /** 0培养中 1阳性 2阴性 3污染 */
  status?: number
  createTime?: string
}

export interface BloodCultureSubculture {
  id?: number
  bloodCultureId?: number
  subcultureTime?: string
  medium?: string // 培养基
  incubatorNo?: string // 培养箱号
  operatorId?: number
  result?: string
  remark?: string
}

export interface BloodCultureQuery extends PageQuery {
  sampleId?: number
  status?: number
}

// 血培养分页 (后端 /api/micro/blood-culture, 参数 current/size/sampleId/status)
export function getBloodCulturePage(params: BloodCultureQuery) {
  const { pageNum = 1, pageSize = 10, sampleId, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (sampleId) query.sampleId = sampleId
  if (status !== undefined) query.status = status
  return request.get<PageResult<BloodCulture>>('/micro/blood-culture', query)
}

// 培养中的血培养列表
export function getCulturingBloodCultures() {
  return request.get<BloodCulture[]>('/micro/blood-culture/culturing')
}

// 血培养详情
export function getBloodCultureById(id: number) {
  return request.get<BloodCulture>(`/micro/blood-culture/${id}`)
}

// 按标本ID查询
export function getBloodCultureBySampleId(sampleId: number) {
  return request.get<BloodCulture>(`/micro/blood-culture/sample/${sampleId}`)
}

// 接种 (后端 POST /api/micro/blood-culture/inoculate)
export function inoculateBloodCulture(data: Partial<BloodCulture>) {
  return request.post<BloodCulture>('/micro/blood-culture/inoculate', data)
}

// 接收报警 (后端 POST /api/micro/blood-culture/{id}/alarm)
export function receiveBloodCultureAlarm(id: number, alarmTime?: string, operatorId?: number) {
  const query: Record<string, any> = {}
  if (alarmTime) query.alarmTime = alarmTime
  if (operatorId) query.operatorId = operatorId
  return request.post<BloodCulture>(`/micro/blood-culture/${id}/alarm`, undefined, { params: query })
}

// 报告最终结果 (后端 POST /api/micro/blood-culture/{id}/final)
export function reportBloodCultureFinal(id: number, finalResult: string, operatorId?: number) {
  const query: Record<string, any> = { finalResult }
  if (operatorId) query.operatorId = operatorId
  return request.post<BloodCulture>(`/micro/blood-culture/${id}/final`, undefined, { params: query })
}

// 转种记录列表
export function getBloodCultureSubcultures(id: number) {
  return request.get<BloodCultureSubculture[]>(`/micro/blood-culture/${id}/subcultures`)
}

// 添加转种记录
export function addBloodCultureSubculture(id: number, data: Partial<BloodCultureSubculture>) {
  return request.post<BloodCultureSubculture>(`/micro/blood-culture/${id}/subcultures`, data)
}

// ==================== 抗生素字典 ====================

/** 抗生素字典 */
export interface Antibiotic {
  id: number
  antibioticCode: string // 抗生素代码
  antibioticName: string // 中文名称
  antibioticNameEn?: string // 英文名称
  category?: string // 分类: β-内酰胺类/氨基糖苷类/喹诺酮类等
  method?: string // 检测方法: K-B纸片法/微量肉汤法
  breakpointLow?: string // 折点低
  breakpointHigh?: string // 折点高
  status?: number // 状态 0停用 1启用
}

export interface AntibioticQuery extends PageQuery {
  keyword?: string
  category?: string
}

// 抗生素分页 (后端 /api/micro/antibiotics, 参数 current/size)
export function getAntibioticPage(params: AntibioticQuery) {
  const { pageNum = 1, pageSize = 10, keyword, category } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (category) query.category = category
  return request.get<PageResult<Antibiotic>>('/micro/antibiotics', query)
}

// 查全部启用抗生素 (后端 GET /api/micro/antibiotics/all)
export function getAllAntibiotics() {
  return request.get<Antibiotic[]>('/micro/antibiotics/all')
}

// 新增/编辑抗生素 (后端 POST/PUT /api/micro/antibiotics)
export function saveAntibiotic(data: Antibiotic) {
  if (data.id) {
    return request.put<boolean>('/micro/antibiotics', data)
  }
  return request.post<boolean>('/micro/antibiotics', data)
}

// 删除抗生素 (后端 DELETE /api/micro/antibiotics/{id})
export function deleteAntibiotic(id: number) {
  return request.delete<boolean>(`/micro/antibiotics/${id}`)
}

// ==================== 细菌字典 ====================

/** 细菌字典 */
export interface Bacteria {
  id: number
  bacteriaCode: string // 细菌编码
  bacteriaName: string // 中文名称
  bacteriaNameLatin?: string // 拉丁学名
  gramType?: string // 革兰氏属性: G+/G-/真菌/抗酸菌
  bacteriaCategory?: string // 菌属分类
  morphology?: string // 形态: 球菌/杆菌/球杆菌/弯曲杆菌/酵母样/丝状真菌
  isCommon?: number // 是否常见菌: 1是 0否
  status?: number // 状态 0停用 1启用
  createTime?: string
}

export interface BacteriaQuery extends PageQuery {
  keyword?: string
  gramType?: string
  category?: string
}

// 细菌分页 (后端 /api/micro/bacteria, 参数 current/size/keyword/gramType/category)
export function getBacteriaPage(params: BacteriaQuery) {
  const { pageNum = 1, pageSize = 20, keyword, gramType, category } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (gramType) query.gramType = gramType
  if (category) query.category = category
  return request.get<PageResult<Bacteria>>('/micro/bacteria', query)
}

// 查全部启用细菌
export function getAllBacteria() {
  return request.get<Bacteria[]>('/micro/bacteria/all')
}

// 查常见菌
export function getCommonBacteria() {
  return request.get<Bacteria[]>('/micro/bacteria/common')
}

// 新增/编辑细菌 (后端 POST/PUT /api/micro/bacteria)
export function saveBacteria(data: Bacteria) {
  if (data.id) {
    return request.put<boolean>('/micro/bacteria', data)
  }
  return request.post<boolean>('/micro/bacteria', data)
}

// 删除细菌 (后端 DELETE /api/micro/bacteria/{id})
export function deleteBacteria(id: number) {
  return request.delete<boolean>(`/micro/bacteria/${id}`)
}

// ==================== 抗生素折点 ====================

export interface AntibioticBreakpoint {
  id?: number
  antibioticId: number
  bacteriaId: number
  method?: string // K-B纸片法/微量肉汤法/E-test
  standard?: string // CLSI/EUCAST
  standardVersion?: string // 如 M100-2023
  sBreakpoint?: string // 敏感界值
  rBreakpoint?: string // 耐药界值
  iBreakpoint?: string // 中介界值
  sDd?: string // K-B法敏感抑菌圈直径
  rDd?: string // K-B法耐药抑菌圈直径
  status?: number
}

export interface AntibioticBreakpointQuery extends PageQuery {
  antibioticId?: number
  bacteriaId?: number
  standard?: string
  method?: string
}

export function getBreakpointPage(params: AntibioticBreakpointQuery) {
  const { pageNum = 1, pageSize = 20, antibioticId, bacteriaId, standard, method } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (antibioticId) query.antibioticId = antibioticId
  if (bacteriaId) query.bacteriaId = bacteriaId
  if (standard) query.standard = standard
  if (method) query.method = method
  return request.get<PageResult<AntibioticBreakpoint>>('/micro/breakpoints', query)
}

export function getBreakpointsByBacteria(bacteriaId: number) {
  return request.get<AntibioticBreakpoint[]>(`/micro/breakpoints/by-bacteria/${bacteriaId}`)
}

export function getBreakpointsByPair(antibioticId: number, bacteriaId: number) {
  return request.get<AntibioticBreakpoint[]>('/micro/breakpoints/by-pair', { antibioticId, bacteriaId })
}

export function saveBreakpoint(data: AntibioticBreakpoint) {
  if (data.id) {
    return request.put<boolean>('/micro/breakpoints', data)
  }
  return request.post<boolean>('/micro/breakpoints', data)
}

export function deleteBreakpoint(id: number) {
  return request.delete<boolean>(`/micro/breakpoints/${id}`)
}

// ==================== 耐药规则 ====================

/** 耐药规则 */
export interface ResistanceRule {
  id: number
  ruleName: string // 规则名称
  ruleType?: string // 规则类型: 表型推断/天然耐药/获得性耐药/特殊耐药/危急值
  antibioticCodes?: string // 关联抗生素代码(JSON 数组串)
  bacteriaCodes?: string // 可应用细菌编码(JSON 数组串), 空="所有细菌"
  organismCode?: string // 兼容旧数据: 单一细菌编码
  result?: string // 期望结果(EXACT 模式): S/I/R
  matchMode?: string // ANY_RESISTANT=任一耐药 EXACT=精确匹配结果
  isCritical?: number // 是否危急值: 0否 1是
  alertMessage?: string // 预警文案
  description?: string // 描述
  isBuiltin?: number // 系统内置: 1是 0否
  status?: number // 状态 0停用 1启用
  bacteriaNames?: string // 细菌名称(展示用, 后端填充)
  antibioticNames?: string // 抗生素名称(展示用, 后端填充)
  createTime?: string
}

export interface ResistanceRuleQuery extends PageQuery {
  keyword?: string
  ruleType?: string
}

// 耐药规则分页 (后端 /api/micro/resistance-rules, 参数 current/size)
export function getResistanceRulePage(params: ResistanceRuleQuery) {
  const { pageNum = 1, pageSize = 10, keyword, ruleType } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (ruleType) query.ruleType = ruleType
  return request.get<PageResult<ResistanceRule>>('/micro/resistance-rules', query)
}

// 新增/编辑耐药规则 (后端 POST/PUT /api/micro/resistance-rules)
export function saveResistanceRule(data: ResistanceRule) {
  if (data.id) {
    return request.put<boolean>('/micro/resistance-rules', data)
  }
  return request.post<boolean>('/micro/resistance-rules', data)
}

// 删除耐药规则 (后端 DELETE /api/micro/resistance-rules/{id})
export function deleteResistanceRule(id: number) {
  return request.delete<boolean>(`/micro/resistance-rules/${id}`)
}

// ==================== 培养基类型字典 ====================

/** 培养基类型字典 */
export interface MediumType {
  id?: number
  mediumCode?: string // 培养基编码
  mediumName: string // 培养基名称
  mediumAbbr?: string // 缩写 (打印条码用)
  remark?: string
  status?: number // 0停用 1启用
  createTime?: string
}

// 培养基分页 (后端 /api/micro/medium-type, 参数 current/size/keyword)
export function getMediumTypePage(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  const query: Record<string, any> = {
    current: params.pageNum ?? 1,
    size: params.pageSize ?? 20
  }
  if (params.keyword) query.keyword = params.keyword
  return request.get<PageResult<MediumType>>('/micro/medium-type', query)
}

// 全部启用培养基 (后端 GET /api/micro/medium-type/all)
export function getAllMediumTypes() {
  return request.get<MediumType[]>('/micro/medium-type/all')
}

// 新增/编辑培养基 (后端 POST/PUT /api/micro/medium-type)
export function saveMediumType(data: MediumType) {
  if (data.id) {
    return request.put<boolean>('/micro/medium-type', data)
  }
  return request.post<boolean>('/micro/medium-type', data)
}

// 删除培养基 (后端 DELETE /api/micro/medium-type/{id})
export function deleteMediumType(id: number) {
  return request.delete<boolean>(`/micro/medium-type/${id}`)
}

// ==================== 培养方案 ====================

/** 培养方案 */
export interface CulturePlan {
  id?: number
  planName: string // 方案名称
  testItemId?: number // 绑定的检验项目ID
  testItemName?: string // 检验项目名称
  sampleType?: string // 标本类型(如 痰/尿/血等); 为空表示适用于全部标本类型
  remark?: string
  isDefault?: number // 0否 1是
  status?: number // 0停用 1启用
  createTime?: string
}

/** 培养方案明细 (培养基及数量) */
export interface CulturePlanItem {
  id?: number
  planId?: number
  mediumTypeId?: number
  mediumTypeName?: string
  quantity?: number
  sortOrder?: number
}

/** 培养方案详情 (含明细) */
export interface CulturePlanVO extends CulturePlan {
  items?: CulturePlanItem[]
}

/** 保存培养方案请求 */
export interface SavePlanRequest {
  plan: CulturePlan
  items: CulturePlanItem[]
}

// 培养方案分页 (后端 GET /api/micro/culture-plan, 参数 current/size/keyword)
export function getCulturePlanPage(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  const query: Record<string, any> = {
    current: params.pageNum ?? 1,
    size: params.pageSize ?? 20
  }
  if (params.keyword) query.keyword = params.keyword
  return request.get<PageResult<CulturePlan>>('/micro/culture-plan', query)
}

// 方案详情 (后端 GET /api/micro/culture-plan/{id})
export function getCulturePlanDetail(id: number) {
  return request.get<CulturePlanVO>(`/micro/culture-plan/${id}`)
}

// 按检验项目查询方案 (后端 GET /api/micro/culture-plan/by-item/{testItemId})
export function getCulturePlansByItem(testItemId: number) {
  return request.get<CulturePlan[]>(`/micro/culture-plan/by-item/${testItemId}`)
}

// 全部启用培养方案 (后端 GET /api/micro/culture-plan/all)
export function getAllCulturePlans() {
  return request.get<CulturePlan[]>('/micro/culture-plan/all')
}

// 保存培养方案 (后端 POST/PUT /api/micro/culture-plan)
export function saveCulturePlan(data: SavePlanRequest) {
  if (data.plan.id) {
    return request.put<boolean>('/micro/culture-plan', data)
  }
  return request.post<boolean>('/micro/culture-plan', data)
}

// 删除培养方案 (后端 DELETE /api/micro/culture-plan/{id})
export function deleteCulturePlan(id: number) {
  return request.delete<boolean>(`/micro/culture-plan/${id}`)
}

// ==================== 标本培养皿条码 ====================

/** 标本培养皿条码 */
export interface SamplePlate {
  id?: number | string
  sampleId?: number | string
  sampleBarcode?: string // 标本条码
  plateBarcode?: string // 培养皿条码
  planId?: number
  mediumTypeId?: number
  mediumTypeName?: string // 培养基名称
  patientName?: string
  seqNo?: number
  status?: number // 0未核收 1已核收 2已作废(取消)
  createTime?: string
  cancelTime?: string
}

/** 生成培养皿条码请求 */
export interface GeneratePlateRequest {
  sampleBarcode: string
  sampleId?: number | string
  /** 标本类型(医生下达申请的标本类型, 如 痰/尿/血等); 用于匹配标本类型专属方案, 为空仅匹配全部方案 */
  sampleType?: string
  /** 检验项目ID列表(可传 Long/String 雪花 ID, 后端 Jackson 自动转换) */
  testItemIds: (number | string)[]
  /** 前端选中的培养方案ID(优先按此方案生成, 兼容未选时按检验项目+标本类型匹配) */
  planId?: number | string
  patientName?: string
  operatorId?: number
  operatorName?: string
}

// 生成培养皿条码 (后端 POST /api/micro/sample-plate/generate)
export function generateSamplePlates(data: GeneratePlateRequest) {
  return request.post<SamplePlate[]>('/micro/sample-plate/generate', data)
}

// 按标本ID查询已生成培养皿 (后端 GET /api/micro/sample-plate/by-sample/{sampleId})
export function getSamplePlatesBySampleId(sampleId: number | string) {
  return request.get<SamplePlate[]>(`/micro/sample-plate/by-sample/${sampleId}`)
}

// 按标本条码查询已生成培养皿 (后端 GET /api/micro/sample-plate/by-barcode/{barcode})
export function getSamplePlatesByBarcode(barcode: string) {
  return request.get<SamplePlate[]>(`/micro/sample-plate/by-barcode/${barcode}`)
}

/**
 * 取消单条培养基条码生成（作废）。
 * 仅允许取消未核收(status=0)的；已核收需先撤销微生物报告。
 */
export function cancelSamplePlate(id: number | string) {
  return request.delete(`/micro/sample-plate/${id}`)
}

/**
 * 批量取消某标本全部培养基条码生成（作废）。
 * 参数 sampleId / sampleBarcode 二选一
 */
export function cancelAllPlatesBySample(params: { sampleId?: number | string; sampleBarcode?: string }) {
  const qs = new URLSearchParams()
  if (params.sampleId != null) qs.set('sampleId', String(params.sampleId))
  if (params.sampleBarcode) qs.set('sampleBarcode', params.sampleBarcode)
  const query = qs.toString()
  return request.delete(`/micro/sample-plate/cancel-by-sample${query ? '?' + query : ''}`)
}

// ==================== 抗生素药敏模板 ====================

/** 抗生素模板主表 */
export interface AntibioticTemplate {
  id: number
  code: string // 模板编码
  name: string // 模板名称
  templateCategory?: string // 分类: G+/G-/非发酵/苛养/厌氧/真菌
  defaultMethod?: string // 默认检测方法 MIC/KB
  description?: string // 描述
  status?: number // 1启用 0禁用
  isSystem?: number // 1系统内置 0客户自建
  sortNo?: number
  itemCount?: number // 抗生素明细条数
  bacteriaIds?: number[] // 绑定细菌ID
  bacteriaNames?: string[] // 绑定细菌名称(后端填充)
  createTime?: string
}

/** 模板明细(编辑弹窗用) */
export interface AntibioticTemplateItem {
  id?: number
  antibioticId: number
  antibioticCode?: string
  antibioticName?: string
  category?: string
  defaultMethod?: string
  sortNo?: number
  remark?: string
}

/** 模板详情(头+细菌+明细) */
export interface AntibioticTemplateVO extends AntibioticTemplate {
  items?: AntibioticTemplateItem[]
}

/** 保存模板请求 */
export interface AntibioticTemplateSaveRequest {
  id?: number
  code: string
  name: string
  templateCategory?: string
  defaultMethod?: string
  description?: string
  status?: number
  sortNo?: number
  bacteriaIds?: number[]
  items?: Array<{
    antibioticId: number
    defaultMethod?: string
    sortNo?: number
    remark?: string
  }>
}

/** 应用模板返回(针对细菌解析折点) */
export interface AntibioticTemplateApply {
  templateId: number
  templateName: string
  defaultMethod?: string
  items: Array<{
    antibioticId: number
    antibioticCode: string
    antibioticName: string
    category?: string
    method?: string
    breakpoint?: string
    breakpointSource?: string
  }>
}

export interface AntibioticTemplateQuery extends PageQuery {
  keyword?: string
  category?: string
  status?: number
}

// 模板分页 (后端 GET /api/micro/antibiotic-templates)
export function getAntibioticTemplatePage(params: AntibioticTemplateQuery) {
  const { pageNum = 1, pageSize = 10, keyword, category, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (category) query.category = category
  if (status !== undefined) query.status = status
  return request.get<PageResult<AntibioticTemplateVO>>('/micro/antibiotic-templates', query)
}

// 按细菌查可用启用模板 (报告页下拉)
export function getAntibioticTemplatesByBacteria(bacteriaId: number) {
  return request.get<AntibioticTemplate[]>(`/micro/antibiotic-templates/by-bacteria/${bacteriaId}`)
}

// 模板详情
export function getAntibioticTemplateDetail(id: number) {
  return request.get<AntibioticTemplateVO>(`/micro/antibiotic-templates/${id}`)
}

// 应用模板(针对细菌解析折点)
export function getAntibioticTemplateApply(id: number, bacteriaId?: number) {
  const query: Record<string, any> = {}
  if (bacteriaId) query.bacteriaId = bacteriaId
  return request.get<AntibioticTemplateApply>(`/micro/antibiotic-templates/${id}/apply`, query)
}

// 新增/编辑模板
export function saveAntibioticTemplate(data: AntibioticTemplateSaveRequest) {
  if (data.id) {
    return request.put<number>('/micro/antibiotic-templates', data)
  }
  return request.post<number>('/micro/antibiotic-templates', data)
}

// 删除模板
export function deleteAntibioticTemplate(id: number) {
  return request.delete<boolean>(`/micro/antibiotic-templates/${id}`)
}
