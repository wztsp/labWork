const { Client } = require('pg')
const client = new Client({
  host: 'localhost', port: 5432, database: 'lis_db',
  user: 'lis_user', password: 'lis_dev_2026'
})

async function main() {
  await client.connect()

  const codesRes = await client.query(`SELECT perm_code FROM sys_permission`)
  const existsCode = new Set(codesRes.rows.map(r => r.perm_code))
  const existsId = new Set((await client.query(`SELECT id FROM sys_permission`)).rows.map(r => Number(r.id)))
  const { rows: [{ max_id }] } = await client.query(`SELECT COALESCE(MAX(id),0) AS max_id FROM sys_permission`)
  let newId = Number(max_id) + 1
  function pick(id) {
    if (!existsId.has(id)) return id
    return newId++
  }

  const menus = [
    [32, 0, 'stats', '统计分析', 1, '/stats', null, 'stats', 7],
    [33, 0, 'hr', '人事管理', 1, '/hr', null, 'hr', 9],
    [34, 0, 'reagent', '试剂管理', 1, '/reagent', null, 'reagent', 10],
    [35, 0, 'micro', '微生物', 1, '/micro', null, 'micro', 11],
    [36, 0, 'monitor', '监控告警', 1, '/monitor', null, 'monitor', 12],

    // ==== 统计分析 (根=32) ====
    [37, 32, 'stats:workload', '工作量统计', 2, '/stats/workload', null, null, 1],
    [38, 32, 'stats:critical', '危急值统计', 2, '/stats/critical', null, null, 2],
    [39, 32, 'stats:trend', '趋势分析', 2, '/stats/trend', null, null, 3],
    [40, 32, 'stats:positive', '阳性率统计', 2, '/stats/positive-rate', null, null, 4],
    [41, 32, 'stats:charge', '收费统计', 2, '/stats/charge', null, null, 5],

    // ==== 系统管理补充 (根=6, 原27-31是5个子菜单) ====
    [42, 6, 'system:dept', '科室管理', 2, '/system/department', null, null, 6],
    [43, 6, 'system:config', '系统参数', 2, '/system/config', null, null, 7],
    [44, 6, 'system:backup', '数据备份', 2, '/system/backup', null, null, 8],

    // ==== 质控管理补充 (根=3) ====
    [45, 3, 'qc:eqa', '室间质控', 2, '/qc/eqa', null, null, 5],
    [46, 3, 'qc:report', '质控月报', 2, '/qc/report', null, null, 6],

    // ==== 仪器管理补充 (根=4) ====
    [47, 4, 'instrument:maintenance', '维护记录', 2, '/instrument/maintenance', null, null, 6],
    [48, 4, 'instrument:plan', '保养计划', 2, '/instrument/plan', null, null, 7],

    // ==== 人事管理 (根=33) ====
    [49, 33, 'hr:staff', '员工档案', 2, '/hr/staff', null, null, 1],
    [50, 33, 'hr:schedule', '排班管理', 2, '/hr/schedule', null, null, 2],
    [51, 33, 'hr:certificate', '资质证书', 2, '/hr/certificate', null, null, 3],
    [52, 33, 'hr:group', '小组管理', 2, '/hr/group', null, null, 4],
    [53, 33, 'hr:title', '职称管理', 2, '/hr/title', null, null, 5],
    [54, 33, 'hr:training', '培训记录', 2, '/hr/training', null, null, 6],
    [55, 33, 'hr:auth', '授权管理', 2, '/hr/authorization', null, null, 7],
    [56, 33, 'hr:doc', '文档资料', 2, '/hr/document', null, null, 8],

    // ==== 试剂管理 (根=34) ====
    [57, 34, 'reagent:dashboard', '库存看板', 2, '/reagent/dashboard', null, null, 1],
    [58, 34, 'reagent:list', '试剂字典', 2, '/reagent/list', null, null, 2],
    [59, 34, 'reagent:stock', '试剂库存', 2, '/reagent/stock', null, null, 3],
    [60, 34, 'reagent:inbound', '入库验收', 2, '/reagent/inbound', null, null, 4],
    [61, 34, 'reagent:purchase-apply', '采购申请', 2, '/reagent/purchase-apply', null, null, 5],
    [62, 34, 'reagent:purchase-order', '采购订单', 2, '/reagent/purchase-order', null, null, 6],
    [63, 34, 'reagent:consumption', '消耗登记', 2, '/reagent/consumption', null, null, 7],
    [64, 34, 'reagent:requisition', '请领业务单', 2, '/reagent/requisition', null, null, 8],
    [65, 34, 'reagent:check', '库存盘点', 2, '/reagent/check', null, null, 9],
    [66, 34, 'reagent:cost', '成本核算', 2, '/reagent/cost', null, null, 10],
    [67, 34, 'reagent:record', '出入库记录', 2, '/reagent/record', null, null, 11],
    [68, 34, 'reagent:supplier', '供应商管理', 2, '/reagent/supplier', null, null, 12],
    [69, 34, 'reagent:supplier-evaluate', '供应商评价', 2, '/reagent/supplier-evaluate', null, null, 13],
    [70, 34, 'reagent:category', '分类管理', 2, '/reagent/category', null, null, 14],
    [71, 34, 'reagent:warehouse', '仓库管理', 2, '/reagent/warehouse', null, null, 15],
    [72, 34, 'reagent:budget', '预算管理', 2, '/reagent/budget', null, null, 16],
    [73, 34, 'reagent:scrap', '环保处置台账', 2, '/reagent/scrap-disposal', null, null, 17],
    [74, 34, 'reagent:report', '试剂报表', 2, '/reagent/report', null, null, 18],
    [75, 34, 'reagent:mobile', '移动扫码作业', 2, '/reagent/mobile', null, null, 19],

    // ==== 微生物 (根=35) ====
    [76, 35, 'micro:report', '微生物报告', 2, '/report/micro', null, null, 1],
    [77, 35, 'micro:blood-culture', '血培养监测', 2, '/micro/blood-culture', null, null, 2],
    [78, 35, 'micro:bacteria', '细菌字典', 2, '/micro/bacteria', null, null, 3],
    [79, 35, 'micro:antibiotic', '抗生素字典', 2, '/micro/antibiotic', null, null, 4],
    [80, 35, 'micro:resistance', '耐药规则', 2, '/micro/resistance', null, null, 5],
    [81, 35, 'micro:expert-rule', '专家规则', 2, '/micro/expert-rule', null, null, 6],
    [82, 35, 'micro:alert', '耐药预警', 2, '/micro/alert', null, null, 7],
    [83, 35, 'micro:instrument-import', '仪器导入', 2, '/micro/instrument-import', null, null, 8],
    [84, 35, 'micro:print-config', '打印模板配置', 2, '/micro/print-config', null, null, 9],
    [85, 35, 'micro:epidemiology', '流行病学统计', 2, '/micro/epidemiology', null, null, 10],
    [86, 35, 'micro:qc', '微生物室内质控', 2, '/micro/qc', null, null, 11],
    [87, 35, 'micro:medium-type', '培养基类型', 2, '/micro/medium-type', null, null, 12],
    [88, 35, 'micro:culture-plan', '培养方案配置', 2, '/micro/culture-plan', null, null, 13],

    // ==== 监控告警 (根=36) ====
    [89, 36, 'monitor:tat-rule', 'TAT规则设置', 2, '/monitor/tat-rule', null, null, 1],
    [90, 36, 'monitor:tat-monitor', 'TAT业务监控', 2, '/monitor/tat-monitor', null, null, 2],
    [91, 36, 'monitor:infectious', '传染病项目配置', 2, '/monitor/infectious', null, null, 3],
    [92, 36, 'monitor:message', '消息中心', 2, '/monitor/message', null, null, 4],

    // ==== 标本管理补充 (根=1) ====
    [93, 1, 'sample:receive2', '标本核收', 2, '/sample/receive', null, null, 5],
    [94, 1, 'sample:batch-import', '批量导入', 2, '/sample/batch-import', null, null, 6],

    // ==== 报告管理补充 (根=2) ====
    [95, 2, 'report:audit-page', '报告审核', 2, '/report/audit', null, null, 5],
    [96, 2, 'report:list-page', '报告列表', 2, '/report/list', null, null, 6],
  ]

  const insertedIds = []
  for (const [sid, pid, code, name, type, path, component, icon, sort] of menus) {
    if (existsCode.has(code)) {
      console.log(`Skip existing perm_code=${code} (${name})`)
      continue
    }
    const id = pick(sid)
    await client.query(`
      INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, status)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 1)
    `, [id, pid, code, name, type, path, component, icon, sort])
    insertedIds.push(id)
    console.log(`INSERT id=${id} code=${code} name=${name}`)
  }

  const allAssignIds = Array.from(new Set([...insertedIds, 32, 33, 34, 35, 36]))
  let assignCount = 0
  for (const pid of allAssignIds) {
    try {
      const { rowCount } = await client.query(`
        INSERT INTO sys_role_permission (role_id, permission_id)
        VALUES (1, $1)
        ON CONFLICT ON CONSTRAINT sys_role_permission_pkey DO NOTHING
      `, [pid])
      if (rowCount && rowCount > 0) assignCount++
    } catch (e) {
      // 没唯一约束的情况下 fallback: SELECT后判断
      try {
        const rr = await client.query(`SELECT 1 FROM sys_role_permission WHERE role_id=1 AND permission_id=$1`, [pid])
        if (rr.rows.length === 0) {
          await client.query(`INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, $1)`, [pid])
          assignCount++
        }
      } catch (e2) { console.warn(e2.message) }
    }
  }
  console.log(`\n新增权限: ${insertedIds.length} 条, ADMIN分配关联: ${assignCount} 条`)
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
