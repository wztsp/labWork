/* eslint-disable */
// ==========================================
// LIS 模拟 5 条微生物全流程数据 v2 (Node.js)
//   0. 自动写入内置培养基/培养方案 (如为空)
//   1. 创建申请 (HIS->LIS)
//   2. 计算试管分组 (合管)
//   3. 批量生成采集条码 (sample/batch)
//   4. 匹配培养方案 -> 生成培养基条码
//   5. 汇总 (微生物报告核收可用)
// ==========================================
import http from 'node:http'

const BASE = 'localhost'
const PORT = 8080

function req(path, { method = 'GET', body = null, token = null, timeoutMs = 20000 } = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? Buffer.from(JSON.stringify(body), 'utf8') : null
    const opts = {
      hostname: BASE,
      port: PORT,
      path: '/api' + path,
      method,
      timeout: timeoutMs,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        ...(data ? { 'Content-Length': data.length } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      }
    }
    const rq = http.request(opts, (res) => {
      const chunks = []
      res.on('data', (c) => chunks.push(c))
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8')
        try { resolve({ status: res.statusCode, data: raw ? JSON.parse(raw) : null }) }
        catch (e) { resolve({ status: res.statusCode, data: raw, _raw: true }) }
      })
    })
    rq.on('error', reject)
    rq.on('timeout', () => rq.destroy(new Error('timeout')))
    if (data) rq.write(data)
    rq.end()
  })
}
const log = {
  cyan: (s) => console.log('\x1b[36m%s\x1b[0m', s),
  green: (s) => console.log('\x1b[32m%s\x1b[0m', s),
  yellow: (s) => console.log('\x1b[33m%s\x1b[0m', s),
  red: (s) => console.log('\x1b[31m%s\x1b[0m', s),
  info: (s) => console.log(s),
}
function toArray(x) {
  if (Array.isArray(x)) return x
  if (x && typeof x === 'object') {
    if (Array.isArray(x.data)) return x.data
    if (Array.isArray(x.records)) return x.records
    if (Array.isArray(x.list)) return x.list
    if (x.data && typeof x.data === 'object') {
      if (Array.isArray(x.data.records)) return x.data.records
      if (Array.isArray(x.data.list)) return x.data.list
    }
  }
  return []
}
function unwrapOne(x) {
  if (x === undefined || x === null) return x
  if (Array.isArray(x)) return x
  if (typeof x !== 'object') return x
  if ('data' in x) {
    const inner = x.data
    if (inner !== undefined && inner !== null && typeof inner === 'object' && !Array.isArray(inner)) {
      if (('records' in inner) || ('list' in inner) || ('data' in inner && inner.data !== undefined)) {
        return unwrapOne(inner)
      }
    }
    return inner
  }
  return x
}
function pickFirst(list, fallbackFields = ['id']) {
  if (!list) return null
  if (Array.isArray(list)) return list.length > 0 ? list[0] : null
  if (typeof list === 'object') {
    for (const f of fallbackFields) if (list[f] !== undefined) return list
  }
  return list
}

