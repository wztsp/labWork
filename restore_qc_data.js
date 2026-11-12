const { Client } = require('pg');
const client = new Client({ user: 'lis_user', host: 'localhost', database: 'lis_db', password: 'lis_dev_2026', port: 5432 });
client.connect();
(async () => {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

  // ====== 1. 先清除 lis_qc_record 的残留数据, 重新插入完整的测试数据 ======
  await client.query("DELETE FROM lis_qc_record WHERE qc_lot_id IN (15,16,17,18,19,20,21)");

  // ====== 2. 针对 LOT ID=18 (DEBUG-13S-LOT, 靶值=100, SD=2) 插入9条失控数据 ======
  // 这些数据有的在控有的失控, 但大部分都失控
  const testLotId = 18;
  const testItemId = 2;  // 红细胞计数
  const instrumentId = 4;

  // 9 条数据 - 基于用户截图, 模拟真实的质控数据
  // 设置靶值=100, SD=2
  // #1: 92  (z = (92-100)/2 = -4.0  → 失控 1-3s)
  // #2: 93  (z = -3.5 → 失控 1-3s)
  // #3: 94  (z = -3.0 → 失控 1-3s, 刚好达到)
  // #4: 101 (z = 0.5  → 在控)
  // #5: 103 (z = 1.5  → 在控)
  // #6: 99  (z = -0.5 → 在控)
  // #7: 100 (z = 0    → 在控)
  // #8: 92  (z = -4.0 → 失控 1-3s)
  // #9: 108 (z = 4.0  → 失控 1-3s, 明显偏高)
  const records = [
    { value: 92,  z: -4.0,  rule: '1-3s', qc: 'REJECT' },
    { value: 93,  z: -3.5,  rule: '1-3s', qc: 'REJECT' },
    { value: 94,  z: -3.0,  rule: '1-3s', qc: 'REJECT' },
    { value: 101, z: 0.5,   rule: '',     qc: 'ACCEPT' },
    { value: 103, z: 1.5,   rule: '',     qc: 'ACCEPT' },
    { value: 99,  z: -0.5,  rule: '',     qc: 'ACCEPT' },
    { value: 100, z: 0,     rule: '',     qc: 'ACCEPT' },
    { value: 92,  z: -4.0,  rule: '1-3s', qc: 'REJECT' },
    { value: 108, z: 4.0,   rule: '1-3s', qc: 'REJECT' }
  ];

  let inserted = 0;
  for (let i = 0; i < records.length; i++) {
    const offsetHours = i * 2;
    const measureTime = new Date(now.getTime() - (records.length - 1 - i) * 2 * 3600 * 1000);
    const rec = records[i];

    await client.query(`
      INSERT INTO lis_qc_record 
        (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, 
         operator_id, z_score, rule_violation, qc_result, create_time, deleted)
      VALUES 
        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 0)`,
      [testLotId, testItemId, instrumentId, rec.value, fmt(measureTime), 
       1, rec.z, rec.rule, rec.qc, fmt(measureTime)]);
    inserted++;
  }
  console.log(`LOT 18: 插入 ${inserted} 条记录`);

  // ====== 3. 针对 LOT ID=15 (TEST-LOT-RULE, 靶值=100, SD=2) 插入5条在控数据 ======
  const records15 = [
    { value: 99,  z: -0.5, rule: '', qc: 'ACCEPT' },
    { value: 100, z: 0,    rule: '', qc: 'ACCEPT' },
    { value: 101, z: 0.5, rule: '', qc: 'ACCEPT' },
    { value: 100, z: 0,    rule: '', qc: 'ACCEPT' },
    { value: 102, z: 1.0,  rule: '', qc: 'ACCEPT' }
  ];
  for (let i = 0; i < records15.length; i++) {
    const offsetHours = (records15.length - 1 - i) * 3;
    const measureTime = new Date(now.getTime() - offsetHours * 3600 * 1000);
    const rec = records15[i];
    await client.query(`
      INSERT INTO lis_qc_record 
        (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, 
         operator_id, z_score, rule_violation, qc_result, create_time, deleted)
      VALUES 
        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 0)`,
      [15, testItemId, instrumentId, rec.value, fmt(measureTime),
       1, rec.z, rec.rule, rec.qc, fmt(measureTime)]);
  }
  console.log('LOT 15: 插入 5 条在控记录');

  // ====== 4. 针对 LOT ID=21 (RECALC-TEST-LOT) 插入5条混合数据(测试重算) ======
  const records21 = [
    { value: 100, z: 0,    rule: '',     qc: 'ACCEPT' },
    { value: 95,  z: -2.5, rule: '',     qc: 'ACCEPT' },
    { value: 106, z: 3.0,  rule: '1-3s', qc: 'REJECT' },
    { value: 94,  z: -3.0, rule: '1-3s', qc: 'REJECT' },
    { value: 107, z: 3.5,  rule: '1-3s', qc: 'REJECT' }
  ];
  for (let i = 0; i < records21.length; i++) {
    const offsetHours = (records21.length - 1 - i) * 2;
    const measureTime = new Date(now.getTime() - offsetHours * 3600 * 1000);
    const rec = records21[i];
    await client.query(`
      INSERT INTO lis_qc_record 
        (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, 
         operator_id, z_score, rule_violation, qc_result, create_time, deleted)
      VALUES 
        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 0)`,
      [21, 3, 7, rec.value, fmt(measureTime),
       1, rec.z, rec.rule, rec.qc, fmt(measureTime)]);
  }
  console.log('LOT 21: 插入 5 条混合数据');

  // ====== 5. 针对 LOT ID=19 (ZSCORE-PRECISE-TEST) 插入3条在控数据 ======
  for (let i = 0; i < 3; i++) {
    const measureTime = new Date(now.getTime() - (2 - i) * 4 * 3600 * 1000);
    await client.query(`
      INSERT INTO lis_qc_record 
        (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, 
         operator_id, z_score, rule_violation, qc_result, create_time, deleted)
      VALUES 
        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 0)`,
      [19, 34, 5, 100 + i, fmt(measureTime),
       1, 0.5 * i, '', 'ACCEPT', fmt(measureTime)]);
  }
  console.log('LOT 19: 插入 3 条在控记录');

  // ====== 6. 针对 LOT ID=20 (ZSCORE-6DEC-VERIFY) 插入3条数据 ======
  for (let i = 0; i < 3; i++) {
    const measureTime = new Date(now.getTime() - (2 - i) * 4 * 3600 * 1000);
    await client.query(`
      INSERT INTO lis_qc_record 
        (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, 
         operator_id, z_score, rule_violation, qc_result, create_time, deleted)
      VALUES 
        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 0)`,
      [20, 35, 6, 98 + i * 2, fmt(measureTime),
       1, -1.0 + i, '', 'ACCEPT', fmt(measureTime)]);
  }
  console.log('LOT 20: 插入 3 条记录');

  // ====== 7. 验证 ======
  const total = await client.query("SELECT count(*) FROM lis_qc_record WHERE deleted = 0");
  console.log(`\n恢复完成! 当前在控记录总数: ${total.rows[0].count}`);

  const byLot = await client.query("SELECT qc_lot_id, count(*) as cnt FROM lis_qc_record WHERE deleted = 0 GROUP BY qc_lot_id ORDER BY qc_lot_id");
  console.log('各批号记录数:');
  byLot.rows.forEach(r => console.log(`  LOT ${r.qc_lot_id}: ${r.cnt} 条`));

  await client.end();
})();
