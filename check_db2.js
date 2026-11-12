const { Client } = require('pg');

async function main() {
  const client = new Client({
    host: 'localhost',
    port: 5432,
    database: 'lis_db',
    user: 'postgres',
    password: 'postgres'
  });

  try {
    await client.connect();
    console.log('=== lis_qc_record 表结构 ===');
    const columns = await client.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'lis_qc_record' ORDER BY ordinal_position"
    );
    columns.rows.forEach(r => console.log(r.column_name, '-', r.data_type));

    console.log('\n=== lis_qc_lot 表结构 ===');
    const lotColumns = await client.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'lis_qc_lot' ORDER BY ordinal_position"
    );
    lotColumns.rows.forEach(r => console.log(r.column_name, '-', r.data_type));

    console.log('\n=== 质控记录详情（前3条）===');
    const records = await client.query(
      'SELECT id, qc_lot_id, measure_value, mean_value, sd_value, target_value, status, deleted, create_time, update_time FROM lis_qc_record ORDER BY id DESC LIMIT 3'
    );
    records.rows.forEach(r => console.log(JSON.stringify(r)));

    console.log('\n=== 批号详情 ===');
    const lots = await client.query(
      'SELECT id, lot_number, test_item_id, target_value, mean_value, sd_value, status, deleted FROM lis_qc_lot WHERE deleted = 0'
    );
    lots.rows.forEach(r => console.log(JSON.stringify(r)));
  } catch(e) {
    console.log('错误:', e.message);
  } finally {
    await client.end().catch(() => {});
  }
}

main().catch(console.error);
