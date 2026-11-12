const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const panels = await c.query("SELECT id,item_code,item_name,category,specimen_type,is_panel,status,tube_id FROM lis_test_item WHERE is_panel=1 ORDER BY id");
  console.log('=== PANELS (is_panel=1) count=' + panels.rows.length + ' ===');
  console.table(panels.rows);
  const ind = await c.query("SELECT count(*) n FROM lis_test_item WHERE is_panel=0");
  console.log('\nindicators (is_panel=0) count = ' + ind.rows[0].n);
  const allNull = await c.query("SELECT count(*) n FROM lis_test_item WHERE is_panel IS NULL");
  console.log('is_panel IS NULL count = ' + allNull.rows[0].n);
  // 血常规/尿常规 上下文
  const cbcUa = await c.query("SELECT id,item_code,item_name,category,specimen_type,is_panel,parent_id,status FROM lis_test_item WHERE item_name IN ('血常规','尿常规','大便常规') ORDER BY id");
  console.log('\n=== 血常规/尿常规/大便常规 rows ===');
  console.table(cbcUa.rows);
  await c.end();
})();