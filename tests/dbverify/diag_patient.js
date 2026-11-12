const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r1 = await c.query("SELECT * FROM lis_barcode_rule ORDER BY id");
  console.log('=== barcode rules ===');
  console.log(JSON.stringify(r1.rows, null, 1));
  const r2 = await c.query("SELECT id,patient_no,name FROM lis_patient ORDER BY id DESC LIMIT 8");
  console.log('=== recent patients ===');
  console.log(JSON.stringify(r2.rows, null, 1));
  const r3 = await c.query("SELECT patient_no, COUNT(*) AS cnt FROM lis_patient GROUP BY patient_no HAVING COUNT(*)>1 LIMIT 10");
  console.log('=== duplicate patient_no ===');
  console.log(JSON.stringify(r3.rows, null, 1));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });