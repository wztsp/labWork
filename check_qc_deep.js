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

    // Check lis_schema_migrations
    console.log('=== Schema Migrations ===');
    const migrations = await client.query(
      "SELECT migration_name, success, applied_at FROM lis_schema_migrations ORDER BY applied_at DESC LIMIT 20"
    );
    console.log('Migrations applied:', migrations.rows.length);
    migrations.rows.forEach(r => console.log(`  ${r.migration_name}: success=${r.success}, at=${r.applied_at}`));

    // Check if lis_qc_record table structure has deleted column
    console.log('\n=== lis_qc_record columns ===');
    const columns = await client.query(
      "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = 'lis_qc_record' ORDER BY ordinal_position"
    );
    columns.rows.forEach(r => console.log(`  ${r.column_name}: ${r.data_type}, nullable=${r.is_nullable}, default=${r.column_default}`));

    // Check all QC tables
    console.log('\n=== QC Table Counts ===');
    const tables = ['lis_qc_record', 'lis_qc_lot', 'lis_qc_rule', 'lis_qc_alert'];
    for (const t of tables) {
      try {
        const r = await client.query(`SELECT COUNT(*) FROM ${t}`);
        console.log(`  ${t}: ${r.rows[0].count}`);
      } catch (e) {
        console.log(`  ${t}: ERROR - ${e.message}`);
      }
    }

    // Check if there are any other tables that are also empty
    console.log('\n=== Check for recently modified tables ===');
    const tableCheck = await client.query(`
      SELECT table_name FROM information_schema.tables 
      WHERE table_schema = 'public' 
      AND table_name LIKE 'lis_qc%'
      ORDER BY table_name
    `);
    console.log('QC tables found:', tableCheck.rows.map(r => r.table_name));

    // Check sequence for lis_qc_record
    console.log('\n=== Sequence Info ===');
    try {
      const seq = await client.query("SELECT last_value, is_called FROM lis_qc_record_id_seq");
      console.log(`lis_qc_record_id_seq: last_value=${seq.rows[0].last_value}, is_called=${seq.rows[0].is_called}`);
    } catch (e) {
      console.log('Error checking sequence:', e.message);
    }

    // Check lis_qc_lot data
    console.log('\n=== lis_qc_lot data ===');
    const lots = await client.query(
      "SELECT id, lot_no, test_item_id, instrument_id, target_value, sd, deleted FROM lis_qc_lot"
    );
    lots.rows.forEach(r => console.log(`  id=${r.id}, lot=${r.lot_no}, item=${r.test_item_id}, inst=${r.instrument_id}, target=${r.target_value}, sd=${r.sd}, deleted=${r.deleted}`));

    // Check if there are delete operations in the audit log
    console.log('\n=== Recent audit logs for QC ===');
    try {
      const logs = await client.query(
        "SELECT operation, method, params, create_time FROM sys_audit_log WHERE module LIKE '%qc%' OR module LIKE '%QC%' ORDER BY create_time DESC LIMIT 10"
      );
      console.log('Audit logs:', logs.rows.length);
      logs.rows.forEach(r => console.log(`  ${r.create_time}: ${r.operation} - ${r.method}`));
    } catch (e) {
      console.log('No audit logs or error:', e.message);
    }

  } catch (e) {
    console.error('Error:', e.message);
  } finally {
    await client.end();
  }
}

main();