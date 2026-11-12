const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  // 患者表
  console.log('=== lis_patient 最近5条 ===');
  const p = await c.query('SELECT id, patient_no, name FROM lis_patient ORDER BY id DESC LIMIT 5');
  console.log(p.rows);
  // 是否有 P 开头的 sample
  console.log('\n=== lis_sample 中 P开头的条码 ===');
  const s = await c.query("SELECT sample_barcode FROM lis_sample WHERE sample_barcode LIKE 'P%' LIMIT 5");
  console.log(s.rows);
  // order_no / visit_no 规则对应表
  console.log('\n=== lis_order 最近3条 order_no ===');
  const o = await c.query('SELECT id, order_no FROM lis_order ORDER BY id DESC LIMIT 3');
  console.log(o.rows);
  console.log('\n=== lis_visit 最近3条 visit_no ===');
  const v = await c.query('SELECT id, visit_no FROM lis_visit ORDER BY id DESC LIMIT 3');
  console.log(v.rows);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });