const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r = await c.query(`SELECT COUNT(*)::int total,
      COUNT(*) FILTER (WHERE age_min IS NULL OR age_max IS NULL)::int null_age,
      COUNT(*) FILTER (WHERE age_min IS NULL AND age_max IS NULL)::int both_null,
      COUNT(*) FILTER (WHERE gender IS NULL)::int null_gender
    FROM lis_reference_range`);
  console.table(r.rows);
  const r2 = await c.query(`SELECT COUNT(*)::int total,
      COUNT(*) FILTER (WHERE condition_remark IS NOT NULL AND condition_remark <> '')::int has_cond
    FROM lis_reference_range`);
  console.table(r2.rows);
  // 检查一个无年龄限制的典型范围
  const r3 = await c.query(`SELECT id, test_item_id, gender, age_min, age_max, condition_remark, ref_low, ref_high
    FROM lis_reference_range WHERE age_min IS NULL LIMIT 5`);
  console.log('age_min 为空的记录:');
  console.table(r3.rows);
  await c.end();
})();