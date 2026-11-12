const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const st = await c.query("SELECT column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_name='lis_specimen_type' ORDER BY ordinal_position");
  console.log('=== lis_specimen_type columns ===');
  console.table(st.rows);
  const st2 = await c.query("SELECT * FROM lis_specimen_type ORDER BY id");
  console.log('=== lis_specimen_type rows ===');
  console.table(st2.rows);
  const ti = await c.query("SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE table_name='lis_test_item' ORDER BY ordinal_position");
  console.log('=== lis_test_item columns ===');
  console.table(ti.rows);
  await c.end();
})();