const http = require('http');

const BASE = 'http://localhost:8080';
let TOKEN = '';

function req(method, path) {
  return new Promise((resolve) => {
    const url = BASE + path;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    const r = http.request(url, { method, headers }, (res) => {
      let body = '';
      res.on('data', (c) => { body += c; });
      res.on('end', () => {
        resolve({ status: res.statusCode, body: (body || '').slice(0, 200) });
      });
    });
    r.on('error', (e) => resolve({ status: 0, body: e.message }));
    r.end();
  });
}

// Each entry: [method, path, description]
const endpoints = [
  ['GET', '/api/auth/login', ''],
];
// Representative endpoints per module (GET)
const probes = [
  ['GET', '/api/sample/orders/page?current=1&size=5', '标本-申请分页'],
  ['GET', '/api/sample/page?current=1&size=5', '标本-分页'],
  ['GET', '/api/report/page?current=1&size=5', '报告-分页'],
  ['GET', '/api/report/technician/summary', '技师-汇总'],
  ['GET', '/api/base/items?current=1&size=5', '基础-检验项目'],
  ['GET', '/api/base/specimen-types?current=1&size=5', '基础-标本类型'],
  ['GET', '/api/base/test-tubes?current=1&size=5', '基础-试管'],
  ['GET', '/api/base/ref-ranges?current=1&size=5', '基础-参考范围'],
  ['GET', '/api/base/instruments?current=1&size=5', '基础-仪器字典'],
  ['GET', '/api/base/collection-methods?current=1&size=5', '基础-采集方式'],
  ['GET', '/api/base/items?current=1&size=5&isPanel=0', '基础-检验指标'],
  ['GET', '/api/qc/records/page?current=1&size=5', '质控-日常'],
  ['GET', '/api/qc/lots/list', '质控-基础'],
  ['GET', '/api/qc/rules', '质控-规则'],
  ['GET', '/api/qc/alerts/page?current=1&size=5', '质控-预警'],
  ['GET', '/api/qc/eqa/page?current=1&size=5', '质控-室间'],
  ['GET', '/api/reagent/dict?current=1&size=5', '试剂-字典'],
  ['GET', '/api/reagent/stock?current=1&size=5', '试剂-库存'],
  ['GET', '/api/reagent/records?current=1&size=5', '试剂-出入库'],
  ['GET', '/api/reagent/inbound?current=1&size=5', '试剂-入库验收'],
  ['GET', '/api/reagent/dashboard/overview', '试剂-看板'],
  ['GET', '/api/micro/blood-culture?current=1&size=5', '微生物-血培养'],
  ['GET', '/api/micro/bacteria?current=1&size=5', '微生物-细菌'],
  ['GET', '/api/micro/antibiotics?current=1&size=5', '微生物-抗生素'],
  ['GET', '/api/micro/breakpoints?current=1&size=5', '微生物-折点'],
  ['GET', '/api/micro/resistance-rules?current=1&size=5', '微生物-耐药规则'],
  ['GET', '/api/micro/print-config', '微生物-打印配置'],
  ['GET', '/api/hr/staff?current=1&size=5', '人事-员工'],
  ['GET', '/api/hr/groups', '人事-小组'],
  ['GET', '/api/hr/schedules?current=1&size=5', '人事-排班'],
  ['GET', '/api/hr/certificates?current=1&size=5', '人事-证书'],
  ['GET', '/api/system/users?current=1&size=5', '系统-用户'],
  ['GET', '/api/system/roles', '系统-角色'],
  ['GET', '/api/system/configs', '系统-参数'],
  ['GET', '/api/stats/workload', '统计-工作量'],
];

(async () => {
  // login
  const loginBody = JSON.stringify({ username: 'admin', password: '123456' });
  const lr = await new Promise((resolve) => {
    const u = BASE + '/api/auth/login';
    const r = http.request(u, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(loginBody) } }, (res) => {
      let b = ''; res.on('data', (c) => b += c); res.on('end', () => resolve({ status: res.statusCode, body: b }));
    });
    r.on('error', (e) => resolve({ status: 0, body: e.message }));
    r.end(loginBody);
  });
  try {
    const j = JSON.parse(lr.body);
    if (j && j.data && j.data.token) {
      TOKEN = j.data.token;
      console.log('LOGIN OK');
    } else {
      console.log('LOGIN FAIL:', lr.status, lr.body.slice(0, 200));
    }
  } catch (e) { console.log('LOGIN PARSE FAIL:', lr.body.slice(0, 200)); }

  for (const [m, p, desc] of probes) {
    const res = await req(m, p);
    const ok = res.status === 200;
    const flag = ok ? 'OK ' : (res.status === 404 ? '404' : ('ERR' + res.status));
    console.log(`${flag}  ${desc.padEnd(16)} ${m} ${p}`);
  }
})();