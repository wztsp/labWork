// 微生物模块业务逻辑测试 (细菌/抗生素/折点/耐药规则/打印配置/血培养/微生物报告)
module.exports = {
  async run({ req, check }) {
    // 1. 细菌字典
    const bac = await req('GET', '/api/micro/bacteria?current=1&size=20');
    check('细菌分页', bac.body && bac.body.code === 200, bac.body && bac.body.code === 200 ? 'total=' + (bac.body.data && bac.body.data.total) : (bac.body && bac.body.message || ''));

    // 2. 全部启用细菌
    const allBac = await req('GET', '/api/micro/bacteria/all');
    check('全部细菌', allBac.body && allBac.body.code === 200 && Array.isArray(allBac.body.data), 'count=' + ((allBac.body && allBac.body.data) || []).length);

    // 3. 常见菌
    const common = await req('GET', '/api/micro/bacteria/common');
    check('常见菌', common.body && common.body.code === 200, 'code=' + common.body.code);

    // 4. 抗生素字典
    const ab = await req('GET', '/api/micro/antibiotics?current=1&size=20');
    check('抗生素分页', ab.body && ab.body.code === 200, ab.body && ab.body.code === 200 ? 'total=' + (ab.body.data && ab.body.data.total) : (ab.body && ab.body.message || ''));

    // 5. 抗生素折点
    const bp = await req('GET', '/api/micro/breakpoints?current=1&size=20');
    check('抗生素折点分页', bp.body && bp.body.code === 200, bp.body && bp.body.code === 200 ? 'total=' + (bp.body.data && bp.body.data.total) : (bp.body && bp.body.message || ''));

    // 6. 耐药规则
    const rr = await req('GET', '/api/micro/resistance-rules?current=1&size=10');
    check('耐药规则分页', rr.body && rr.body.code === 200, rr.body && rr.body.code === 200 ? 'total=' + (rr.body.data && rr.body.data.total) : (rr.body && rr.body.message || ''));

    // 7. 打印配置
    const pc = await req('GET', '/api/micro/print-config');
    check('打印配置列表', pc.body && pc.body.code === 200 && Array.isArray(pc.body.data), 'count=' + ((pc.body && pc.body.data) || []).length);
    const defPc = await req('GET', '/api/micro/print-config/default');
    check('默认打印配置', defPc.body && defPc.body.code === 200, 'code=' + defPc.body.code);

    // 8. 血培养
    const bc = await req('GET', '/api/micro/blood-culture?current=1&size=10');
    check('血培养分页', bc.body && bc.body.code === 200, bc.body && bc.body.code === 200 ? 'total=' + (bc.body.data && bc.body.data.total) : (bc.body && bc.body.message || ''));
    const culturing = await req('GET', '/api/micro/blood-culture/culturing');
    check('培养中血培养', culturing.body && culturing.body.code === 200 && Array.isArray(culturing.body.data), 'count=' + ((culturing.body && culturing.body.data) || []).length);

    // 9. 微生物报告分页
    const mr = await req('GET', '/api/report/micro/page?current=1&size=10');
    check('微生物报告分页', mr.body && mr.body.code === 200, mr.body && mr.body.code === 200 ? 'total=' + (mr.body.data && mr.body.data.total) : (mr.body && mr.body.message || ''));
  }
};