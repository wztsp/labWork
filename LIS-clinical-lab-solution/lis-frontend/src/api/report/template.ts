import { request } from '@/api/request'

/**
 * 报告元素类型
 */
export type ReportElementType =
  | 'hospital-name'      // 医院名称
  | 'report-title'       // 报告标题
  | 'barcode'            // 条码
  | 'field'              // 通用字段(细粒度,每个字段独立元素)
  | 'results-table'      // 结果表格
  | 'custom-text'        // 自定义文本
  | 'micro-organism'     // 微生物细菌鉴定表格
  | 'micro-sensitivity'  // 微生物药敏结果表格
  | 'micro-negative'     // 微生物阴性报告结论
  | 'micro-blood'        // 微生物血培养信息
  | 'sample-images'      // 标本图像(血常规/尿常规等形态图像)
  // 以下为兼容旧模板,新模板使用 field 类型
  | 'patient-info'       // 患者信息块(兼容旧)
  | 'sample-info'        // 标本信息块(兼容旧)
  | 'doctor-signature'   // 医生签名(兼容旧)
  | 'report-time'        // 报告时间
  | 'remark'             // 备注

/**
 * 报告元素配置
 */
export interface ReportElement {
  id: string
  type: ReportElementType
  label: string
  x: number
  y: number
  width: number
  height: number
  fontSize: number
  fontWeight: number
  color: string
  backgroundColor: string
  textAlign: 'left' | 'center' | 'right'
  border: number
  visible: boolean
  text?: string
  fields?: string[]
  columns?: string[]
  fieldKey?: string
  fieldLabel?: string
}

export interface ReportTemplate {
  canvasWidth: number
  canvasHeight: number
  backgroundColor: string
  padding: number
  elements: ReportElement[]
  updateTime?: string
}

/** 纸张尺寸 (默认横向, 匹配广东医院检验报告单) */
export const PAPER_SIZES: Record<string, { width: number; height: number; label: string }> = {
  A3: { width: 1587, height: 1123, label: 'A3 横 (420×297mm)' },
  A4: { width: 1123, height: 794, label: 'A4 横 (297×210mm)' },
  A5: { width: 794, height: 559, label: 'A5 横 (210×148mm)' },
  B4: { width: 1414, height: 1000, label: 'B4 横 (353×250mm)' },
  B5: { width: 949, height: 666, label: 'B5 横 (250×176mm)' }
}

/**
 * B5 竖版 (666×949)  - 适合长报告/微生物报告/多页打印
 */
export function getB5PortraitTemplate(): ReportTemplate {
  const t = getDefaultTemplate()
  t.canvasWidth = 666
  t.canvasHeight = 949
  const results = t.elements.find(e => e.type === 'results-table')
  if (results) { results.x = 28; results.width = 610; results.height = 400 }
  const title = t.elements.find(e => e.id === 'el-title')
  if (title) { title.x = 28; title.width = 610 }
  const tips = t.elements.find(e => e.id === 'el-warm-tips')
  if (tips) { tips.x = 28; tips.y = 600; tips.width = 460 }
  const remark = t.elements.find(e => e.id === 'el-remark')
  if (remark) { remark.x = 28; remark.y = 640; remark.width = 610 }
  return t
}

export function applyPaperSizeToTemplate(
  t: ReportTemplate,
  size: string,
  orientation: 'portrait' | 'landscape' = 'landscape'
): boolean {
  const ps = PAPER_SIZES[size]
  if (!ps) return false
  if (orientation === 'landscape') {
    t.canvasWidth = ps.width
    t.canvasHeight = ps.height
  } else {
    t.canvasWidth = ps.height
    t.canvasHeight = ps.width
  }
  return true
}

/** 内部工具: 生成最小模板元素集 (结果表+标题+备注, 实际渲染器已按CSS规范布局) */
function minimalElements(): ReportElement[] {
  return [
    { id: 'el-title', type: 'report-title', label: '报告标题', x: 30, y: 30, width: 889, height: 48, fontSize: 22, fontWeight: 800, color: '#000000', backgroundColor: 'transparent', textAlign: 'center', border: 0, visible: true, text: '广东省第一人民医院医学检验中心报告单' },
    { id: 'el-results', type: 'results-table', label: '检验结果表格', x: 30, y: 160, width: 889, height: 280, fontSize: 12, fontWeight: 400, color: '#000000', backgroundColor: 'transparent', textAlign: 'left', border: 1, visible: true, columns: ['idx', 'testItemName', 'englishCode', 'resultValue', 'unit', 'refRange', 'testMethod'] },
    { id: 'el-warm-tips', type: 'custom-text', label: '温馨提示', x: 30, y: 452, width: 660, height: 34, fontSize: 11, fontWeight: 400, color: '#333333', backgroundColor: 'transparent', textAlign: 'left', border: 0, visible: true, text: '温馨提示: 本报告结果仅对该送检标本负责,带*标项目已开通广东省检验结果互认。' },
    { id: 'el-remark', type: 'remark', label: '备注', x: 30, y: 490, width: 889, height: 32, fontSize: 12, fontWeight: 400, color: '#000000', backgroundColor: 'transparent', textAlign: 'left', border: 1, visible: true, text: '' }
  ]
}

