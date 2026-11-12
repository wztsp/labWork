const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };
(async () => {
  const c = new Client(conn);
  await c.connect();
  console.log('=== lis_result 表结构(前几列) ===');
  const cols = await c.query("SELECT column_name, data_type, column_default FROM information_schema.columns WHERE table_name='lis_result' ORDER BY ordinal_position LIMIT 12");
  console.table(cols.rows);
  console.log('\n=== lis_result 现有 id 分布 ===');
  const r1 = await c.query('SELECT MAX(id) AS max_id, COUNT(*) AS cnt FROM lis_result');
  console.log(r1.rows);
  console.log('\n=== id 相邻记录(检查100) ===');
  const r2 = await c.query("SELECT id, sample_id, test_item_id FROM lis_result WHERE id IN (99,100,101) ORDER BY id");
  console.log(r2.rows);
  console.log('\n=== 序列清单 ===');
  const seqs = await c.query("SELECT c.relname AS seq FROM pg_class c WHERE c.relkind='S' AND c.relname LIKE '%result%'");
  console.log(seqs.rows);
  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });