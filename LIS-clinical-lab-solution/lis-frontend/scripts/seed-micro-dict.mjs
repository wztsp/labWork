// 本地种子脚本：解析 docs/database/seed/{02-bacteria,03-antibiotics}.sql
// 通过后端 /api/micro/* 接口（免鉴权）增量写入：
//   - 细菌字典（按 bacteria_code 幂等）
//   - 抗生素字典（按 antibiotic_code 幂等）
//   - CLSI 折点（按 antibioticId+bacteriaId+method 幂等）
// 用法：
//   cd LIS-clinical-lab-solution\lis-frontend
//   node scripts\seed-micro-dict.mjs  [--api http://localhost:8080]
import { readFileSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..', '..')
const SEED_DIR = join(ROOT, 'lis-system', 'docs', 'database', 'seed')

const API = process.argv.includes('--api')
  ? process.argv[process.argv.indexOf('--api') + 1]
  : 'http://localhost:8080'

async function request(path, { method = 'GET', body } = {}) {
  const url = API + path
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json; charset=UTF-8', Accept: 'application/json' }
  }
  if (body != null) opts.body = JSON.stringify(body)
  const res = await fetch(url, opts)
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch { data = { raw: text } }
  if (!res.ok || (data && typeof data === 'object' && data.code && data.code !== 200)) {
    throw new Error(`HTTP ${res.status} ${method} ${path} -> ${text.slice(0, 300)}`)
  }
  return data
}

// -------- 解析 SQL --------
// 支持两种格式：
//   A) INSERT INTO ... VALUES (...),(...),... ON CONFLICT ...
//   B) INSERT INTO ... SELECT * FROM (VALUES (...),(...),... AS v(...)) ...
function extractValues(sql) {
  // 找到第一个 VALUES 之后的大段内容，取到 ON CONFLICT / ; / AS v( 结束之前
  const vi = sql.search(/VALUES\s/i)
  if (vi < 0) return []
  let tail = sql.slice(vi)
  // 去掉开头的 VALUES 关键字
  tail = tail.replace(/^VALUES\s*/i, '')
  // 可能开头紧跟 '('
  // 找到闭合边界：下一个顶级 'ON CONFLICT' / 结束括号 + AS v / 末尾分号
  // 策略：从左开始匹配括号深度，记录每个 tuple 段
  const tuples = []
  let depth = 0, start = -1, buf = ''
  for (let i = 0; i < tail.length; i++) {
    const c = tail[i]
    if (c === "'") {
      // 跳过字符串字面量（支持 ''）
      buf += c
      i++
      while (i < tail.length) {
        const ch = tail[i]
        buf += ch
        if (ch === "'") {
          if (tail[i + 1] === "'") { buf += "'"; i++ }
          else break
        }
        i++
      }
      continue
    }
    if (c === '(') {
      depth++
      if (depth === 1) { start = buf.length; buf += '' } // 标记 start
      else buf += c
      continue
    }
    if (c === ')') {
      depth--
      if (depth === 0) {
        // 一个 tuple 结束：使用 start..buf.length 的 slice（其实我们一直没把 ( 和 ) 放进 buf）
        // 所以直接整个 buf 就是一个 tuple 内容
        tuples.push(buf.trim())
        buf = ''
        // 跳过 ) 之后直到 ( 或 结束 的分隔符
        i++
        while (i < tail.length && /\s|,/.test(tail[i])) i++
        if (i >= tail.length) break
        const rest = tail.slice(i)
        // 如果之后的前缀是 'ON' 或 'AS' 或 ';' 或 'WHERE' → 结束
        if (/^(ON\b|AS\b|;|WHERE|FROM|JOIN)/i.test(rest)) break
        // 否则回退一个字符（因为 for 循环会 i++）
        i--
        continue
      } else {
        buf += c
        continue
      }
    }
    buf += c
  }
  return tuples.filter(t => t.length > 0)
}

function splitTopLevel(s) {
  // 按顶层逗号切分，忽略字符串里的逗号
  const out = []
  let cur = '', inStr = false, q = ''
  for (let i = 0; i < s.length; i++) {
    const c = s[i]
    if (inStr) {
      cur += c
      if (c === q && s[i - 1] !== '\\') inStr = false
      continue
    }
    if (c === "'" || c === '"') { inStr = true; q = c; cur += c; continue }
    if (c === ',') { out.push(cur.trim()); cur = ''; continue }
    cur += c
  }
  if (cur.trim()) out.push(cur.trim())
  return out
}

function unquote(s) {
  if (s === 'NULL' || s === 'null') return null
  if (/^'.*'$/s.test(s)) return s.slice(1, -1).replace(/''/g, "'")
  return s
}

// -------- SQL 特殊：折点用 VALUES 表 JOIN，且以 '−'(U+2212) 表示不适用 --------
const MINUS = '\u2212'
function dashToNull(v) { return v === MINUS || v === '-' || v == null ? null : v }

// -------- 主流程：定位 SQL 中每个 INSERT 语句的独立片段 --------
function firstInsert(sql, tableName) {
  // 返回以 `INSERT INTO <tableName>` 开头直到末尾分号或下一个 INSERT/END/的片段
  const re = new RegExp(`INSERT\\s+INTO\\s+${tableName}\\b[\\s\\S]*?(?=\\nINSERT\\s+INTO|$)`, 'i')
  const m = sql.match(re)
  return m ? m[0] : sql
}

const bacSqlFull = readFileSync(join(SEED_DIR, '02-bacteria.sql'), 'utf8')
const abxSqlFull = readFileSync(join(SEED_DIR, '03-antibiotics.sql'), 'utf8')

