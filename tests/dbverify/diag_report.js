const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r1 = await c.query("SELECT column_name,is_nullable,column_default,data_type FROM information_schema.columns WHERE table_name='lis_report' ORDER BY ordinal_position");
  console.log('=== lis_report columns ===');
  console.log(JSON.stringify(r1.rows, null, 1));
  const r2 = await c.query('SELECT id,sample_id,report_no,status,report_type,report_doctor,his_sync_status FROM lis_report ORDER BY id DESC LIMIT 8');
  console.log('=== recent reports ===');
  console.log(JSON.stringify(r2.rows, null, 1));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });