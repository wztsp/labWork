const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const tables = ['lis_sample','lis_lab_order','lis_visit','lis_patient','lis_order_item','lis_sample_order_item','lis_result','lis_sample_image'];
  for (const t of tables) {
    const cols = await c.query(`SELECT column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_name='${t}' ORDER BY ordinal_position`);
    console.log(`=== ${t} columns ===`);
    console.table(cols.rows.map(r=>({c:r.column_name,ty:r.data_type,n:r.is_nullable,d:r.column_default==null?'':String(r.column_default).slice(0,40)})));
  }
  await c.end();
})();