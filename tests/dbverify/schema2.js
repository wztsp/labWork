const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  // check lis_sample_image existence
  const t = await c.query("SELECT to_regclass('public.lis_sample_image') AS exists_t, to_regclass('public.lis_result_image') AS exists_r");
  console.table(t.rows);
  // full lis_sample columns
  const cols = await c.query(`SELECT column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_name='lis_sample' ORDER BY ordinal_position`);
  console.log('=== lis_sample columns ===');
  console.table(cols.rows.map(r=>({c:r.column_name,ty:r.data_type})));
  // search for any image-related table
  const imgs = await c.query("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE '%image%'");
  console.log('=== image tables ===');
  console.table(imgs.rows);
  await c.end();
})();