const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r = await c.query("SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name='lis_report' AND column_name IN ('report_no','report_type')");
  console.log(JSON.stringify(r.rows));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });