// LIS 全功能业务逻辑测试工具
// 用法: node test_api.js <模块关键字过滤可选>
const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN = '';
let USER_ID = '';

function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (USER_ID) headers['X-User-Id'] = USER_ID;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, res => {
      let x = '';
      res.on('data', c => x += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(x) }); }
        catch (e) { resolve({ status: res.statusCode, body: { raw: x.slice(0, 300) } }); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

const results = [];
function check(name, cond, detail = '') {
  const ok = !!cond;
  results.push({ name, ok, detail });
  const mark = ok ? 'PASS' : 'FAIL';
  console.log(`[${mark}] ${name}${detail ? ' | ' + detail : ''}`);
  return ok;
}

async function login(username = 'admin', password = '123456') {
  const r = await req('POST', '/api/auth/login', { username, password });
  if (r.body && r.body.code === 200) {
    TOKEN = r.body.data.token;
    // 从 JWT 中解析 userId (sub 字段)
    try {
      const payload = JSON.parse(Buffer.from(TOKEN.split('.')[1], 'base64').toString());
      USER_ID = String(payload.sub);
    } catch (e) { USER_ID = ''; }
    return r.body.data;
  }
  return null;
}

module.exports = { req, login, check, getResults: () => results };

(async () => {
  const which = process.argv[2] || '';
  const files = {
    base: './tests/base.js',
    e2e: './tests/e2e.js',
    sample: './tests/sample.js',
    report: './tests/report.js',
    system: './tests/system.js',
    hr: './tests/hr.js',
    reagent: './tests/reagent.js',
    qc: './tests/qc.js',
    micro: './tests/micro.js',
    instrument: './tests/instrument.js',
    stats: './tests/stats.js',
  };
  const run = [];
  if (which) {
    if (files[which]) run.push(files[which]);
    else run.push('./tests/' + which + '.js');
  } else {
    Object.values(files).forEach(f => run.push(f));
  }
  const user = await login();
  if (!user) { console.log('登录失败, 无法继续测试'); process.exit(1); }
  console.log('登录成功:', user.username, '(' + user.realName + ')\n');
  for (const f of run) {
    try {
      const mod = require(f);
      if (typeof mod.run === 'function') await mod.run({ req, check, login });
    } catch (e) {
      console.log('加载模块失败', f, e.message);
    }
  }
  const failed = results.filter(x => !x.ok);
  console.log('\n===== 汇总 =====');
  console.log('总用例:', results.length, ' 通过:', results.length - failed.length, ' 失败:', failed.length);
  if (failed.length) {
    console.log('\n失败用例:');
    failed.forEach(f => console.log('  -', f.name, f.detail));
  }
  process.exit(failed.length ? 2 : 0);
})();