const { Client } = require('pg');

async function main() {
  const client = new Client({
    host: 'localhost',
    port: 5432,
    database: 'lis_db',
    user: 'lis_user',
    password: 'lis_dev_2026'
  });

  try {
    await client.connect();

    // Check lis_qc_record
    const recordResult = await client.query(
      "SELECT COUNT(*) as total, SUM(CASE WHEN deleted=0 THEN 1 ELSE 0 END) as active, SUM(CASE WHEN deleted=1 THEN 1 ELSE 0 END) as logical_deleted FROM lis_qc_record"
    );
    console.log('lis_qc_record:', recordResult.rows[0]);

    // Check lis_qc_lot
    const lotResult = await client.query(
      "SELECT COUNT(*) as total, SUM(CASE WHEN deleted=0 THEN 1 ELSE 0 END) as active FROM lis_qc_lot"
    );
    console.log('lis_qc_lot:', lotResult.rows[0]);

    // Check lis_qc_item
    const itemResult = await client.query(
      "SELECT COUNT(*) as total, SUM(CASE WHEN deleted=0 THEN 1 ELSE 0 END) as active FROM lis_qc_item"
    );
    console.log('lis_qc_item:', itemResult.rows[0]);

    // Check sequence
    const seqResult = await client.query(
      "SELECT last_value FROM lis_qc_record_id_seq"
    );
    console.log('Sequence last_value:', seqResult.rows[0].last_value);

    // Check if any records exist at all (including logically deleted)
    const allRecords = await client.query(
      "SELECT id, lot_id, item_id, measure_value, deleted, create_time FROM lis_qc_record ORDER BY id DESC LIMIT 10"
    );
    console.log('\nAll records (last 10):');
    allRecords.rows.forEach(r => console.log(`  id=${r.id}, lot=${r.lot_id}, item=${r.item_id}, value=${r.measure_value}, deleted=${r.deleted}, time=${r.create_time}`));

    // Check lis_qc_alert
    const alertResult = await client.query(
      "SELECT COUNT(*) as total FROM lis_qc_alert"
    );
    console.log('\nlis_qc_alert count:', alertResult.rows[0].total);

  } catch (e) {
    console.error('Error:', e.message);
  } finally {
    await client.end();
  }
}

main();