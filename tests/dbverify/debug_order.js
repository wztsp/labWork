const http = require('http');
const BASE = 'http://localhost:8080';
function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x = '';
      res.on('data', c => x += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(x) }); }
        catch (e) { resolve({ status: res.statusCode, body: { raw: x.slice(0, 500) } }); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
(async () => {
  const stamp = Date.now();
  const patientName = 'Debug申请' + stamp % 100000;
  const r = await req('POST', '/api/sample/orders/receive', {
    patientName,
    orderType: '门诊',
    applyDoctor: '张医生',
    applyDept: '门诊内科',
    clinicalDiag: '调试',
    items: [{ testItemId: 9, testItemCode: 'CBP', testItemName: '血常规', fee: 35 }]
  });
  console.log('status=', r.status);
  console.log('body=', JSON.stringify(r.body, null, 2).slice(0, 2000));
})().catch(e => console.error('ERR', e.message));