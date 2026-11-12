/**
 * 统一打印配置工具 (P2-8 打印设置)
 *
 * 打印场景配置:
 * - paperWidth / paperHeight: 纸张尺寸, 单位 mm (A4: 210x297, A5: 148x210, 标签: 80x40 等)
 * - scale: 打印缩放比例 (0.5 ~ 2.0), 默认 1.0
 * - barcodeWidth: JsBarcode 单条宽度 px (1.0 ~ 3.0), 默认 1.5
 * - barcodeHeight: JsBarcode 条码高度 px, 默认 40
 * - labelFontSize: 标签字体大小 px, 默认 10
 * - margin: 标签外边距 mm
 * - border: 是否打印边框 (0/1)
 * - paperSizePreset: 快速预设名称 (A4, A5, LABEL_60x40, LABEL_80x40, LABEL_100x60, CUSTOM)
 *
 * localStorage key:
 *   lis:print-config:${sceneKey}   —— 场景个性化配置
 *   lis:print-config:default      —— 全局默认配置 (未配置场景时使用)
 */

/** 打印场景键 */
export type PrintSceneKey =
  | 'sample-barcode'      // 采集工作站: 标本条码
  | 'plate-barcode'       // 微生物: 培养基条码
  | 'reagent-barcode'     // 试剂: 入库/库存条码
  | 'report-clinical'     // 报告单: 常规检验
  | 'report-micro'        // 报告单: 微生物
  | 'report-qc'           // 报告单: 质控

/** 纸张预设 */
export interface PaperSizePreset {
  key: string
  name: string
  width: number  // mm
  height: number // mm
}

export const PAPER_PRESETS: PaperSizePreset[] = [
  { key: 'A4', name: 'A4 (210×297mm)', width: 210, height: 297 },
  { key: 'A5', name: 'A5 (148×210mm)', width: 148, height: 210 },
  { key: 'LABEL_40x30', name: '标签 40×30mm', width: 40, height: 30 },
  { key: 'LABEL_50x30', name: '标签 50×30mm', width: 50, height: 30 },
  { key: 'LABEL_60x40', name: '标签 60×40mm', width: 60, height: 40 },
  { key: 'LABEL_70x40', name: '标签 70×40mm', width: 70, height: 40 },
  { key: 'LABEL_80x40', name: '标签 80×40mm', width: 80, height: 40 },
  { key: 'LABEL_80x50', name: '标签 80×50mm', width: 80, height: 50 },
  { key: 'LABEL_90x60', name: '标签 90×60mm', width: 90, height: 60 },
  { key: 'LABEL_100x60', name: '标签 100×60mm', width: 100, height: 60 },
  { key: 'LABEL_100x80', name: '标签 100×80mm', width: 100, height: 80 },
  { key: 'CUSTOM', name: '自定义', width: 0, height: 0 },
]

/** 打印配置 */
export interface PrintConfig {
  scene: PrintSceneKey | 'default'
  paperSizePreset: string
  paperWidth: number   // mm
  paperHeight: number  // mm
  scale: number        // 0.5 ~ 2.0
  barcodeWidth: number // 1.0 ~ 3.0
  barcodeHeight: number // px
  labelFontSize: number // px
  margin: number       // mm
  border: 0 | 1
  /** 自定义 CSS 片段, 插入 @media print 块 */
  extraCss?: string
}

const LS_PREFIX = 'lis:print-config:'

