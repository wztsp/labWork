// 查询数据库: 核收标本的 lis_sample_order_item 关联 + 血常规检验指标
const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };

(async () => {
  const c = new Client(conn);
  await c.connect();

  const sampleId = '2085739820032520194'; // 核收的标本
  const barcode = '260807000005';

  // 1. 标本基本信息
  const s = await c.query('SELECT id, sample_barcode, order_id, patient_id, status, sample_type FROM lis_sample WHERE id=$1', [sampleId]);
  console.log('=== 标本 ===');
  console.log(s.rows);

  // 2. lis_sample_order_item 关联
  const soi = await c.query('SELECT * FROM lis_sample_order_item WHERE sample_id=$1', [sampleId]);
  console.log('\n=== lis_sample_order_item ===');
  console.log(soi.rows);

  // 3. 通过 order 查 order_item
  if (s.rows[0]) {
    const oi = await c.query('SELECT id, test_item_id, test_item_code, test_item_name FROM lis_order_item WHERE order_id=$1', [s.rows[0].order_id]);
    console.log('\n=== lis_order_item ===');
    console.log(oi.rows);
  }

  // 4. 血常规(9)的检验指标
  const ind = await c.query("SELECT id, item_code, item_name, parent_id, is_panel, status FROM lis_test_item WHERE parent_id=9 AND is_panel=0");
  console.log('\n=== 血常规指标(应为22个左右) ===');
  console.log('count=', ind.rows.length);
  console.log(ind.rows.slice(0, 5));

  // 5. 报告
  const rep = await c.query("SELECT id, sample_id, patient_name, status, order_id FROM lis_report WHERE sample_id=$1", [sampleId]);
  console.log('\n=== lis_report ===');
  console.log(rep.rows);

  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });