const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  // 找一个全血(血常规)已核收标本
  const samples = await c.query("SELECT id,sample_barcode,sample_no,order_id,patient_id,patient_name,sample_type,status,instrument_id FROM lis_sample WHERE status=4 AND sample_type='全血' ORDER BY id DESC LIMIT 5");
  console.log('=== 全血已核收样例 ===');
  console.table(samples.rows);
  const sid = '1198'; // BC202608050198 全血
  const s = await c.query("SELECT * FROM lis_sample WHERE id=$1", [sid]);
  console.log('\n=== lis_sample id=1198 ===');
  console.table(s.rows);
  const oid = s.rows[0].order_id;
  const o = await c.query("SELECT * FROM lis_lab_order WHERE id=$1", [oid]);
  console.log('\n=== lis_lab_order id='+oid+' ===');
  console.table(o.rows);
  const oi = await c.query("SELECT * FROM lis_order_item WHERE order_id=$1", [oid]);
  console.log('\n=== lis_order_item ===');
  console.table(oi.rows);
  const soi = await c.query("SELECT * FROM lis_sample_order_item WHERE sample_id=$1", [sid]);
  console.log('\n=== lis_sample_order_item ===');
  console.table(soi.rows);
  const r = await c.query("SELECT sample_id,test_item_id,result_value,unit,ref_range,abnormal_flag,test_time FROM lis_result WHERE sample_id=$1 ORDER BY test_item_id", [sid]);
  console.log('\n=== lis_result count='+r.rows.length+' ===');
  console.table(r.rows);
  await c.end();
})();