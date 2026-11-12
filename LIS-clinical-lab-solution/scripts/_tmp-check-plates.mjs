import http from 'node:http'
const BASE='localhost', PORT=8080

function req(path, { method='GET', body=null, token=null, rawPath=false } = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? Buffer.from(JSON.stringify(body), 'utf8') : null
    const fullPath = rawPath ? path : '/api' + path
    const opts = {
      hostname: BASE, port: PORT, path: fullPath, method,
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

const SAMPLES = ['260824000026','260824000027','260824000028','260824000029','260824000030']

;(async()=>{
  const lr = await req('/auth/login', { method:'POST', body:{username:'admin',password:'123456'} })
  const token = (uo(lr.data)||{}).token || (lr.data||{}).token
  console.log('login', token?'OK':'FAIL')
  const T = { token }
  for (const sb of SAMPLES) {
    const r = await req(`/micro/sample-plate/by-barcode/${encodeURIComponent(sb)}`, T)
    const list = uo(r.data) || []
    console.log(`\n[${sb}] HTTP ${r.status} plates=${list.length}`)
    list.forEach(p => console.log(`  plateBarcode=${p.plateBarcode}  sampleId=${p.sampleId}  sampleBarcode=${p.sampleBarcode}  medium=${p.mediumTypeName}  status=${p.status}`))
  }
})()
