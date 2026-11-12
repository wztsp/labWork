const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN = '', USER_ID='';
function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (USER_ID) headers['X-User-Id'] = USER_ID;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x = '';
      res.on('data', c => x += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(x) }); }
        catch (e) { resolve({ status: res.statusCode, body: { raw: x.slice(0,500) } }); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
(async () => {
  const login = await req('POST', '/api/auth/login', { username: 'admin', password: '123456' });
  TOKEN = login.body.data.token;
  try { const p = JSON.parse(Buffer.from(TOKEN.split('.')[1],'base64').toString()); USER_ID = String(p.sub); } catch(e){}
  console.log('login ok, userId=', USER_ID);

  // 找一条当前为核收(4)/待审核的标本
  const list = await req('GET', '/api/report/technician/samples?size=50');
  console.log('技师待审核列表 data:', JSON.stringify(list.body.data).slice(0, 300));
  const recs = (list.body.data && (list.body.data.records||list.body.data)) || [];
  // 优先选取 id/雪花ID 对应的标本
  let sampleId = recs[0] && (recs[0].sampleId || recs[0].id);
  console.log('选取 sampleId=', sampleId);
  if (!sampleId) return;

  // 先用详情接口确认该标本 sampleId 字段
  const det = await req('GET', '/api/report/technician/' + sampleId);
  console.log('\n=== 详情 ===');
  console.log('detail.data.sampleId=', det.body.data && det.body.data.sampleId);
  console.log('detail.code=', det.body.code, 'msg=', det.body.message);
  if (det.body.data && det.body.data.sampleId) sampleId = det.body.data.sampleId;

  const audit = await req('PUT', '/api/report/technician/' + sampleId + '/audit', { pass: true });
  console.log('\n=== 审核接口 ===');
  console.log('status=', audit.status);
  console.log(JSON.stringify(audit.body, null, 2).slice(0, 1500));
})().catch(e => console.error('ERR', e.message));