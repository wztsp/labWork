/**
 * 统一打印窗口工具 (P2-6)
 *
 * 所有打印场景统一走此工具:
 *  - 静默打印 (默认): 把 HTML 写入隐藏 <iframe> 后触发 iframe.contentWindow.print(),
 *    用户无需再点"打印"对话框里的按钮(仍会弹出浏览器打印对话, 但在已配置
 *    "--kiosk-printing" 的环境下会直接把任务发往默认打印机, 实现真正静默);
 *  - 新窗口打印: 通过 mode='window' 走 window.open + document.write,
 *    兼容必须要"另存为/选择打印机"的人工交互场景。
 *
 * 注意: 新窗口 document.write 写入的页面 window.onload 永远不会触发(因为写入是同步的),
 * 旧实现把"触发打印"写在嵌入 <script>window.onload=...</script> 里就会完全没反应,
 * 统一改在当前文档侧驱动打印时序。
 *
 * XSS 防护约定: 本函数写入的是"可信模板"本身, 动态值(患者姓名、检验结果等)
 * 必须在调用方拼入模板前先经 sanitizeHtml / escapeHtml 处理, 否则本函数不做整体消毒
 * (整体消毒会误删模板必需的 <style>/<script>, 破坏打印样式与条码渲染)。
 *
 * @param html 待打印的完整 HTML 字符串 (含 <!DOCTYPE html>...)
 * @param opts 可选配置
 * @returns 打印载体: mode=iframe 返回 HTMLIFrameElement, mode=window 返回 Window|null
 */
export function openPrintWindow(
  html: string,
  opts?: {
    width?: number
    height?: number
    autoPrint?: boolean
    mode?: 'iframe' | 'window'
    printerName?: string
  }
): Window | HTMLIFrameElement | null {
  const mode = opts?.mode || 'iframe'
  const autoPrint = opts?.autoPrint !== false

  // ============== 静默打印 (iframe 模式) — 默认 ==============
  if (mode === 'iframe') {
    // 复用或创建 iframe
    let iframe = document.getElementById('__lis_silent_print_frame') as HTMLIFrameElement | null
    if (!iframe) {
      iframe = document.createElement('iframe')
      iframe.id = '__lis_silent_print_frame'
      // 关键: 让 iframe 实际"可渲染", 但不可见 — 用 position:fixed 移出可视区,
      // 不要用 display:none 或 visibility:hidden, 有些浏览器会跳过打印。
      iframe.style.cssText = [
        'position:fixed',
        'top:-10000px',
        'left:-10000px',
        'width:850px',
        'height:1200px',
        'border:0',
        'pointer-events:none',
        'opacity:0',
        'z-index:-1'
      ].join(';')
      iframe.setAttribute('aria-hidden', 'true')
      document.body.appendChild(iframe)
    }
    const doc = iframe.contentWindow?.document
    if (!doc) return iframe
    // 用 open/write/close 触发一次完整加载周期, 保证样式/字体已就绪
    doc.open()
    doc.write(html)
    doc.close()

    if (autoPrint) {
      const tryPrint = () => {
        try {
          // 如果部署时通过命令行参数启用了 --kiosk-printing, print() 会直接发往默认打印机
          iframe!.contentWindow!.focus()
          iframe!.contentWindow!.print()
        } catch (_e) {
          // 兜底: 退化成新窗口打印
          fallbackWindowPrint(html, opts)
        }
      }
      // 等待 iframe 内图片/字体就绪, 避免空白页
      const win = iframe.contentWindow!
      const loaded = () => win.setTimeout(tryPrint, 250)
      if ((doc.readyState as string) === 'complete') {
        loaded()
      } else {
        win.addEventListener('load', loaded, { once: true })
        // 500ms 兜底, 避免 load 永不触发
        win.setTimeout(loaded, 1200)
      }
    }
    return iframe
  }

  // ============== 新窗口打印 ==============
  return fallbackWindowPrint(html, opts)
}

function fallbackWindowPrint(
  html: string,
  opts?: { width?: number; height?: number; autoPrint?: boolean }
): Window | null {
  const features =
    opts?.width && opts?.height
      ? `width=${opts.width},height=${opts.height}`
      : 'width=900,height=1200'
  const win = window.open('', '_blank', features)
  if (!win) {
    // 新窗口被拦截时, 把 HTML 写到当前 window 进行打印 — 最差也要有反应
    const doc = document.implementation.createHTMLDocument('打印')
    doc.documentElement.innerHTML = html
    try {
      const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const fallback = window.open(url, '_blank', features)
      if (fallback) {
        fallback.addEventListener('load', () => URL.revokeObjectURL(url), { once: true })
      }
      return fallback
    } catch {
      return null
    }
  }
  win.focus()
  win.document.open()
  win.document.write(html)
  win.document.close()
  if (opts?.autoPrint !== false) {
    // 注意: document.write 写入的页面 window.onload 不会触发,
    // 所以在父窗口 setTimeout 后触发打印。
    win.setTimeout(() => {
      try {
        win.focus()
        win.print()
      } catch (_e) {
        /* ignore */
      }
    }, 400)
  }
  return win
}

/**
 * 便捷封装: 触发一个"仅预览不立即打印"的窗口 (用于要人工再点打印)
 */
export function openPreviewWindow(html: string, opts?: { width?: number; height?: number }) {
  return openPrintWindow(html, { ...opts, mode: 'window', autoPrint: false })
}
