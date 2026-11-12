const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
const sampleId = '2085749177940000769';
(async () => {
  await c.connect();
  try {
    const r = await c.query(
      'SELECT patient_id, patient_name, patient_gender, patient_age FROM lis_sample WHERE id=$1', [sampleId]
    );
    console.log('sample row:', JSON.stringify(r.rows[0]));
    // 模拟 insert (不提交, 先验证)
    const ins = await c.query(
      'INSERT INTO lis_report(report_no, sample_id, patient_id, patient_name, report_type, status, auditor_id, audit_time, publisher_id, publish_time, his_sync_status, create_time, update_time) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) RETURNING id',
      ['RPT-TEST-' + Date.now(), sampleId, r.rows[0].patient_id, r.rows[0].patient_name, 1, 2, 1, new Date(), 1, new Date(), 0, new Date(), new Date()]
    );
    console.log('insert OK id=', ins.rows[0].id);
    await c.query('ROLLBACK');
    console.log('rolled back');
  } catch (e) {
    console.error('INSERT FAILED:', e.message);
  }
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });