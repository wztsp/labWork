const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r1 = await c.query("SELECT last_value, is_called FROM lis_report_id_seq");
  console.log('lis_report_id_seq:', JSON.stringify(r1.rows[0]));
  const r2 = await c.query("SELECT MAX(id) AS max_id, COUNT(*) AS cnt FROM lis_report");
  console.log('lis_report max/count:', JSON.stringify(r2.rows[0]));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });