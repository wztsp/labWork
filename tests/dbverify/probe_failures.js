const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN='', USER_ID='';
function raw(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type':'application/json' };
    if (TOKEN) headers['Authorization']='Bearer '+TOKEN;
    if (USER_ID) headers['X-User-Id']=USER_ID;
    if (data) headers['Content-Length']=Buffer.byteLength(data);
    const r = http.request(BASE+path,{method,headers},res=>{let x='';res.on('data',c=>x+=c);res.on('end',()=>resolve({status:res.statusCode,body:x.slice(0,800)}));});
    r.on('error',reject); if(data)r.write(data); r.end();
  });
}
// full-body version
function full(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type':'application/json' };
    if (TOKEN) headers['Authorization']='Bearer '+TOKEN;
    if (USER_ID) headers['X-User-Id']=USER_ID;
    if (data) headers['Content-Length']=Buffer.byteLength(data);
    const r = http.request(BASE+path,{method,headers},res=>{let x='';res.on('data',c=>x+=c);res.on('end',()=>resolve({status:res.statusCode,body:x}));});
    r.on('error',reject); if(data)r.write(data); r.end();
  });
}
(async () => {
  let r = await full('POST','/api/auth/login',{username:'admin',password:'123456'});
  const b = JSON.parse(r.body); TOKEN=b.data.token;
  const p=JSON.parse(Buffer.from(TOKEN.split('.')[1],'base64').toString()); USER_ID=String(p.sub);

  console.log('--- dict by reagentName=葡萄糖 ---');
  r = await full('GET','/api/reagent/dict?current=1&size=50&reagentName=%E8%91%A1%E8%90%84%E7%B3%96');
  console.log(r.status, r.body.slice(0,600));

  console.log('\n--- POST /api/reagent/stock {} ---');
  r = await full('POST','/api/reagent/stock',{});
  console.log(r.status, r.body.slice(0,300));
  console.log('--- POST /api/reagent/records {} ---');
  r = await full('POST','/api/reagent/records',{});
  console.log(r.status, r.body.slice(0,300));

  console.log('\n--- budget judge id=8 amount=50000 ---');
  r = await full('GET','/api/reagent/budget/8/judge?amount=50000');
  console.log(r.status, r.body.slice(0,300));

  console.log('\n--- create fresh budget ---');
  r = await full('POST','/api/reagent/budget',{budgetYear:2026,deptId:999,categoryId:null,totalAmount:100000,warnRatio:0.8,status:1});
  console.log('create:', r.status, r.body.slice(0,300));
  const bid = JSON.parse(r.body).data.id;
  r = await full('GET',`/api/reagent/budget/${bid}/judge?amount=50000`);
  console.log('judge:', r.status, r.body.slice(0,300));

  process.exit(0);
})();