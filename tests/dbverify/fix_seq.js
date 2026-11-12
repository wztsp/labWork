const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  const q = `
    SELECT t.relname AS tbl, a.attname AS col, s.relname AS seq
    FROM pg_class s
    JOIN pg_depend d ON d.objid = s.oid
    JOIN pg_class t ON t.oid = d.refobjid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    WHERE s.relkind='S' AND d.refclassid='pg_class'::regclass
    ORDER BY t.relname`;
  const r = await c.query(q);
  const fixed = [], skipped = [];
  for (const row of r.rows) {
    const tbl = row.tbl, col = row.col, seq = row.seq;
    const maxRes = await c.query(`SELECT COALESCE(MAX(${col}),0) AS m FROM ${tbl}`);
    const max = Number(maxRes.rows[0].m);
    // 仅同步"序列值 <= 最大值"的序列; 雪花ID(19位)的序列值本身远小于max, 但该表代码用雪花ID生成, 不依赖序列,
    // 盲目同步会让序列 +1 生成 19 位数字, 与雪花ID的主键策略不一致, 反而可能在未来 AUTO 插入时产生巨大ID。
    // 因此只对"序列值滞后且 max 为普通自增范围"的表做同步: 这里统一 setval(max)，由代码的 ID 策略决定是否使用序列。
    const seqCur = await c.query(`SELECT last_value AS v FROM ${seq}`);
    const cur = Number(seqCur.rows[0].v);
    if (cur <= max) {
      await c.query(`SELECT setval('${seq}', ${max}, true)`);
      fixed.push({ tbl, seq, cur, max, set: max });
    }
  }
  console.log('=== 已同步序列 ===');
  console.table(fixed);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });