// 调试核收接口 - 通过8080单体直连
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
        try { resolve({ status: res.statusCode, data: raw ? JSON.parse(raw) : null, allow: res.headers['allow'] }) }
        catch (e) { resolve({ status: res.statusCode, data: raw, allow: res.headers['allow'] }) }
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
function short(x, n=260) { return JSON.stringify(x).slice(0,n) }

;(async () => {
  const login = await req('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  const token = (unwrapOne(login.data)||{}).token || (login.data||{}).token
  console.log(`[login] HTTP ${login.status} token=${token?'OK':'FAIL'} msg=${short(login.data,120)}`)
  const T = { token }

  // 先用一个已知可用的接口确认路由可达
  console.log('\n===== 对照：GET page =====')
  const rp = await req('/report/micro/page', { ...T, qs:'current=1&size=1' })
  console.log(`  GET page HTTP ${rp.status}  -> ${short(rp.data, 200)}`)

  const TEST_BC = 'L20260824123540747811'
  const enc = encodeURIComponent(TEST_BC)

  console.log('\n===== 测试 receive-by-plate 多种调用格式 =====')
  const cases = [
    { m:'POST', path:'/report/micro/receive-by-plate', qs:`plateBarcode=${enc}`, body:null, desc:'POST + query plateBarcode' },
    { m:'POST', path:'/report/micro/receive-by-plate', qs:'', body:{ plateBarcode: TEST_BC }, desc:'POST + JSON body' },
    { m:'GET',  path:'/report/micro/receive-by-plate', qs:`plateBarcode=${enc}`, body:null, desc:'GET + query' },
    { m:'PUT',  path:'/report/micro/receive-by-plate', qs:`plateBarcode=${enc}`, body:null, desc:'PUT + query' },
  ]
  for (const c of cases) {
    const r = await req(c.path, { method: c.m, body: c.body, qs: c.qs, ...T })
    console.log(`  [${c.desc}] HTTP ${r.status}${r.allow?' Allow:'+r.allow:''} -> ${short(r.data)}`)
  }

  // 也试试不带前缀 /report （看是否有历史路由）
  console.log('\n===== 对照：尝试无 report 前缀 =====')
  const rp2 = await req('/micro/receive-by-plate', { method:'POST', qs:`plateBarcode=${enc}`, ...T })
  console.log(`  POST /api/micro/receive-by-plate  HTTP ${rp2.status} -> ${short(rp2.data)}`)
})()
