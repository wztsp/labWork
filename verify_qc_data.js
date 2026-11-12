const { Client } = require('pg');
const client = new Client({
  host: 'localhost',
  port: 5432,
  database: 'lis_db',
  user: 'postgres',
  password: 'postgres'
});

async function verifyData() {
  try {
    await client.connect();
    console.log('=== 验证质控数据完整性 ===\n');

    // 1. 检查记录数量
    const recordCount = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record WHERE deleted = 0');
    console.log('质控记录数 (deleted=0):', recordCount.rows[0].cnt);

    // 2. 检查批号关联
    const lotWithRecords = await client.query(`
      SELECT l.id, l.lot_no, COUNT(r.id) as record_count 
      FROM lis_qc_lot l 
      LEFT JOIN lis_qc_record r ON l.id = r.qc_lot_id AND r.deleted = 0 
      WHERE l.deleted = 0
      GROUP BY l.id, l.lot_no
      ORDER BY l.id
    `);
    console.log('\n=== 批号与记录关联 ===');
    lotWithRecords.rows.forEach(row => {
      console.log(`  批号: ${row.lot_no}, 记录数: ${row.record_count}`);
    });

    // 3. 检查记录的完整性
    const samples = await client.query(`
      SELECT id, qc_lot_id, measure_value, z_score, qc_result, rule_violation, measure_time 
      FROM lis_qc_record 
      WHERE deleted = 0 
      ORDER BY id DESC 
      LIMIT 10
    `);
    console.log('\n=== 最近10条记录 ===');
    samples.rows.forEach(row => {
      console.log(`  ID: ${row.id}, 值: ${row.measure_value}, Z-Score: ${row.z_score}, 结果: ${row.qc_result}, 规则: ${row.rule_violation}, 时间: ${row.measure_time}`);
    });

    // 4. 检查是否有空值
    const nullCheck = await client.query(`
      SELECT 
        SUM(CASE WHEN measure_value IS NULL THEN 1 ELSE 0 END) as null_values,
        SUM(CASE WHEN z_score IS NULL THEN 1 ELSE 0 END) as null_zscores,
        SUM(CASE WHEN qc_result IS NULL THEN 1 ELSE 0 END) as null_results
      FROM lis_qc_record 
      WHERE deleted = 0
    `);
    console.log('\n=== 空值检查 ===');
    console.log(`  NULL测量值: ${nullCheck.rows[0].null_values}`);
    console.log(`  NULL Z-Score: ${nullCheck.rows[0].null_zscores}`);
    console.log(`  NULL结果: ${nullCheck.rows[0].null_results}`);

    console.log('\n=== 数据验证完成 ===');

  } catch (err) {
    console.error('Error:', err.message);
  } finally {
    await client.end();
  }
}

verifyData();