/** 场景默认配置 —— 基于使用经验给出合理值, 避免"打印非常大" */
const SCENE_DEFAULTS: Record<PrintSceneKey, Omit<PrintConfig, 'scene'>> = {
  'sample-barcode': {
    paperSizePreset: 'LABEL_70x40',
    paperWidth: 70,
    paperHeight: 40,
    scale: 1.0,
    barcodeWidth: 1.5,
    barcodeHeight: 30,
    labelFontSize: 9,
    margin: 2,
    border: 1,
  },
  'plate-barcode': {
    paperSizePreset: 'LABEL_60x40',
    paperWidth: 60,
    paperHeight: 40,
    scale: 1.0,
    barcodeWidth: 1.3,
    barcodeHeight: 26,
    labelFontSize: 8,
    margin: 2,
    border: 1,
  },
  'reagent-barcode': {
    paperSizePreset: 'LABEL_60x40',
    paperWidth: 60,
    paperHeight: 40,
    scale: 1.0,
    barcodeWidth: 1.3,
    barcodeHeight: 26,
    labelFontSize: 9,
    margin: 2,
    border: 1,
  },
  'report-clinical': {
    paperSizePreset: 'A4',
    paperWidth: 210,
    paperHeight: 297,
    scale: 1.0,
    barcodeWidth: 2,
    barcodeHeight: 50,
    labelFontSize: 12,
    margin: 10,
    border: 0,
  },
  'report-micro': {
    paperSizePreset: 'A4',
    paperWidth: 210,
    paperHeight: 297,
    scale: 1.0,
    barcodeWidth: 2,
    barcodeHeight: 50,
    labelFontSize: 12,
    margin: 10,
    border: 0,
  },
  'report-qc': {
    paperSizePreset: 'A4',
    paperWidth: 210,
    paperHeight: 297,
    scale: 1.0,
    barcodeWidth: 2,
    barcodeHeight: 50,
    labelFontSize: 12,
    margin: 10,
    border: 0,
  },
}

/** 全局默认配置 (兜底) */
const GLOBAL_DEFAULT: PrintConfig = {
  scene: 'default',
  paperSizePreset: 'LABEL_70x40',
  paperWidth: 70,
  paperHeight: 40,
  scale: 1.0,
  barcodeWidth: 1.5,
  barcodeHeight: 40,
  labelFontSize: 10,
  margin: 2,
  border: 1,
}

function clamp(v: number, min: number, max: number) {
  if (Number.isNaN(v)) return (min + max) / 2
  return Math.min(max, Math.max(min, v))
}

function lsKey(scene: PrintSceneKey | 'default') {
  return LS_PREFIX + scene
}

/** 读取场景打印配置, 不存在则使用该场景默认 */
export function getPrintConfig(scene: PrintSceneKey): PrintConfig {
  try {
    const raw = localStorage.getItem(lsKey(scene))
    if (raw) {
      const parsed = JSON.parse(raw)
      const base = SCENE_DEFAULTS[scene]
      return {
        scene,
        paperSizePreset: parsed.paperSizePreset || base.paperSizePreset,
        paperWidth: clamp(Number(parsed.paperWidth) || base.paperWidth, 20, 500),
        paperHeight: clamp(Number(parsed.paperHeight) || base.paperHeight, 20, 500),
        scale: clamp(Number(parsed.scale) || base.scale, 0.3, 3.0),
        barcodeWidth: clamp(Number(parsed.barcodeWidth) || base.barcodeWidth, 0.8, 4.0),
        barcodeHeight: clamp(Number(parsed.barcodeHeight) || base.barcodeHeight, 10, 120),
        labelFontSize: clamp(Number(parsed.labelFontSize) || base.labelFontSize, 6, 24),
        margin: clamp(Number(parsed.margin) ?? base.margin, 0, 50),
        border: (parsed.border === 1 || parsed.border === 0) ? parsed.border : base.border,
        extraCss: typeof parsed.extraCss === 'string' ? parsed.extraCss : undefined,
      }
    }
  } catch {}
  return { scene, ...SCENE_DEFAULTS[scene] }
}

/** 保存场景打印配置 */
export function savePrintConfig(cfg: PrintConfig) {
  const key = cfg.scene === 'default' ? 'default' : cfg.scene
  const toSave = { ...cfg }
  // default 场景不用单独存 scene
  localStorage.setItem(lsKey(key as any), JSON.stringify(toSave))
  return true
}

