const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  // 找出所有 serial 序列及其关联表、列、当前值、max值
  const q = `
    SELECT t.relname AS tbl, a.attname AS col, s.relname AS seq
    FROM pg_class s
    JOIN pg_depend d ON d.objid = s.oid
    JOIN pg_class t ON t.oid = d.refobjid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    WHERE s.relkind='S' AND d.refclassid='pg_class'::regclass
    ORDER BY t.relname`;
  const r = await c.query(q);
  const problems = [];
  for (const row of r.rows) {
    const seqCur = await c.query(`SELECT last_value FROM ${row.seq}`);
    const maxRes = await c.query(`SELECT COALESCE(MAX(${row.col}),0) AS m FROM ${row.tbl}`);
    const cur = Number(seqCur.rows[0].last_value);
    const max = Number(maxRes.rows[0].m);
    if (cur <= max) {
      problems.push({ ...row, cur, max, diff: max - cur });
    }
  }
  console.log('=== 序列滞后(当前值<=最大值)的表 ===');
  console.table(problems);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });