const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
const sampleId = process.argv[2] || '2085749177940000769';
(async () => {
  await c.connect();
  const r = await c.query('SELECT id,sample_id,status,report_no,patient_id FROM lis_report WHERE sample_id=$1', [sampleId]);
  console.log('reports:', JSON.stringify(r.rows));
  const s = await c.query('SELECT id,sample_barcode,status,patient_id,order_id FROM lis_sample WHERE id=$1', [sampleId]);
  console.log('samples:', JSON.stringify(s.rows));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });