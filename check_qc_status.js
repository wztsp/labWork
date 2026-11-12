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
    console.log('=== lis_qc_record 表数据检查 ===');

    // 检查 deleted 字段分布
    const deletedDistribution = await client.query(
      'SELECT deleted, COUNT(*) as count FROM lis_qc_record GROUP BY deleted'
    );
    console.log('deleted 字段分布:');
    deletedDistribution.rows.forEach(r => console.log(`  deleted=${r.deleted}: ${r.count} 条`));

    // 检查批号关联情况
    const lotDistribution = await client.query(
      'SELECT qc_lot_id, COUNT(*) as count, SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) as active_count FROM lis_qc_record GROUP BY qc_lot_id ORDER BY qc_lot_id'
    );
    console.log('\n各批号记录数:');
    lotDistribution.rows.forEach(r => console.log(`  lot_id=${r.qc_lot_id}: 总${r.count}条, 活跃${r.active_count}条`));

    // 检查是否有 NULL 记录
    const nullRecords = await client.query(
      'SELECT COUNT(*) as count FROM lis_qc_record WHERE deleted IS NULL'
    );
    console.log('\ndeleted 为 NULL 的记录:', nullRecords.rows[0].count);

    // 检查lis_qc_lot表状态
    const lotStatus = await client.query(
      'SELECT id, lot_no, status, deleted, target_value, sd FROM lis_qc_lot'
    );
    console.log('\n=== lis_qc_lot 表详情 ===');
    lotStatus.rows.forEach(r => console.log(`  id=${r.id}, lot_no=${r.lot_no}, status=${r.status}, deleted=${r.deleted}, target=${r.target_value}, sd=${r.sd}`));

    // 检查qcrecord实体类对应的查询
    console.log('\n=== 模拟 MyBatis Plus 查询 (WHERE deleted=0) ===');
    const mpQuery = await client.query('SELECT COUNT(*) FROM lis_qc_record WHERE deleted = 0');
    console.log('MyBatis Plus 查询结果:', mpQuery.rows[0].count, '条');

  } catch(e) {
    console.log('错误:', e.message);
  } finally {
    await client.end().catch(() => {});
  }
}

main().catch(console.error);
