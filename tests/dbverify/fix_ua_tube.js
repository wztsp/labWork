const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  await c.query("UPDATE lis_test_item SET tube_id=8 WHERE id=21 AND tube_id IS NULL");
  console.log('尿常规面板 tube_id 已设为尿杯(8)');
  await c.end();
})();