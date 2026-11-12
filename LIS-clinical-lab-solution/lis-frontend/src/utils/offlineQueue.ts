/**
 * 离线暂存队列（T-17 / R-12）
 *
 * <p>短时弱网场景（冰箱间、库房角落）下，扫码作业提交失败时把数据暂存到浏览器；
 * 恢复网络后由用户手动触发「一键提交」，服务端按幂等键去重，避免重复入库。</p>
 *
 * <p>幂等键 = {@code idempotencyKey}（如 {@code checkId + itemId} 或 {@code reqNo + itemId}），
 * 同一键只入队一次。</p>
 *
 * <p>【安全】队列数据含患者/检验信息，存储前使用 Web Crypto AES-GCM 加密；
 * 加密密钥仅存放于 <b>sessionStorage</b>（随标签页关闭即失效，不落盘），
 * 避免医疗数据以明文持久化到 localStorage。若密钥缺失（跨标签页/重启），
 * 视为暂存已过期并自动清空，不暴露明文。</p>
 */

const STORAGE_KEY = 'lis_offline_requisition_queue'
const KEY_STORAGE = 'lis_offline_requisition_key'

interface QueueItem<T = any> {
  id: string // 本地唯一 id（时间戳 + 随机）
  idempotencyKey: string // 幂等键，同一键只入队一次
  payload: T // 业务数据
  createTime: number // 入队时间戳
}

/** 加密密钥（AES-GCM），仅存于 sessionStorage */
async function getOrCreateKey(): Promise<CryptoKey | null> {
  try {
    let raw = sessionStorage.getItem(KEY_STORAGE)
    if (raw) {
      const bytes = Uint8Array.from(atob(raw), (c) => c.charCodeAt(0))
      return await crypto.subtle.importKey('raw', bytes, 'AES-GCM', true, ['encrypt', 'decrypt'])
    }
    const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
    const exported = new Uint8Array(await crypto.subtle.exportKey('raw', key))
    sessionStorage.setItem(KEY_STORAGE, btoa(String.fromCharCode(...exported)))
    return key
  } catch (e) {
    return null
  }
}

async function encrypt(key: CryptoKey, plaintext: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const data = new TextEncoder().encode(plaintext)
  const cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, data)
  const out = new Uint8Array(iv.length + cipher.byteLength)
  out.set(iv, 0)
  out.set(new Uint8Array(cipher), iv.length)
  return btoa(String.fromCharCode(...out))
}

async function decrypt(key: CryptoKey, blob: string): Promise<string> {
  const bytes = Uint8Array.from(atob(blob), (c) => c.charCodeAt(0))
  const iv = bytes.slice(0, 12)
  const cipher = bytes.slice(12)
  const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, cipher)
  return new TextDecoder().decode(plain)
}

async function readAll<T>(): Promise<QueueItem<T>[]> {
  try {
    const key = await getOrCreateKey()
    if (!key) {
      clearQueue()
      return []
    }
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(await decrypt(key, raw))
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

async function writeAll<T>(items: QueueItem<T>[]): Promise<void> {
  try {
    const key = await getOrCreateKey()
    if (!key) return
    localStorage.setItem(STORAGE_KEY, await encrypt(key, JSON.stringify(items)))
  } catch (e) {
    // localStorage 满或不可用时静默失败，不影响主流程
  }
}

/** 入队；若同一幂等键已存在则忽略（幂等） */
export async function enqueue<T>(idempotencyKey: string, payload: T): Promise<boolean> {
  const items = await readAll<T>()
  if (items.some((it) => it.idempotencyKey === idempotencyKey)) {
    return false
  }
  items.push({
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    idempotencyKey,
    payload,
    createTime: Date.now()
  })
  await writeAll(items)
  return true
}

/** 出队：按数组顺序移除一个元素 */
export async function dequeue(id: string): Promise<void> {
  const items = await readAll()
  const next = items.filter((it) => it.id !== id)
  await writeAll(next)
}

/** 待上传条数 */
export async function pendingCount(): Promise<number> {
  return (await readAll()).length
}

/** 待上传明细（用于页面展示） */
export async function listPending<T>(): Promise<Array<QueueItem<T> & { error?: string }>> {
  return readAll<T>()
}

/**
 * 一键提交：按入队顺序逐条调用 submitFn，成功即出队，失败则停止并抛出。
 *
 * @param submitFn 单条提交函数，返回 Promise；成功 resolve，失败 reject(error)
 * @returns 成功提交条数
 */
export async function flush<T = any>(
  submitFn: (item: QueueItem<T>) => Promise<void>
): Promise<{ success: number; failed: number; error?: string }> {
  const items = await readAll<T>()
  let success = 0
  let failed = 0
  let lastError: string | undefined
  for (const item of items) {
    try {
      await submitFn(item)
      await dequeue(item.id)
      success++
    } catch (e: any) {
      failed++
      lastError = e?.message || String(e)
      break // 失败即停，避免重复提交同一批
    }
  }
  return { success, failed, error: lastError }
}

/** 清空队列（谨慎使用） */
export function clearQueue(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) {
    // ignore
  }
}