/**
 * 默认报告单模板 (广东医院7列标准版式, B5横 949×666)
 *
 *  1. 标题: 左Logo+医院名 / 中间大标题 / 右上角 第X页/共X页
 *  2. 患者信息 3行: 姓名/性别/年龄/标本条码/仪器名称; 科室/床号/住院号/标本编号/标本类型; 临床诊断+申请项目
 *  3. 结果表格7列: 序号(带*互认) / 中文名称 / 英文缩写 / 结果 / 单位 / 参考 / 检验方法
 *  4. 温馨提示 + 广东省HR方框 + 备注横线
 *  5. 签名行: 开嘱医生/采样人/接收人/检测医师/审核者
 *  6. 时间行: 开嘱/采样/接收/报告
 */
export function getDefaultTemplate(): ReportTemplate {
  return {
    canvasWidth: 949,
    canvasHeight: 666,
    backgroundColor: '#ffffff',
    padding: 28,
    elements: minimalElements()
  }
}

/**
 * B5 横向检验报告单 (与默认模板一致 - 广东医院报告单默认 B5 横)
 */
export function getB5Template(): ReportTemplate {
  return getDefaultTemplate()
}

/**
 * B5 竖版检验报告单 (666×949) - 长报告/微生物/住院用
 */
export function getB5VerticalTemplate(): ReportTemplate {
  return getB5PortraitTemplate()
}

/**
 * 尿常规双栏报告模板
 *  - 左右分栏: 左 "尿液化学检测项目"4列 / 右 "尿沉渣检测项目"4列, 中间竖线
 *  - 右下角方框: "尿常规项目为广东省HR"
 *  - 签名行: 末位为"报告人"而非"审核者"
 */
export function getUrineTemplate(): ReportTemplate {
  const t = getDefaultTemplate()
  const table = t.elements.find(e => e.type === 'results-table')
  if (table) table.height = 250
  const title = t.elements.find(e => e.id === 'el-title')
  if (title) title.text = '广东省第一人民医院医学检验中心 尿液分析报告单'
  return t
}

// ==================== 内置模板类型 ====================

/**
 * - standard   标准7列B5横报告 (术前四项/化学发光/免疫等)
 * - b5         B5横打印版 (同standard)
 * - b5-vert    B5竖版检验报告 (长报告/住院)
 * - urine      尿常规双栏 (尿液化学+尿沉渣 左右分栏)
 * - micro      微生物报告 (细菌鉴定/药敏)
 * - micro-negative  微生物阴性报告 (培养阴性结论)
 * - micro-blood     微生物血培养报告 (血培养信息+鉴定/药敏)
 * - multi-ref  多参考报告 (含上次结果/上次日期对比列)
 * - dual       旧版双列 (仍兼容, 但渲染按7列)
 */
export type BuiltinTemplateType = 'standard' | 'dual' | 'b5' | 'b5-vert' | 'micro' | 'micro-negative' | 'micro-blood' | 'multi-ref' | 'urine'

/** 是否 "微生物专属" 内置模板 (只能用于微生物项目/报告) */
export function isMicroBuiltinType(type?: string): boolean {
  return !!type && ['micro', 'micro-negative', 'micro-blood'].includes(type)
}

export const BUILTIN_TEMPLATE_TYPES: { value: BuiltinTemplateType; label: string; desc: string }[] = [
  { value: 'standard',  label: '标准打印版',   desc: 'B5横 7列报告 (广东医院通用报告单)' },
  { value: 'urine',     label: '尿常规双栏',   desc: '尿液化学 + 尿沉渣, 左右双栏分表' },
  { value: 'b5',        label: 'B5横版',       desc: '同标准打印版' },
  { value: 'b5-vert',   label: 'B5竖版',       desc: '666×949 长报告/住院病历单' },
  { value: 'multi-ref', label: '多参考报告',   desc: '含上次结果/上次日期/历史对比列' },
  { value: 'micro',     label: '微生物报告',   desc: '细菌鉴定 + 药敏结果(S/I/R着色)' },
  { value: 'micro-negative', label: '微生物阴性报告', desc: '培养阴性结论 + 签名, 仅微生物项目使用' },
  { value: 'micro-blood', label: '微生物血培养报告', desc: '血培养瓶信息 + 细菌鉴定/药敏, 仅微生物项目使用' },
  { value: 'dual',      label: '旧版双列',     desc: '兼容旧模板' }
]

