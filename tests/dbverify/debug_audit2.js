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
        catch (e) { resolve({ status: res.statusCode, body: null }); }
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
  console.log('login userId=', USER_ID);

  const stamp = Date.now();
  const patientName = '复现流程' + stamp % 100000;
  const order = await req('POST', '/api/sample/orders/receive', {
    patientName, orderType: '门诊', applyDoctor: '张医生', applyDept: '门诊内科',
    clinicalDiag: '复现', items: [{ testItemId: 9, testItemCode: 'CBP', testItemName: '血常规', fee: 35 }]
  });
  console.log('创建申请 code=', order.body && order.body.code, (order.body&&order.body.message||''));
  const created = (order.body && order.body.data) || [];
  const orderObj = created[0];
  const q = await req('GET', '/api/sample/orders/page?current=1&size=20&patientName=' + encodeURIComponent(patientName));
  const recs = q.body && q.body.data && q.body.data.records || [];
  const myOrder = recs.find(x => String(x.id) === String(orderObj && orderObj.id)) || recs[0];
  const visitId = myOrder.visitId, patientId = myOrder.patientId, orderId = myOrder.id;
  const orderItemId = myOrder.items && myOrder.items[0] && myOrder.items[0].id;
  console.log('visitId=', visitId, 'orderId=', orderId, 'orderItemId=', orderItemId);

  const tg = await req('GET', '/api/sample/tube-groups?visitId=' + visitId);
  const groups = (tg.body && tg.body.data) || [];
  const g0 = groups[0];
  const batch = await req('POST', '/api/sample/batch', {
    visitId, patientId, patientName, visitNo: myOrder.visitNo, department: myOrder.applyDept,
    groups: groups.map(g => ({ tubeId: g.tubeId, tubeName: g.tubeName, sampleType: g.sampleType, containerType: g.containerType || g.tubeName,
      testItems: (g.testItems||[]).map(ti => ({ testItemId: ti.testItemId, orderId: ti.orderId, orderItemId: ti.orderItemId })) }))
  });
  const samples = (batch.body && batch.body.data) || [];
  const sample = samples[0];
  const sampleBarcode = sample.sampleBarcode, sampleId = sample.id;
  console.log('sampleBarcode=', sampleBarcode, 'sampleId=', sampleId);

  await req('PUT', '/api/sample/collect', { sampleBarcode });
  await req('PUT', '/api/sample/deliver', { barcodes: [sampleBarcode], operator: '测试员' });
  await req('PUT', '/api/sample/signoff', { barcodes: [sampleBarcode], operator: '测试员' });
  const rec = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3, department: '门诊内科' });
  console.log('核收 code=', rec.body && rec.body.code, rec.body && rec.body.message);

  const det = await req('GET', '/api/report/technician/' + sampleId);
  console.log('详情 code=', det.body && det.body.code, 'msg=', det.body && det.body.message);
  const results = det.body && det.body.data && det.body.data.results || [];
  console.log('results=', results.length);
  if (results.length) {
    const filled = results.map((r, i) => ({ ...r, resultValue: r.resultValue || String(10 + i), unit: r.unit || '' }));
    const save = await req('POST', '/api/report/technician/' + sampleId + '/results', filled);
    console.log('保存结果 code=', save.body && save.body.code, save.body && save.body.message);
  }

  const audit = await req('PUT', '/api/report/technician/' + sampleId + '/audit', { pass: true });
  console.log('\n=== 审核 ===');
  console.log('status=', audit.status);
  console.log('body=', JSON.stringify(audit.body));
})().catch(e => console.error('ERR', e.message));