// 查询: 核收标本的报告记录 + 患者名 + 报告状态
const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };

(async () => {
  const c = new Client(conn);
  await c.connect();
  const sampleId = '2085739820032520194'; // 核收的标本

  // 1. 报告记录
  const rep = await c.query("SELECT id, report_no, sample_id, patient_name, status, auditor_id, publisher_id, audit_time, publish_time, create_time FROM lis_report WHERE sample_id=$1", [sampleId]);
  console.log('=== lis_report (sample=' + sampleId + ') ===');
  console.log(rep.rows.length ? rep.rows : '无报告记录');

  // 2. 该标本的申请/就诊患者名
  const o = await c.query("SELECT o.id, o.patient_name, o.patient_id, v.visit_no FROM lis_lab_order o LEFT JOIN lis_visit v ON o.visit_id=v.id WHERE o.id=$1", ['2085739819768279042']);
  console.log('\n=== lis_lab_order ===');
  console.log(o.rows);

  // 3. 患者表中该患者名
  const p = await c.query("SELECT id, patient_name, visit_no FROM lis_patient WHERE id=$1", ['2085739819705364482']);
  console.log('\n=== lis_patient ===');
  console.log(p.rows);

  // 4. 采样表患者名
  const s = await c.query("SELECT id, patient_name, sample_barcode FROM lis_sample WHERE id=$1", [sampleId]);
  console.log('\n=== lis_sample ===');
  console.log(s.rows);

  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });