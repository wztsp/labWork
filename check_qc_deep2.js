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
    console.log('=== lis_qc_record 数据检查 ===\n');

    // 检查所有记录（包括已删除的）
    const allRecords = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record');
    console.log('所有记录总数:', allRecords.rows[0].cnt);

    const activeRecords = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record WHERE deleted = 0');
    console.log('有效记录 (deleted=0):', activeRecords.rows[0].cnt);

    const deletedRecords = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record WHERE deleted = 1');
    console.log('已删除记录 (deleted=1):', deletedRecords.rows[0].cnt);

    const nullDeleted = await client.query('SELECT COUNT(*) as cnt FROM lis_qc_record WHERE deleted IS NULL');
    console.log('deleted为NULL的记录:', nullDeleted.rows[0].cnt);

    // 检查lis_qc_lot的关联
    const lots = await client.query('SELECT id, lot_no, test_item_id, instrument_id, target_value, sd, deleted FROM lis_qc_lot');
    console.log('\n=== lis_qc_lot 批号数据 ===');
    lots.rows.forEach(row => {
      console.log(`  ID: ${row.id}, 批号: ${row.lot_no}, 项目: ${row.test_item_id}, 仪器: ${row.instrument_id}, 靶值: ${row.target_value}, SD: ${row.sd}, deleted: ${row.deleted}`);
    });

    // 检查序列状态
    const seqInfo = await client.query("SELECT last_value, is_called FROM lis_qc_record_id_seq");
    console.log(`\n序列 last_value: ${seqInfo.rows[0].last_value}, is_called: ${seqInfo.rows[0].is_called}`);

    // 检查是否有数据但deleted不为0的情况
    if (parseInt(deletedRecords.rows[0].cnt) > 0) {
      console.log('\n=== 已删除的记录样本 ===');
      const deletedSample = await client.query('SELECT id, qc_lot_id, measure_value, deleted FROM lis_qc_record WHERE deleted = 1 LIMIT 5');
      deletedSample.rows.forEach(row => {
        console.log(`  ID: ${row.id}, 批号ID: ${row.qc_lot_id}, 测量值: ${row.measure_value}, deleted: ${row.deleted}`);
      });
    }

  } catch (err) {
    console.error('Error:', err.message);
  } finally {
    await client.end();
  }
}

checkData();
