/* eslint-disable */
// ==========================================
// LIS 模拟 5 条微生物全流程数据 (Node.js UTF-8)
// 申请 -> 标本 -> 采集 -> 培养基条码生成
// ==========================================
import http from 'node:http'

const BASE = 'localhost'
const PORT = 8080

// ---- HTTP helper ----
function req(path, { method = 'GET', body = null, token = null, timeoutMs = 15000 } = {}) {
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
    const req = http.request(opts, (res) => {
      const chunks = []
      res.on('data', (c) => chunks.push(c))
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8')
        try {
          resolve({ status: res.statusCode, data: raw ? JSON.parse(raw) : null })
        } catch (e) {
          resolve({ status: res.statusCode, data: raw, _raw: true })
        }
      })
    })
    req.on('error', reject)
    req.on('timeout', () => { req.destroy(new Error('timeout')) })
    if (data) req.write(data)
    req.end()
  })
}

const log = {
  cyan: (s) => console.log('\x1b[36m%s\x1b[0m', s),
  green: (s) => console.log('\x1b[32m%s\x1b[0m', s),
  yellow: (s) => console.log('\x1b[33m%s\x1b[0m', s),
  red: (s) => console.log('\x1b[31m%s\x1b[0m', s),
  info: (s) => console.log(s)
}

// unwrap: { data: ... } or { code: 0, data: ... } or raw value
function unwrap(r) {
  if (!r || r.data === undefined || r.data === null) return r
  const d = r.data
  if (d && typeof d === 'object') {
    // { data: T, code }  or { code:0, msg:'', data:T }
    if ('data' in d && (d.code !== undefined || Object.keys(d).length <= 4)) {
      const inner = d.data
      if (inner !== undefined) return inner
    }
    if ('records' in d) return d.records
    if ('list' in d) return d.list
  }
  return d
}
function toArray(x) {
  if (Array.isArray(x)) return x
  if (x && Array.isArray(x.data)) return x.data
  if (x && Array.isArray(x.records)) return x.records
  if (x && Array.isArray(x.list)) return x.list
  return []
}

