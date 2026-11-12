const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'postgres', password: 'postgres' };
(async () => {
  const c = new Client(conn);
  try { await c.connect(); } catch(e) { console.error('postgres/postgres 失败:', e.message); process.exit(1); }
  const q = `
    SELECT t.relname AS tbl, a.attname AS col, s.relname AS seq
    FROM pg_class s
    JOIN pg_depend d ON d.objid = s.oid
    JOIN pg_class t ON t.oid = d.refobjid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    WHERE s.relkind='S' AND d.refclassid='pg_class'::regclass
    ORDER BY t.relname`;
  const r = await c.query(q);
  const fixed = [];
  for (const row of r.rows) {
    const tbl = row.tbl, col = row.col, seq = row.seq;
    const maxRes = await c.query(`SELECT COALESCE(MAX(${col}),0) AS m FROM ${tbl}`);
    const max = Number(maxRes.rows[0].m);
    const seqCur = await c.query(`SELECT last_value AS v FROM ${seq}`);
    const cur = Number(seqCur.rows[0].v);
    if (cur <= max) {
      await c.query(`SELECT setval('${seq}', ${max}, true)`);
      fixed.push({ tbl, seq, cur, max });
    }
  }
  console.log('=== 已同步序列 (count=' + fixed.length + ') ===');
  console.table(fixed);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });