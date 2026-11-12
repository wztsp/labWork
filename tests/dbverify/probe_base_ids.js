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
  console.log('login:', user && user.username, 'userId='+USER_ID);
  const pr = async (label, path) => {
    const r = await req('GET', path);
    const d = r.body && r.body.data;
    console.log(`\n== ${label} ==`);
    console.log(JSON.stringify(d, null, 1).slice(0, 2500));
  };
  await pr('suppliers', '/api/reagent/supplier/page?current=1&size=3');
  await pr('categories', '/api/reagent/category/tree');
  await pr('warehouses', '/api/reagent/warehouse/tree');
  await pr('budgets', '/api/reagent/budget/page?current=1&size=3');
  await pr('reagents', '/api/reagent/dict?current=1&size=3');
  await pr('stock', '/api/reagent/stock?current=1&size=5');
  process.exit(0);
})();