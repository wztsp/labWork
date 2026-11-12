// 全模块业务逻辑回归测试运行器
// 登录一次，依次执行各业务模块的 run({req, check})，汇总结果
const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN = '', USER_ID = '';
const results = [];

function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body != null ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (USER_ID) headers['X-User-Id'] = USER_ID;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x = ''; res.on('data', c => x += c); res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(x) }); }
        catch (e) { resolve({ status: res.statusCode, body: { raw: x.slice(0, 400) } }); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function login(username = 'admin', password = '123456') {
  const r = await req('POST', '/api/auth/login', { username, password });
  if (r.body && r.body.code === 200) {
    TOKEN = r.body.data.token;
    try { const p = JSON.parse(Buffer.from(TOKEN.split('.')[1], 'base64').toString()); USER_ID = String(p.sub); } catch (e) {}
    return r.body.data;
  }
  return null;
}

function makeCheck(module) {
  let count = 0, failed = 0;
  const c = (name, cond, detail) => {
    count++;
    const ok = !!cond;
    if (!ok) failed++;
    results.push({ module, name, ok, detail });
    console.log(`[${ok ? 'PASS' : 'FAIL'}] ${module} | ${name}${detail ? ' | ' + detail : ''}`);
    return ok;
  };
  return { c, stat: () => ({ count, failed }) };
}

const modules = [
  'base', 'e2e', 'sample', 'report', 'qc', 'hr', 'stats', 'system', 'instrument', 'micro', 'reagent'
];

(async () => {
  const user = await login();
  if (!user) { console.log('登录失败'); process.exit(2); }
  console.log('登录:', user.username, '(' + user.realName + ')\n');

  for (const name of modules) {
    let mod;
    try { mod = require('./' + name + '.js'); } catch (e) { console.log(`[LOAD-FAIL] ${name} | ${e.message}`); continue; }
    const { c, stat } = makeCheck(name);
    console.log(`===== 模块: ${name} =====`);
    try {
      await mod.run({ req, check: c });
    } catch (e) {
      c(`${name} run 异常`, false, e.message);
    }
    const s = stat();
    console.log(`----- ${name}: ${s.count - s.failed}/${s.count} 通过\n`);
  }

  const failedAll = results.filter(r => !r.ok);
  console.log('========== 汇总 ==========');
  console.log('总用例:', results.length, ' 通过:', results.length - failedAll.length, ' 失败:', failedAll.length);
  if (failedAll.length) {
    console.log('\n失败用例:');
    failedAll.forEach(f => console.log(`  - [${f.module}] ${f.name}${f.detail ? ' | ' + f.detail : ''}`));
  }
  process.exit(failedAll.length ? 2 : 0);
})();