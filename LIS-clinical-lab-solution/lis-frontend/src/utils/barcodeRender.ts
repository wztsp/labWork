/**
 * 条码 / 二维码本地渲染工具
 *
 * 关键约束：打印 HTML 会在 iframe / 新窗口中独立加载，无法访问父窗口的 JS 状态，
 * 也不能依赖 CDN（医院内网/离线部署环境下外链脚本必然加载失败）。
 * 因此这里在父窗口内把条码渲染成 SVG 字符串，再由模板渲染器内联进打印 HTML。
 */
import JsBarcode from 'jsbarcode'
import qrcode from 'qrcode-generator'

/** 1mm ≈ 3.7795px（96dpi） */
export const MM_TO_PX = 3.7795275591

const SVG_NS = 'http://www.w3.org/2000/svg'

/**
 * 估算 CODE128 的模块数，用于反推条宽，保证生成的条码宽度不超出标签区域。
 * CODE128：起始符 11 + 校验符 11 + 终止符 13，每个数据字符 11 个模块。
 */
function estimateModules(value: string): number {
  const len = Math.max(1, value.length)
  return 11 * len + 35
}

/**
 * 生成 CODE128 条码 SVG 字符串。
 *
 * @param value   条码值（为空时返回空串）
 * @param widthMm 可用宽度（mm）
 * @param heightMm 可用高度（mm）
 * @param showText 是否在条码下方显示可读文本
 */
export function renderBarcodeSvg(
  value: string,
  widthMm: number,
  heightMm: number,
  showText = true
): string {
  const text = (value || '').trim()
  if (!text) return ''
  try {
    const svg = document.createElementNS(SVG_NS, 'svg')
    const boxWidth = Math.max(10, widthMm * MM_TO_PX)
    const modules = estimateModules(text)
    // 条宽取 0.05px 的整数倍并向下取整，确保总宽不超出容器
    const barWidth = Math.max(0.6, Math.floor((boxWidth / modules) * 20) / 20)
    const height = Math.max(16, heightMm * MM_TO_PX - (showText ? 13 : 0))
    JsBarcode(svg, text, {
      format: 'CODE128',
      width: barWidth,
      height,
      displayValue: showText,
      fontSize: 12,
      textMargin: 1,
      margin: 0,
      background: '#ffffff',
      lineColor: '#000000'
    })
    return svg.outerHTML
  } catch (_e) {
    // 值不符合 CODE128 编码规则（如含非法字符）时静默降级，避免打印整体失败
    return ''
  }
}

/**
 * 生成二维码 SVG 字符串（纠错级别 M，自动选择版本）。
 * 使用 viewBox + preserveAspectRatio 保证缩放到任意尺寸都不失真。
 */
export function renderQrSvg(value: string): string {
  const text = (value || '').trim()
  if (!text) return ''
  try {
    const qr = qrcode(0, 'M')
    qr.addData(text)
    qr.make()
    const count = qr.getModuleCount()
    let rects = ''
    for (let row = 0; row < count; row++) {
      for (let col = 0; col < count; col++) {
        if (qr.isDark(row, col)) {
          rects += `<rect x="${col}" y="${row}" width="1" height="1" fill="#000000"/>`
        }
      }
    }
    return (
      `<svg xmlns="${SVG_NS}" viewBox="0 0 ${count} ${count}" ` +
      `preserveAspectRatio="xMidYMid meet" shape-rendering="crispEdges">` +
      `<rect x="0" y="0" width="${count}" height="${count}" fill="#ffffff"/>${rects}</svg>`
    )
  } catch (_e) {
    return ''
  }
}