function cloneTemplate(t: ReportTemplate): ReportTemplate {
  return JSON.parse(JSON.stringify(t))
}

/** 按类型生成内置模板 */
export function getBuiltInTemplate(type: BuiltinTemplateType): ReportTemplateConfig {
  const meta = BUILTIN_TEMPLATE_TYPES.find(m => m.value === type)!
  let t: ReportTemplate
  switch (type) {
    case 'urine':
      t = cloneTemplate(getUrineTemplate())
      break
    case 'b5':
    case 'standard':
    case 'dual':
      t = cloneTemplate(getDefaultTemplate())
      break
    case 'b5-vert':
      t = cloneTemplate(getB5VerticalTemplate())
      break
    case 'multi-ref': {
      t = cloneTemplate(getDefaultTemplate())
      const title = t.elements.find(e => e.id === 'el-title')
      if (title) title.text = '广东省第一人民医院医学检验中心报告单 (历史对比)'
      const table = t.elements.find(e => e.type === 'results-table')
      if (table) {
        table.columns = ['idx', 'testItemName', 'englishCode', 'resultValue', 'unit', 'refRange', 'lastResult', 'lastDate', 'lastFlag', 'testMethod']
        table.width = 889
      }
      const tips = t.elements.find(e => e.id === 'el-warm-tips')
      if (tips) tips.text = '温馨提示: 带*标项目已开通广东省检验结果互认,本次结果与上次对比仅供临床参考,请结合病情判断。'
      break
    }
    case 'micro': {
      t = cloneTemplate(getB5VerticalTemplate())
      const title = t.elements.find(e => e.id === 'el-title')
      if (title) title.text = '广东省第一人民医院医学检验中心 微生物检验报告单'
      const table = t.elements.find(e => e.type === 'results-table')
      if (table) {
        table.type = 'micro-organism'
        table.label = '细菌鉴定'
        table.columns = ['organismName', 'organismCode', 'quantity', 'colonsMethod']
        table.x = 28
        table.width = 610
        table.height = 160
        table.y = 160
      }
      t.elements.push({
        id: 'el-micro-sens', type: 'micro-sensitivity', label: '药敏结果', x: 28, y: 340,
        width: 610, height: 260, fontSize: 12, fontWeight: 400, color: '#000000',
        backgroundColor: 'transparent', textAlign: 'left', border: 1, visible: true,
        columns: ['antibioticName', 'micValue', 'result', 'breakpoint']
      })
      const tips = t.elements.find(e => e.id === 'el-warm-tips')
      if (tips) { tips.x = 28; tips.y = 620; tips.width = 460; tips.text = '温馨提示: 本报告仅对送检标本负责,药敏结果采用CLSI 2024标准判读,S=敏感 I=中介 R=耐药。' }
      const remark = t.elements.find(e => e.id === 'el-remark')
      if (remark) { remark.x = 28; remark.y = 660; remark.width = 610; remark.text = '备注: 培养结果及药敏结果仅供临床参考,必要时请结合患者情况复核。' }
      break
    }
    case 'micro-negative': {
      // 微生物阴性报告: 突出培养阴性结论 + 签名, 不展示药敏表
      t = cloneTemplate(getB5VerticalTemplate())
      const title = t.elements.find(e => e.id === 'el-title')
      if (title) title.text = '广东省第一人民医院医学检验中心 微生物检验报告单(阴性)'
      // 移除常规结果表, 替换为阴性结论块
      const idx = t.elements.findIndex(e => e.type === 'results-table')
      if (idx >= 0) t.elements.splice(idx, 1)
      t.elements.push({
        id: 'el-micro-negative', type: 'micro-negative', label: '培养阴性结论',
        x: 28, y: 170, width: 610, height: 120, fontSize: 14, fontWeight: 500,
        color: '#000000', backgroundColor: 'transparent', textAlign: 'left', border: 1,
        visible: true, text: '培养48小时, 未见致病菌生长。'
      })
      const tips = t.elements.find(e => e.id === 'el-warm-tips')
      if (tips) { tips.x = 28; tips.y = 320; tips.width = 460; tips.text = '温馨提示: 本报告仅对送检标本负责,培养 48~72 小时未见致病菌生长。' }
      const remark = t.elements.find(e => e.id === 'el-remark')
      if (remark) { remark.x = 28; remark.y = 360; remark.width = 610; remark.text = '备注: 阴性结果仅表示本次标本培养未见指定致病菌。' }
      break
    }
    case 'micro-blood': {
      // 微生物血培养报告: 血培养瓶信息 + 细菌鉴定/药敏
      t = cloneTemplate(getB5VerticalTemplate())
      const title = t.elements.find(e => e.id === 'el-title')
      if (title) title.text = '广东省第一人民医院医学检验中心 血培养检验报告单'
      t.elements.push({
        id: 'el-micro-blood', type: 'micro-blood', label: '血培养信息',
        x: 28, y: 130, width: 610, height: 100, fontSize: 12, fontWeight: 400,
        color: '#000000', backgroundColor: 'transparent', textAlign: 'left', border: 1,
        visible: true, text: ''
      })
      const org = t.elements.find(e => e.type === 'results-table')
      if (org) {
        org.type = 'micro-organism'
        org.label = '细菌鉴定'
        org.columns = ['organismName', 'organismCode', 'quantity', 'colonsMethod']
        org.x = 28; org.width = 610; org.height = 150; org.y = 250
      }
      t.elements.push({
        id: 'el-micro-sens', type: 'micro-sensitivity', label: '药敏结果', x: 28, y: 420,
        width: 610, height: 240, fontSize: 12, fontWeight: 400, color: '#000000',
        backgroundColor: 'transparent', textAlign: 'left', border: 1, visible: true,
        columns: ['antibioticName', 'micValue', 'result', 'breakpoint']
      })
      const tips = t.elements.find(e => e.id === 'el-warm-tips')
      if (tips) { tips.x = 28; tips.y = 680; tips.width = 460; tips.text = '温馨提示: 本报告仅对送检标本负责,药敏结果采用CLSI 2024标准判读。' }
      const remark = t.elements.find(e => e.id === 'el-remark')
      if (remark) { remark.x = 28; remark.y = 720; remark.width = 610; remark.text = '备注: 血培养阳性结果提示血流感染风险,必要时复查。' }
      break
    }
  }
  return {
    ...t,
    templateName: meta.label,
    templateType: type,
    dualColumn: type === 'urine',
    paperSize: 'B5',
    orientation: type === 'b5-vert' ? 'portrait' : 'landscape',
    margin: 10,
    reportType: 3, // 标记为「微生物」业务类型, 保证只有微生物项目/报告能使用
    isDefault: isMicroBuiltinType(type) ? false : undefined
  }
}

