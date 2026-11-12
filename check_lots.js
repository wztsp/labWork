const { Client } = require('pg');
const client = new Client({ user: 'lis_user', host: 'localhost', database: 'lis_db', password: 'lis_dev_2026', port: 5432 });
client.connect();
(async () => {
  // Get existing lots
  const lots = await client.query("SELECT id, lot_no, test_item_id, instrument_id, target_value, sd, cv, level, status, deleted FROM lis_qc_lot WHERE deleted = 0 OR deleted IS NULL");
  console.log('Existing lots:');
  lots.rows.forEach(r => console.log(JSON.stringify(r)));

  // Get test items
  const items = await client.query("SELECT id, item_name FROM lis_test_item WHERE item_name ILIKE '%红细胞%' OR item_name ILIKE '%RBC%' OR item_name ILIKE '%白细胞%' OR item_name ILIKE '%WBC%' OR item_name ILIKE '%血小板%' OR item_name ILIKE '%PLT%' OR item_name ILIKE '%血红蛋白%' OR item_name ILIKE '%HGB%' LIMIT 10");
  console.log('\nTest items matching:', items.rows.map(r => r.id + ': ' + r.item_name).join(', '));

  // Get instruments
  const instruments = await client.query("SELECT id, instrument_name FROM lis_instrument WHERE deleted = 0 OR deleted IS NULL LIMIT 5");
  console.log('\nInstruments:', instruments.rows.map(r => r.id + ': ' + r.instrument_name).join(', '));

  await client.end();
})();