/** 读取全局默认配置 */
export function getGlobalDefaultConfig(): PrintConfig {
  try {
    const raw = localStorage.getItem(lsKey('default'))
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        ...GLOBAL_DEFAULT,
        ...parsed,
        scene: 'default',
        paperWidth: clamp(Number(parsed.paperWidth) || GLOBAL_DEFAULT.paperWidth, 20, 500),
        paperHeight: clamp(Number(parsed.paperHeight) || GLOBAL_DEFAULT.paperHeight, 20, 500),
        scale: clamp(Number(parsed.scale) || GLOBAL_DEFAULT.scale, 0.3, 3.0),
        barcodeWidth: clamp(Number(parsed.barcodeWidth) || GLOBAL_DEFAULT.barcodeWidth, 0.8, 4.0),
        barcodeHeight: clamp(Number(parsed.barcodeHeight) || GLOBAL_DEFAULT.barcodeHeight, 10, 120),
        labelFontSize: clamp(Number(parsed.labelFontSize) || GLOBAL_DEFAULT.labelFontSize, 6, 24),
        margin: clamp(Number(parsed.margin) ?? GLOBAL_DEFAULT.margin, 0, 50),
        border: (parsed.border === 1 || parsed.border === 0) ? parsed.border : GLOBAL_DEFAULT.border,
      }
    }
  } catch {}
  return { ...GLOBAL_DEFAULT }
}

/** 重置某场景为默认值 */
export function resetPrintConfig(scene: PrintSceneKey | 'default') {
  localStorage.removeItem(lsKey(scene))
}

/** 按预设更新 paperWidth / paperHeight, 不修改其他字段 */
export function applyPaperPreset(cfg: PrintConfig, presetKey: string): PrintConfig {
  const preset = PAPER_PRESETS.find(p => p.key === presetKey)
  if (!preset || presetKey === 'CUSTOM') return { ...cfg, paperSizePreset: presetKey }
  return {
    ...cfg,
    paperSizePreset: presetKey,
    paperWidth: preset.width,
    paperHeight: preset.height,
  }
}

/**
 * 根据配置生成 @page + body + label 的 CSS 片段。
 * 核心: 使用 mm 单位而非 px, 并通过 transform: scale(${cfg.scale}) 实现统一缩放。
 *
 * 调用方在打印 HTML 的 <style> 块中嵌入返回值即可。
 *
 * @param cfg 打印配置
 * @param labelSelector 标签根元素选择器 (默认 .barcode-label, .plate-label 等)
 */
export function buildPrintCss(cfg: PrintConfig, labelSelector: string = '.barcode-label') {
  const { paperWidth, paperHeight, margin, scale, labelFontSize, border, extraCss } = cfg
  return `
  @page {
    size: ${paperWidth}mm ${paperHeight}mm;
    margin: ${margin}mm;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: ${paperWidth}mm;
    height: auto;
    min-height: ${paperHeight}mm;
  }
  body {
    font-family: "Microsoft YaHei", "SimSun", sans-serif;
    font-size: ${labelFontSize}px;
    padding: 0;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .print-scale-wrap {
    /* 打印缩放: 在每一页根元素上应用 */
    width: ${paperWidth - margin * 2}mm;
    transform-origin: top left;
    transform: scale(${scale});
    margin: 0 auto;
  }
  ${labelSelector} {
    width: ${paperWidth - margin * 2}mm;
    min-height: ${paperHeight - margin * 2}mm;
    ${border ? 'border: 1px solid #333;' : 'border: none;'}
    padding: 3mm 4mm;
    margin: 0 auto;
    page-break-after: always;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    font-size: ${labelFontSize}px;
    line-height: 1.45;
  }
  ${labelSelector}:last-child {
    page-break-after: auto;
  }
  @media print {
    body { padding: 0; }
    ${labelSelector} { margin: 0; }
    .no-print { display: none !important; }
  }
  ${extraCss || ''}
`
}

/**
 * 生成 JsBarcode 参数对象。
 * 统一将 cfg.barcodeWidth / cfg.barcodeHeight 映射为 JsBarcode 选项。
 */
export function buildJsBarcodeOptions(cfg: PrintConfig, extra?: Record<string, any>) {
  return {
    format: 'CODE128',
    width: cfg.barcodeWidth,
    height: cfg.barcodeHeight,
    displayValue: false,
    margin: 0,
    background: '#ffffff',
    lineColor: '#000000',
    ...(extra || {}),
  }
}
