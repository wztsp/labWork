import { openPrintWindow } from './print'
import { escapeHtml } from './sanitize'
import { buildPrintCss, getPrintConfig } from './printConfig'
import { MM_TO_PX, renderBarcodeSvg } from './barcodeRender'
import { parseTemplateJson, printWithTemplate } from './printTemplate'
import {
  getEnabledPrintTemplates,
  type PrintData,
  type PrintTemplate
} from '@/api/reagent/printTemplate'

/** 试剂库存批次条码标签（用于打印/补打） */
export interface ReagentBarcodeLabel {
  barcode: string
  reagentName?: string
  lotNo?: string
  expiryDate?: string
  location?: string
  /** 自定义附带字段（如入库单号） */
  extra?: { label: string; value?: string }[]
}

/** 附带字段中文名 → 打印字段编码 */
const EXTRA_FIELD_MAP: Record<string, string> = {
  入库单: 'inboundNo',
  采购订单: 'orderNo',
  供应商: 'supplierName'
}

/** 条码标签 → 模板打印数据 */
function toDataset(label: ReagentBarcodeLabel): PrintData {
  const header: Record<string, string> = {
    barcode: label.barcode || '',
    reagentName: label.reagentName || '',
    lotNo: label.lotNo || '',
    expiryDate: label.expiryDate || '',
    location: label.location || '',
    inboundNo: '',
    orderNo: '',
    supplierName: ''
  }
  for (const e of label.extra || []) {
    const field = EXTRA_FIELD_MAP[e.label]
    if (field) header[field] = e.value || ''
  }
  return { header, items: [{}] }
}

/**
 * 打印/补打试剂库存批次条码标签。
 *
 * 优先使用后台配置的「试剂条码标签」默认打印模板（可在打印模板管理中自定义内容与布局）；
 * 未配置模板时回退到内置布局。两种方式都在父窗口预渲染条码 SVG，打印页不依赖外网脚本。
 *
 * @returns 是否成功触发打印（被浏览器拦截时 false）
 */
export async function printReagentBarcodeLabels(
  title: string,
  labels: ReagentBarcodeLabel[]
): Promise<boolean> {
  if (!labels || labels.length === 0) return false

  const template = await loadDefaultLabelTemplate()
  if (template) {
    printWithTemplate(template, labels.map(toDataset), { copies: 1 })
    return true
  }
  return printWithLegacyLayout(title, labels)
}

async function loadDefaultLabelTemplate(): Promise<PrintTemplate | null> {
  try {
    const list = await getEnabledPrintTemplates('REAGENT_LABEL')
    const tpl = list.find(t => t.isDefault === 1) || list[0]
    if (!tpl || !parseTemplateJson(tpl.templateJson)) return null
    return tpl
  } catch {
    return null
  }
}

/** 内置布局（未配置模板时的兜底），沿用 reagent-barcode 场景打印配置 */
function printWithLegacyLayout(title: string, labels: ReagentBarcodeLabel[]): boolean {
  const cfg = getPrintConfig('reagent-barcode')
  const fontSize = cfg.labelFontSize

  // 条码在父窗口渲染为 SVG，打印页不请求任何外部资源（离线部署可用）
  const barcodeWidthMm = Math.max(20, cfg.paperWidth - cfg.margin * 2 - 8)
  const barcodeHeightMm = Math.max(6, cfg.barcodeHeight / MM_TO_PX)

  const labelsHtml = labels
    .map((it) => {
      const svg = renderBarcodeSvg(it.barcode, barcodeWidthMm, barcodeHeightMm, false)
      return `
    <div class="barcode-label">
      <div class="label-row"><span class="label-field">试剂:</span><span class="label-value">${escapeHtml(it.reagentName || '')}</span></div>
      <div class="label-row"><span class="label-field">批号:</span><span class="label-value">${escapeHtml(it.lotNo || '')}</span></div>
      ${it.expiryDate ? `<div class="label-row"><span class="label-field">效期:</span><span class="label-value">${escapeHtml(it.expiryDate)}</span></div>` : ''}
      ${it.location ? `<div class="label-row"><span class="label-field">位置:</span><span class="label-value">${escapeHtml(it.location)}</span></div>` : ''}
      ${(it.extra || []).map((e) => `<div class="label-row"><span class="label-field">${escapeHtml(e.label)}:</span><span class="label-value">${escapeHtml(e.value || '')}</span></div>`).join('')}
      <div class="barcode-area">${svg}</div>
      <div class="barcode-text">${escapeHtml(it.barcode)}</div>
    </div>
  `
    })
    .join('')

  const win = openPrintWindow(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<style>
${buildPrintCss(cfg, '.barcode-label')}
  .label-row { display: flex; margin-bottom: 1.5px; font-size: ${fontSize}px; }
  .label-field { color: #666; width: 14mm; flex-shrink: 0; font-size: ${fontSize}px; }
  .label-value { color: #333; flex: 1; word-break: break-all; font-size: ${fontSize}px; }
  .barcode-area { text-align: center; margin-top: auto; padding-top: 1mm; }
  .barcode-area svg { max-width: 100%; display: block; margin: 0 auto; }
  .barcode-text { text-align: center; font-weight: bold; letter-spacing: 1px; margin-top: 1mm; font-size: ${Math.max(9, fontSize)}px; }
</style>
</head>
<body>
<div class="print-scale-wrap">
${labelsHtml}
</div>
</body>
</html>`, { width: 600, height: 800 })

  return !!win
}
