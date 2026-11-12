// 报告管理模块业务逻辑测试 (技师工作站核心流程 + 报告审核 + 危急值)
module.exports = {
  async run({ req, check }) {
    // 1. 技师工作站待审核列表
    const pend = await req('GET', '/api/report/technician/samples?size=100');
    check('技师待审核列表', pend.body && pend.body.code === 200 && Array.isArray(pend.body.data), 'count=' + ((pend.body && pend.body.data) || []).length);

    // 2. 用待审核血常规标本测详情 (BC202608060301)
    const list = pend.body && pend.body.data || [];
    const target = list.find(x => String(x.sampleBarcode) === 'BC202608060301') || list[0];
    check('待审核列表有标本', !!target);
    if (target) {
      const sid = target.id;
      const det = await req('GET', '/api/report/technician/' + sid);
      check('技师详情返回', det.body && det.body.code === 200 && det.body.data, JSON.stringify(det.body && det.body.data).slice(0, 120));
      const data = det.body && det.body.data;
      if (data) {
        check('详情含患者信息', !!data.patient && !!data.patient.patientName, 'patient=' + data.patient.patientName);
        check('详情含检验结果', Array.isArray(data.results), 'results=' + (data.results || []).length);
        check('详情含图像', Array.isArray(data.images), 'images=' + (data.images || []).length);
        // 结果必须包含指标信息
        if (Array.isArray(data.results) && data.results.length > 0) {
          const r0 = data.results[0];
          check('结果含指标名', !!r0.testItemName, 'first=' + r0.testItemName);
        }
      }
      // 3. 技师汇总
      const summary = await req('GET', '/api/report/technician/summary');
      check('技师汇总', summary.body && summary.body.code === 200);
    }

    // 4. 报告分页查询
    const rp = await req('GET', '/api/report/page?current=1&size=10');
    check('报告分页', rp.body && rp.body.code === 200);
    const rpCount = rp.body && rp.body.data ? rp.body.data.total : 0;
    check('报告存在', rpCount > 0, 'total=' + rpCount);

    // 5. 报告审核通过后状态流转: 找一个已核收标本做审核测试
    // 用待审核血常规标本 (status=4) 审核
    const pendList = (await req('GET', '/api/report/technician/samples?size=200')).body.data || [];
    const testSample = pendList.find(x => '1234567890'.includes(x.id)) || pendList.find(x => String(x.sampleBarcode) === 'BC202608060305');
    if (testSample) {
      const preDetail = await req('GET', '/api/report/technician/' + testSample.id);
      const results = preDetail.body && preDetail.body.data && preDetail.body.data.results;
      if (Array.isArray(results) && results.length > 0) {
        // 保存结果 (填一个值)
        const filled = results.map(r => ({ ...r, resultValue: r.resultValue || '测试值' }));
        const save = await req('POST', '/api/report/technician/' + testSample.id + '/results', filled);
        check('保存检验结果', save.body && save.body.code === 200, 'code=' + save.body.code);
      }
    }

    // 6. 危急值列表
    const crit = await req('GET', '/api/report/critical/list');
    check('危急值列表', crit.body && crit.body.code === 200, 'code=' + crit.body.code);

    // 6.1 危急值统计
    const critStats = await req('GET', '/api/report/critical/stats?startDate=2026-01-01&endDate=2026-12-31');
    check('危急值统计', critStats.body && critStats.body.code === 200, 'code=' + critStats.body.code);

    // 6.2 危急值确认接口(契约校验: 不存在的resultId返回业务错误而非崩溃)
    const critConfirm = await req('POST', '/api/report/critical/999999999999/confirm?confirmerId=1', { note: '已通知' });
    check('危急值确认接口已接通', critConfirm.body && critConfirm.body.code !== 200 && critConfirm.body.message,
      'code=' + (critConfirm.body && critConfirm.body.code) + ' msg=' + (critConfirm.body && critConfirm.body.message || ''));

    // 6.3 技师撤销核收接口(契约校验: 批量操作返回 BatchOpResult 结果信封)
    const cancelReceive = await req('POST', '/api/report/technician/cancel-receive', [999999999999]);
    check('技师撤销核收接口已接通', cancelReceive.body && cancelReceive.body.code === 200 && cancelReceive.body.data &&
      typeof cancelReceive.body.data.success === 'number',
      'code=' + (cancelReceive.body && cancelReceive.body.code) + ' data=' + JSON.stringify(cancelReceive.body && cancelReceive.body.data));

    // 6.4 技师修改标本号接口(契约校验: 返回结构化响应信封)
    const modNo = await req('PUT', '/api/report/technician/999999999999/sample-no?sampleNo=NX999');
    check('技师修改标本号接口已接通', modNo.body && typeof modNo.body.code === 'number',
      'code=' + (modNo.body && modNo.body.code) + ' msg=' + (modNo.body && modNo.body.message || ''));

    // 7. 报告模板 (正确路径为复数 /templates/list)
    const tpl = await req('GET', '/api/report/templates/list');
    check('报告模板列表', tpl.body && tpl.body.code === 200 && Array.isArray(tpl.body.data), 'code=' + tpl.body.code + ' count=' + ((tpl.body && tpl.body.data) || []).length);
    if (tpl.body && tpl.body.code === 200 && Array.isArray(tpl.body.data)) {
      check('模板列表非空', tpl.body.data.length > 0, 'count=' + tpl.body.data.length);
    }
  }
};