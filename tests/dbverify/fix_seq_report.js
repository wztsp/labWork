const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  // 将 lis_report_id_seq 同步到 max(id), 使 nextval 返回 max+1
  const r = await c.query("SELECT setval('lis_report_id_seq', GREATEST((SELECT COALESCE(MAX(id),1) FROM lis_report), 1))");
  console.log('setval lis_report_id_seq ->', JSON.stringify(r.rows[0]));
  const chk = await c.query("SELECT last_value, is_called FROM lis_report_id_seq");
  console.log('after:', JSON.stringify(chk.rows[0]));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });