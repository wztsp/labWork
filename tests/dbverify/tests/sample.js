// 标本管理模块业务逻辑测试 (核心业务流程 + 状态机校验)
module.exports = {
  async run({ req, check }) {
    // 1. 标本分页列表
    const page = await req('GET', '/api/sample/page?current=1&size=10');
    check('标本分页列表', page.body && page.body.code === 200);
    const total = page.body && page.body.data ? page.body.data.total : 0;
    check('标本total>0', total > 0, 'total=' + total);

    // 2. 按条码查询 (用之前构造的待审核血常规标本)
    const barcode = 'BC202608060301';
    const byBarcode = await req('GET', '/api/sample/barcode/' + barcode);
    check('按条码查询标本', byBarcode.body && byBarcode.body.code === 200 && byBarcode.body.data, byBarcode.body && byBarcode.body.code === 200 ? 'status=' + byBarcode.body.data.status : '');
    const sampleId = byBarcode.body && byBarcode.body.data ? byBarcode.body.data.id : null;

    if (sampleId) {
      // 3. 试管分组计算 (按就诊跨申请合管)
      const visitId = byBarcode.body.data.visitId;
      const tubeGroups = await req('GET', '/api/sample/tube-groups?visitId=' + visitId);
      check('试管分组计算', tubeGroups.body && tubeGroups.body.code === 200 && Array.isArray(tubeGroups.body.data), JSON.stringify(tubeGroups.body && tubeGroups.body.data).slice(0, 120));

      // 4. 标本追踪
      const track = await req('GET', '/api/sample/' + sampleId + '/track');
      check('标本追踪', track.body && track.body.code === 200);

      // 5. 业务规则校验: 未采集(status=0)的标本不可送检
      const page2 = await req('GET', '/api/sample/page?current=1&size=500&status=0');
      const notCollected = page2.body && page2.body.data && page2.body.data.records ? page2.body.data.records.find(x => x.status === 0) : null;
      if (notCollected) {
        const deliver = await req('PUT', '/api/sample/deliver', { barcodes: [notCollected.sampleBarcode], operator: '测试员' });
        check('未采集标本不可送检(应失败)', deliver.body && deliver.body.code !== 200, 'code=' + deliver.body.code + ' msg=' + (deliver.body.message || ''));
      } else {
        check('存在未采集标本用以测试', false, '未找到status=0标本');
      }

      // 6. 业务规则校验: 未送检(status<=1)标本不可签收
      const notDelivered = page2.body && page2.body.data && page2.body.data.records ? page2.body.data.records.find(x => x.status === 0 || x.status === 1) : null;
      if (notDelivered) {
        const signoff = await req('PUT', '/api/sample/signoff', { barcodes: [notDelivered.sampleBarcode], operator: '测试员' });
        check('未送检标本不可签收(应失败)', signoff.body && signoff.body.code !== 200, 'code=' + signoff.body.code + ' msg=' + (signoff.body.message || ''));
      }

      // 7. 已核收标本不可取消条码(契约校验: 仅未采集可取消)
      const cancel = await req('DELETE', '/api/sample/barcode/' + barcode);
      check('已核收标本不可取消条码(应拒绝)', cancel.body && cancel.body.code !== 200, 'code=' + (cancel.body && cancel.body.code) + ' msg=' + (cancel.body && cancel.body.message || ''));

      // 8. 标本拒收接口存在(契约校验: 不存在的条码应返回业务错误而非崩溃)
      const reject = await req('PUT', '/api/sample/reject', { barcode: 'NONEXIST' + Date.now() % 100000, rejectType: '溶血', reason: '测试' });
      check('标本拒收接口已接通', reject.body && reject.body.code !== 200 && reject.body.message, 'code=' + (reject.body && reject.body.code) + ' msg=' + (reject.body && reject.body.message || ''));

      // 9. 标本作废接口已接通(契约校验: 不存在的ID应返回业务错误而非崩溃)
      const avoid = await req('PUT', '/api/sample/999999999999/void?reason=测试作废');
      check('标本作废接口已接通', avoid.body && avoid.body.code !== 200 && avoid.body.message, 'code=' + (avoid.body && avoid.body.code) + ' msg=' + (avoid.body && avoid.body.message || ''));
    } else {
      check('按条码查询返回标本ID', false, 'sampleId为空');
    }
  }
};