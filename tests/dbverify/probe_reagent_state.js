const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN='', USER_ID='';
function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (USER_ID) headers['X-User-Id'] = USER_ID;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x=''; res.on('data',c=>x+=c); res.on('end',()=>{
        try { resolve({status:res.statusCode, body:JSON.parse(x)}); }
        catch(e){ resolve({status:res.statusCode, body:{raw:x.slice(0,300)}}); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
async function login(username='admin', password='123456') {
  const r = await req('POST','/api/auth/login',{username,password});
  if (r.body && r.body.code===200) {
    TOKEN = r.body.data.token;
    try { const p=JSON.parse(Buffer.from(TOKEN.split('.')[1],'base64').toString()); USER_ID=String(p.sub); } catch(e){}
    return r.body.data;
  }
  return null;
}
(async () => {
  const user = await login();
  console.log('login:', user && user.username, user && user.realName, 'userId='+USER_ID);
  const probes = [
    ['supplier/page', '/api/reagent/supplier/page?current=1&size=5'],
    ['category/tree', '/api/reagent/category/tree'],
    ['warehouse/tree', '/api/reagent/warehouse/tree'],
    ['budget/page', '/api/reagent/budget/page?current=1&size=5'],
    ['reagent/dict', '/api/reagent/dict?current=1&size=3'],
    ['stock', '/api/reagent/stock?current=1&size=8'],
    ['purchase-apply', '/api/reagent/purchase-apply/page?current=1&size=5'],
    ['purchase-order', '/api/reagent/purchase-order/page?current=1&size=5'],
    ['requisition', '/api/reagent/requisition/page?current=1&size=5'],
    ['stock-check', '/api/reagent/stock-check?current=1&size=5'],
    ['supplier-evaluate/rank', '/api/reagent/supplier-evaluate/rank'],
    ['report/purchase-stat', '/api/reagent/report/purchase-stat?groupBy=supplier'],
    ['report/trend', '/api/reagent/report/trend?granularity=day'],
    ['report/stock-structure', '/api/reagent/report/stock-structure'],
    ['scrap-disposal/page', '/api/reagent/scrap-disposal/page?current=1&size=5'],
  ];
  for (const [name, path] of probes) {
    try {
      const r = await req('GET', path);
      const d = r.body && r.body.data;
      let summary;
      if (Array.isArray(d)) summary = 'array len=' + d.length;
      else if (d && d.records) summary = 'total=' + d.total + ' records=' + d.records.length;
      else if (r.body && r.body.raw) summary = 'RAW:' + r.body.raw;
      else summary = JSON.stringify(d).slice(0,120);
      console.log(`[${r.statusCode}] ${name}: ${summary}`);
    } catch (e) { console.log(`[ERR] ${name}: ${e.message}`); }
  }
  process.exit(0);
})();