export function getBuiltinTemplateList(): ReportTemplateConfig[] {
  return BUILTIN_TEMPLATE_TYPES.map(m => getBuiltInTemplate(m.value))
}

// ==================== 模板存储 ====================

const STORAGE_KEY = 'lis_report_template'

export async function loadTemplate(): Promise<ReportTemplate> {
  try {
    const res: any = await request.get('/system/configs/report_template')
    if (res && res.configValue) return JSON.parse(res.configValue) as ReportTemplate
  } catch { /* ignore */ }
  const local = localStorage.getItem(STORAGE_KEY)
  if (local) {
    try { return JSON.parse(local) as ReportTemplate } catch { /* ignore */ }
  }
  return getDefaultTemplate()
}

export async function saveTemplate(template: ReportTemplate): Promise<void> {
  template.updateTime = new Date().toISOString()
  const json = JSON.stringify(template)
  localStorage.setItem(STORAGE_KEY, json)
  try {
    await request.post('/system/configs', {
      configKey: 'report_template', configValue: json, remark: '报告模板配置'
    })
  } catch { /* ignore */ }
}

export function loadLocalTemplate(): ReportTemplate {
  const local = localStorage.getItem(STORAGE_KEY)
  if (local) {
    try { return JSON.parse(local) as ReportTemplate } catch { /* ignore */ }
  }
  return getDefaultTemplate()
}

export function saveLocalTemplate(template: ReportTemplate): void {
  template.updateTime = new Date().toISOString()
  localStorage.setItem(STORAGE_KEY, JSON.stringify(template))
}

export function resetTemplate(): ReportTemplate {
  const def = getDefaultTemplate()
  localStorage.setItem(STORAGE_KEY, JSON.stringify(def))
  return def
}

