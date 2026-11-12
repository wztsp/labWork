const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  // 最近生成的报告及其关联标本
  const r = await c.query(`
    SELECT r.id, r.report_no, r.status, r.sample_id, s.sample_barcode, s.status AS sample_status
    FROM lis_report r LEFT JOIN lis_sample s ON r.sample_id = s.id
    ORDER BY r.id DESC LIMIT 5`);
  console.log('recent reports:', JSON.stringify(r.rows, null, 1));
  // 最近核收/已报告的标本
  const s = await c.query(`SELECT id, sample_barcode, status FROM lis_sample WHERE status=6 ORDER BY id DESC LIMIT 5`);
  console.log('status6 samples:', JSON.stringify(s.rows, null, 1));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });