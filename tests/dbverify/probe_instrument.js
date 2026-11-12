const http = require('http');
const BASE = 'http://localhost:8080';
let TOKEN = '';

function req(method, path, body) {
  return new Promise((resolve) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);
    const r = http.request(BASE + path, { method, headers }, (res) => {
      let b = '';
      res.on('data', (c) => b += c);
      res.on('end', () => resolve({ status: res.statusCode, body: (b || '').slice(0, 160) }));
    });
    r.on('error', (e) => resolve({ status: 0, body: e.message }));
    r.end(data);
  });
}

const probes = [
  ['GET', '/api/instrument/status', '仪器-状态'],
  ['GET', '/api/instrument/calibration?current=1&size=5', '仪器-校准'],
  ['GET', '/api/instrument/repair?current=1&size=5', '仪器-维修'],
  ['GET', '/api/instrument-mgmt/logs?current=1&size=5', '仪器维护-log'],
  ['GET', '/api/instrument-mgmt/templates?current=1&size=5', '仪器维护-模板'],
  ['GET', '/api/instrument-mgmt/plans/due', '仪器维护-到期计划'],
  ['GET', '/api/instrument-mgmt/plans/tasks', '仪器维护-任务'],
  ['GET', '/api/base/instruments?current=1&size=5', '仪器-字典'],
  ['GET', '/api/base/instruments/mine', '仪器-我的'],
];

(async () => {
  const loginBody = JSON.stringify({ username: 'admin', password: '123456' });
  const lr = await new Promise((resolve) => {
    const r = http.request(BASE + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(loginBody) } }, (res) => {
      let b = ''; res.on('data', (c) => b += c); res.on('end', () => resolve({ status: res.statusCode, body: b }));
    });
    r.on('error', (e) => resolve({ status: 0, body: e.message }));
    r.end(loginBody);
  });
  try { const j = JSON.parse(lr.body); if (j && j.data && j.data.token) TOKEN = j.data.token; else console.log('LOGIN FAIL', lr.body.slice(0,120)); } catch (e) { console.log('LOGIN ERR', lr.body.slice(0,120)); }

  for (const [m, p, desc] of probes) {
    const res = await req(m, p);
    const flag = res.status === 200 ? 'OK ' : (res.status === 404 ? '404' : ('ERR' + res.status));
    console.log(`${flag}  ${desc.padEnd(18)} ${m} ${p}`);
  }
})();