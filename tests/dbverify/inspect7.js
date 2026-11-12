const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const instr = await c.query("SELECT id,instrument_code,instrument_name FROM lis_instrument ORDER BY id");
  console.log('=== instruments ===');
  console.table(instr.rows);
  const ex = await c.query("SELECT id,sample_barcode,sample_no,order_id,patient_id,patient_name,sample_type,status,is_urgent,department,instrument_id FROM lis_sample WHERE status>=4 ORDER BY id DESC LIMIT 8");
  console.log('=== recent received samples ===');
  console.table(ex.rows);
  const rr = await c.query("SELECT t.item_code,t.item_name,rr.gender,rr.age_min,rr.age_max,rr.ref_low,rr.ref_high,rr.ref_text,rr.unit FROM lis_reference_range rr JOIN lis_test_item t ON rr.test_item_id=t.id WHERE t.parent_id=9 ORDER BY rr.test_item_id,rr.id");
  console.log('=== 血常规参考范围 ===');
  console.table(rr.rows);
  await c.end();
})();