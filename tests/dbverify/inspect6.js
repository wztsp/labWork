const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const cat = await c.query("SELECT DISTINCT category FROM lis_test_item WHERE category IS NOT NULL ORDER BY category");
  console.log('=== distinct categories ===');
  console.table(cat.rows);
  const p21 = await c.query("SELECT * FROM lis_test_item WHERE id=21");
  console.log('=== panel 21 full row ===');
  console.table(p21.rows);
  // 检查参考范围表结构
  const rr = await c.query("SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE table_name='lis_ref_range' ORDER BY ordinal_position");
  console.log('=== lis_ref_range columns ===');
  console.table(rr.rows);
  await c.end();
})();