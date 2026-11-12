// 统计报表模块业务逻辑测试 (工作量/危急值/趋势/阳性率/收费)
module.exports = {
  async run({ req, check }) {
    // 1. 工作量统计
    const wl = await req('GET', '/api/stats/workload?beginDate=&endDate=');
    check('工作量统计', wl.body && wl.body.code === 200, 'code=' + wl.body.code);

    // 2. 工作量按日
    const wd = await req('GET', '/api/stats/workload/by-day');
    check('工作量按日', wd.body && wd.body.code === 200, 'code=' + wd.body.code);

    // 3. 工作量按科室
    const wdep = await req('GET', '/api/stats/workload/by-dept');
    check('工作量按科室', wdep.body && wdep.body.code === 200, 'code=' + wdep.body.code);

    // 4. 工作量按仪器
    const wins = await req('GET', '/api/stats/workload/by-instrument');
    check('工作量按仪器', wins.body && wins.body.code === 200, 'code=' + wins.body.code);

    // 5. 危急值统计
    const crit = await req('GET', '/api/stats/critical?beginDate=&endDate=');
    check('危急值统计', crit.body && crit.body.code === 200, 'code=' + crit.body.code);

    // 6. 趋势分析 (需 patientId + testItemId)
    // 先取一条报告获取 patientId
    const rpg = await req('GET', '/api/report/page?current=1&size=5');
    const report = rpg.body && rpg.body.data && rpg.body.data.records && rpg.body.data.records[0];
    if (report && report.patientId) {
      const trend = await req('GET', '/api/stats/trend?patientId=' + report.patientId + '&testItemId=9');
      check('患者趋势分析', trend.body && trend.body.code === 200, trend.body && trend.body.code === 200 ? JSON.stringify(trend.body.data).slice(0, 100) : (trend.body && trend.body.message || ''));
    } else {
      check('趋势分析数据源', false, '未找到报告 patientId');
    }

    // 7. 阳性率
    const pr = await req('GET', '/api/stats/positive-rate?beginDate=&endDate=');
    check('阳性率统计', pr.body && pr.body.code === 200, 'code=' + pr.body.code);

    // 8. 收费统计
    const charge = await req('GET', '/api/stats/charge?beginDate=&endDate=');
    check('收费统计', charge.body && charge.body.code === 200, 'code=' + charge.body.code);
  }
};