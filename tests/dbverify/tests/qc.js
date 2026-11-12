// 质控模块业务逻辑测试 (质控品批号/记录/Westgard规则/质控图/预警/月报)
module.exports = {
  async run({ req, check }) {
    // 1. 质控品批号列表
    const lots = await req('GET', '/api/qc/lots/list');
    check('质控品批号列表', lots.body && lots.body.code === 200 && Array.isArray(lots.body.data), 'count=' + ((lots.body && lots.body.data) || []).length);
    const lotList = (lots.body && lots.body.data) || [];
    const lot0 = lotList[0];

    // 2. 启用质控品批号(下拉)
    const enabled = await req('GET', '/api/qc/lots/enabled');
    check('启用质控品批号', enabled.body && enabled.body.code === 200 && Array.isArray(enabled.body.data), 'count=' + ((enabled.body && enabled.body.data) || []).length);

    // 3. 质控记录分页
    const rec = await req('GET', '/api/qc/records/page?current=1&size=10');
    check('质控记录分页', rec.body && rec.body.code === 200, rec.body && rec.body.code === 200 ? 'total=' + (rec.body.data && rec.body.data.total) : (rec.body && rec.body.message || ''));

    // 4. Westgard 规则列表
    const rules = await req('GET', '/api/qc/rules');
    check('质控规则列表', rules.body && rules.body.code === 200 && Array.isArray(rules.body.data), 'count=' + ((rules.body && rules.body.data) || []).length);
    const ruleList = (rules.body && rules.body.data) || [];
    check('存在Westgard规则', ruleList.length >= 6, 'count=' + ruleList.length);

    // 5. 质控图数据 (用第一个批号)
    if (lot0) {
      const chart = await req('GET', '/api/qc/charts/lj?lotId=' + lot0.id);
      check('质控图数据', chart.body && chart.body.code === 200, chart.body ? JSON.stringify(chart.body.data).slice(0, 120) : '');

      // 6. Z分数图
      const z = await req('GET', '/api/qc/charts/z-score?lotId=' + lot0.id);
      check('Z分数图', z.body && z.body.code === 200, 'code=' + z.body.code);
    }

    // 7. 质控预警分页
    const alert = await req('GET', '/api/qc/alerts/page?current=1&size=10');
    check('质控预警分页', alert.body && alert.body.code === 200, alert.body && alert.body.code === 200 ? 'total=' + (alert.body.data && alert.body.data.total) : (alert.body && alert.body.message || ''));

    // 8. 质控月报
    const now = new Date();
    const report = await req('GET', '/api/qc/report/monthly?year=' + now.getFullYear() + '&month=' + (now.getMonth() + 1));
    check('质控月报', report.body && report.body.code === 200, report.body ? JSON.stringify(report.body.data).slice(0, 120) : '');

    // 9. 质控规则启用(下拉)
    const enabledRules = await req('GET', '/api/qc/rules/enabled');
    check('质控规则启用列表', enabledRules.body && enabledRules.body.code === 200, 'code=' + (enabledRules.body && enabledRules.body.code));

    // 10. 质控记录重判(契约校验: 用第一个批号重算不引入异常)
    if (lot0) {
      const recal = await req('POST', '/api/qc/records/recalculate?lotId=' + lot0.id);
      check('质控记录重判接口已接通', recal.body && recal.body.code === 200, 'code=' + (recal.body && recal.body.code) + ' msg=' + (recal.body && recal.body.message || ''));
    }

    // 11. EQA 统计
    const eqaStats = await req('GET', '/api/qc/eqa/stats');
    check('EQA统计接口已接通', eqaStats.body && eqaStats.body.code === 200, 'code=' + (eqaStats.body && eqaStats.body.code));
  }
};