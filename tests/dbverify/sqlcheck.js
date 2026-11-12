// 验证: 参数为NULL时PG无法推断类型 -> 异常; 加CAST后正常
const { Client } = require('pg');
const conn = { host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' };

(async () => {
  const c = new Client(conn);
  await c.connect();

  // 测试1: 原始SQL, gender=null (只绑一个参数)
  console.log('=== 测试1: 原始SQL, gender=null, age=null ===');
  try {
    const sql = `
      SELECT DISTINCT ON (t.id) t.id AS test_item_id, t.item_name
      FROM lis_test_item t
      LEFT JOIN lis_reference_range rr ON rr.test_item_id = t.id
        AND (rr.gender IS NULL OR rr.gender = $1)
        AND ($2 IS NULL OR rr.age_min IS NULL OR rr.age_min <= $2)
        AND ($2 IS NULL OR rr.age_max IS NULL OR rr.age_max >= $2)
      WHERE t.parent_id IN (9) AND t.is_panel = 0 AND t.status = 1
      ORDER BY t.id,
        CASE WHEN rr.gender = $1 THEN 0 WHEN rr.gender IS NULL THEN 1 ELSE 2 END,
        COALESCE(rr.age_max - rr.age_min, 999999), rr.id
    `;
    const r = await c.query(sql, [null, null]);
    console.log('  行数=', r.rows.length);
  } catch (e) { console.log('  异常:', e.message); }

  // 测试2: 加 CAST 后, gender=null, age=null
  console.log('\n=== 测试2: 加CAST, gender=null, age=null ===');
  try {
    const sql = `
      SELECT DISTINCT ON (t.id) t.id AS test_item_id, t.item_name
      FROM lis_test_item t
      LEFT JOIN lis_reference_range rr ON rr.test_item_id = t.id
        AND (rr.gender IS NULL OR rr.gender = CAST($1 AS INT))
        AND (CAST($2 AS INT) IS NULL OR rr.age_min IS NULL OR rr.age_min <= CAST($2 AS INT))
        AND (CAST($2 AS INT) IS NULL OR rr.age_max IS NULL OR rr.age_max >= CAST($2 AS INT))
      WHERE t.parent_id IN (9) AND t.is_panel = 0 AND t.status = 1
      ORDER BY t.id,
        CASE WHEN rr.gender = CAST($1 AS INT) THEN 0 WHEN rr.gender IS NULL THEN 1 ELSE 2 END,
        COALESCE(rr.age_max - rr.age_min, 999999), rr.id
    `;
    const r = await c.query(sql, [null, null]);
    console.log('  行数=', r.rows.length);
  } catch (e) { console.log('  异常:', e.message); }

  await c.end();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });