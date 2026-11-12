const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  console.log('=== 含 order/visit/patient 的表 ===');
  const t = await c.query("SELECT tablename FROM pg_tables WHERE schemaname='public' AND (tablename LIKE '%order%' OR tablename LIKE '%visit%' OR tablename LIKE '%patient%') ORDER BY tablename");
  console.log(t.rows.map(r => r.tablename));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });