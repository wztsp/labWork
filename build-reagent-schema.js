// 合并试剂管理模块全部数据库脚本为一个独立、幂等可执行的 SQL 文件
// 输出: reagent-module-package/db/reagent-module-schema.sql
const fs = require('fs');
const path = require('path');

const DB_DIR = path.resolve(__dirname, 'LIS-clinical-lab-solution', 'lis-system', 'docs', 'database');
const OUT = path.resolve(__dirname, 'reagent-module-package', 'db', 'reagent-module-schema.sql');

// 1) 从 init-schema.sql 提取 3 张 reagent 基础表，改造为 IF NOT EXISTS
function extractBaseTables() {
  const init = fs.readFileSync(path.join(DB_DIR, 'init-schema.sql'), 'utf8');
  // 匹配 CREATE TABLE lis_reagent (...)、lis_reagent_stock、lis_reagent_stock_record 三个建表块
  const blocks = [];
  const names = ['lis_reagent', 'lis_reagent_stock', 'lis_reagent_stock_record'];
  for (const name of names) {
    const re = new RegExp(`CREATE TABLE ${name} \\([\\s\\S]*?\\);`, 'm');
    const m = init.match(re);
    if (!m) throw new Error(`未找到建表语句: ${name}`);
    let sql = m[0];
    // CREATE TABLE -> CREATE TABLE IF NOT EXISTS
    sql = sql.replace(`CREATE TABLE ${name} (`, `CREATE TABLE IF NOT EXISTS ${name} (`);
    blocks.push(`-- ============ ${name}（来自 init-schema.sql，已加 IF NOT EXISTS） ============\n${sql}\n`);

    // 提取紧跟其后的 COMMENT 和索引（直到下一个 CREATE TABLE 或 -- ==== 分隔线）
    const afterRe = new RegExp(`COMMENT ON TABLE ${name}[\\s\\S]*?(?=CREATE TABLE|-- ={2,}|\\z)`);
    const am = init.match(afterRe);
    if (am) {
      let idxSql = am[0];
      // 索引 IF NOT EXISTS 化
      idxSql = idxSql.replace(/CREATE UNIQUE INDEX /g, 'CREATE UNIQUE INDEX IF NOT EXISTS ');
      idxSql = idxSql.replace(/CREATE INDEX /g, 'CREATE INDEX IF NOT EXISTS ');
      blocks.push(idxSql.trim() + '\n');
    }
  }
  return blocks.join('\n');
}

// 2) 读取并原样拼接各个增量脚本
function readFile(name) {
  return fs.readFileSync(path.join(DB_DIR, name), 'utf8');
}

// 3) 把 add-reagent-lot-trace.sql 改成安全版本（lis_result 表可能不存在）
function buildLotTraceSafe() {
  return `-- =============================================================================
-- 试剂批次追溯 - 数据库变更脚本（独立模块版，lis_result 不存在时自动跳过）
-- =============================================================================
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'lis_result') THEN
    ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;
    ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);
    COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次库存ID (关联 lis_reagent_stock.id)';
    COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号 (冗余, 便于追溯查询)';
    CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_no ON lis_result (reagent_lot_no);
    CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_id ON lis_result (reagent_lot_id);
    RAISE NOTICE '已为 lis_result 添加试剂批次追溯字段';
  ELSE
    RAISE NOTICE 'lis_result 表不存在，跳过试剂批次追溯字段添加';
  END IF;
END $$;`;
}

// ============ 主流程 ============
const header = `-- =============================================================================
-- LIS 试剂管理模块 - 独立数据库脚本（v1.0）
-- 生成日期: ${new Date().toISOString().slice(0, 10)}
--
-- 【说明】
--   本脚本将试剂管理模块涉及的全部数据库变更合并为一个文件，可独立执行。
--   执行顺序（与原始增量脚本保持一致）：
--     ① 基础表（lis_reagent / lis_reagent_stock / lis_reagent_stock_record）
--     ② 核心扩展（入库/消耗/开瓶）—— reagent-core-tables.sql
--     ③ 库存盘点 —— add-reagent-stock-check.sql
--     ④ 增强功能（分类/供应商/仓库/预算/采购/出入库/报废）—— add-reagent-enhance.sql
--     ⑤ 批次追溯（lis_result 加列，安全包裹）—— add-reagent-lot-trace.sql
--
-- 【幂等性】
--   全部建表使用 CREATE TABLE IF NOT EXISTS；加列使用 ADD COLUMN IF NOT EXISTS；
--   索引使用 IF NOT EXISTS。可重复执行。
--
-- 【依赖】
--   仅依赖 PostgreSQL 本身，不依赖 LIS 其他模块表。
--   第 ⑤ 步对 lis_result 的加列做了 IF EXISTS 守卫，即使该表不存在也不会报错。
--
-- 【执行】
--   psql -h <host> -U <user> -d <db> -v ON_ERROR_STOP=1 -f reagent-module-schema.sql
-- =============================================================================

`;

const parts = [
  header,
  '-- =============================================================================\n-- ① 基础表\n-- =============================================================================\n',
  extractBaseTables(),
  '\n-- =============================================================================\n-- ② 核心扩展（reagent-core-tables.sql）\n-- =============================================================================\n',
  readFile('reagent-core-tables.sql'),
  '\n-- =============================================================================\n-- ③ 库存盘点（add-reagent-stock-check.sql）\n-- =============================================================================\n',
  readFile('add-reagent-stock-check.sql'),
  '\n-- =============================================================================\n-- ④ 增强功能（add-reagent-enhance.sql）\n-- =============================================================================\n',
  readFile('add-reagent-enhance.sql'),
  '\n-- =============================================================================\n-- ⑤ 批次追溯（add-reagent-lot-trace.sql 安全版）\n-- =============================================================================\n',
  buildLotTraceSafe(),
  '\n-- =============================================================================\n-- 脚本结束\n-- =============================================================================\n'
];

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, parts.join('\n'), 'utf8');
console.log('已生成:', OUT, `(${(fs.statSync(OUT).size/1024).toFixed(1)} KB)`);
