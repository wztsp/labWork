// 验证培养基条码核收 + 微生物报告列表
import http from 'node:http'
const BASE = 'localhost', PORT = 8080

function req(path, { method = 'GET', body = null, token = null, qs = '' } = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? Buffer.from(JSON.stringify(body), 'utf8') : null
    const q = qs ? (path.includes('?') ? '&' : '?') + qs : ''
    const opts = {
      hostname: BASE, port: PORT, path: '/api' + path + q, method,
      headers: {
        ...(data ? { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': data.length } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      }
    }
    const rq = http.request(opts, (res) => {
      const chunks = []
      res.on('data', (c) => chunks.push(c))
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8')
        try { resolve({ status: res.statusCode, data: raw ? JSON.parse(raw) : null }) }
        catch (e) { resolve({ status: res.statusCode, data: raw }) }
      })
    })
    rq.on('error', reject)
    if (data) rq.write(data)
    rq.end()
  })
}

function unwrapOne(x) {
  if (x && typeof x === 'object' && 'data' in x) return x.data
  return x
}

// 从模拟脚本输出中提取的14个培养基条码（每个患者取第一个条码核收即可，因为一个标本只创建一个报告）
const FIRST_PER_PATIENT = [
  { pt: '张三', barcode: 'L20260824125342601714' },
  { pt: '李四', barcode: 'L20260824125342659624' },
  { pt: '王五', barcode: 'L20260824125342733630' },
  { pt: '赵六', barcode: 'L20260824125342823172' },
  { pt: '孙七', barcode: 'L20260824125342899529' },
]

function short(x, n=320) { return JSON.stringify(x).slice(0,n) }

;(async () => {
  const login = await req('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  const token = (unwrapOne(login.data)||{}).token || (login.data||{}).token
  console.log(`[login] HTTP ${login.status} token=${token?'OK':'FAIL'}`)
  const T = { token }

  console.log('\n===== 5位患者培养基条码核收 =====')
  const results = []
  for (const { pt, barcode } of FIRST_PER_PATIENT) {
    const enc = encodeURIComponent(barcode)
    const r = await req('/report/micro/receive-by-plate', { method: 'POST', qs: `plateBarcode=${enc}`, ...T })
    const ok = r.data && (r.data.code === 200 || r.data.success === true) && r.data && r.data.data
    const data = (r.data && r.data.data) || {}
    const line = {
      患者: pt,
      培养基条码: barcode,
      HTTP: r.status,
      核收成功: ok ? '是' : '否',
      报告号: data.reportNo || '',
      报告ID: data.id || '',
      患者名: data.patientName || '',
      标本条码: data.sampleBarcode || '',
      状态: data.reportStatusText || (r.data && r.data.message) || ''
    }
    results.push(line)
    console.log(`  ${pt}  ${barcode}  HTTP ${r.status}  ->  ${ok ? '✔' : '✗ ' + (r.data && r.data.message)}  report=${data.reportNo||''} pt=${data.patientName||''} sample=${data.sampleBarcode||''}`)
  }
  console.log('\n【核收结果汇总】')
  console.table(results)

  console.log('\n===== 按今天时间筛选查询微生物报告列表 =====')
  const today = new Date().toISOString().slice(0,10)
  const page = await req('/report/micro/page', { ...T, qs: `current=1&size=50&beginDate=${today}&endDate=${today}` })
  const d1 = unwrapOne(page.data) || {}
  const list = d1.records || []
  console.log(`  今天共有 ${list.length} 条微生物报告 (按begin/end筛选)`)
  console.table(list.map(r => ({
    报告ID: r.id,
    报告号: r.reportNo,
    患者: r.patientName,
    标本类型: r.specimenType,
    标本条码: r.sampleBarcode,
    报告状态: r.reportStatusText + '(' + r.reportStatus + ')',
    创建时间: (r.createTime||'').slice(0,19)
  })))
})()
