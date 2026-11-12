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
    console.log('=== 检查现有质控记录的Z-Score和判定结果 ===');

    // 检查各批号的记录详情
    const records = await client.query(`
      SELECT 
        r.id, r.qc_lot_id, r.measure_value, r.z_score, r.qc_result, r.rule_violation,
        l.lot_no, l.target_value, l.sd
      FROM lis_qc_record r
      JOIN lis_qc_lot l ON r.qc_lot_id = l.id
      WHERE r.deleted = 0
      ORDER BY r.qc_lot_id, r.measure_time
      LIMIT 30
    `);
    console.log('前30条记录:');
    records.rows.forEach(r => {
      console.log(`id=${r.id}, lot=${r.lot_no}, value=${r.measure_value}, target=${r.target_value}, sd=${r.sd}, z=${r.z_score}, result=${r.qc_result}, rule=${r.rule_violation}`);
    });

    // 统计失控情况
    const stats = await client.query(`
      SELECT 
        qc_result, 
        COUNT(*) as count 
      FROM lis_qc_record 
      WHERE deleted = 0 
      GROUP BY qc_result
    `);
    console.log('\n=== 判定结果统计 ===');
    stats.rows.forEach(r => console.log(`  ${r.qc_result}: ${r.count} 条`));

    // 检查有多少条超出3SD的记录
    const outOf3sd = await client.query(`
      SELECT 
        r.id, r.qc_lot_id, r.measure_value, r.z_score,
        l.lot_no, l.target_value, l.sd
      FROM lis_qc_record r
      JOIN lis_qc_lot l ON r.qc_lot_id = l.id
      WHERE r.deleted = 0 AND ABS(COALESCE(r.z_score, 0)) >= 2
      ORDER BY ABS(r.z_score) DESC
    `);
    console.log('\n=== Z-Score绝对值>=2的记录 ===');
    outOf3sd.rows.forEach(r => {
      console.log(`id=${r.id}, lot=${r.lot_no}, value=${r.measure_value}, z=${r.z_score}, |z|>=3: ${Math.abs(parseFloat(r.z_score)) >= 3}`);
    });

  } catch(e) {
    console.log('错误:', e.message);
  } finally {
    await client.end().catch(() => {});
  }
}

main().catch(console.error);