// ============ MAIN ============
;(async () => {
  // 1) login
  log.cyan('\n=== 1. 登录 ===')
  let token = null
  try {
    const r = await req('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
    const d = unwrapOne(r.data) || {}
    token = d.token || (r.data && r.data.token) || (r.data && r.data.data && r.data.data.token)
    if (!token) { log.red('登录失败: ' + JSON.stringify(r.data).slice(0, 300)); process.exit(1) }
    log.green('  Token OK')
  } catch (e) { log.red('登录异常: ' + e.message); process.exit(1) }
  const T = { token }

  // ---- helpers with token ----
  const GET  = (p) =>       req(p, { ...T })
  const POST = (p, b) =>    req(p, { method: 'POST', body: b, ...T })
  const PUT  = (p, b) =>    req(p, { method: 'PUT',  body: b, ...T })

  // 2) init medium & culture plan (if empty)
  log.cyan('\n=== 2. 初始化内置培养基/培养方案数据 (如为空) ===')
  let plans = []
  // 先强制清空所有旧方案：因为 /all 接口路由可能被吞，用 /page 查询
  log.cyan('\n  → 清空旧培养方案 (用 /page 分页接口查询所有方案ID后删除)')
  const pagePreResp = await GET('/micro/culture-plan?current=1&size=500')
  const preList = toArray(unwrapOne(pagePreResp.data))
  log.info(`    通过/page接口查到 ${preList.length} 条旧方案`)
  if (preList.length > 0) {
    preList.forEach(p => log.info(`      id=${p.id} name=${p.planName} testItemId=${p.testItemId ?? p.test_item_id ?? 'NULL'}`))
    for (const p of preList) {
      try {
        const delR = await req(`/micro/culture-plan/${p.id}`, { method: 'DELETE', token })
        if (delR.status && delR.status >= 400) log.yellow(`      删除 id=${p.id} HTTP ${delR.status}: ${JSON.stringify(delR.data).slice(0,150)}`)
      } catch (e) {}
    }
    log.info(`    旧方案删除完成`)
  }
  // 再次确认已空
  const confirmEmpty = toArray(unwrapOne((await GET('/micro/culture-plan?current=1&size=500')).data))
  log.info(`    确认培养方案表剩余: ${confirmEmpty.length} 条`)
  plans = []

  let mediums = toArray(unwrapOne((await GET('/micro/medium-type/all')).data))
  log.info(`  现有培养基数量: ${mediums.length}`)
  if (mediums.length === 0) {
    log.yellow('  培养基为空，正在写入 20 种内置...')
    const MED = [
      ['BAP','血平板','血','通用分离培养'],
      ['MAC','麦康凯平板','麦','肠道革兰阴性杆菌分离'],
      ['CHOC','巧克力平板','巧','嗜血杆菌/奈瑟菌等苛养菌'],
      ['SS','SS平板','SS','沙门菌/志贺菌分离'],
      ['EMB','伊红美蓝平板','EM','肠道菌分离鉴别'],
      ['XLD','木糖赖氨酸脱氧胆盐平板','XL','沙门菌/志贺菌分离'],
      ['SDA','沙保弱葡萄糖琼脂','沙','真菌分离培养'],
      ['CA','哥伦比亚血琼脂','哥','苛养菌和厌氧'],
      ['CNA','哥伦比亚CNA琼脂','CN','革兰阳性菌选择'],
      ['AN','厌氧血平板','厌','厌氧菌分离'],
      ['KV','卡那霉素万古霉素平板','KV','厌氧菌选择(拟杆菌)'],
      ['PEA','苯乙基乙醇平板','PE','厌氧/革兰阳性选择'],
      ['T-M','Thayer-Martin平板','TM','淋病奈瑟菌选择'],
      ['BCYE','缓冲炭酵母浸膏','BC','军团菌分离'],
      ['LJ','罗氏培养基','罗','结核分枝杆菌'],
      ['MB','Middlebrook 7H10','MB','分枝杆菌分离'],
      ['TCBS','硫代硫酸盐枸橼酸盐胆盐平板','TC','弧菌分离'],
      ['Cary-Blair','Cary-Blair运送','CB','标本运送'],
      ['Stuart','Stuart运送','ST','运送培养基'],
      ['MHA','MH琼脂平板','MH','药敏KB纸片法'],
    ]
    for (const m of MED) {
      const r = await POST('/micro/medium-type', { mediumCode:m[0], mediumName:m[1], mediumAbbr:m[2], remark:m[3], status:1 })
      const obj = unwrapOne(r.data)
      mediums.push(Array.isArray(obj) ? (obj[0]||{}) : (obj||{}))
    }
    log.green(`  写入完成，培养基共 ${mediums.length} 种`)
  } else {
    log.green('  培养基已存在，跳过写入')
  }

  // 强制写入 10 种新方案（表已空）
  log.yellow('  培养方案已清空，写入 10 种内置...')
  const items = toArray(unwrapOne((await GET('/base/items?current=1&size=500')).data))
  function pickItem(kws) {
    for (const kw of kws) {
      const hit = items.find(i =>
        ((i.itemName || i.testItemName || '') + (i.itemCode || i.testItemCode || '')).includes(kw)
      )
      if (hit) return hit
    }
    return null
  }
  const mediumByName = (name) => mediums.find(m => (m.mediumName || '').includes(name)) || mediums[0]
  const PLAN_TEMPLATE = [
    { name:'痰培养方案',     testItemKws:['痰培养','一般细菌培养','MIC_GEN'], medias:['血平板','麦康凯平板','巧克力平板'] },
    { name:'尿培养方案',     testItemKws:['尿培养','尿一般细菌','尿液','MIC_URINE'], medias:['血平板','麦康凯平板'] },
    { name:'血培养方案',     testItemKws:['血液培养','血培养','MIC_BLD'],       medias:['血平板','厌氧血平板','巧克力平板'] },
    { name:'分泌物培养方案', testItemKws:['分泌物培养','脓液','MIC_SECR'],       medias:['血平板','麦康凯平板','巧克力平板'] },
    { name:'脑脊液培养方案', testItemKws:['脑脊液','MIC_CSF'],                  medias:['血平板','巧克力平板','沙保弱葡萄糖琼脂'] },
    { name:'胸腹培养方案',   testItemKws:['胸腹水','胸腹','MIC_FLUID'],            medias:['血平板','麦康凯平板','巧克力平板'] },
    { name:'粪便培养方案',   testItemKws:['粪便培养','大便培养','MIC_STOOL'],      medias:['SS平板','麦康凯平板','木糖赖氨酸脱氧胆盐平板'] },
    { name:'真菌培养方案',   testItemKws:['真菌','MIC_FUNGUS'],                     medias:['沙保弱葡萄糖琼脂','沙保弱葡萄糖琼脂'] },
    { name:'厌氧培养方案',   testItemKws:['厌氧','MIC_ANA'],                     medias:['厌氧血平板','卡那霉素万古霉素平板','苯乙基乙醇平板'] },
    { name:'淋球菌培养方案', testItemKws:['淋球菌','淋病'],                   medias:['Thayer-Martin平板','巧克力平板'] },
  ]
  for (const tpl of PLAN_TEMPLATE) {
    let testItemId = null, testItemName = ''
    const it = pickItem(tpl.testItemKws)
    if (it) { testItemId = it.id ?? it.testItemId; testItemName = it.itemName ?? it.testItemName }
    const fallback = items.find(x => /培养/.test(x.itemName || x.testItemName || ''))
    if (!testItemId && fallback) { testItemId = fallback.id ?? fallback.testItemId; testItemName = fallback.itemName ?? fallback.testItemName }
    const itemsForPlan = []
    for (let i = 0; i < tpl.medias.length; i++) {
      const med = mediumByName(tpl.medias[i])
      itemsForPlan.push({
        mediumTypeId: med.id,
        mediumTypeName: med.mediumName,
        quantity: 1,
        sortOrder: i + 1
      })
    }
    const body = {
      plan: { planName:tpl.name, testItemId, testItemName, remark:'系统内置方案', isDefault:0, status:1 },
      items: itemsForPlan
    }
    const r = await POST('/micro/culture-plan', body)
    if (r.status && r.status >= 400) log.red(`    [POST] HTTP ${r.status}: ${JSON.stringify(r.data).slice(0, 200)}`)
    const saved = unwrapOne(r.data) || (r.data && r.data.data) || {}
    plans.push(saved)
    log.green(`    + ${tpl.name}  (绑定项目: id=${testItemId} name=${testItemName||'未绑定'})  培养基明细 ${itemsForPlan.length} 条  返回保存id=${saved.id}`)
  }
  // 用分页接口刷新
  const pageAfter = toArray(unwrapOne((await GET('/micro/culture-plan?current=1&size=500')).data))
  plans = pageAfter
  log.info(`  培养基=${mediums.length}  培养方案=${plans.length}`)
  plans.forEach(p => log.info(`    方案: id=${p.id} name=${p.planName} testItemId=${p.testItemId ?? p.test_item_id ?? 'NULL'} tName=${p.testItemName ?? p.test_item_name ?? 'NULL'}`))

  // 3) 获取微生物检验项目，并打印所有 micro items 便于调试
  log.cyan('\n=== 3. 匹配微生物检验项目（并打印明细） ===')
  let allItems = toArray(unwrapOne((await GET('/base/items/dict')).data))
  if (allItems.length === 0) allItems = toArray(unwrapOne((await GET('/base/items?current=1&size=500')).data))
  function norm(it) {
    // Long 类型 ID 保留为字符串，防止 Number() 精度丢失（雪花 ID 超过 MAX_SAFE_INTEGER）
    return {
      id: String(it.id ?? it.testItemId ?? ''),
      code: it.itemCode ?? it.testItemCode ?? '',
      name: it.itemName ?? it.testItemName ?? ''
    }
  }
  const microItems = allItems.filter(i => {
    const n = norm(i)
    return /培养|微生物|MIC_/i.test(n.code + ' ' + n.name)
  })
  log.info(`  微生物类项目共 ${microItems.length} 条 (总项目 ${allItems.length}):`)
  microItems.forEach((it, idx) => {
    const n = norm(it)
    log.info(`    ${String(idx+1).padStart(2,' ')}. id=${n.id}  code=${n.code.padEnd(20,' ')}  name=${n.name}`)
  })

  // ====== 关键修复：5 位患者的检验项目直接从培养方案中取绑定的 testItemId/testItemName ======
  // 这样才能保证方案的 testItemId 和申请中的 testItemId 完全一致，后端 generatePlates 的 IN 查询才能命中
  function resolveItemFromPlan(planName, fallbackKws) {
    log.info(`    [resolveItemFromPlan] 查找 ${planName}...`)
    // 1. 优先在 plans 里找对应方案，取它绑定的
    const hit = plans.find(p =>
      (p.planName || '') === planName ||
      ((p.planName || '').includes(planName.slice(0,2))) // 兼容痰培养/痰培养方案这样的
    )
    if (hit) {
      const tId = String(hit.testItemId ?? hit.test_item_id ?? '')
      const tName = hit.testItemName ?? hit.test_item_name
      log.info(`      命中方案 id=${hit.id} name=${hit.planName} tId=${tId} tName=${tName}`)
      if (tId && tId !== 'null' && tId !== 'undefined' && tId.length > 0 && Number(tId) > 0) {
        return { id: tId, code: '', name: tName || '' }  // ID 保留为字符串防精度丢失
      }
    }
    log.yellow(`      未通过方案找到，fallback 关键字匹配 ${fallbackKws}`)
    // 2. fallback: 用关键字在 microItems 中找
    for (const kw of fallbackKws) {
      const r = microItems.find(i => (norm(i).code + ' ' + norm(i).name).includes(kw))
      if (r) { const n = norm(r); log.info(`      关键字匹配 ${kw}: ${n.id}/${n.name}`); return n }
    }
    return microItems.length > 0 ? norm(microItems[0]) : (allItems.length ? norm(allItems[0]) : null)
  }

  const itemSputum    = resolveItemFromPlan('痰培养方案', ['痰培养','痰一般','MIC_GEN','痰'])
  const itemUrine     = resolveItemFromPlan('尿培养方案', ['尿培养','尿液','尿一般','尿'])
  const itemBlood     = resolveItemFromPlan('血培养方案', ['血培养','血液培养','BLOOD_CULTURE','血'])
  const itemSecretion = resolveItemFromPlan('分泌物培养方案', ['分泌物培养','MIC_SECR','分泌','脓'])
  const itemStool     = resolveItemFromPlan('粪便培养方案', ['粪便培养','MIC_STOOL','大便','粪便'])
  log.info('  痰培养   -> ' + (itemSputum    ? `${itemSputum.id}/${itemSputum.code}/${itemSputum.name}` : 'NULL'))
  log.info('  尿培养   -> ' + (itemUrine     ? `${itemUrine.id}/${itemUrine.code}/${itemUrine.name}` : 'NULL'))
  log.info('  血培养   -> ' + (itemBlood     ? `${itemBlood.id}/${itemBlood.code}/${itemBlood.name}` : 'NULL'))
  log.info('  分泌物   -> ' + (itemSecretion ? `${itemSecretion.id}/${itemSecretion.code}/${itemSecretion.name}` : 'NULL'))
  log.info('  粪便培养 -> ' + (itemStool     ? `${itemStool.id}/${itemStool.code}/${itemStool.name}` : 'NULL'))

  // 4) 5位患者
  const visitTypes = { '门诊':1, '住院':2, '急诊':3 }
  const patients = [
    { Name:'张三', Gender:'男', Age:'45岁', Type:'门诊', Dept:'呼吸内科', Doctor:'李医生', Diag:'咳嗽咳痰3天，疑似肺部感染',       Item:itemSputum,    Cond:'空腹',  SampleType:'呼吸道分泌物' },
    { Name:'李四', Gender:'女', Age:'32岁', Type:'门诊', Dept:'肾内科',   Doctor:'王医生', Diag:'尿频尿急，疑似尿路感染',         Item:itemUrine,     Cond:'',      SampleType:'尿液' },
    { Name:'王五', Gender:'男', Age:'67岁', Type:'急诊', Dept:'急诊科',   Doctor:'赵医生', Diag:'发热39.2度，疑似败血症',         Item:itemBlood,     Cond:'空腹',  SampleType:'血液' },
    { Name:'赵六', Gender:'女', Age:'29岁', Type:'门诊', Dept:'普外科',   Doctor:'钱医生', Diag:'术后伤口红肿渗出',               Item:itemSecretion, Cond:'',      SampleType:'分泌物' },
    { Name:'孙七', Gender:'男', Age:'55岁', Type:'住院', Dept:'消化内科', Doctor:'孙医生', Diag:'腹泻伴发热2天',                 Item:itemStool,     Cond:'',      SampleType:'粪便' },
  ]

  // 5) 创建申请 + 生成标本
  log.cyan('\n=== 4. 创建申请 & 生成采集条码 (申请→合管→采集条码) ===')
  const samples = []
  for (let i = 0; i < patients.length; i++) {
    const p = patients[i]
    if (!p.Item) { log.yellow(`  [${i+1}/5] 跳过 ${p.Name} (未找到对应检验项目)`); continue }
    log.info(`\n  [${i+1}/5] ${p.Name} / ${p.SampleType} / ${p.Diag.slice(0,14)}`)

    // 5.1 创建申请
    const orderBody = {
      patientName: p.Name, patientGender: p.Gender, patientAge: p.Age,
      orderType: p.Type, visitType: visitTypes[p.Type],
      applyDoctor: p.Doctor, applyDept: p.Dept, clinicalDiag: p.Diag,
      patientCondition: p.Cond || '',
      collectionMethodId: null, collectionMethodName: '常规采集',
      specimenType: p.SampleType,
      items: [{ testItemId: p.Item.id, testItemCode: p.Item.code, testItemName: p.Item.name }]
    }
    let order = unwrapOne((await POST('/sample/orders/receive', orderBody)).data)
    if (Array.isArray(order)) order = order[0]
    if (!order || !order.id) {
      log.red('    创建申请失败: ' + JSON.stringify(order).slice(0,300))
      continue
    }
    log.green(`    ✔ 申请创建: orderId=${order.id}  orderNo=${order.orderNo}  visitId=${order.visitId}  patientId=${order.patientId}`)

    // 5.2 合管分组
    let groups = toArray(unwrapOne((await GET(`/sample/tube-groups?visitId=${order.visitId}`)).data))
    if (!groups || groups.length === 0) {
      log.yellow('    合管为空，使用兜底分组 (1管 = 1申请项目)')
      // fallback: 手工构造一个 group
      groups = [{
        groupIndex: 1,
        tubeId: null,
        tubeName: p.SampleType + '管',
        sampleType: p.SampleType,
        containerType: '无试管',
        testItems: [{
          testItemId: p.Item.id,
          itemCode: p.Item.code,
          itemName: p.Item.name,
          orderId: order.id,
          orderItemId: (order.items && order.items[0] && order.items[0].id) || null
        }]
      }]
    }
    log.info(`    合管分组: ${groups.length} 组`)

    // 5.3 生成标本条码 (sample/batch)
    const batch = {
      visitId: order.visitId,
      patientId: order.patientId,
      patientName: order.patientName || p.Name,
      visitNo: order.visitNo || '',
      department: order.applyDept || p.Dept,
      groups: groups.map(g => ({
        tubeId: g.tubeId || null,
        tubeName: g.tubeName || '',
        sampleType: g.sampleType || p.SampleType,
        containerType: g.containerType || '',
        testItems: (g.testItems || []).map(t => ({
          testItemId: t.testItemId,
          orderId: t.orderId ?? order.id,
          orderItemId: t.orderItemId ?? null
        }))
      }))
    }
    const created = toArray(unwrapOne((await POST('/sample/batch', batch)).data))
    if (created.length === 0) {
      log.yellow('    生成标本条码返回为空，尝试用 sample/page 查询已生成标本')
      // fallback: 用 orderId 查标本
      const pageData = unwrapOne((await GET(`/sample/page?current=1&size=20&orderId=${order.id}`)).data)
      const list = toArray(pageData)
      created.push(...list.filter(s => String(s.orderId) === String(order.id) || s.orderId == order.id))
    }
    for (const s of created) {
      samples.push({
        No: i + 1,
        Patient: p.Name, Gender: p.Gender, Age: p.Age, OrderType: p.Type,
        Dept: p.Dept, Doctor: p.Doctor, Diag: p.Diag,
        TestItem: p.Item.name, TestItemId: p.Item.id,
        OrderId: order.id, OrderNo: order.orderNo,
        VisitId: order.visitId, PatientId: order.patientId, VisitNo: order.visitNo || '',
        SampleId: s.id,
        SampleBarcode: s.sampleBarcode,
        SampleType: s.sampleType || g_sampleType(groups) || p.SampleType,
      })
      log.green(`    ✔ 生成采集条码: ${s.sampleBarcode}  类型=${s.sampleType || p.SampleType}  id=${s.id}`)
    }
  }

  function g_sampleType(groups){ if (!groups) return ''; return (groups[0] && groups[0].sampleType) || '' }

  // 6) 匹配方案生成培养基条码
  log.cyan('\n=== 5. 匹配培养方案 & 生成培养基条码 ===')
  const plates = []
  // 再次读取方案详情 (含明细)
  async function planDetail(pid) {
    const d = unwrapOne((await GET(`/micro/culture-plan/${pid}`)).data)
    return d || null
  }
  function matchPlan(patient) {
    const name = patient.TestItem + ' ' + patient.Diag
    // 优先按 TestItemId 精确匹配（最可靠），用 String 比较防止精度差异
    const byId = plans.find(p => String(p.testItemId ?? p.test_item_id ?? '') === String(patient.TestItemId ?? ''))
    if (byId) return byId
    // 再按检验项目名精确匹配
    const exact = plans.find(p => (p.testItemName ?? p.test_item_name ?? '') === patient.TestItem)
    if (exact) return exact
    // 再按方案名包含检验项目名
    const byName = plans.find(p =>
      (p.planName || '').includes(patient.TestItem) ||
      patient.TestItem.includes(p.planName || '')
    )
    if (byName) return byName
    // 再按关键字规则
    const rules = [
      { name:'痰培养方案',      kw:['痰','肺部','呼吸','呼吸道'] },
      { name:'尿培养方案',      kw:['尿','泌尿','肾','尿频'] },
      { name:'血培养方案',      kw:['血培养','败血症','发热','血液'] },
      { name:'分泌物培养方案',  kw:['分泌','伤口','脓','渗出'] },
      { name:'粪便培养方案',    kw:['粪便','大便','腹泻','消化'] },
      { name:'脑脊液培养方案',  kw:['脑脊液','脑膜'] },
      { name:'胸腹培养方案',    kw:['胸','腹'] },
      { name:'真菌培养方案',    kw:['真菌'] },
      { name:'厌氧培养方案',    kw:['厌氧'] },
      { name:'淋球菌培养方案',  kw:['淋球菌','淋病'] },
    ]
    for (const r of rules) if (r.kw.some(k => name.includes(k))) {
      const hit = plans.find(p => (p.planName || '') === r.name || (p.planName || '').includes(r.name))
      if (hit) return hit
    }
    return plans[0] || null
  }

  for (const s of samples) {
    const plan = matchPlan(s)
    if (!plan) { log.yellow(`  [${s.No}] ${s.Patient} 未匹配方案，跳过培养基生成`); continue }
    const detail = await planDetail(plan.id)
    const mediaCount = (detail && detail.items && detail.items.length) || 0
    const planTestItemId = String(plan.testItemId ?? plan.test_item_id ?? '')
    log.info(`  [${s.No}] ${s.Patient}  标本=${s.SampleBarcode}  方案=${plan.planName}(id=${plan.id},planTID=${planTestItemId} 申请TID=${String(s.TestItemId)})  培养基明细=${mediaCount}`)
    const body = {
      sampleBarcode: s.SampleBarcode,
      sampleId: s.SampleId,           // 保留原类型 (Long 字符串) 不 Number 化
      testItemIds: [s.TestItemId],    // 保留原类型 (Long 字符串) 不 Number 化
      patientName: s.Patient,
      operatorName: '系统管理员'
    }
    const rawResp = await POST('/micro/sample-plate/generate', body)
    if (rawResp.status && rawResp.status >= 400) {
      const m = rawResp.data && (rawResp.data.message || rawResp.data.msg || JSON.stringify(rawResp.data))
      log.red(`    生成培养基失败 HTTP ${rawResp.status}: ${String(m).slice(0,400)}`)
      continue
    }
    const list = toArray(unwrapOne(rawResp.data))
    if (list.length === 0 && rawResp.data) {
      const m = rawResp.data.message || rawResp.data.msg
      if (m) log.red(`    生成失败: ${m}  body=${JSON.stringify(body)}`)
      else log.yellow(`    生成返回空数组 (未知原因)。resp=${JSON.stringify(rawResp.data).slice(0,400)}`)
      continue
    }
    log.green(`    生成 ${list.length} 管培养基:`)
    for (const pl of list) {
      log.green(`      · [${pl.seqNo}] ${pl.mediumTypeName} -> ${pl.plateBarcode}`)
      plates.push({
        No: s.No, Patient: s.Patient, Dept: s.Dept,
        TestItem: s.TestItem,
        SampleBarcode: s.SampleBarcode,
        PlanName: plan.planName,
        MediumName: pl.mediumTypeName,
        SeqNo: pl.seqNo,
        PlateBarcode: pl.plateBarcode,
        PlateStatus: pl.status ?? 0,
      })
    }
  }

  // 7) 汇总
  log.cyan('\n==================================================')
  log.cyan('  ★★★ 模拟 5 条微生物全流程数据完成 ★★★')
  log.cyan('==================================================\n')

  log.cyan('【表 1 — 申请/标本/采集条码】')
  const t1 = samples.map(s => ({
    序号: s.No,
    患者: `${s.Patient}(${s.Gender}/${s.Age})`,
    科室_类型: `${s.Dept}/${s.OrderType}`,
    诊断: (s.Diag.length > 14 ? s.Diag.slice(0,14)+'…' : s.Diag),
    检验项目: s.TestItem,
    申请单号: s.OrderNo,
    采集条码: s.SampleBarcode
  }))
  console.table(t1)

  log.cyan('\n【表 2 — 培养基条码（共 ' + plates.length + ' 管）】→ 微生物报告页面核收使用这些条码')
  const t2 = plates.map(p => ({
    序号: p.No,
    患者: p.Patient,
    采集条码: p.SampleBarcode,
    培养方案: p.PlanName,
    管序: p.SeqNo,
    培养基: p.MediumName,
    培养基条码: p.PlateBarcode,
  }))
  console.table(t2)

  log.cyan('\n【浏览器端操作指引】')
  log.info('  A. 培养基条码生成页  #/micro/patient-data-by-barcode')
  log.info('     · 输入【表 1】中"采集条码"回车查询 → 核对患者信息 → 选择方案 → 生成/重打 → 打印标签')
  log.info('  B. 微生物报告页  #/report/micro')
  log.info('     · 顶部【培养基条码核收】输入框中，复制【表 2】中的"培养基条码"后回车 → 自动核收/创建报告')
  log.info('     · 然后在左侧列表中可看到刚创建的报告')
  log.info('')
  log.cyan('【复制即用 - 培养基条码清单 (按患者排序)】')
  for (const p of plates) log.green(`  ${String(p.No).padStart(2,' ')}  ${p.Patient.padEnd(2,' ')}  ${p.MediumName.padEnd(16,' ')}  ${p.PlateBarcode}`)
  log.info('')
})()
