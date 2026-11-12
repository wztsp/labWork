const { Client } = require('pg');
const http = require('http');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
function get(p, t) { return new Promise((res, rej) => { http.get('http://localhost:8080' + p, { headers: { Authorization: 'Bearer ' + t } }, r => { let x = ''; r.on('data', d => x += d); r.on('end', () => res(JSON.parse(x))); }).on('error', rej); }); }
(async () => {
  await c.connect();
  // 找出一个"只有 null-gender 参考范围"的检验指标
  const r = await c.query(`SELECT t.id AS test_item_id, t.item_name,
      COUNT(rr.id) FILTER (WHERE rr.gender IS NULL)::int null_g,
      COUNT(rr.id) FILTER (WHERE rr.gender=1)::int g1,
      COUNT(rr.id) FILTER (WHERE rr.gender=2)::int g2,
      COUNT(rr.id) FILTER (WHERE rr.gender=0)::int g0
    FROM lis_test_item t JOIN lis_reference_range rr ON rr.test_item_id=t.id
    GROUP BY t.id, t.item_name
    HAVING COUNT(rr.id) FILTER (WHERE rr.gender IS NULL) = COUNT(rr.id)
    ORDER BY t.id LIMIT 8`);
  console.log('只有null-gender参考范围的指标:');
  console.table(r.rows);
  // 登录
  const b = JSON.stringify({ username: 'admin', password: '123456' });
  const t = await new Promise((res, rej) => { const req = http.request('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) } }, x => { let d = ''; x.on('data', c2 => d += c2); x.on('end', () => res(JSON.parse(d).data.token)); }).on('error', rej); req.write(b); req.end(); });
  for (const row of r.rows) {
    const m = await get('/api/base/ref-ranges/match?testItemId=' + row.test_item_id + '&gender=1&age=30', t);
    console.log(`testItemId=${row.test_item_id} ${row.item_name}: match=${m.data ? '命中' : 'NULL(未命中)'}`);
  }
  await c.end();
})();