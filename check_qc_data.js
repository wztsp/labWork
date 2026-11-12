const { Client } = require('pg');
const client = new Client({
  host: 'localhost',
  port: 5432,
  database: 'lis_db',
  user: 'postgres',
  password: 'postgres'
});

async function checkData() {
  try {
    await client.connect();
    console.log('Connected to lis_db');

    const recordCount = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record');
    console.log('lis_qc_record total count:', recordCount.rows[0].cnt);

    const activeRecordCount = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record WHERE deleted = 0');
    console.log('lis_qc_record (deleted=0):', activeRecordCount.rows[0].cnt);

    const lotCount = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_lot');
    console.log('lis_qc_lot total count:', lotCount.rows[0].cnt);

    const activeLotCount = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_lot WHERE deleted = 0');
    console.log('lis_qc_lot (deleted=0):', activeLotCount.rows[0].cnt);

    // Check for foreign key violations or orphan records
    const orphanRecords = await client.query(`
      SELECT COUNT(*) as cnt FROM lis_qc_record r 
      LEFT JOIN lis_qc_lot l ON r.qc_lot_id = l.id 
      WHERE l.id IS NULL
    `);
    console.log('Orphan records (no matching lot):', orphanRecords.rows[0].cnt);

    // Show recent records if any
    if (parseInt(activeRecordCount.rows[0].cnt) > 0) {
      const recentRecords = await client.query(`
        SELECT r.id, r.qc_lot_id, r.measure_value, r.is_abnormal, r.measure_time, l.lot_number 
        FROM lis_qc_record r 
        LEFT JOIN lis_qc_lot l ON r.qc_lot_id = l.id 
        WHERE r.deleted = 0 
        ORDER BY r.id DESC 
        LIMIT 5
      `);
      console.log('\nRecent active records:');
      recentRecords.rows.forEach(row => {
        console.log(`  ID: ${row.id}, Lot: ${row.lot_number}, Value: ${row.measure_value}, Abnormal: ${row.is_abnormal}, Time: ${row.measure_time}`);
      });
    }

    // Check schema.sql
    try {
      const schemaInfo = await client.query(`
        SELECT column_name, data_type FROM information_schema.columns 
        WHERE table_name = 'lis_qc_record' 
        ORDER BY ordinal_position
      `);
      console.log('\nlis_qc_record columns:');
      schemaInfo.rows.forEach(col => {
        console.log(`  ${col.column_name}: ${col.data_type}`);
      });
    } catch (e) {
      console.log('Schema check failed:', e.message);
    }

  } catch (err) {
    console.error('Error:', err.message);
  } finally {
    await client.end();
  }
}

checkData();
