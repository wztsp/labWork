// 端到端核心业务流测试: 检验申请 -> 生成条码 -> 采集 -> 送检 -> 签收 -> 核收
// 校验完整状态机流转 (0已申请 -> 1已采集 -> 2已送检 -> 3已签收 -> 4已核收)
// 并验证非法状态跳转必须被拦截 (业务逻辑正确性)
module.exports = {
  async run({ req, check }) {
    const stamp = Date.now();
    const patientName = 'E2E流程测试' + stamp % 100000;
    let sampleBarcode = null;
    let sampleId = null;

    // ========== 1. 创建检验申请 (血常规) ==========
    const order = await req('POST', '/api/sample/orders/receive', {
      patientName,
      orderType: '门诊',
      applyDoctor: '张医生',
      applyDept: '门诊内科',
      clinicalDiag: 'E2E业务流测试',
      items: [{ testItemId: 9, testItemCode: 'CBP', testItemName: '血常规', fee: 35 }]
    });
    check('创建检验申请', order.body && order.body.code === 200 && Array.isArray(order.body.data) && order.body.data.length > 0,
      order.body ? JSON.stringify(order.body.data).slice(0, 160) : (order.body && order.body.message || ''));
    const createdOrders = (order.body && order.body.data) || [];
    const orderObj = createdOrders[0];
    check('申请拆分一申请一项目', createdOrders.length === 1, 'count=' + createdOrders.length);

    // ========== 2. 查询申请, 获取 visitId/patientId/orderId/orderItemId ==========
    const q = await req('GET', '/api/sample/orders/page?current=1&size=20&patientName=' + encodeURIComponent(patientName));
    const rec = q.body && q.body.data && q.body.data.records || [];
    const myOrder = rec.find(x => String(x.id) === String(orderObj.id)) || rec[0];
    check('查询到新申请', !!myOrder, myOrder ? 'orderNo=' + myOrder.orderNo : '未找到');
    if (!myOrder) return;
    const visitId = myOrder.visitId;
    const patientId = myOrder.patientId;
    const orderId = myOrder.id;
    check('申请含项目明细', Array.isArray(myOrder.items) && myOrder.items.length > 0, 'items=' + (myOrder.items || []).length);
    const orderItemId = myOrder.items && myOrder.items[0] && myOrder.items[0].id;

    // ========== 3. 试管分组计算 (合管) ==========
    const tg = await req('GET', '/api/sample/tube-groups?visitId=' + visitId);
    check('试管分组计算', tg.body && tg.body.code === 200 && Array.isArray(tg.body.data) && tg.body.data.length > 0,
      'count=' + ((tg.body && tg.body.data) || []).length);
    const groups = (tg.body && tg.body.data) || [];
    const group0 = groups[0];
    check('分组含检验项目', !!group0 && Array.isArray(group0.testItems) && group0.testItems.length > 0,
      group0 ? 'items=' + (group0.testItems || []).length : '');

    // ========== 4. 批量生成条码 ==========
    const batchPayload = {
      visitId, patientId, patientName,
      visitNo: myOrder.visitNo,
      department: myOrder.applyDept,
      groups: groups.map(g => ({
        tubeId: g.tubeId,
        tubeName: g.tubeName,
        sampleType: g.sampleType,
        containerType: g.containerType || g.tubeName,
        testItems: (g.testItems || []).map(ti => ({
          testItemId: ti.testItemId,
          orderId: ti.orderId,
          orderItemId: ti.orderItemId
        }))
      }))
    };
    const batch = await req('POST', '/api/sample/batch', batchPayload);
    check('批量生成条码', batch.body && batch.body.code === 200 && Array.isArray(batch.body.data) && batch.body.data.length > 0,
      batch.body ? (Array.isArray(batch.body.data) ? 'count=' + batch.body.data.length : batch.body.message) : '');
    const samples = (batch.body && batch.body.data) || [];
    const sample0 = samples[0];
    if (sample0) {
      sampleBarcode = sample0.sampleBarcode;
      sampleId = sample0.id;
      check('新标本初始状态=已申请(0)', sample0.status === 0, 'barcode=' + sampleBarcode + ' status=' + sample0.status);
    }

    // ========== 5. 状态机校验 ==========
    if (sampleBarcode) {
      // 5.1 未采集不可送检
      const d1 = await req('PUT', '/api/sample/deliver', { barcodes: [sampleBarcode], operator: '测试员' });
      check('未采集不可送检(应拒绝)', d1.body && d1.body.code !== 200, d1.body && (d1.body.message || 'code=' + d1.body.code));

      // 5.2 未采集不可签收
      const s1 = await req('PUT', '/api/sample/signoff', { barcodes: [sampleBarcode], operator: '测试员' });
      check('未采集不可签收(应拒绝)', s1.body && s1.body.code !== 200, s1.body && (s1.body.message || 'code=' + s1.body.code));

      // 5.3 未采集不可核收
      const r1 = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3 });
      check('未采集不可核收(应拒绝)', r1.body && r1.body.code !== 200, r1.body && (r1.body.message || 'code=' + r1.body.code));

      // ========== 6. 采集 -> 已采集(1) ==========
      const collect = await req('PUT', '/api/sample/collect', { sampleBarcode });
      check('采集标本', collect.body && collect.body.code === 200, collect.body ? 'status=' + collect.body.data.status : '');
      check('采集后状态=已采集(1)', collect.body && collect.body.data && collect.body.data.status === 1, 'status=' + (collect.body.data && collect.body.data.status));

      // 6.1 重复采集应拒绝
      const collect2 = await req('PUT', '/api/sample/collect', { sampleBarcode });
      check('重复采集(应拒绝)', collect2.body && collect2.body.code !== 200, collect2.body && (collect2.body.message || 'code=' + collect2.body.code));

      // 6.2 已采集未送检不可核收
      const r2 = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3 });
      check('已采集未送检不可核收(应拒绝)', r2.body && r2.body.code !== 200, r2.body && (r2.body.message || 'code=' + r2.body.code));

      // ========== 7. 送检 -> 已送检(2) ==========
      const deliver = await req('PUT', '/api/sample/deliver', { barcodes: [sampleBarcode], operator: '测试员' });
      check('送检标本', deliver.body && deliver.body.code === 200, deliver.body ? 'status=' + (deliver.body.data && deliver.body.data[0] && deliver.body.data[0].status) : '');
      const dStatus = deliver.body && deliver.body.data && deliver.body.data[0] && deliver.body.data[0].status;
      check('送检后状态=已送检(2)', dStatus === 2, 'status=' + dStatus);

      // 7.1 重复送检应拒绝
      const deliver2 = await req('PUT', '/api/sample/deliver', { barcodes: [sampleBarcode], operator: '测试员' });
      check('重复送检(应拒绝)', deliver2.body && deliver2.body.code !== 200, deliver2.body && (deliver2.body.message || 'code=' + deliver2.body.code));

      // ========== 8. 签收 -> 已签收(3) ==========
      const signoff = await req('PUT', '/api/sample/signoff', { barcodes: [sampleBarcode], operator: '测试员' });
      check('签收标本', signoff.body && signoff.body.code === 200, signoff.body ? 'status=' + (signoff.body.data && signoff.body.data[0] && signoff.body.data[0].status) : '');
      const sStatus = signoff.body && signoff.body.data && signoff.body.data[0] && signoff.body.data[0].status;
      check('签收后状态=已签收(3)', sStatus === 3, 'status=' + sStatus);

      // 8.1 重复签收应拒绝
      const signoff2 = await req('PUT', '/api/sample/signoff', { barcodes: [sampleBarcode], operator: '测试员' });
      check('重复签收(应拒绝)', signoff2.body && signoff2.body.code !== 200, signoff2.body && (signoff2.body.message || 'code=' + signoff2.body.code));

      // ========== 9. 核收 -> 已核收(4) ==========
      const pre = await req('GET', '/api/sample/receive/precheck?barcode=' + sampleBarcode + '&instrumentId=3');
      check('核收预检', pre.body && pre.body.code === 200 && Array.isArray(pre.body.data) && pre.body.data.length === 0,
        pre.body ? JSON.stringify(pre.body.data).slice(0, 100) : '');
      const receive = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3, department: '门诊内科' });
      check('核收标本', receive.body && receive.body.code === 200, receive.body ? 'status=' + (receive.body.data && receive.body.data.status) : (receive.body && receive.body.message || ''));
      const rStatus = receive.body && receive.body.data && receive.body.data.status;
      check('核收后状态=已核收(4)', rStatus === 4, 'status=' + rStatus);

      // 9.1 重复核收应拒绝
      const receive2 = await req('PUT', '/api/sample/receive', { sampleBarcode, instrumentId: 3 });
      check('重复核收(应拒绝)', receive2.body && receive2.body.code !== 200, receive2.body && (receive2.body.message || 'code=' + receive2.body.code));

      // ========== 10. 最终查询确认状态机 ==========
      const fin = await req('GET', '/api/sample/barcode/' + sampleBarcode);
      check('最终标本状态=已核收(4)', fin.body && fin.body.data && fin.body.data.status === 4,
        'status=' + (fin.body.data && fin.body.data.status) + ' statusText=' + (fin.body.data && fin.body.data.statusText));
      check('核收记录标本号', fin.body && fin.body.data && fin.body.data.sampleNo, 'sampleNo=' + (fin.body.data && fin.body.data.sampleNo));

      // ========== 11. 技师保存检验结果 ==========
      const det = await req('GET', '/api/report/technician/' + sampleId);
      const results = det.body && det.body.data && det.body.data.results || [];
      check('技师详情含结果指标', Array.isArray(results) && results.length > 0, 'results=' + results.length);
      if (results.length > 0) {
        const filled = results.map((r, i) => ({ ...r, resultValue: r.resultValue || String(10 + i), unit: r.unit || '' }));
        const save = await req('POST', '/api/report/technician/' + sampleId + '/results', filled);
        check('保存检验结果', save.body && save.body.code === 200, 'code=' + save.body.code);
      }

      // ========== 12. 审核通过 -> 终审(报告状态6) ==========
      const audit = await req('PUT', '/api/report/technician/' + sampleId + '/audit', { pass: true });
      check('报告审核通过', audit.body && audit.body.code === 200, audit.body ? JSON.stringify(audit.body.data).slice(0, 60) : (audit.body && audit.body.message || ''));
      const afterAudit = await req('GET', '/api/sample/barcode/' + sampleBarcode);
      check('审核后标本状态=已报告(6)', afterAudit.body && afterAudit.body.data && afterAudit.body.data.status === 6,
        'status=' + (afterAudit.body.data && afterAudit.body.data.status) + ' statusText=' + (afterAudit.body.data && afterAudit.body.data.statusText));

      // ========== 13. 报告已生成并对临床可见 ==========
      const rp = await req('GET', '/api/report/page?current=1&size=20&sampleBarcode=' + sampleBarcode);
      const rep = rp.body && rp.body.data && rp.body.data.records || [];
      check('报告已生成', rep.length > 0, 'reportCount=' + rep.length);
      const rep0 = rep[0];
      if (rep0) {
        check('报告状态=已发布', String(rep0.status) === '2' || rep0.status === 2, 'status=' + rep0.status);
        check('报告关联患者', !!rep0.patientName, 'patient=' + rep0.patientName);
      }
    } else {
      check('批量生成返回标本', false, '未获得标本条码');
    }
  }
};