// 基础数据模块业务逻辑测试
module.exports = {
  async run({ req, check }) {
    // 1. 检验项目分页列表
    const items = await req('GET', '/api/base/items?current=1&size=10');
    check('检验项目分页列表', items.body && items.body.code === 200, items.body ? JSON.stringify(items.body.data).slice(0, 150) : '');
    const itemTotal = items.body && items.body.data ? items.body.data.total : 0;
    check('检验项目total>0', itemTotal > 0, 'total=' + itemTotal);

    // 血常规项目必须是 panel (is_panel=1) - 从完整字典中查找
    const dict = await req('GET', '/api/base/items/dict');
    check('检验项目字典', dict.body && dict.body.code === 200 && Array.isArray(dict.body.data), 'count=' + ((dict.body && dict.body.data) || []).length);
    const cbc = dict.body && dict.body.data ? dict.body.data.find(x => x.itemName === '血常规') : null;
    check('血常规项目存在', !!cbc, cbc ? 'id=' + cbc.id + ' isPanel=' + cbc.isPanel : '未找到');

    // 3. 参考范围
    const rr = await req('GET', '/api/base/ref-ranges?current=1&size=10');
    check('参考范围分页', rr.body && rr.body.code === 200);
    const rrTotal = rr.body && rr.body.data ? rr.body.data.total : 0;
    check('参考范围total>0', rrTotal > 0, 'total=' + rrTotal);

    // 参考范围匹配接口 (testItemId=159 存在"男性-成人"记录 gender=1 age 0-999)
    const match = await req('GET', '/api/base/ref-ranges/match?testItemId=159&gender=1&age=30');
    check('参考范围匹配(男成人)', match.body && match.body.code === 200 && match.body.data, match.body ? JSON.stringify(match.body.data).slice(0, 120) : '未匹配到(应匹配男性-成人)');
    // 女性儿童匹配 (testItemId=160 gender=2 age=5)
    const matchF = await req('GET', '/api/base/ref-ranges/match?testItemId=160&gender=2&age=5');
    check('参考范围匹配(女儿童)', matchF.body && matchF.body.code === 200 && matchF.body.data, matchF.body && matchF.body.data ? 'condition=' + matchF.body.data.conditionRemark : '未匹配到(应匹配女性-儿童)');

    // 4. 仪器字典
    const inst = await req('GET', '/api/base/instruments?current=1&size=10');
    check('仪器字典分页', inst.body && inst.body.code === 200);
    const instRecords = inst.body && inst.body.data && inst.body.data.records ? inst.body.data.records : [];
    check('仪器字典有数据', instRecords.length > 0, 'count=' + instRecords.length);
    const au5800 = instRecords.find(x => x.instrumentName && String(x.instrumentName).includes('AU5800'));
    check('AU5800仪器存在', !!au5800, au5800 ? 'id=' + au5800.id : '未找到');

    // 5. 标本类型
    const spec = await req('GET', '/api/base/specimen-types?current=1&size=10');
    check('标本类型分页', spec.body && spec.body.code === 200);
    const allSpec = await req('GET', '/api/base/specimen-types/all');
    check('标本类型字典', allSpec.body && allSpec.body.code === 200 && Array.isArray(allSpec.body.data), 'count=' + ((allSpec.body && allSpec.body.data) || []).length);

    // 6. 采集方式
    const cm = await req('GET', '/api/base/collection-methods?current=1&size=10');
    check('采集方式分页', cm.body && cm.body.code === 200);

    // 7. 检验试管
    const tube = await req('GET', '/api/base/test-tubes?current=1&size=10');
    check('检验试管分页', tube.body && tube.body.code === 200);
    const allTube = await req('GET', '/api/base/test-tubes/all');
    check('试管字典', allTube.body && allTube.body.code === 200 && Array.isArray(allTube.body.data), 'count=' + ((allTube.body && allTube.body.data) || []).length);

    // 8. 条码规则
    const rules = await req('GET', '/api/sample/barcode/rules');
    check('条码规则列表', rules.body && rules.body.code === 200 && Array.isArray(rules.body.data), 'count=' + ((rules.body && rules.body.data) || []).length);
  }
};