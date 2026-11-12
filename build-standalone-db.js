// 生成试剂独立安装包的数据库脚本：
// 原试剂模块脚本 + lis_instrument / lis_result 最小桩表（用于成本报表与批次追溯）
const fs = require('fs');
const path = require('path');

const src = path.resolve(__dirname, 'reagent-module-package', 'db', 'reagent-module-schema.sql');
const enhance = path.resolve(__dirname, 'reagent-module-package', 'db', 'reagent-enhance-v1.1.sql');
const outDir = path.resolve(__dirname, 'reagent-installer', 'lis-reagent-1.0.0', 'db');
const out = path.join(outDir, '01-schema.sql');

const raw = fs.readFileSync(src, 'utf8');
// 去掉原文件头（第一段 -- ==== 注释块），保留正文
const body = raw.replace(/^-- ={10,}[\s\S]*?-- ={10,}\s*/, '');
// v1.1 增量：系统参数、试剂字段、订单审核、开封、发票等（幂等）
const enhanceBody = fs.readFileSync(enhance, 'utf8');

const header = `-- =============================================================================
-- LIS 试剂管理独立系统 - 数据库初始化脚本 v1.0
--
-- 【适用】PostgreSQL 14+
-- 【执行】psql -h <host> -p 5432 -U <user> -d lis_reagent -v ON_ERROR_STOP=1 -f 01-schema.sql
--
-- 【内容】
--   0. 外部依赖桩表（lis_instrument / lis_result，仅含试剂模块用到的字段）
--   1. 试剂基础表（字典 / 批次库存 / 出入库流水）
--   2. 核心业务（入库单 / 消耗单 / 开瓶记录）
--   3. 库存盘点
--   4. 增强功能（分类 / 供应商 / 仓库 / 预算 / 采购 / 请领 / 报废处置）
--   5. 批次追溯（lis_result 追加试剂批号字段）
--
-- 【幂等】全部 CREATE TABLE / CREATE INDEX 均带 IF NOT EXISTS，可重复执行。
--
-- 【说明】独立部署时不依赖 LIS 主系统。
--   · lis_instrument：仅用于「成本核算」按仪器汇总，可后续替换为真实仪器主数据
--   · lis_result    ：仅用于「批次追溯」查询检测结果，无检验业务时可保持空表
-- =============================================================================

-- =============================================================================
-- 0. 外部依赖桩表
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_instrument (
    id              BIGSERIAL PRIMARY KEY,
    instrument_code VARCHAR(32),
    instrument_name VARCHAR(100)
);
COMMENT ON TABLE lis_instrument IS '仪器主数据（独立版桩表，供成本核算按仪器汇总）';

CREATE TABLE IF NOT EXISTS lis_result (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT,
    test_item_id    BIGINT,
    result_value    VARCHAR(200),
    unit            VARCHAR(30),
    ref_range       VARCHAR(100),
    abnormal_flag   VARCHAR(10),
    is_critical     SMALLINT DEFAULT 0,
    instrument_id   BIGINT,
    test_time       TIMESTAMP,
    reagent_lot_id  BIGINT,
    reagent_lot_no  VARCHAR(50)
);
COMMENT ON TABLE lis_result IS '检验结果（独立版桩表，供试剂批次追溯查询）';
CREATE INDEX IF NOT EXISTS idx_lis_result_lot_no ON lis_result (reagent_lot_no);

`;

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(out, header + body + '\n' + enhanceBody, 'utf8');
console.log('已生成:', out, `(${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);
