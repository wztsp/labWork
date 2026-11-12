// 调试: 完整流程逐步打印, 定位技师详情 results=0 与中文乱码问题
const { req, login } = require('./test_api');

(async () => {
  const user = await login();
  if (!user) { console.log('登录失败'); return; }
  console.log('登录成功:', user.username);

  const stamp = Date.now();
  const patientName = 'E2E流程测试' + stamp % 100000;
  console.log('\n[patientName]', patientName);

  // 1. 创建申请
  const order = await req('POST', '/api/sample/orders/receive', {
    patientName,
    orderType: '门诊',
    applyDoctor: '张医生',
    applyDept: '门诊内科',
    clinicalDiag: 'E2E业务流测试',
    items: [{ testItemId: 9, testItemCode: 'CBP', testItemName: '血常规', fee: 35 }]
  });
  console.log('\n[创建申请] code=', order.body && order.body.code);
  console.log('[创建申请] 返回=', JSON.stringify(order.body).slice(0, 400));
  const createdOrders = (order.body && order.body.data) || [];
  const orderObj = createdOrders[0];

  // 2. 查询申请
  const q = await req('GET', '/api/sample/orders/page?current=1&size=20&patientName=' + encodeURIComponent(patientName));
  const rec = q.body && q.body.data && q.body.data.records || [];
  const myOrder = rec.find(x => String(x.id) === String(orderObj && orderObj.id)) || rec[0];
  console.log('\n[查询申请] records=', rec.length, 'patientName字段=', rec[0] ? rec[0].patientName : 'N/A');
  if (!myOrder) { console.log('未找到申请'); return; }
  const visitId = myOrder.visitId, patientId = myOrder.patientId, orderId = myOrder.id;
  console.log('[申请] id=', orderId, 'visitId=', visitId, 'patientId=', patientId, 'patientName=', myOrder.patientName);
  const orderItemId = myOrder.items && myOrder.items[0] && myOrder.items[0].id;
  console.log('[申请] orderItemId=', orderItemId);

  // 3. 试管分组
  const tg = await req('GET', '/api/sample/tube-groups?visitId=' + visitId);
  const groups = (tg.body && tg.body.data) || [];
  console.log('\n[试管分组] count=', groups.length, JSON.stringify(groups).slice(0, 300));
  const group0 = groups[0];
  console.log('[分组0] testItems=', JSON.stringify(group0 && group0.testItems));

  // 4. 批量生成条码
  const batchPayload = {
    visitId, patientId, patientName,
    visitNo: myOrder.visitNo,
    department: myOrder.applyDept,
    groups: groups.map(g => ({
      tubeId: g.tubeId, tubeName: g.tubeName, sampleType: g.sampleType,
      containerType: g.containerType || g.tubeName,
      testItems: (g.testItems || []).map(ti => ({
        testItemId: ti.testItemId, orderId: ti.orderId, orderItemId: ti.orderItemId
      }))
    }))
  };
  const batch = await req('POST', '/api/sample/batch', batchPayload);
  const samples = (batch.body && batch.body.data) || [];
  console.log('\n[批量生成条码] code=', batch.body && batch.body.code, 'count=', samples.length);
  const sample0 = samples[0];
  if (!sample0) { console.log('无标本'); return; }
  const sampleBarcode = sample0.sampleBarcode, sampleId = sample0.id;
  console.log('[标本] id=', sampleId, 'barcode=', sampleBarcode, 'status=', sample0.status, 'patientName=', sample0.patientName);

  // 采集 -> 送检 -> 签收 -> 核收
  await req('PUT', '/api/sample/collect', { sampleBarcode });
  await req('PUT', '/api/sample/deliver', { barcodes: [sampleBarcode], operator: '测试员' });
  await req('PUT', '/api/sample/signoff', { barcodes: [sampleBarcode], operator: '测试员' });
  const receive = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3, department: '门诊内科' });
  console.log('\n[核收] code=', receive.body && receive.body.code, 'status=', receive.body.data && receive.body.data.status);

  // 技师详情
  const det = await req('GET', '/api/report/technician/' + sampleId);
  console.log('\n[技师详情] code=', det.body && det.body.code);
  const d = det.body && det.body.data;
  if (d) {
    console.log('[技师详情] patient=', JSON.stringify(d.patient));
    console.log('[技师详情] results=', d.results ? d.results.length : 'N/A');
    if (d.results && d.results.length > 0) console.log('[技师详情] results[0]=', JSON.stringify(d.results[0]).slice(0, 300));
  } else {
    console.log('[技师详情] 无data, body=', JSON.stringify(det.body).slice(0, 300));
  }

  // 检查标本详情里的检验项目关联(通过sample接口)
  const sd = await req('GET', '/api/sample/page?current=1&size=5&barcode=' + sampleBarcode);
  console.log('\n[标本分页] 返回=', JSON.stringify(sd.body).slice(0, 400));

  // 检查数据库关联(通过其他接口无法直接查,这里打印条码详情)
  const fin = await req('GET', '/api/sample/barcode/' + sampleBarcode);
  console.log('\n[条码详情]=', JSON.stringify(fin.body).slice(0, 300));
})();