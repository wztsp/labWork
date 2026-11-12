/**
 * 移除 HTML 中的脚本与危险内容, 防止打印窗口 XSS
 * - 删除 <script> 及其内容
 * - 删除危险标签 (script/iframe/object/embed/base/link/form/input/button/style/meta)
 * - 删除所有 on* 事件属性
 * - 删除 javascript: 协议的 href/src
 * - 其余结构 (表格/图片等) 原样保留
 *
 * @param html 待消毒的 HTML 字符串
 * @returns 消毒后的安全 HTML
 */
export function sanitizeHtml(html: string): string {
  if (!html) return ''
  if (typeof DOMParser === 'undefined') return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')

  const dangerousTags = [
    'script', 'iframe', 'object', 'embed', 'base', 'link',
    'form', 'input', 'button', 'style', 'meta'
  ]
  dangerousTags.forEach((tag) => {
    doc.querySelectorAll(tag).forEach((el) => el.remove())
  })

  doc.querySelectorAll('*').forEach((el) => {
    Array.from(el.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase()
      const value = attr.value
      if (name.startsWith('on')) {
        el.removeAttribute(attr.name)
      } else if (
        (name === 'href' || name === 'src' || name === 'xlink:href') &&
        /^\s*javascript:/i.test(value)
      ) {
        el.removeAttribute(attr.name)
      }
    })
  })

  return doc.body.innerHTML
}

/**
 * 对单个动态值做 HTML 转义, 防止注入 HTML/脚本
 * 用于模板字符串中拼接的字段值 (如 ${escapeHtml(d.itemName)})
 */
export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}