;(async () => {
  // 1) Login
  log.cyan('\n=== 1. 登录获取 Token ===')
  let token = null
  try {
    const r = await req('/auth/login', {
      method: 'POST',
      body: { username: 'admin', password: '123456' }
    })
    const d = unwrap(r)
    token = d && d.token ? d.token : (r.data && r.data.token ? r.data.token : null)
    if (!token) {
      // fallback: try raw
      const data = r.data
      token = (data && data.data && data.data.token) ? data.data.token : null
    }
    if (!token) {
      log.red('  登录失败, 返回: ' + JSON.stringify(r.data).slice(0, 200))
      process.exit(1)
    }
    log.green(`  Token 获取成功: ${token.slice(0, 16)}...`)
  } catch (e) {
    log.red('  登录异常: ' + e.message)
    process.exit(1)
  }

  // 2) 拉取检验项目
  log.cyan('\n=== 2. 获取检验项目字典 ===')
  let items = []
  try {
    const r1 = await req('/base/items/dict', { token })
    items = toArray(unwrap(r1) || r1.data)
    if (items.length === 0) {
      const r2 = await req('/base/items?current=1&size=500', { token })
      items = toArray(unwrap(r2) || r2.data)
    }
  } catch (e) {
    log.yellow('  dict接口失败，尝试分页接口: ' + e.message)
  }
  log.info(`  检验项目共 ${items.length} 条`)

  function normItem(it) {
    return {
      id: it.id ?? it.testItemId,
      code: it.itemCode ?? it.testItemCode ?? '',
      name: it.itemName ?? it.testItemName ?? ''
    }
  }
  function findItem(keyword) {
    const kws = Array.isArray(keyword) ? keyword : [keyword]
    for (const kw of kws) {
      const r = items.filter(i => {
        const x = normItem(i)
        const s = (x.name + ' ' + x.code)
        return s.includes(kw)
      })
      if (r.length > 0) return normItem(r[0])
    }
    return items.length > 0 ? normItem(items[0]) : null
  }

  const sputumItem    = findItem(['痰培养', '痰一般细菌', '一般细菌培养'])
  const urineItem     = findItem(['尿培养', '尿一般细菌'])
  const bloodItem     = findItem(['血培养', '血液培养', '血一般细菌'])
  const secretionItem = findItem(['分泌物培养', '伤口', '脓液'])
  const stoolItem     = findItem(['粪便培养', '大便培养'])

  log.info('  痰培养项目:    ' + (sputumItem    ? `id=${sputumItem.id}    code=${sputumItem.code}    name=${sputumItem.name}` : 'NULL'))
  log.info('  尿培养项目:    ' + (urineItem     ? `id=${urineItem.id}     code=${urineItem.code}     name=${urineItem.name}` : 'NULL'))
  log.info('  血培养项目:    ' + (bloodItem     ? `id=${bloodItem.id}     code=${bloodItem.code}     name=${bloodItem.name}` : 'NULL'))
  log.info('  分泌物培养:    ' + (secretionItem ? `id=${secretionItem.id} code=${secretionItem.code} name=${secretionItem.name}` : 'NULL'))
  log.info('  粪便培养项目:  ' + (stoolItem     ? `id=${stoolItem.id}     code=${stoolItem.code}     name=${stoolItem.name}` : 'NULL'))

  // 3) 患者
  const visitTypes = { '门诊': 1, '住院': 2, '急诊': 3 }
  const patients = [
    { Name:'张三', Gender:'男', Age:'45岁', Type:'门诊', Dept:'呼吸内科', Doctor:'李医生', Diag:'咳嗽咳痰3天，疑似肺部感染',       Item:sputumItem,    Cond:'空腹',  SampleType:'呼吸道分泌物' },
    { Name:'李四', Gender:'女', Age:'32岁', Type:'门诊', Dept:'肾内科',   Doctor:'王医生', Diag:'尿频尿急，疑似尿路感染',         Item:urineItem,     Cond:'',      SampleType:'尿液' },
    { Name:'王五', Gender:'男', Age:'67岁', Type:'急诊', Dept:'急诊科',   Doctor:'赵医生', Diag:'发热39.2度，疑似败血症',         Item:bloodItem,     Cond:'空腹',  SampleType:'血液' },
    { Name:'赵六', Gender:'女', Age:'29岁', Type:'门诊', Dept:'普外科',   Doctor:'钱医生', Diag:'术后伤口红肿渗出',               Item:secretionItem, Cond:'',      SampleType:'分泌物' },
    { Name:'孙七', Gender:'男', Age:'55岁', Type:'住院', Dept:'消化内科', Doctor:'孙医生', Diag:'腹泻伴发热2天',                 Item:stoolItem,     Cond:'',      SampleType:'粪便' },
  ]

  // 4) 创建申请 -> 生成标本
  log.cyan('\n=== 3. 创建 5 条检验申请 -> 生成标本与采集条码 ===')
  const generated = []
  for (let i = 0; i < patients.length; i++) {
    const p = patients[i]
    if (!p.Item) { log.yellow(`  [${i+1}/5] 跳过 ${p.Name}: 未找到检验项目`); continue }
    const body = {
      patientName: p.Name,
      patientGender: p.Gender,
      patientAge: p.Age,
      orderType: p.Type,
      visitType: visitTypes[p.Type],
      applyDoctor: p.Doctor,
      applyDept: p.Dept,
      clinicalDiag: p.Diag,
      patientCondition: p.Cond || '',
      collectionMethodId: null,
      collectionMethodName: '常规采集',
      specimenType: p.SampleType,
      items: [{
        testItemId: Number(p.Item.id),
        testItemCode: p.Item.code,
        testItemName: p.Item.name
      }]
    }
    log.info(`  [${i+1}/5] 创建申请: ${p.Name} / ${p.SampleType} / ${p.Diag.slice(0, 14)}`)
    try {
      const r = await req('/sample/orders/receive', { method: 'POST', body, token })
      const samples = toArray(unwrap(r) || r.data)
      for (const s of samples) {
        if (s && s.sampleBarcode) {
          generated.push({
            No: i + 1,
            Patient: p.Name, Gender: p.Gender, Age: p.Age, OrderType: p.Type,
            Dept: p.Dept, Doctor: p.Doctor, Diag: p.Diag,
            TestItemName: p.Item.name,
            TestItemId: Number(p.Item.id),
            SampleId: s.id,
            SampleBarcode: s.sampleBarcode,
            SampleType: s.sampleType || p.SampleType,
            VisitNo: s.visitNo || ''
          })
          log.green(`    + 生成标本: 条码=${s.sampleBarcode}  type=${s.sampleType || p.SampleType}  id=${s.id}`)
        } else {
          log.yellow('    + 返回结构异常: ' + JSON.stringify(s || r.data).slice(0, 200))
        }
      }
    } catch (e) {
      log.red('    + 异常: ' + e.message)
    }
  }

  // 5) 可选：尝试采集 (后端若缺少接口则忽略)
  log.cyan('\n=== 4. 模拟采集工作站扫码采集 ===')
  for (const g of generated) {
    try {
      const body = {
        sampleBarcode: g.SampleBarcode,
        sampleId: Number(g.SampleId),
        collectorName: '张护士',
        collectTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
        remark: '模拟采集'
      }
      await req('/sample/collect', { method: 'POST', body, token })
      log.green(`  [${g.No}] ${g.Patient} 已采集: ${g.SampleBarcode}`)
    } catch (e) {
      log.yellow(`  [${g.No}] 采集接口不可用 (不影响后续培养基条码生成): ${e.message}`)
    }
  }

  // 6) 获取培养方案
  log.cyan('\n=== 5. 获取所有启用培养方案 ===')
  let plans = []
  try {
    const r = await req('/micro/culture-plan/all', { token })
    plans = toArray(unwrap(r) || r.data)
  } catch (_) {}
  if (plans.length === 0) {
    try {
      const r2 = await req('/micro/culture-plan?current=1&size=50', { token })
      plans = toArray(unwrap(r2) || r2.data)
    } catch (e) {
      log.yellow('  获取方案失败: ' + e.message)
    }
  }
  log.info(`  培养方案 ${plans.length} 条:`)
  plans.forEach(p => log.info(`    · id=${p.id}  ${p.planName} (${p.testItemName || ''})`))

  function matchPlan(g) {
    const exact = plans.find(p => p.testItemName && p.testItemName === g.TestItemName)
    if (exact) return exact
    const rules = [
      { name:'痰培养方案',      kw:['痰', '肺部', '呼吸'] },
      { name:'尿培养方案',      kw:['尿', '泌尿', '肾'] },
      { name:'血培养方案',      kw:['血培养', '败血症', '发热'] },
      { name:'分泌物培养方案',  kw:['分泌', '伤口', '脓'] },
      { name:'粪便培养方案',    kw:['粪便', '大便', '腹泻'] },
      { name:'脑脊液培养方案',  kw:['脑脊液', '脑膜'] },
      { name:'胸腹培养方案',    kw:['胸', '腹'] },
      { name:'真菌培养方案',    kw:['真菌'] },
      { name:'厌氧培养方案',    kw:['厌氧'] },
      { name:'淋球菌培养方案',  kw:['淋球菌', '淋病'] },
    ]
    for (const rule of rules) {
      if (rule.kw.some(k => (g.TestItemName + g.Diag).includes(k))) {
        const h = plans.find(p => p.planName === rule.name)
        if (h) return h
      }
    }
    return plans[0] || null
  }

  // 7) 生成培养基条码
  log.cyan('\n=== 6. 结合培养方案生成培养基条码 ===')
  const plates = []
  for (const g of generated) {
    const plan = matchPlan(g)
    if (!plan) { log.yellow(`  [${g.No}] 未匹配到培养方案, 跳过`); continue }
    const body = {
      sampleBarcode: g.SampleBarcode,
      sampleId: Number(g.SampleId),
      testItemIds: [Number(g.TestItemId)],
      patientName: g.Patient,
      operatorName: '系统管理员'
    }
    log.info(`  [${g.No}] ${g.Patient} 标本=${g.SampleBarcode} 方案=${plan.planName} -> 生成培养基条码`)
    try {
      const r = await req('/micro/sample-plate/generate', { method: 'POST', body, token })
      const list = toArray(unwrap(r) || r.data)
      log.green(`    生成 ${list.length} 管:`)
      for (const pl of list) {
        log.green(`      · [${pl.seqNo}] ${pl.mediumTypeName}  条码=${pl.plateBarcode}`)
        plates.push({
          Patient: g.Patient, Dept: g.Dept, Diag: g.Diag,
          SampleBarcode: g.SampleBarcode,
          PlanName: plan.planName,
          MediumName: pl.mediumTypeName,
          SeqNo: pl.seqNo,
          PlateBarcode: pl.plateBarcode,
          PlateStatus: pl.status ?? 0,
        })
      }
    } catch (e) {
      log.red('    生成失败: ' + e.message)
    }
  }

  // 8) 汇总
  log.cyan('\n============================================')
  log.cyan('  ★★ 模拟完成 - 结果汇总 ★★')
  log.cyan('============================================\n')

  log.cyan('【表1：5条申请 / 标本 / 采集条码】')
  console.table(generated.map(g => ({
    序号: g.No,
    患者: `${g.Patient}(${g.Gender}/${g.Age})`,
    科室_类型: `${g.Dept}/${g.OrderType}`,
    临床诊断: g.Diag.length > 14 ? g.Diag.slice(0,14)+'…' : g.Diag,
    检验项目: g.TestItemName,
    采集条码: g.SampleBarcode
  })))

  log.cyan('\n【表2：培养基条码（共 ' + plates.length + ' 管）】- 请用于微生物报告页面核收')
  console.table(plates.map(p => ({
    患者: p.Patient,
    采集条码: p.SampleBarcode,
    培养方案: p.PlanName,
    序号: p.SeqNo,
    培养基: p.MediumName,
    培养基条码: p.PlateBarcode
  })))

  log.cyan('\n【下一步操作】')
  log.info('  1) 培养基条码生成页 (#/micro/patient-data-by-barcode):')
  log.info('     · 输入「采集条码」查询患者信息、生成或重打条码标签')
  log.info('  2) 微生物报告页 (#/report/micro):')
  log.info('     · 在顶部【培养基条码核收】输入框输入上表「培养基条码」后回车 → 自动核收并创建报告')
  log.info('  3) 本次生成的培养基条码 (可直接复制到核收框测试):')
  plates.forEach(p => log.green(`     · ${p.Patient} / ${p.MediumName.padEnd(8)} -> ${p.PlateBarcode}`))
})()
