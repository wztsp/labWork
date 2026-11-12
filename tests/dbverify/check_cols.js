const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  for (const t of ['lis_sample','lis_lab_order','lis_visit','lis_patient']) {
    const r = await c.query("SELECT column_name FROM information_schema.columns WHERE table_name=$1 AND (column_name LIKE '%no' OR column_name LIKE '%code' OR column_name LIKE '%barcode')", [t]);
    console.log(t, '->', r.rows.map(x=>x.column_name).join(', '));
  }
  console.log('\n=== lis_patient 最大 patient_no ===');
  const p = await c.query("SELECT patient_no FROM lis_patient ORDER BY patient_no DESC LIMIT 1");
  console.log(p.rows);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });