const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  // 血常规 panel (is_panel=1)
  const panel = await c.query("SELECT id,item_code,item_name,specimen_type,is_panel FROM lis_test_item WHERE is_panel=1 AND (item_name LIKE '%血常规%' OR item_code ILIKE '%CBC%' OR item_code ILIKE '%BLOOD%') ORDER BY id");
  console.log('=== 血常规 panel ===');
  console.table(panel.rows);
  // 现有待审核(已核收 status=4)全血标本
  const s = await c.query("SELECT id,sample_barcode,sample_no,order_id,patient_id,patient_name,sample_type,status,instrument_id,receive_time FROM lis_sample WHERE deleted=0 AND status=4 AND sample_type='全血' ORDER BY id DESC LIMIT 10");
  console.log('\n=== status=4 全血 标本 ===');
  console.table(s.rows);
  // 仪器列表
  const inst = await c.query("SELECT id,instrument_name,instrument_code FROM lis_instrument ORDER BY id");
  console.log('\n=== 仪器 ===');
  console.table(inst.rows);
  // 患者样例
  const pat = await c.query("SELECT id,patient_no,name,gender,age FROM lis_patient ORDER BY id DESC LIMIT 8");
  console.log('\n=== 患者 ===');
  console.table(pat.rows);
  // 科室样例
  const dept = await c.query("SELECT DISTINCT department FROM lis_sample WHERE department IS NOT NULL AND department != '' LIMIT 10");
  console.log('\n=== 科室 ===');
  console.table(dept.rows);
  await c.end();
})();