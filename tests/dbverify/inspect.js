const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const t = await c.query("SELECT column_name,data_type FROM information_schema.columns WHERE table_name IN ('lis_ref_range','lis_test_item_ref_range') ORDER BY table_name,ordinal_position");
  console.log('=== ref-range tables ===');
  console.table(t.rows);
  const rr = await c.query("SELECT count(*) n FROM lis_ref_range");
  console.log('lis_ref_range rows = ' + rr.rows[0].n);
  const forWbc = await c.query("SELECT * FROM lis_ref_range WHERE item_id=1 LIMIT 5");
  console.log('\n=== ref ranges for WBC(id=1) ===');
  console.table(forWbc.rows);
  await c.end();
})();