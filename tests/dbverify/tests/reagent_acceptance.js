// LIS 试剂模块 B1(T-06~T-13) + B2(T-14~T-19) 设计验收点全量测试
const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN='', USER_ID='';
function req(method, path, body, raw) {
  return new Promise((resolve, reject) => {
    const data = body != null ? (raw ? body : JSON.stringify(body)) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (USER_ID) headers['X-User-Id'] = USER_ID;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x=''; res.on('data',c=>x+=c); res.on('end',()=>{
        try { resolve({status:res.statusCode, body:JSON.parse(x)}); }
        catch(e){ resolve({status:res.statusCode, body:{raw:x.slice(0,400)}}); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
async function login(username='admin', password='123456') {
  const r = await req('POST','/api/auth/login',{username,password});
  if (r.body && r.body.code===200) {
    TOKEN = r.body.data.token;
    try { const p=JSON.parse(Buffer.from(TOKEN.split('.')[1],'base64').toString()); USER_ID=String(p.sub); } catch(e){}
    return r.body.data;
  }
  return null;
}
const results = [];
function check(name, cond, detail='') {
  const ok = !!cond;
  results.push({ name, ok, detail });
  console.log(`[${ok?'PASS':'FAIL'}] ${name}${detail?' | '+detail:''}`);
  return ok;
}
const stamp = (p) => 'T' + p + '-' + Date.now().toString().slice(-6);

(async () => {
  const user = await login();
  if (!user) { console.log('登录失败'); process.exit(2); }
  console.log('登录:', user.username, '(' + user.realName + ')\n');

  // ================= B1 T-06 存量控制器治理 (R-01 + 缺陷O/P) =================
  console.log('===== B1 T-06 存量控制器治理 =====');
  {
    // R-01: 字典按名称搜索生效
    const all = await req('GET','/api/reagent/dict?current=1&size=100');
    const totalAll = Number(all.body.data.total);
    const byName = await req('GET','/api/reagent/dict?current=1&size=100&reagentName=葡萄糖');
    const recs = byName.body.data.records || [];
    check('T-06 字典按名称搜索返回结果', byName.body.code===200 && recs.length>0 && recs.length < totalAll,
      'total='+byName.body.data.total+' 全量='+totalAll);
    check('T-06 名称搜索结果均包含关键字', recs.every(r => (r.reagentName||'').includes('葡萄糖')));

    // 缺陷O: 裸奔写端点已删除（全局异常处理器会把"方法不支持/无静态资源"包成 5xx，故断言非 200 成功即可）
    const r1 = await req('POST','/api/reagent/stock', {});
    const r2 = await req('PUT','/api/reagent/stock', {});
    const r3 = await req('DELETE','/api/reagent/stock/1');
    const r4 = await req('POST','/api/reagent/records', {});
    check('T-06 缺陷O: stock POST 已删除(非200)', r1.status!==200 && r1.status!==201);
    check('T-06 缺陷O: stock PUT 已删除(非200)', r2.status!==200 && r2.status!==201);
    check('T-06 缺陷O: stock DELETE 已删除(非200)', r3.status!==200 && r3.status!==201);
    check('T-06 缺陷O: records POST 已删除(非200)', r4.status!==200 && r4.status!==201);
  }

  // ================= B1 T-10 分类字典 + 危险品 + MSDS (R-06) =================
  console.log('\n===== B1 T-10 分类字典 + MSDS =====');
  let testCatId = null;
  {
    const tree = await req('GET','/api/reagent/category/tree');
    check('T-10 分类树返回多级结构', tree.body.code===200 && Array.isArray(tree.body.data) && tree.body.data.length>=2);
    const catBody = { categoryCode: 'CAT_QA_'+Date.now().toString().slice(-5), categoryName:'QA分类_'+Date.now().toString().slice(-5), parentId:0, hazardCategory:0, sortOrder:999, status:1 };
    const c = await req('POST','/api/reagent/category', catBody);
    check('T-10 创建分类', c.body.code===200 && c.body.data && c.body.data.id);
    testCatId = c.body.data && c.body.data.id;
    if (testCatId) {
      const del = await req('DELETE','/api/reagent/category/'+testCatId);
      check('T-10 删除空分类', del.body.code===200);
    }
  }

  // ================= B1 T-11 多仓库 (R-09) =================
  console.log('\n===== B1 T-11 多仓库 =====');
  let testWhId = null;
  {
    const tree = await req('GET','/api/reagent/warehouse/tree');
    check('T-11 仓库树返回', tree.body.code===200 && Array.isArray(tree.body.data) && tree.body.data.length>0);
    const whBody = { whCode:'WH_QA_'+Date.now().toString().slice(-5), whName:'QA冰箱_'+Date.now().toString().slice(-5), parentId:0, whType:2, tempMin:2, tempMax:8, sortOrder:888, status:1 };
    const w = await req('POST','/api/reagent/warehouse', whBody);
    check('T-11 创建仓库', w.body.code===200 && w.body.data && w.body.data.id);
    testWhId = w.body.data && w.body.data.id;
  }

  // ================= B1 T-09 供应商主数据 (R-04) =================
  console.log('\n===== B1 T-09 供应商主数据 =====');
  let testSupplierId = null;
  {
    // 供应商编码唯一
    const code = 'SUP_QA_'+Date.now().toString().slice(-6);
    const s = await req('POST','/api/reagent/supplier', { supplierCode:code, supplierName:'QA供应商_'+Date.now().toString().slice(-5), qualStatus:1, status:1,
      licenseNo:'LIC-'+code, licenseExpiry:'2027-12-31', medicalCertNo:'MED-'+code, medicalCertExpiry:'2027-12-31' });
    check('T-09 创建供应商', s.body.code===200 && s.body.data && s.body.data.id);
    testSupplierId = s.body.data && s.body.data.id;
    // 重复编码被拒
    if (testSupplierId) {
      const dup = await req('POST','/api/reagent/supplier', { supplierCode:code, supplierName:'QA供应商重复' });
      check('T-09 重复编码被拒', dup.body.code!==200);
    }
    const sel = await req('GET','/api/reagent/supplier/selectable');
    check('T-09 可选供应商(证照有效)', sel.body.code===200 && Array.isArray(sel.body.data) && sel.body.data.length>0);
    const exp = await req('GET','/api/reagent/supplier/expiring?days=30');
    check('T-09 临期供应商筛选接口', exp.body.code===200);
  }

  // ================= B1 T-07 预算域 (R-03) =================
  console.log('\n===== B1 T-07 预算域 =====');
  let testBudgetId = null;
  {
    // 预算维度 (year, dept, category) 唯一，用时间戳生成独立 deptId 避免重复冲突
    const uniqDept = 900000 + (Date.now() % 100000);
    const b = await req('POST','/api/reagent/budget', { budgetYear:2026, deptId:uniqDept, categoryId:null, totalAmount:100000, warnRatio:0.8, status:1, remark:'QA预算_'+Date.now().toString().slice(-5) });
    check('T-07 创建预算', b.body.code===200 && b.body.data && b.body.data.id);
    testBudgetId = b.body.data && b.body.data.id;
    if (testBudgetId) {
      const judge = await req('GET', `/api/reagent/budget/${testBudgetId}/judge?amount=50000`);
      check('T-07 预算判定接口', judge.body.code===200 && judge.body.data==='PASS');
      const re = await req('GET', `/api/reagent/budget/${testBudgetId}/reconcile`);
      check('T-07 预算对账(空预算一致)', re.body.code===200 && re.body.data && re.body.data.consistent===true);
    }
  }

  // ================= B1 T-08 采购闭环 (R-02) =================
  console.log('\n===== B1 T-08 采购申请+审批+订单 =====');
  let testApplyId = null, testOrderId = null;
  {
    const applyBody = { applyDeptId:999, applyDeptName:'QA科室', applyUserId:Number(USER_ID), applyUserName:'QA', budgetYear:2026, urgentFlag:0,
      items:[ { reagentId:100000, quantity:10, unit:'mL', estimatedPrice:35 }, { reagentId:100001, quantity:5, unit:'mL', estimatedPrice:120 } ] };
    const a = await req('POST','/api/reagent/purchase-apply', applyBody);
    check('T-08 创建采购申请(草稿)', a.body.code===200 && a.body.data && a.body.data.id);
    testApplyId = a.body.data && a.body.data.id;
    if (testApplyId) {
      const sub = await req('POST', `/api/reagent/purchase-apply/${testApplyId}/submit?operatorId=${USER_ID}&operatorName=QA`);
      // 无匹配预算时可能阻止；这里兼容两种合理结局(占用成功或"未分配预算"阻断)
      const subOk = sub.body.code===200 && sub.body.data && sub.body.data.verdict;
      check('T-08 提交申请(预算判定)', subOk, 'verdict='+(sub.body.data&&sub.body.data.verdict));
      // 详情
      const det = await req('GET', `/api/reagent/purchase-apply/${testApplyId}`);
      check('T-08 申请详情返回明细/审批流水', det.body.code===200 && det.body.data && det.body.data.apply);
      // 审批通过（若已进入审批）
      if (subOk && sub.body.data.verdict!=='EXCEED') {
        const ap = await req('POST', `/api/reagent/purchase-apply/${testApplyId}/approve`, { action:1, approverId:Number(USER_ID), approverName:'QA批准', opinion:'同意' });
        check('T-08 审批通过', ap.body.code===200);
        // 生成订单
        const orderBody = { applyId:testApplyId, supplierId:100000, supplierName:'罗氏', deptId:999, deptName:'QA科室', operatorId:Number(USER_ID), operatorName:'QA',
          items:[ { applyItemId:null, reagentId:100000, quantity:10, unitPrice:35 }, { applyItemId:null, reagentId:100001, quantity:5, unitPrice:120 } ] };
        const o = await req('POST','/api/reagent/purchase-order', orderBody);
        check('T-08 由批准申请生成订单', o.body.code===200 && o.body.data && o.body.data.id);
        testOrderId = o.body.data && o.body.data.id;
        if (testOrderId) {
          const pl = await req('POST', `/api/reagent/purchase-order/${testOrderId}/place?operatorId=${USER_ID}&operatorName=QA`);
          check('T-08 订单下单', pl.body.code===200);
        }
      }
    }
  }

  // ================= B1 T-12 统一业务单 (R-05) =================
  console.log('\n===== B1 T-12 领用/退库/报废业务单 =====');
  let testReqId = null;
  {
    // 领用单(普通免审)
    const reqBody = { reqType:1, deptId:999, deptName:'QA科室', applyUserId:Number(USER_ID), applyUserName:'QA',
      items:[ { reagentId:100000, stockId:2, lotNo:'LOT20260201', quantity:10, unit:'mL' } ] };
    const r = await req('POST','/api/reagent/requisition', reqBody);
    check('T-12 创建领用单', r.body.code===200 && r.body.data && r.body.data.id);
    testReqId = r.body.data && r.body.data.id;
    if (testReqId) {
      const sub = await req('POST', `/api/reagent/requisition/${testReqId}/submit?operatorId=${USER_ID}&operatorName=QA`);
      check('T-12 领用单提交(普通免审)', sub.body.code===200);
      // 查询库存变动前
      const before = await req('GET','/api/reagent/stock?current=1&size=100');
      const rec = before.body.data.records.find(x => String(x.id)==='2');
      const qtyBefore = rec ? Number(rec.quantity) : null;
      const ex = await req('POST', `/api/reagent/requisition/${testReqId}/execute?operatorId=${USER_ID}&operatorName=QA&deptId=999&deptName=QA科室`);
      check('T-12 领用单执行', ex.body.code===200);
      const after = await req('GET','/api/reagent/stock?current=1&size=100');
      const rec2 = after.body.data.records.find(x => String(x.id)==='2');
      const qtyAfter = rec2 ? Number(rec2.quantity) : null;
      check('T-12 执行后库存扣减10', qtyBefore!=null && qtyAfter!=null && (qtyBefore-qtyAfter)===10, `before=${qtyBefore} after=${qtyAfter}`);
      // 流水确认 recordType=2
      const recList = await req('GET','/api/reagent/records?current=1&size=100');
      const recRows = recList.body.data.records.filter(x => String(x.reagentId)==='100000' || String(x.reagentId)==='2');
      check('T-12 生成领用流水', recRows.length>0);
    }
  }

  // ================= B1 T-13 盘点调账 (R-07) =================
  console.log('\n===== B1 T-13 盘点调账 =====');
  {
    const scBody = { warehouseId:0, includeChildren:true, operatorId:Number(USER_ID), operatorName:'QA', remark:'QA盘点_'+stamp(13) };
    const sc = await req('POST','/api/reagent/stock-check', scBody);
    check('T-13 创建盘点单', sc.body.code===200 && sc.body.data && sc.body.data.id);
    const checkId = sc.body.data && sc.body.data.id;
    if (checkId) {
      const items = await req('GET', `/api/reagent/stock-check/${checkId}/items`);
      const list = items.body.data || [];
      check('T-13 盘点单生成账面明细', items.body.code===200 && list.length>0, 'items='+list.length);
      if (list.length>0) {
        // 对第一条实际数量+5 制造差异
        const it = list[0];
        const submit = await req('POST', `/api/reagent/stock-check/${checkId}/submit?operatorId=${USER_ID}&operatorName=QA&deptId=999&deptName=QA科室`,
          [{ itemId:Number(it.id), actualQty:Number(it.bookQty)+5, remark:'QA调账' }]);
        check('T-13 盘点提交(生成调账流水)', submit.body.code===200);
        // 重复提交被拒
        const dup = await req('POST', `/api/reagent/stock-check/${checkId}/submit`, [{ itemId:Number(it.id), actualQty:99, remark:'' }]);
        check('T-13 已完成盘点单重复提交被拒', dup.body.code!==200);
      }
    }
  }

  // ================= B2 T-14 调拨/借用/归还 (R-08) =================
  console.log('\n===== B2 T-14 调拨/借用/归还 =====');
  {
    // 借用单
    const borrowBody = { reqType:5, deptId:999, deptName:'QA科室', applyUserId:Number(USER_ID), applyUserName:'QA', expectReturnDate:'2026-09-01',
      items:[ { reagentId:100000, stockId:2, lotNo:'LOT20260201', quantity:5, unit:'mL' } ] };
    const b = await req('POST','/api/reagent/requisition', borrowBody);
    check('T-14 创建借用单(reqType=5)', b.body.code===200 && b.body.data && b.body.data.id);
    const bid = b.body.data && b.body.data.id;
    if (bid) {
      const sub = await req('POST', `/api/reagent/requisition/${bid}/submit?operatorId=${USER_ID}&operatorName=QA`);
      check('T-14 借用单提交', sub.body.code===200);
      // 借用单默认需审批，审批通过后方可执行
      const apv = await req('POST', `/api/reagent/requisition/${bid}/approve`, { action:1, approverId:Number(USER_ID), approverName:'QA批准', opinion:'同意' });
      check('T-14 借用单审批通过', apv.body.code===200);
      const ex = await req('POST', `/api/reagent/requisition/${bid}/execute?operatorId=${USER_ID}&operatorName=QA&deptId=999&deptName=QA科室`);
      check('T-14 借用单执行', ex.body.code===200);
    }
    // 调拨单(需双仓库)
    const transferBody = { reqType:4, deptId:999, deptName:'QA科室', applyUserId:Number(USER_ID), applyUserName:'QA', fromWarehouseId:0, toWarehouseId:100000,
      items:[ { reagentId:100000, stockId:2, lotNo:'LOT20260201', quantity:3, unit:'mL' } ] };
    const t = await req('POST','/api/reagent/requisition', transferBody);
    check('T-14 创建调拨单(reqType=4)', t.body.code===200 && t.body.data && t.body.data.id);
    const tid = t.body.data && t.body.data.id;
    if (tid) {
      const sub = await req('POST', `/api/reagent/requisition/${tid}/submit?operatorId=${USER_ID}&operatorName=QA`);
      check('T-14 调拨单提交', sub.body.code===200);
    }
  }

  // ================= B2 T-15 高危审批+环保处置 (R-10) =================
  console.log('\n===== B2 T-15 环保处置 =====');
  {
    const pageAll = await req('GET','/api/reagent/scrap-disposal/page?current=1&size=5');
    check('T-15 处置台账分页', pageAll.body.code===200);
    const saveBody = { reqId:999999, hazardFlag:1, disposalMethod:'高温焚烧', disposalOrg:'XX危废处置中心', disposalCert:'CERT-'+Date.now().toString().slice(-6), disposalDate:'2026-08-11', remark:'QA环保处置' };
    const s = await req('POST','/api/reagent/scrap-disposal', saveBody);
    check('T-15 保存环保处置信息(upsert)', s.body.code===200 && s.body.data && s.body.data.id);
    const byReq = await req('GET','/api/reagent/scrap-disposal/by-req/999999');
    check('T-15 按报废单查询处置信息', byReq.body.code===200 && byReq.body.data && byReq.body.data.disposalMethod==='高温焚烧');
    const exp = await req('GET','/api/reagent/scrap-disposal/export');
    check('T-15 处置台账导出', exp.status===200 && (exp.body.raw||'').includes('disposalMethod'));
  }

  // ================= B2 T-16 供应商评价 (R-11) =================
  console.log('\n===== B2 T-16 供应商评价 =====');
  {
    const rank = await req('GET','/api/reagent/supplier-evaluate/rank?includeManual=true');
    check('T-16 供应商评分排名', rank.body.code===200 && Array.isArray(rank.body.data) && rank.body.data.length>0);
    if (rank.body.data && rank.body.data.length>0) {
      const row = rank.body.data[0];
      check('T-16 评分含自动分/综合分', row.autoScore!=null && row.compositeScore!=null, 'supplier='+row.supplierName);
      if (row.supplierId != null) {
        const ms = await req('POST', `/api/reagent/supplier-evaluate/${row.supplierId}/manual?score=90`);
        check('T-16 保存人工评分', ms.body.code===200);
        const one = await req('GET', `/api/reagent/supplier-evaluate/${row.supplierId}`);
        check('T-16 单供应商评分明细', one.body.code===200 && one.body.data);
      }
    }
  }

  // ================= B2 T-17 移动端扫码 (R-12) =================
  console.log('\n===== B2 T-17 扫码 =====');
  {
    const qry = await req('GET','/api/reagent/scan/query?barcode=BC002');
    check('T-17 扫码查询库存', qry.body.code===200 && qry.body.data && qry.body.data.barcode==='BC002');
    const qryMiss = await req('GET','/api/reagent/scan/query?barcode=NO-SUCH-'+Date.now().toString().slice(-4));
    check('T-17 未命中条码返回null', qryMiss.body.code===200 && (qryMiss.body.data==null));
  }

  // ================= B2 T-18 报表 (R-13) =================
  console.log('\n===== B2 T-18 报表 =====');
  {
    const ps = await req('GET','/api/reagent/report/purchase-stat?groupBy=supplier');
    check('T-18 采购统计(按供应商)', ps.body.code===200 && Array.isArray(ps.body.data));
    const tr = await req('GET','/api/reagent/report/trend?granularity=day&start=2026-08-01&end=2026-08-11');
    check('T-18 领用趋势时间序列', tr.body.code===200 && Array.isArray(tr.body.data), 'rows='+(tr.body.data||[]).length);
    const ss = await req('GET','/api/reagent/report/stock-structure');
    check('T-18 库存结构分析', ss.body.code===200 && Array.isArray(ss.body.data));
    const exp = await req('GET','/api/reagent/report/export/purchase-stat?groupBy=supplier');
    check('T-18 采购统计Excel导出', exp.status===200);
  }

  // ================= B2 T-19 智能检索 (R-14) =================
  console.log('\n===== B2 T-19 智能检索 =====');
  {
    const comb = await req('GET','/api/reagent/dict?current=1&size=50&categoryId=100&minQuantity=0&maxQuantity=10000&hazardFlag=0');
    check('T-19 多条件组合筛选', comb.body.code===200 && Array.isArray(comb.body.data.records));
    const min = await req('GET','/api/reagent/dict?current=1&size=50&minQuantity=500');
    check('T-19 库存量下限筛选', min.body.code===200 && Array.isArray(min.body.data.records));
    const exp = await req('GET','/api/reagent/dict/export');
    check('T-19 试剂字典Excel导出', exp.status===200);
  }

  // ================= 汇总 =================
  const failed = results.filter(x => !x.ok);
  console.log('\n===== 汇总 =====');
  console.log('总用例:', results.length, ' 通过:', results.length-failed.length, ' 失败:', failed.length);
  if (failed.length) {
    console.log('\n失败用例:');
    failed.forEach(f => console.log('  -', f.name, f.detail));
  }
  process.exit(failed.length ? 2 : 0);
})();