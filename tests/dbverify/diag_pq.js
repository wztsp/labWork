const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r = await c.query("SELECT patient_no FROM lis_patient WHERE patient_no LIKE 'P__________' ORDER BY patient_no DESC LIMIT 5");
  console.log('=== LIKE P + 10 underscores, DESC LIMIT 5 ===');
  console.log(JSON.stringify(r.rows, null, 1));
  const r2 = await c.query("SELECT patient_no, length(patient_no) AS len FROM lis_patient ORDER BY len DESC, patient_no DESC LIMIT 10");
  console.log('=== by length DESC ===');
  console.log(JSON.stringify(r2.rows, null, 1));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });