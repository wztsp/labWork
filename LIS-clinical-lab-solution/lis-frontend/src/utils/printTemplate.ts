/**
 * 打印模板解析与渲染
 *
 * 设计原则：
 * 1. 模板 = 画布 + 绝对定位元素（坐标单位 mm），前端设计器产出、这里消费；
 * 2. 条码/二维码在父窗口预渲染为 SVG 字符串，打印页不加载任何外部资源（离线可用）；
 * 3. 明细表格按页高自动分页，表头在每页重复；
 * 4. 动态值一律 escapeHtml，再拼入模板。
 */
import { openPreviewWindow, openPrintWindow } from '@/utils/print'
import { renderBarcodeSvg, renderQrSvg } from '@/utils/barcodeRender'
import type { PrintData, PrintTemplate } from '@/api/reagent/printTemplate'

/** 元素类型 */
export type PrintElementType =
  | 'text'
  | 'field'
  | 'barcode'
  | 'qrcode'
  | 'line'
  | 'rect'
  | 'image'
  | 'table'

/** 表格列 */
export interface PrintTableColumn {
  field: string
  title: string
  /** 列宽（mm） */
  width: number
  align?: 'left' | 'center' | 'right'
}

/** 画布元素（各类型共用可选属性，便于设计器统一编辑） */
export interface PrintElement {
  id: string
  type: PrintElementType
  /** 相对内容区左上角的位置（mm） */
  x: number
  y: number
  w: number
  h: number
  /** text */
  text?: string
  fontSize?: number
  bold?: boolean
  align?: 'left' | 'center' | 'right'
  color?: string
  /** field */
  field?: string
  label?: string
  labelWidth?: number
  /** barcode / qrcode */
  symbology?: 'CODE128'
  value?: string
  showText?: boolean
  /** line / rect 线宽（mm） */
  thickness?: number
  /** image 的 src（建议 dataURL） */
  src?: string
  /** table */
  rowHeight?: number
  columns?: PrintTableColumn[]
}

/** 模板 JSON 结构 */
export interface PrintTemplateModel {
  version: number
  canvas: {
    width: number
    height: number
    background?: string
  }
  elements: PrintElement[]
}

export interface RenderOptions {
  /** 打印份数，默认 1（整份重复） */
  copies?: number
}

const DEFAULT_ELEMENT_FONT_SIZE = 10

/** 解析模板 JSON，失败返回 null（调用方回退默认模板） */
export function parseTemplateJson(json?: string): PrintTemplateModel | null {
  if (!json) return null
  try {
    const model = JSON.parse(json) as PrintTemplateModel
    if (!model || !model.canvas || !Array.isArray(model.elements)) return null
    return model
  } catch {
    return null
  }
}

function escapeHtml(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 变量插值：{{field}}（设计器预览与打印渲染共用） */
export function interpolate(text: string, data: PrintData): string {
  const merged: Record<string, string> = { ...data.header }
  if (data.printTime) merged.printTime = data.printTime
  return String(text || '').replace(/\{\{\s*(\w+)\s*\}\}/g, (_m, key: string) => {
    const v = merged[key]
    return v === undefined || v === null ? '' : String(v)
  })
}

/** 纸张与页容器 CSS（.print-page 为每一页根元素） */
export function buildPrintPageCss(tpl: PrintTemplate): string {
  const pageWidth = Number(tpl.paperWidth) || 210
  const pageHeight = Number(tpl.paperHeight) || 297
  const margin = Number(tpl.margin) || 0
  return `
  @page { size: ${pageWidth}mm ${pageHeight}mm; margin: 0; }
  html, body { margin: 0; padding: 0; }
  body {
    font-family: "Microsoft YaHei", "SimSun", sans-serif;
    color: #000;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .print-page {
    position: relative;
    width: ${pageWidth}mm;
    height: ${pageHeight}mm;
    padding: ${margin}mm;
    box-sizing: border-box;
    overflow: hidden;
    page-break-after: always;
    background: #fff;
  }
  .print-page:last-child { page-break-after: auto; }
  .print-canvas { position: relative; width: 100%; height: 100%; }
  .print-el { position: absolute; box-sizing: border-box; overflow: hidden; }
  .print-el-table { overflow: visible; }
  .print-el-text { white-space: pre-wrap; word-break: break-all; line-height: 1.35; }
  .print-el-field { line-height: 1.35; display: flex; align-items: flex-start; }
  .print-el-field-label { flex: 0 0 auto; }
  .print-el-field-value { flex: 1 1 auto; word-break: break-all; }
  .print-el-barcode, .print-el-qrcode { display: flex; align-items: center; justify-content: center; }
  .print-el-barcode svg { max-width: 100%; }
  .print-el-qrcode svg { width: 100%; height: 100%; }
  .print-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .print-table th, .print-table td {
    border: 1px solid #333;
    padding: 0 1mm;
    font-size: 10px;
    line-height: 1.2;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .print-table th { background: #f2f2f2; font-weight: bold; text-align: center; }
  `
}

interface PageSlice {
  dataset: PrintData
  pageIndex: number
  pageTotal: number
}

/** 计算每个数据源需要打印多少页（由表格元素高度推出） */
function planPages(
  model: PrintTemplateModel,
  datasets: PrintData[],
  options?: RenderOptions
): PageSlice[] {
  const plan: PageSlice[] = []
  const copies = Math.max(1, Math.floor(options?.copies || 1))
  if (datasets.length === 0) return plan

  for (const dataset of datasets) {
    let pages = 1
    for (const el of model.elements) {
      if (el.type !== 'table') continue
      const rowHeight = Math.max(4, Number(el.rowHeight) || 8)
      const capacity = Math.floor(Number(el.h) / rowHeight) - 1 // 预留表头一行
      const rows = (dataset.items || []).length
      pages = Math.max(pages, Math.ceil(rows / Math.max(1, capacity)) || 1)
    }
    for (let i = 0; i < pages; i++) {
      plan.push({ dataset, pageIndex: i, pageTotal: pages })
    }
  }

  // 份数：整份重复
  const repeated: PageSlice[] = []
  for (let c = 0; c < copies; c++) repeated.push(...plan)
  return repeated
}

/** 表格元素分页渲染 */
function renderTable(el: PrintElement, slice: PageSlice): string {
  const rowHeight = Math.max(4, Number(el.rowHeight) || 8)
  const capacity = Math.max(1, Math.floor(Number(el.h) / rowHeight) - 1)
  const items = slice.dataset.items || []
  const start = slice.pageIndex * capacity
  const rows = items.slice(start, start + capacity)
  const columns = el.columns && el.columns.length ? el.columns : []

  let html = '<table class="print-table"><colgroup>'
  for (const col of columns) {
    html += `<col style="width:${Number(col.width) || 20}mm"/>`
  }
  html += '</colgroup><thead><tr>'
  for (const col of columns) {
    html += `<th>${escapeHtml(col.title)}</th>`
  }
  html += '</tr></thead><tbody>'
  for (let r = 0; r < rows.length; r++) {
    const row = rows[r]
    html += `<tr style="height:${rowHeight}mm">`
    for (const col of columns) {
      const align = col.align || 'left'
      // index 列按整份单据连续编号，跨页不重置
      const value = col.field === 'index' ? String(start + r + 1) : escapeHtml(row[col.field])
      html += `<td style="text-align:${align}">${value}</td>`
    }
    html += '</tr>'
  }
  html += '</tbody></table>'
  return html
}

/** 单个元素渲染 */
function renderElement(el: PrintElement, slice: PageSlice): string {
  const style = `left:${Number(el.x) || 0}mm;top:${Number(el.y) || 0}mm;` +
    `width:${Number(el.w) || 0}mm;height:${Number(el.h) || 0}mm;`
  const fontSize = Number(el.fontSize) || DEFAULT_ELEMENT_FONT_SIZE
  const color = el.color || '#000'

  switch (el.type) {
    case 'text': {
      const content = escapeHtml(interpolate(el.text || '', slice.dataset))
      const align = el.align || 'left'
      const weight = el.bold ? 'bold' : 'normal'
      return `<div class="print-el print-el-text" style="${style}font-size:${fontSize}px;` +
        `font-weight:${weight};text-align:${align};color:${color}">${content}</div>`
    }
    case 'field': {
      const value = escapeHtml(slice.dataset.header?.[el.field || ''] ?? '')
      const label = el.label
        ? `<span class="print-el-field-label" style="width:${Number(el.labelWidth) || 0}mm">${escapeHtml(el.label)}</span>`
        : ''
      const weight = el.bold ? 'bold' : 'normal'
      return `<div class="print-el print-el-field" style="${style}font-size:${fontSize}px;` +
        `font-weight:${weight};color:${color}">${label}` +
        `<span class="print-el-field-value">${value}</span></div>`
    }
    case 'barcode': {
      const value = interpolate(el.value || '{{barcode}}', slice.dataset)
      const svg = renderBarcodeSvg(value, Number(el.w) || 0, Number(el.h) || 0, el.showText !== false)
      return `<div class="print-el print-el-barcode" style="${style}">${svg}</div>`
    }
    case 'qrcode': {
      const value = interpolate(el.value || '{{barcode}}', slice.dataset)
      const svg = renderQrSvg(value)
      return `<div class="print-el print-el-qrcode" style="${style}">${svg}</div>`
    }
    case 'line': {
      // 线条元素常被设计为 h=0（只关心线宽），这里以线宽兜底高度，避免被 overflow 裁掉
      const thickness = Number(el.thickness) || 0.3
      const lineHeight = Math.max(thickness, Number(el.h) || 0)
      return `<div class="print-el" style="left:${Number(el.x) || 0}mm;top:${Number(el.y) || 0}mm;` +
        `width:${Number(el.w) || 0}mm;height:${lineHeight}mm;">` +
        `<div style="height:${thickness}mm;background:${color}"></div></div>`
    }
    case 'rect': {
      const thickness = Number(el.thickness) || 0.3
      return `<div class="print-el" style="${style}border:${thickness}mm solid ${color}"></div>`
    }
    case 'image': {
      if (!el.src) {
        return `<div class="print-el" style="${style}"></div>`
      }
      return `<div class="print-el" style="${style}">` +
        `<img src="${escapeHtml(el.src)}" style="width:100%;height:100%;object-fit:contain"/></div>`
    }
    case 'table': {
      return `<div class="print-el print-el-table" style="${style}">${renderTable(el, slice)}</div>`
    }
    default:
      return ''
  }
}

/** 渲染每一页的 HTML 片段 */
export function renderTemplatePages(
  tpl: PrintTemplate,
  datasets: PrintData[],
  options?: RenderOptions
): string[] {
  const model = parseTemplateJson(tpl.templateJson)
  if (!model) return []
  const plan = planPages(model, datasets.length ? datasets : [{ header: {}, items: [] }], options)
  return plan.map(slice => {
    const background = model.canvas.background || '#ffffff'
    let body = ''
    for (const el of model.elements) {
      body += renderElement(el, slice)
    }
    return `<div class="print-page" style="background:${background}">` +
      `<div class="print-canvas">${body}</div></div>`
  })
}

/** 生成完整打印 HTML（可直接交给 openPrintWindow） */
export function renderTemplateHtml(
  tpl: PrintTemplate,
  datasets: PrintData[],
  options?: RenderOptions
): string {
  const pages = renderTemplatePages(tpl, datasets, options)
  const title = escapeHtml(tpl.templateName || '打印')
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"/>` +
    `<title>${title}</title><style>${buildPrintPageCss(tpl)}</style></head>` +
    `<body>${pages.join('')}</body></html>`
}

/** 打印（默认静默打印到浏览器默认打印机） */
export function printWithTemplate(
  tpl: PrintTemplate,
  datasets: PrintData[],
  options?: RenderOptions
): void {
  openPrintWindow(renderTemplateHtml(tpl, datasets, options))
}

/** 预览（打开新窗口，由用户再点打印） */
export function previewTemplate(
  tpl: PrintTemplate,
  datasets: PrintData[],
  options?: RenderOptions
): void {
  openPreviewWindow(renderTemplateHtml(tpl, datasets, options), {
    width: Math.max(600, (Number(tpl.paperWidth) || 210) * 3 + 100),
    height: 900
  })
}
