const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r = await c.query("SELECT column_name,data_type FROM information_schema.columns WHERE table_name='lis_sample' ORDER BY ordinal_position");
  console.log(r.rows.map(x => x.column_name).join(', '));
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });