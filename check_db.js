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
    console.log('=== lis_db 数据库连接成功 ===');

    const tables = await client.query(
      "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
    );
    console.log('=== 数据库表列表 ===');
    tables.rows.forEach(r => console.log(r.tablename));

    if (tables.rows.some(r => r.tablename === 'lis_qc_record')) {
      const qcRecordCount = await client.query('SELECT COUNT(*) as total FROM lis_qc_record');
      console.log('=== lis_qc_record 记录数 ===:', qcRecordCount.rows[0].total);

      const qcLotCount = await client.query('SELECT COUNT(*) as total FROM lis_qc_lot');
      console.log('=== lis_qc_lot 批次数 ===:', qcLotCount.rows[0].total);

      if (qcRecordCount.rows[0].total > 0) {
        const recentRecords = await client.query(
          'SELECT id, qc_lot_id, measure_value, target_value, status, deleted, create_time FROM lis_qc_record ORDER BY id DESC LIMIT 5'
        );
        console.log('=== 最近5条记录 ===');
        recentRecords.rows.forEach(r => console.log(JSON.stringify(r)));
      }
    }
  } catch(e) {
    console.log('连接失败:', e.message);
  } finally {
    await client.end().catch(() => {});
  }
}

main().catch(console.error);
