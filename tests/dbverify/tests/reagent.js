// 试剂管理模块业务逻辑测试 (字典/库存/出入库/入库验收/消耗/开瓶/看板/盘点/成本/采购/请领/供应商/分类/仓库/预算)
module.exports = {
  async run({ req, check }) {
    // 1. 试剂字典
    const dict = await req('GET', '/api/reagent/dict?current=1&size=10');
    check('试剂字典分页', dict.body && dict.body.code === 200, dict.body && dict.body.code === 200 ? 'total=' + (dict.body.data && dict.body.data.total) : (dict.body && dict.body.message || ''));
    const reagent = dict.body && dict.body.data && dict.body.data.records && dict.body.data.records[0];

    // 2. 库存
    const stock = await req('GET', '/api/reagent/stock?current=1&size=10');
    check('库存分页', stock.body && stock.body.code === 200, stock.body && stock.body.code === 200 ? 'total=' + (stock.body.data && stock.body.data.total) : (stock.body && stock.body.message || ''));

    // 3. 出入库记录
    const records = await req('GET', '/api/reagent/records?current=1&size=10');
    check('出入库记录分页', records.body && records.body.code === 200, records.body && records.body.code === 200 ? 'total=' + (records.body.data && records.body.data.total) : (records.body && records.body.message || ''));

    // 4. 库存预警 (near-expiry / below-safety / low-stock)
    const nearExp = await req('GET', '/api/reagent/stock/near-expiry?days=30');
    check('临期库存', nearExp.body && nearExp.body.code === 200 && Array.isArray(nearExp.body.data), 'count=' + ((nearExp.body && nearExp.body.data) || []).length);
    const lowStock = await req('GET', '/api/reagent/stock/low-stock');
    check('低库存预警', lowStock.body && lowStock.body.code === 200 && Array.isArray(lowStock.body.data), 'count=' + ((lowStock.body && lowStock.body.data) || []).length);

    // 5. 入库验收
    const inbound = await req('GET', '/api/reagent/inbound?current=1&size=10');
    check('入库验收分页', inbound.body && inbound.body.code === 200, inbound.body && inbound.body.code === 200 ? 'total=' + (inbound.body.data && inbound.body.data.total) : (inbound.body && inbound.body.message || ''));

    // 6. 消耗登记
    const cons = await req('GET', '/api/reagent/consumption?current=1&size=10');
    check('消耗登记分页', cons.body && cons.body.code === 200, cons.body && cons.body.code === 200 ? 'total=' + (cons.body.data && cons.body.data.total) : (cons.body && cons.body.message || ''));

    // 7. 开瓶记录
    const opening = await req('GET', '/api/reagent/opening?current=1&size=10');
    check('开瓶记录分页', opening.body && opening.body.code === 200, opening.body && opening.body.code === 200 ? 'total=' + (opening.body.data && opening.body.data.total) : (opening.body && opening.body.message || ''));

    // 8. 库存看板
    const dash = await req('GET', '/api/reagent/dashboard/overview');
    check('库存看板概览', dash.body && dash.body.code === 200, dash.body ? JSON.stringify(dash.body.data).slice(0, 120) : '');

    // 9. 盘点
    const sc = await req('GET', '/api/reagent/stock-check?current=1&size=10');
    check('盘点单分页', sc.body && sc.body.code === 200, sc.body && sc.body.code === 200 ? 'total=' + (sc.body.data && sc.body.data.total) : (sc.body && sc.body.message || ''));

    // 10. 成本核算
    const cost = await req('GET', '/api/reagent/cost/summary');
    check('成本汇总', cost.body && cost.body.code === 200, 'code=' + cost.body.code);

    // 11. 采购申请
    const apply = await req('GET', '/api/reagent/purchase-apply/page?current=1&size=10');
    check('采购申请分页', apply.body && apply.body.code === 200, apply.body && apply.body.code === 200 ? 'total=' + (apply.body.data && apply.body.data.total) : (apply.body && apply.body.message || ''));

    // 12. 采购订单
    const order = await req('GET', '/api/reagent/purchase-order/page?current=1&size=10');
    check('采购订单分页', order.body && order.body.code === 200, order.body && order.body.code === 200 ? 'total=' + (order.body.data && order.body.data.total) : (order.body && order.body.message || ''));

    // 13. 请领业务单
    const reqQ = await req('GET', '/api/reagent/requisition/page?current=1&size=10');
    check('请领单分页', reqQ.body && reqQ.body.code === 200, reqQ.body && reqQ.body.code === 200 ? 'total=' + (reqQ.body.data && reqQ.body.data.total) : (reqQ.body && reqQ.body.message || ''));

    // 14. 供应商
    const sup = await req('GET', '/api/reagent/supplier/page?current=1&size=10');
    check('供应商分页', sup.body && sup.body.code === 200, sup.body && sup.body.code === 200 ? 'total=' + (sup.body.data && sup.body.data.total) : (sup.body && sup.body.message || ''));
    const supSel = await req('GET', '/api/reagent/supplier/selectable');
    check('可选供应商(证照有效)', supSel.body && supSel.body.code === 200 && Array.isArray(supSel.body.data), 'count=' + ((supSel.body && supSel.body.data) || []).length);

    // 15. 试剂分类
    const cat = await req('GET', '/api/reagent/category/tree');
    check('分类树', cat.body && cat.body.code === 200 && Array.isArray(cat.body.data), 'count=' + ((cat.body && cat.body.data) || []).length);

    // 16. 仓库
    const wh = await req('GET', '/api/reagent/warehouse/tree');
    check('仓库树', wh.body && wh.body.code === 200 && Array.isArray(wh.body.data), 'count=' + ((wh.body && wh.body.data) || []).length);

    // 17. 预算
    const budget = await req('GET', '/api/reagent/budget/page?current=1&size=10');
    check('预算分页', budget.body && budget.body.code === 200, budget.body && budget.body.code === 200 ? 'total=' + (budget.body.data && budget.body.data.total) : (budget.body && budget.body.message || ''));
  }
};