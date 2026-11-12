import http from 'node:http'
const BASE='localhost', PORT=8080

function req(path, { method='GET', body=null, token=null, qs='' } = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? Buffer.from(JSON.stringify(body), 'utf8') : null
    const q = qs ? (path.includes('?')?'&':'?') + qs : ''
    const opts = {
      hostname: BASE, port: PORT, path: '/api'+path+q, method,
      headers: {
        ...(data ? { 'Content-Type':'application/json; charset=utf-8','Content-Length':data.length }:{}),
        ...(token ? { Authorization:'Bearer '+token } : {})
      }
    }
    const rq = http.request(opts, (res) => {
      const c=[];res.on('data',x=>c.push(x));res.on('end',()=>{
        const raw=Buffer.concat(c).toString(); try{resolve({status:res.statusCode,data:raw?JSON.parse(raw):null})}catch(e){resolve({status:res.statusCode,data:raw})}
      })
    })
    rq.on('error', reject); if(data) rq.write(data); rq.end()
  })
}
function uo(x){ if(x && typeof x==='object' && 'data' in x) return x.data; return x }

const PLATES = [
  'L20260824125342601714', // 张三
  'L20260824125342659624', // 李四
  'L20260824125342733630', // 王五
  'L20260824125342823172', // 赵六
  'L20260824125342899529', // 孙七
]

;(async()=>{
  const lr = await req('/auth/login', { method:'POST', body:{username:'admin',password:'123456'} })
  const token = (uo(lr.data)||{}).token || (lr.data||{}).token
  console.log('login', token?'OK':'FAIL')
  const T = { token }

  // 1) 先查 lis_sample 表中 sample_barcode 对应标本 (直接用 by-sample 接口看 sampleId 对不对得上)
  // 用 by-barcode 查第一个 plate 看 sample 信息
  console.log('\n===== A. 用 by-barcode 接口查培养基条码看 sampleId/sampleBarcode =====')
  for (const bc of PLATES.slice(0,1)) {
    // by-barcode 接口是 sampleBarcode ，plate 无独立 by-plate 接口
    // 只能通过 SQL 或 by-sample 了，先直接调 SQL（如果有），或者直接用已知 sampleBarcode 找 sample
  }

  // 2) 直接打印后端 SQL：查 lis_sample_plate 是否按 plate_barcode=? 能查到 (在 receiveByPlateBarcode 中)
  // 为了对比，做一件事：用 SQL 控制台查 (如果没有的话，打印 sample-plate 接口中的 by-sample 返回)
  // 通过样本条码查询 sample 信息 (调用 /sample/page 接口)，对比 receiveByPlateBarcode 里 JOIN 的 s.id = sp.sample_id
  const SAMPLE_BC = ['260824000026','260824000027','260824000028','260824000029','260824000030']
  console.log('\n===== B. 用 /sample/page 按 sampleBarcode 查标本 (确认 sample.id 对得上 JOIN) =====')
  for (const sb of SAMPLE_BC) {
    const r = await req('/sample/page', { ...T, qs:`current=1&size=1&sampleBarcode=${encodeURIComponent(sb)}` })
    const d = uo(r.data) || {}
    const l = d.records || []
    l.forEach(s => console.log(`  sampleBarcode=${s.sampleBarcode}  id=${s.id}  specimenType=${s.specimenType}  patientName=${s.patientName}  patientId=${s.patientId}  cultureSite=${s.cultureSite||''}`))
  }

  // 3) 用 by-sample 查 (但它是按 sampleId，我们现在先不用)
  // 4) 直接测试核收接口，打印更详细的错误：
  console.log('\n===== C. 测试核收接口并返回完整 trace =====')
  for (const bc of PLATES) {
    // 模拟 receiveByPlateBarcode 中的 SQL 用 jdbcTemplate：我们改用 /micro/sample-plate/by-sample 接口需要 sampleId
    // 先 by-barcode (sample) 拿 sampleId，再 by-sample 拿 plates，再核收 plates 中的第一个 plateBarcode
    // 但我们已经知道 plateBarcode 了，直接核收
    const r = await req('/report/micro/receive-by-plate', { method:'POST', ...T, qs:`plateBarcode=${encodeURIComponent(bc)}` })
    const ok = r.data && (r.data.code===200 || r.data.success===true) && r.data.data
    const msg = r.data && (r.data.message || r.data.msg) || ''
    const trace = r.data && r.data.trace || ''
    console.log(`  ${bc}  HTTP ${r.status}  ok=${ok}  msg=${msg.slice(0,300)}  ${trace?'trace='+trace.slice(0,500):''}`)
  }
})()