const bacInsert = firstInsert(bacSqlFull, 'lis_bacteria')
const abxInsert = firstInsert(abxSqlFull, 'lis_antibiotic')
const bpInsert  = firstInsert(abxSqlFull, 'lis_antibiotic_breakpoint')

const bacTuples = extractValues(bacInsert)
const abxTuples = extractValues(abxInsert)
const bpTuples  = extractValues(bpInsert)
console.log(`[解析] 细菌 tuples=${bacTuples.length}, 抗生素 tuples=${abxTuples.length}, 折点 tuples=${bpTuples.length}`)

// -------- 拉取现有字典（按 code 映射 id） --------
const existingBac = await request('/api/micro/bacteria/all')
const existingAbx = await request('/api/micro/antibiotics/all')
const bacById = new Map(existingBac.data.map(b => [String(b.id), b]))
const bacByCode = new Map(existingBac.data.map(b => [b.bacteriaCode, b]))
const abxById = new Map(existingAbx.data.map(a => [String(a.id), a]))
const abxByCode = new Map(existingAbx.data.map(a => [a.antibioticCode, a]))
console.log(`[现状] 细菌 ${bacByCode.size}, 抗生素 ${abxByCode.size}`)

// -------- 写入细菌字典 --------
let bacCreated = 0, bacSkipped = 0
for (const t of bacTuples) {
  const cols = splitTopLevel(t).map(unquote)
  if (cols.length < 7) continue
  const [code, name, latin, gram, cat, morph, common] = cols
  if (!code || !name) continue
  if (bacByCode.has(code)) { bacSkipped++; continue }
  const payload = {
    bacteriaCode: code, bacteriaName: name, bacteriaNameLatin: latin || null,
    gramType: gram || null, bacteriaCategory: cat || null, morphology: morph || null,
    isCommon: common == null ? 0 : Number(common), status: 1
  }
  try {
    const r = await request('/api/micro/bacteria', { method: 'POST', body: payload })
    bacByCode.set(code, r.data)
    bacById.set(String(r.data.id), r.data)
    bacCreated++
  } catch (e) {
    console.error(`  细菌写入失败 ${code} ${name}:`, e.message)
  }
}
console.log(`[细菌] 新增 ${bacCreated}, 跳过 ${bacSkipped}`)

// -------- 写入抗生素字典 --------
let abxCreated = 0, abxSkipped = 0
for (const t of abxTuples) {
  const cols = splitTopLevel(t).map(unquote)
  if (cols.length < 3) continue
  const [code, name, category] = cols
  if (!code || !name) continue
  if (abxByCode.has(code)) { abxSkipped++; continue }
  const payload = { antibioticCode: code, antibioticName: name, category: category || null, status: 1 }
  try {
    const r = await request('/api/micro/antibiotics', { method: 'POST', body: payload })
    abxByCode.set(code, r.data)
    abxById.set(String(r.data.id), r.data)
    abxCreated++
  } catch (e) {
    console.error(`  抗生素写入失败 ${code} ${name}:`, e.message)
  }
}
console.log(`[抗生素] 新增 ${abxCreated}, 跳过 ${abxSkipped}`)

// -------- 写入折点（按 abId+bacId+method 去重） --------
const existingBp = await request('/api/micro/breakpoints/all')
// index: `${abId}_${bacId}_${method}`
const bpKey = new Set()
for (const b of existingBp.data) {
  bpKey.add(`${b.antibioticId}_${b.bacteriaId}_${b.method || ''}`)
}
let bpCreated = 0, bpSkipped = 0, bpMissed = 0
for (const t of bpTuples) {
  // (bacteria, ab, method, s, r, i, sdd, rdd)
  const cols = splitTopLevel(t).map(unquote)
  if (cols.length < 8) { bpMissed++; continue }
  const [bacCode, abCode, method, s, r, i, sdd, rdd] = cols
  const bac = bacByCode.get(bacCode)
  const abx = abxByCode.get(abCode)
  if (!bac || !abx) { console.warn(`  折点缺字典: 细菌=${bacCode} 抗生素=${abCode}`); bpMissed++; continue }
  const key = `${abx.id}_${bac.id}_${method || ''}`
  if (bpKey.has(key)) { bpSkipped++; continue }
  const payload = {
    antibioticId: Number(abx.id), bacteriaId: Number(bac.id),
    method: method || null, standard: 'CLSI', standardVersion: 'M100',
    sBreakpoint: dashToNull(s), rBreakpoint: dashToNull(r), iBreakpoint: dashToNull(i),
    sDd: dashToNull(sdd), rDd: dashToNull(rdd), status: 1
  }
  try {
    await request('/api/micro/breakpoints', { method: 'POST', body: payload })
    bpKey.add(key)
    bpCreated++
  } catch (e) {
    console.error(`  折点写入失败 ${bacCode}/${abCode}/${method}:`, e.message)
  }
}
console.log(`[折点] 新增 ${bpCreated}, 跳过 ${bpSkipped}, 丢失/跳过 ${bpMissed}`)

// -------- 清理刚才为了测试写入的临时菌 --------
try {
  const testBac = bacByCode.get('ZZZTEST')
  if (testBac) {
    await request(`/api/micro/bacteria/${testBac.id}`, { method: 'DELETE' })
    console.log(`[清理] 已删除 ZZZTEST`)
  }
} catch (e) {
  console.warn('[清理] 删除 ZZZTEST 失败:', e.message)
}

// -------- 汇总验证 --------
const bacCount = (await request('/api/micro/bacteria/all')).data.length
const abxCount = (await request('/api/micro/antibiotics/all')).data.length
const bpPage = await request('/api/micro/breakpoints?current=1&size=1')
console.log(`[完成] 细菌字典 ${bacCount}, 抗生素字典 ${abxCount}, 折点 ${bpPage.data.total}`)
