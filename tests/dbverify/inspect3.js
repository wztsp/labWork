const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const cbc = await c.query("SELECT id,item_code,item_name,short_name,unit,method,parent_id FROM lis_test_item WHERE parent_id=9 ORDER BY id");
  console.log('=== 血常规(9) indicators count=' + cbc.rows.length + ' ===');
  console.table(cbc.rows);
  const ua = await c.query("SELECT id,item_code,item_name,short_name,unit,method FROM lis_test_item WHERE parent_id=21 ORDER BY id");
  console.log('\n=== 尿液分析/尿常规(21) indicators count=' + ua.rows.length + ' ===');
  console.table(ua.rows);
  const tubes = await c.query("SELECT id,tube_name,tube_color FROM lis_test_tube ORDER BY id");
  console.log('\n=== 试管 ===');
  console.table(tubes.rows);
  const st = await c.query("SELECT type_code,type_name FROM lis_sample_type ORDER BY id");
  console.log('\n=== 标本类型 ===');
  console.table(st.rows);
  await c.end();
})();