// ==================== 多模板管理 API ====================

export interface ReportTemplateConfig extends ReportTemplate {
  templateId?: number
  templateName?: string
  instrumentId?: number | null
  instrumentName?: string
  dualColumn?: boolean
  paperSize?: string
  orientation?: string
  margin?: number
  isDefault?: boolean
  templateType?: string
  reportType?: number
}

export async function getTemplateList(): Promise<ReportTemplateConfig[]> {
  try {
    const res: any = await request.get('/report/templates/list')
    if (Array.isArray(res)) return res.map((t: any) => normalizeTemplate(t))
    return []
  } catch { return [] }
}

export async function getTemplatePage(params: { current?: number; size?: number; name?: string; instrumentId?: number; reportType?: number }): Promise<{ records: ReportTemplateConfig[]; total: number }> {
  try {
    const res: any = await request.get('/report/templates', { params })
    if (res && res.records) {
      return { records: res.records.map((t: any) => normalizeTemplate(t)), total: res.total || 0 }
    }
    return { records: [], total: 0 }
  } catch { return { records: [], total: 0 } }
}

export async function getTemplateById(id: number): Promise<ReportTemplateConfig | null> {
  try {
    const res: any = await request.get(`/report/templates/${id}`)
    return res ? normalizeTemplate(res) : null
  } catch { return null }
}

export async function getTemplateByInstrument(instrumentId?: number, reportType?: number): Promise<ReportTemplateConfig | null> {
  try {
    const params: any = {}
    if (instrumentId != null) params.instrumentId = instrumentId
    if (reportType != null) params.reportType = reportType
    const res: any = await request.get('/report/templates/by-instrument', { params })
    return res ? normalizeTemplate(res) : null
  } catch { return null }
}

export async function getDefaultTemplateFromServer(reportType?: number): Promise<ReportTemplateConfig | null> {
  try {
    const params: any = {}
    if (reportType != null) params.reportType = reportType
    const res: any = await request.get('/report/templates/default', { params })
    return res ? normalizeTemplate(res) : null
  } catch { return null }
}

export async function createTemplate(config: ReportTemplateConfig): Promise<ReportTemplateConfig | null> {
  try {
    const res: any = await request.post('/report/templates', denormalizeTemplate(config))
    return res ? normalizeTemplate(res) : null
  } catch { return null }
}

export async function updateTemplate(config: ReportTemplateConfig): Promise<ReportTemplateConfig | null> {
  try {
    const res: any = await request.put('/report/templates', denormalizeTemplate(config))
    return res ? normalizeTemplate(res) : null
  } catch { return null }
}

export async function deleteTemplate(id: number): Promise<boolean> {
  try { await request.delete(`/report/templates/${id}`); return true } catch { return false }
}

export async function setDefaultTemplate(id: number): Promise<boolean> {
  try { await request.put(`/report/templates/${id}/default`); return true } catch { return false }
}

// ==================== 辅助函数 ====================

function normalizeTemplate(raw: any): ReportTemplateConfig {
  let config: ReportTemplate = raw.templateConfig
  try {
    config = typeof raw.templateConfig === 'string'
      ? JSON.parse(raw.templateConfig)
      : (raw.templateConfig || getDefaultTemplate())
  } catch {
    config = getDefaultTemplate()
  }
  return {
    ...config,
    templateId: raw.id,
    templateName: raw.templateName,
    instrumentId: raw.instrumentId,
    instrumentName: raw.instrumentName,
    reportType: raw.reportType,
    dualColumn: raw.dualColumn === 1 || raw.dualColumn === true,
    paperSize: raw.paperSize || 'B5',
    orientation: raw.orientation || 'landscape',
    margin: raw.margin ?? 10,
    isDefault: raw.isDefault === 1 || raw.isDefault === true
  }
}

function denormalizeTemplate(config: ReportTemplateConfig): any {
  const { templateId, templateName, instrumentId, instrumentName, reportType, dualColumn, paperSize, orientation, margin, isDefault, ...templateData } = config
  return {
    id: templateId,
    templateName: templateName || '未命名模板',
    instrumentId: instrumentId || null,
    instrumentName: instrumentName || null,
    reportType: reportType || null,
    templateConfig: JSON.stringify(templateData),
    dualColumn: dualColumn ? 1 : 0,
    paperSize: paperSize || 'B5',
    orientation: orientation || 'landscape',
    margin: margin ?? 10,
    isDefault: isDefault ? 1 : 0,
    status: 1
  }
}
