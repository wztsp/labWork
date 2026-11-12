-- =============================================================================
-- LIS 试剂管理模块 - 独立数据库脚本（v1.0）
-- 生成日期: 2026-10-25
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


-- =============================================================================
-- ① 基础表
-- =============================================================================

-- ============ lis_reagent（来自 init-schema.sql，已加 IF NOT EXISTS） ============
CREATE TABLE IF NOT EXISTS lis_reagent (
    id              BIGSERIAL PRIMARY KEY,
    reagent_code    VARCHAR(32) NOT NULL,                 -- 试剂编码
    reagent_name    VARCHAR(100) NOT NULL,                -- 试剂名称
    specification   VARCHAR(50),                          -- 规格
    manufacturer    VARCHAR(100),                         -- 生产厂家
    cas_no          VARCHAR(50),                          -- CAS 号
    storage_cond    VARCHAR(50),                          -- 储存条件
    unit            VARCHAR(20),                          -- 单位
    instrument_id   BIGINT,                               -- 适用仪器
    test_item_id    BIGINT,                               -- 关联项目
    safety_stock    DECIMAL(10,2),                        -- 安全库存
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_reagent IS '试剂字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_code ON lis_reagent (reagent_code);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_instrument ON lis_reagent (instrument_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_item ON lis_reagent (test_item_id);

-- 试剂库存表 (按批次管理)

-- ============ lis_reagent_stock（来自 init-schema.sql，已加 IF NOT EXISTS） ============
CREATE TABLE IF NOT EXISTS lis_reagent_stock (
    id              BIGSERIAL PRIMARY KEY,
    reagent_id      BIGINT NOT NULL,
    lot_no          VARCHAR(32) NOT NULL,                 -- 批号
    barcode         VARCHAR(64),                          -- 二维码/条码
    expiry_date     DATE NOT NULL,                       -- 效期
    quantity        DECIMAL(10,2),                        -- 当前数量
    location        VARCHAR(100),                         -- 存放位置
    status          SMALLINT DEFAULT 1,                   -- 0过期锁定 1可用 2已耗尽
    in_stock_time   TIMESTAMP,                            -- 入库时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_reagent_stock IS '试剂库存表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_lot ON lis_reagent_stock (reagent_id, lot_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_expiry ON lis_reagent_stock (expiry_date);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_status ON lis_reagent_stock (status);

-- 试剂出入库记录表

-- ============ lis_reagent_stock_record（来自 init-schema.sql，已加 IF NOT EXISTS） ============
CREATE TABLE IF NOT EXISTS lis_reagent_stock_record (
    id              BIGSERIAL PRIMARY KEY,
    stock_id        BIGINT NOT NULL,
    reagent_id      BIGINT NOT NULL,
    record_type     SMALLINT,                             -- 1入库 2出库 3报损 4退库 5盘点
    quantity        DECIMAL(10,2),                        -- 正数入库/负数出库
    before_qty      DECIMAL(10,2),
    after_qty       DECIMAL(10,2),
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    source_no       VARCHAR(32),                          -- 采购单号/领用单号
    related_id      BIGINT,                               -- 关联业务 ID (结果/质控)
    related_type    VARCHAR(20),                          -- RESULT / QC
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_reagent_stock_record IS '试剂出入库记录表';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_reagent ON lis_reagent_stock_record (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_time ON lis_reagent_stock_record (create_time);


-- =============================================================================
-- ② 核心扩展（reagent-core-tables.sql）
-- =============================================================================

-- =============================================================================
-- 试剂管理核心模块 - 数据库变更脚本
-- 1. 扩展 lis_reagent 表
-- 2. 新增入库单、入库明细、消耗单、开瓶记录表
-- =============================================================================

-- 1. 扩展试剂字典表
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS category_id BIGINT;           -- 分类ID
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS manufacturer_id BIGINT;       -- 厂商ID
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS reg_cert_no VARCHAR(64);      -- 注册证号
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS expected_use TEXT;            -- 预期用途
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS price_purchase DECIMAL(12,2); -- 采购价
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS price_selling DECIMAL(12,2);  -- 销售价
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS usage_per_test DECIMAL(10,4); -- 每测试用量
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS opened_expiry_days INT;       -- 开瓶效期(天)
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS min_stock DECIMAL(10,2);      -- 库存下限
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS max_stock DECIMAL(10,2);      -- 库存上限
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS supplier VARCHAR(100);        -- 供应商

-- 2. 试剂入库单
CREATE TABLE IF NOT EXISTS lis_reagent_inbound (
    id              BIGSERIAL PRIMARY KEY,
    inbound_no      VARCHAR(32) NOT NULL,                  -- 入库单号
    supplier        VARCHAR(100),                          -- 供应商
    total_amount    DECIMAL(12,2) DEFAULT 0,              -- 总金额
    verify_status   SMALLINT DEFAULT 0,                    -- 0待验收 1已验收 2拒收
    verifier_id     BIGINT,                                -- 验收人ID
    verifier_name   VARCHAR(50),                           -- 验收人姓名
    verify_time     TIMESTAMP,                             -- 验收时间
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_inbound IS '试剂入库单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_inbound_no ON lis_reagent_inbound (inbound_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_status ON lis_reagent_inbound (verify_status);

-- 3. 试剂入库明细
CREATE TABLE IF NOT EXISTS lis_reagent_inbound_item (
    id              BIGSERIAL PRIMARY KEY,
    inbound_id      BIGINT NOT NULL,                       -- 入库单ID
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    lot_no          VARCHAR(32) NOT NULL,                  -- 批号
    barcode         VARCHAR(64),                           -- 条码
    quantity        DECIMAL(10,2) NOT NULL,               -- 数量
    unit            VARCHAR(20),                           -- 单位
    purchase_price  DECIMAL(12,2),                         -- 采购单价
    amount          DECIMAL(12,2),                         -- 金额
    expiry_date     DATE NOT NULL,                        -- 效期
    location        VARCHAR(100),                          -- 存放位置
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_inbound_item IS '试剂入库明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_inbound ON lis_reagent_inbound_item (inbound_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_reagent ON lis_reagent_inbound_item (reagent_id);

-- 4. 试剂消耗单
CREATE TABLE IF NOT EXISTS lis_reagent_consumption (
    id              BIGSERIAL PRIMARY KEY,
    consumption_no  VARCHAR(32) NOT NULL,                  -- 消耗单号
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    stock_id        BIGINT,                                -- 批次库存ID
    lot_no          VARCHAR(32),                           -- 批号
    quantity        DECIMAL(10,2) NOT NULL,               -- 消耗数量
    unit            VARCHAR(20),                           -- 单位
    consume_type    SMALLINT DEFAULT 1,                    -- 1手工 2自动(检验结果) 3质控 4校准 5维护
    related_no      VARCHAR(32),                           -- 关联单号(检验号/质控号)
    operator_id     BIGINT,                                -- 操作人ID
    operator_name   VARCHAR(50),                           -- 操作人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_consumption IS '试剂消耗单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_consumption_no ON lis_reagent_consumption (consumption_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_consumption_reagent ON lis_reagent_consumption (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_consumption_time ON lis_reagent_consumption (create_time);

-- 5. 试剂开瓶记录
CREATE TABLE IF NOT EXISTS lis_reagent_opening (
    id              BIGSERIAL PRIMARY KEY,
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    stock_id        BIGINT,                                -- 批次库存ID
    lot_no          VARCHAR(32),                           -- 批号
    open_time       TIMESTAMP NOT NULL,                    -- 开瓶时间
    opened_expiry   DATE,                                  -- 开瓶效期
    status          SMALLINT DEFAULT 1,                    -- 1正常 0已过期
    operator_id     BIGINT,                                -- 操作人ID
    operator_name   VARCHAR(50),                           -- 操作人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_opening IS '试剂开瓶记录';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_opening_reagent ON lis_reagent_opening (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_opening_status ON lis_reagent_opening (status);

-- 6. 扩展出入库记录类型 (增加6消耗 7调拨)
-- record_type: 1入库 2出库 3报损 4退库 5盘点 6消耗 7调拨


-- =============================================================================
-- ③ 库存盘点（add-reagent-stock-check.sql）
-- =============================================================================

-- =============================================================================
-- 试剂库存盘点 - 数据库变更脚本
-- 新增盘点单主表与盘点明细表
-- =============================================================================

-- 1. 试剂库存盘点单
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id              BIGSERIAL PRIMARY KEY,
    check_no        VARCHAR(32) NOT NULL,                  -- 盘点单号
    warehouse       VARCHAR(100),                          -- 盘点仓库/库房
    check_date      DATE,                                   -- 盘点日期
    status          SMALLINT DEFAULT 0,                     -- 0进行中 1已完成 2已作废
    operator_id     BIGINT,                                 -- 盘点人ID
    operator_name   VARCHAR(50),                            -- 盘点人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check IS '试剂库存盘点单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_check_no ON lis_reagent_stock_check (check_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_status ON lis_reagent_stock_check (status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_warehouse ON lis_reagent_stock_check (warehouse);

-- 2. 试剂库存盘点明细
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL,                       -- 盘点单ID
    stock_id        BIGINT,                                -- 批次库存ID
    reagent_id      BIGINT,                                -- 试剂ID
    lot_no          VARCHAR(50),                           -- 批号
    book_qty        DECIMAL(12,2),                         -- 账面数量
    actual_qty      DECIMAL(12,2),                         -- 实盘数量
    diff_qty        DECIMAL(12,2) DEFAULT 0,               -- 差异数量 (实盘-账面)
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check_item IS '试剂库存盘点明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_check ON lis_reagent_stock_check_item (check_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_reagent ON lis_reagent_stock_check_item (reagent_id);


-- =============================================================================
-- ④ 增强功能（add-reagent-enhance.sql）
-- =============================================================================

-- =============================================================================
-- 试剂管理增量开发 - 数据库变更脚本
-- 对应设计文档: docs/reagent-enhance-design.md 第 3 章
-- 对应任务    : T-01（地基层）
-- 覆盖范围    : 第一批(R-01~R-07, R-09) + 第二批(R-08, R-10~R-14) 的全部表结构
--               表结构一次到位，第二批不再改表
--
-- 【执行说明】
--   1. 本脚本幂等，可重复执行（建表 IF NOT EXISTS / 加列 IF NOT EXISTS / 迁移带 WHERE 条件）
--   2. 依赖 init-schema.sql / reagent-core-tables.sql / add-reagent-stock-check.sql 已执行
--   3. 第 17 节的索引重建是【破坏性变更】，执行前请备份 lis_reagent_stock
--   4. ⚠️ 绝不修改 init-schema.sql（裸 CREATE TABLE，在已有库重跑会失败）
--   5. 改造存量表共 6 张：lis_reagent、lis_reagent_stock、lis_reagent_inbound、
--      lis_reagent_inbound_item、lis_reagent_stock_record、lis_reagent_stock_check
--   6. 第 22.0.1 节【前置守卫】会在存在「人工录入之间重名」时主动 RAISE EXCEPTION 中断，
--      这是设计行为：人工录入的重名供应商必须先在页面归并，脚本不代做（避免误删证照数据）。
--      守卫位于所有破坏性语句【之前】，触发时一条破坏性语句都不会执行；
--      第 22.2 节为归并后的全量兜底断言，正常情况下永不触发。
--
-- 【已核实无需处理的事项】
--   × lis_reagent.reagent_code 唯一索引 —— init-schema.sql L648 已存在 (uk_lis_reagent_code)，
--     无需重复检测、无需补建（设计文档 C-1 / 主理人 U-1 裁决）。仅在第 24 节保留存量重复检测查询。
--   × deleted 逻辑删除列 —— 不新增。详见本文件末尾「附录 A：C-9 复核结论」。
--     application.yml 的 mybatis-plus.global-config.db-config.logic-delete-field: deleted
--     仅在实体类存在同名字段时才生效；reagent 模块 9 个实体均无 deleted 字段，配置惰性无害。
--     本脚本所有新表同样不加 deleted 列，新实体禁止声明 deleted 字段，逻辑删除语义一律用 status。
--   × lis_reagent_stock_record.req_id —— 不新增。复用已有 related_id / related_type
--     多态外键（设计文档 C-11）。
--
-- 【⚠️ 单事务执行警告（P2-01）】
--   本脚本自 BEGIN; 起至 COMMIT; 止，全文包裹在【单个事务】中，任何一句失败都会
--   整体回滚，不会留下「半迁移态」。代价与注意事项：
--     · 事务期间对涉及的表持有 ACCESS EXCLUSIVE 等强锁直至 COMMIT（ALTER TABLE /
--       CREATE INDEX / DROP INDEX 均如此），期间业务读写会被阻塞；
--       ==> 必须在【停机窗口】执行，不可在业务高峰在线执行。
--     · 已核实全文不含 CREATE INDEX CONCURRENTLY / VACUUM / CREATE DATABASE /
--       ALTER SYSTEM / REINDEX SYSTEM 等「事务块内禁止」语句，整体包裹安全。
--     · 因文件自带 BEGIN;/COMMIT;，用 `psql -1`（--single-transaction）执行属等效
--       嵌套，PostgreSQL 仅告警 "there is already a transaction in progress"，无害。
--     · 推荐执行方式：
--         psql -v ON_ERROR_STOP=1 -f add-reagent-enhance.sql
--       ON_ERROR_STOP=1 不可省：否则出错后 psql 会继续执行到文件末尾的 COMMIT;，
--       虽然事务已进入 aborted 状态、COMMIT 实际等价 ROLLBACK，但退出码会失真。
-- =============================================================================


-- ⚠️ P2-01：单事务开始。与文件末尾的 COMMIT; 严格配对，中间不得插入 COMMIT/ROLLBACK。
BEGIN;


-- =============================================================================
-- 一、基础主数据（3 张）
-- =============================================================================

-- 1. 试剂分类字典（树形，填实 lis_reagent.category_id 悬空外键）
CREATE TABLE IF NOT EXISTS lis_reagent_category (
    id               BIGSERIAL PRIMARY KEY,
    category_code    VARCHAR(32)  NOT NULL,              -- 分类编码
    category_name    VARCHAR(100) NOT NULL,              -- 分类名称
    parent_id        BIGINT       NOT NULL DEFAULT 0,    -- 父分类ID，0=根
    path             VARCHAR(255),                       -- 物化路径 /CAT_CHEM/，用于子树查询
    level_no         SMALLINT     DEFAULT 1,             -- 层级 1/2/3
    hazard_category  SMALLINT     DEFAULT 0,             -- 0普通 1危险品类（该类下试剂默认危险品）
    sort_order       INT          DEFAULT 0,
    status           SMALLINT     DEFAULT 1,             -- 0停用 1启用
    remark           TEXT,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_category IS '试剂分类字典（树形）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_category_code    ON lis_reagent_category (category_code);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_category_parent ON lis_reagent_category (parent_id);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_category_path   ON lis_reagent_category (path);


-- 2. 供应商主数据（通用表，不带 reagent 前缀，仪器模块未来可复用 —— 主理人 Q1 裁决）
CREATE TABLE IF NOT EXISTS lis_supplier (
    id                   BIGSERIAL PRIMARY KEY,
    supplier_code        VARCHAR(32)  NOT NULL,          -- 供应商编码
    supplier_name        VARCHAR(200) NOT NULL,          -- 供应商名称
    short_name           VARCHAR(100),                   -- 简称
    credit_code          VARCHAR(64),                    -- 统一社会信用代码
    contact_person       VARCHAR(50),                    -- 联系人
    phone                VARCHAR(30),
    email                VARCHAR(100),
    address              VARCHAR(255),
    license_no           VARCHAR(64),                    -- 营业执照号
    license_expiry       DATE,                           -- 营业执照到期日
    medical_cert_no      VARCHAR(64),                    -- 医疗器械经营许可证号
    medical_cert_expiry  DATE,                           -- 医疗器械经营许可证到期日
    qual_status          SMALLINT DEFAULT 0,             -- 资质状态 0待完善 1有效 2已过期（由 Job 每日刷新）
    score                DECIMAL(5,2),                   -- 综合评分（第二批 R-11 使用）
    source_type          SMALLINT DEFAULT 1,             -- 1手工录入 2存量字符串迁移生成（草稿）
    status               SMALLINT DEFAULT 1,             -- 0停用 1启用
    remark               TEXT,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_supplier IS '供应商主数据（通用，非试剂专属）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_supplier_code    ON lis_supplier (supplier_code);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_name   ON lis_supplier (supplier_name);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_qual   ON lis_supplier (qual_status, status);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_expiry ON lis_supplier (license_expiry, medical_cert_expiry);


-- 3. 仓库/冰箱/层架主数据（三级树，带温度区间）
CREATE TABLE IF NOT EXISTS lis_reagent_warehouse (
    id            BIGSERIAL PRIMARY KEY,
    wh_code       VARCHAR(32)  NOT NULL,                 -- 仓库编码
    wh_name       VARCHAR(100) NOT NULL,                 -- 仓库名称
    parent_id     BIGINT       NOT NULL DEFAULT 0,       -- 父节点，0=根
    path          VARCHAR(255),                          -- 物化路径 /1/5/23/，子树查询用 likeRight
    wh_type       SMALLINT     DEFAULT 1,                -- 1库房 2冰箱 3层架
    dept_id       BIGINT,                                -- 归属科室（快照，指向 sys_department.id，不建外键）
    dept_name     VARCHAR(100),                          -- 科室名称冗余快照
    temp_min      DECIMAL(6,2),                          -- 温度区间下限(℃)
    temp_max      DECIMAL(6,2),                          -- 温度区间上限(℃)
    location_desc VARCHAR(255),                          -- 位置描述
    sort_order    INT      DEFAULT 0,
    status        SMALLINT DEFAULT 1,                    -- 0停用 1启用
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_warehouse IS '试剂仓库/冰箱/层架主数据（三级树）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_warehouse_code    ON lis_reagent_warehouse (wh_code);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_warehouse_parent ON lis_reagent_warehouse (parent_id);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_warehouse_path   ON lis_reagent_warehouse (path);

-- 3.1 【关键】插入 id=0 的默认库房哨兵。
--     存量 lis_reagent_stock 无仓库归属，统一落到 warehouse_id=0；
--     用哨兵值而非 NULL，是因为 PostgreSQL 唯一索引中 NULL<>NULL 会让第 17 节的
--     三列唯一约束对存量数据完全失效（详见设计文档 C-2）。
INSERT INTO lis_reagent_warehouse (id, wh_code, wh_name, parent_id, path, wh_type, sort_order, status, remark)
SELECT 0, 'WH_DEFAULT', '默认库房（待归集）', 0, '/0/', 1, 9999, 1,
       '系统内置。存量库存与未指定仓库的入库默认归入此节点，请逐步归集到实际仓库。'
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_warehouse WHERE id = 0);


-- =============================================================================
-- 二、预算域（2 张）
-- =============================================================================

-- 4. 预算主表
CREATE TABLE IF NOT EXISTS lis_reagent_budget (
    id            BIGSERIAL PRIMARY KEY,
    budget_no     VARCHAR(32),                           -- 预算编号（可选，展示用）
    budget_year   INT           NOT NULL,                -- 预算年度
    dept_id       BIGINT        NOT NULL,                -- 科室ID（指向 sys_department.id，不建外键，跨模块解耦）
    dept_name     VARCHAR(100),                          -- 科室名称冗余快照
    category_id   BIGINT,                                -- 试剂类别ID，NULL=不限类别（通用预算）
    category_name VARCHAR(100),                          -- 类别名称冗余快照
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 预算总额
    frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 已占用（申请提交时 +，驳回/作废时 -，实扣时 -）
    used_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 已实扣（入库验收通过时 +）
    warn_ratio    DECIMAL(5,4)  NOT NULL DEFAULT 0.8000, -- 预警阈值，默认 80%
    version       INT           NOT NULL DEFAULT 0,      -- 乐观锁版本号（配合 FOR UPDATE 做双保险）
    status        SMALLINT      DEFAULT 1,               -- 0停用 1启用
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 金额恒不为负的数据库级保护
    CONSTRAINT ck_lis_reagent_budget_amount CHECK (
        total_amount >= 0 AND frozen_amount >= 0 AND used_amount >= 0
    )
);
COMMENT ON TABLE lis_reagent_budget IS '试剂预算主表（年度+科室+类别 三维）';

-- 唯一约束：(budgetYear, deptId, categoryId)。
-- category_id 可为 NULL 表示「不限类别」，而 PostgreSQL 唯一索引中 NULL<>NULL，
-- 会导致可以插入多条 category_id IS NULL 的重复记录 —— 故用 COALESCE 表达式索引。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_budget_dim
    ON lis_reagent_budget (budget_year, dept_id, COALESCE(category_id, -1));
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_dept ON lis_reagent_budget (dept_id, budget_year);


-- 5. 预算变动流水（只增不改不删，支撑对账断言）
CREATE TABLE IF NOT EXISTS lis_reagent_budget_record (
    id            BIGSERIAL PRIMARY KEY,
    budget_id     BIGINT        NOT NULL,
    change_type   SMALLINT      NOT NULL,                -- 1占用 2释放 3实扣 4调增 5调减
    amount        DECIMAL(14,2) NOT NULL,                -- 变动金额（恒为正，方向由 change_type 决定）
    before_frozen DECIMAL(14,2),
    after_frozen  DECIMAL(14,2),
    before_used   DECIMAL(14,2),
    after_used    DECIMAL(14,2),
    source_type   VARCHAR(20),                           -- APPLY / ORDER / INBOUND / MANUAL
    source_id     BIGINT,                                -- 来源单据ID
    source_no     VARCHAR(32),                           -- 来源单号
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_budget_record IS '试剂预算变动流水（只增不改不删）';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_record_budget ON lis_reagent_budget_record (budget_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_record_source ON lis_reagent_budget_record (source_type, source_id);

-- 【幂等保护】同一单据对同一预算的同一种变动只能发生一次。
-- 重复提交（如重复点击验收）会撞唯一约束导致事务回滚，天然幂等，无需在代码里判重。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_budget_record_src
    ON lis_reagent_budget_record (budget_id, change_type, source_type, source_id);


-- =============================================================================
-- 三、采购域（5 张）
-- =============================================================================

-- 6. 审批节点定义（配置表 —— 主理人 Q2「节点定义写配置表不硬编码」）
--    注：PRD 5.1 的 13 张表清单遗漏了本表，实际新增表为 14 张（详见设计文档 C-6）
CREATE TABLE IF NOT EXISTS lis_reagent_approval_node (
    id                  BIGSERIAL PRIMARY KEY,
    biz_type            VARCHAR(30) NOT NULL,            -- PURCHASE 采购申请 / REQUISITION 出入库业务单
    node_level          SMALLINT    NOT NULL,            -- 节点顺序 1,2,3...
    node_name           VARCHAR(50) NOT NULL,            -- 节点名称
    trigger_cond        VARCHAR(20) NOT NULL DEFAULT 'ALWAYS', -- ALWAYS 恒触发 / OVER_BUDGET 超预算触发 / HAZARD 危险品触发
    approver_role_code  VARCHAR(50),                     -- 审批人角色编码（指向 sys_role.role_code，主理人 U-4：复用现有角色体系）
    approver_dept_scope SMALLINT DEFAULT 1,              -- 1本科室 2全院
    status              SMALLINT DEFAULT 1,              -- 0停用 1启用（同时充当「是否需要审批」的开关，替代 sys_config）
    remark              TEXT,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_approval_node IS '试剂审批节点定义（配置表，本轮不做配置界面）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_approval_node
    ON lis_reagent_approval_node (biz_type, node_level, trigger_cond);

-- 6.1 初始化节点定义（幂等）
INSERT INTO lis_reagent_approval_node (biz_type, node_level, node_name, trigger_cond, approver_role_code, status, remark)
SELECT v.biz_type, v.node_level, v.node_name, v.trigger_cond, v.role_code, v.status, v.remark
FROM (VALUES
    ('PURCHASE',    1::smallint, '科室主任审批', 'ALWAYS',      'DEPT_DIRECTOR',  1::smallint, '采购申请第一级，恒触发'),
    ('PURCHASE',    2::smallint, '分管院长审批', 'OVER_BUDGET', 'VICE_PRESIDENT', 1::smallint, '仅当预算使用率达到预警阈值时追加'),
    ('REQUISITION', 1::smallint, '科室主任审批', 'ALWAYS',      'DEPT_DIRECTOR',  0::smallint, '普通试剂领用默认免审(status=0)，科室可自行启用 —— 主理人 Q6'),
    ('REQUISITION', 2::smallint, '安全员审批',   'HAZARD',      'SAFETY_OFFICER', 1::smallint, '危险品强制审批，不可跳过 —— 第二批 R-10')
) AS v(biz_type, node_level, node_name, trigger_cond, role_code, status, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM lis_reagent_approval_node n
     WHERE n.biz_type = v.biz_type AND n.node_level = v.node_level AND n.trigger_cond = v.trigger_cond
);


-- 7. 采购申请单
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_apply (
    id                 BIGSERIAL PRIMARY KEY,
    apply_no           VARCHAR(32)   NOT NULL,           -- 申请单号 PA + yyyyMMdd + 4位序列
    apply_dept_id      BIGINT        NOT NULL,           -- 申请科室（sys_department.id 快照）
    apply_dept_name    VARCHAR(100),
    apply_user_id      BIGINT,
    apply_user_name    VARCHAR(50),
    budget_id          BIGINT,                           -- 命中的预算ID（一申请一预算，见设计文档 2.2.4）
    budget_year        INT,
    category_id        BIGINT,                           -- 本申请收敛到的试剂类别
    total_amount       DECIMAL(14,2) NOT NULL DEFAULT 0, -- 明细汇总金额
    status             SMALLINT DEFAULT 0,               -- 0草稿 1待审批 2审批中 3已批准 4已驳回 5已冻结 6已作废
    current_node_level SMALLINT DEFAULT 0,               -- 当前审批到第几级，0=未进入审批
    total_node_count   SMALLINT DEFAULT 0,               -- 本次解析出的审批节点总数
    over_budget_flag   SMALLINT DEFAULT 0,               -- 0正常 1超预算（冻结原因）
    excess_amount      DECIMAL(14,2),                    -- 超额金额（供页面明示，R-03 验收 7）
    urgent_flag        SMALLINT DEFAULT 0,               -- 0普通 1紧急
    expect_date        DATE,                             -- 期望到货日期
    submit_time        TIMESTAMP,
    finish_time        TIMESTAMP,
    remark             TEXT,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_apply IS '试剂采购申请单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_purchase_apply_no ON lis_reagent_purchase_apply (apply_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_dept   ON lis_reagent_purchase_apply (apply_dept_id, status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_budget ON lis_reagent_purchase_apply (budget_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_time   ON lis_reagent_purchase_apply (create_time);


-- 8. 采购申请明细
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_apply_item (
    id              BIGSERIAL PRIMARY KEY,
    apply_id        BIGINT        NOT NULL,
    reagent_id      BIGINT        NOT NULL,
    reagent_code    VARCHAR(32),                         -- 冗余快照
    reagent_name    VARCHAR(100),                        -- 冗余快照
    specification   VARCHAR(50),
    category_id     BIGINT,                              -- 冗余，用于预算类别收敛校验
    quantity        DECIMAL(12,2) NOT NULL,
    unit            VARCHAR(20),
    estimated_price DECIMAL(12,2),                       -- 预估单价
    amount          DECIMAL(14,2),                       -- 行金额 = quantity * estimated_price
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lis_reagent_purchase_apply_item_qty CHECK (quantity > 0)
);
COMMENT ON TABLE lis_reagent_purchase_apply_item IS '试剂采购申请明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_item_apply   ON lis_reagent_purchase_apply_item (apply_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_item_reagent ON lis_reagent_purchase_apply_item (reagent_id);


-- 9. 采购审批流水（只增不改不删）
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_approval (
    id            BIGSERIAL PRIMARY KEY,
    apply_id      BIGINT   NOT NULL,
    apply_no      VARCHAR(32),
    node_level    SMALLINT NOT NULL,
    node_name     VARCHAR(50),
    action        SMALLINT NOT NULL,                     -- 1通过 2驳回
    approver_id   BIGINT,
    approver_name VARCHAR(50),
    opinion       TEXT,                                  -- 审批意见
    approve_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    round_no      SMALLINT  DEFAULT 1,                   -- 第几轮（驳回后重新提交，轮次+1）
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_approval IS '试剂采购审批流水（只增不改不删，Service 层不提供 update/delete）';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_approval_apply ON lis_reagent_purchase_approval (apply_id, round_no, node_level);


-- 10. 采购订单
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_order (
    id            BIGSERIAL PRIMARY KEY,
    order_no      VARCHAR(32)   NOT NULL,                -- 订单号 PO + yyyyMMdd + 4位序列
    apply_id      BIGINT,                                -- 来源申请单
    apply_no      VARCHAR(32),
    supplier_id   BIGINT        NOT NULL,                -- 供应商主数据ID
    supplier_name VARCHAR(200),                          -- 冗余快照（供应商改名不影响历史订单）
    dept_id       BIGINT,
    dept_name     VARCHAR(100),
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    expect_date   DATE,                                  -- 期望到货日
    order_date    DATE,                                  -- 下单日
    status        SMALLINT DEFAULT 0,                    -- 0待下单 1已下单 2部分到货 3已到货 4已取消
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_order IS '试剂采购订单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_purchase_order_no ON lis_reagent_purchase_order (order_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_apply    ON lis_reagent_purchase_order (apply_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_supplier ON lis_reagent_purchase_order (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_status   ON lis_reagent_purchase_order (status);


-- 11. 采购订单明细
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_order_item (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    apply_item_id BIGINT,                                -- 来源申请明细
    reagent_id    BIGINT        NOT NULL,
    reagent_code  VARCHAR(32),
    reagent_name  VARCHAR(100),
    specification VARCHAR(50),
    quantity      DECIMAL(12,2) NOT NULL,                -- 订购数量
    arrived_qty   DECIMAL(12,2) NOT NULL DEFAULT 0,      -- 已到货累计（由 verifyInbound() 回写）
    unit          VARCHAR(20),
    unit_price    DECIMAL(12,2),
    amount        DECIMAL(14,2),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 数据库级防超收（R-02 验收 7 的兜底）
    CONSTRAINT ck_lis_reagent_purchase_order_item_arrived CHECK (arrived_qty >= 0 AND arrived_qty <= quantity)
);
COMMENT ON TABLE lis_reagent_purchase_order_item IS '试剂采购订单明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_item_order   ON lis_reagent_purchase_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_item_reagent ON lis_reagent_purchase_order_item (reagent_id);


-- =============================================================================
-- 四、统一出入库业务单（3 张）
-- =============================================================================

-- 12. 统一出入库业务单（领用/退库/报废/调拨/借用/归还 六类合一）
CREATE TABLE IF NOT EXISTS lis_reagent_requisition (
    id                 BIGSERIAL PRIMARY KEY,
    req_no             VARCHAR(32) NOT NULL,             -- 单号 RQ + yyyyMMdd + 4位序列
    req_type           SMALLINT    NOT NULL,             -- 1领用 2退库 3报废 4调拨 5借用 6归还
    dept_id            BIGINT,                           -- 归属科室（sys_department.id 快照）
    dept_name          VARCHAR(100),
    apply_user_id      BIGINT,
    apply_user_name    VARCHAR(50),
    status             SMALLINT DEFAULT 0,               -- 0草稿 1待审 2已审 3已执行 4已驳回 5已作废
    approver_id        BIGINT,
    approver_name      VARCHAR(50),
    approve_time       TIMESTAMP,
    approve_opinion    TEXT,
    executor_id        BIGINT,
    executor_name      VARCHAR(50),
    execute_time       TIMESTAMP,
    from_warehouse_id  BIGINT,                           -- 源仓库（出库类/调拨源）
    to_warehouse_id    BIGINT,                           -- 目标仓库（入库类/调拨目标）
    hazard_flag        SMALLINT DEFAULT 0,               -- 明细中是否含危险品（决定是否追加安全员节点）
    expect_return_date DATE,                             -- 预计归还日（req_type=5 借用）
    source_req_id      BIGINT,                           -- 归还单指向被归还的借用单（req_type=6）
    reason             TEXT,                             -- 报废原因（req_type=3 必填，R-05 验收 5）
    total_amount       DECIMAL(14,2),                    -- 单据金额（成本归集用）
    remark             TEXT,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_requisition IS '试剂统一出入库业务单（领用/退库/报废/调拨/借用/归还）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_requisition_no ON lis_reagent_requisition (req_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_type_status ON lis_reagent_requisition (req_type, status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_dept        ON lis_reagent_requisition (dept_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_source      ON lis_reagent_requisition (source_req_id);


-- 13. 业务单明细
CREATE TABLE IF NOT EXISTS lis_reagent_requisition_item (
    id           BIGSERIAL PRIMARY KEY,
    req_id       BIGINT        NOT NULL,
    reagent_id   BIGINT        NOT NULL,
    reagent_code VARCHAR(32),
    reagent_name VARCHAR(100),
    stock_id     BIGINT,                                 -- 指定批次（出库类必填）
    lot_no       VARCHAR(32),
    expiry_date  DATE,
    quantity     DECIMAL(12,2) NOT NULL,                 -- 恒为正数，方向由 req_type 决定
    unit         VARCHAR(20),
    unit_price   DECIMAL(12,2),                          -- 成本单价快照
    amount       DECIMAL(14,2),
    hazard_flag  SMALLINT DEFAULT 0,
    remark       TEXT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lis_reagent_requisition_item_qty CHECK (quantity > 0)
);
COMMENT ON TABLE lis_reagent_requisition_item IS '试剂出入库业务单明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_req     ON lis_reagent_requisition_item (req_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_reagent ON lis_reagent_requisition_item (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_stock   ON lis_reagent_requisition_item (stock_id);


-- 14. 报废环保处置扩展（与报废单 1:1，不做独立流程）—— 第二批 R-10 使用，本批建表
CREATE TABLE IF NOT EXISTS lis_reagent_scrap_disposal (
    id                   BIGSERIAL PRIMARY KEY,
    req_id               BIGINT NOT NULL,                -- 关联报废单（req_type=3），1:1
    disposal_method      VARCHAR(50),                    -- 处置方式：焚烧/中和/委托回收/填埋
    disposal_org         VARCHAR(200),                   -- 处置单位
    disposal_cert        VARCHAR(100),                   -- 处置凭证号
    disposal_date        DATE,                           -- 处置日期
    hazard_flag          SMALLINT DEFAULT 0,
    hazard_class         VARCHAR(50),
    safety_approver_id   BIGINT,                         -- 安全员
    safety_approver_name VARCHAR(50),
    safety_approve_time  TIMESTAMP,
    remark               TEXT,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_scrap_disposal IS '试剂报废环保处置扩展（与报废单 1:1）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_scrap_disposal_req ON lis_reagent_scrap_disposal (req_id);


-- =============================================================================
-- 五、现有表改造（只加不删）
-- =============================================================================

-- 15. lis_reagent 扩展（7 个字段）
--     注：category_id 已由 reagent-core-tables.sql L8 添加，此处不重复
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS supplier_id      BIGINT;              -- 供应商主数据ID（旧 supplier 字符串保留冗余）
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS hazard_flag      SMALLINT DEFAULT 0;  -- 0普通 1危险品
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS hazard_class     VARCHAR(50);         -- 危险等级：易燃/易爆/剧毒/腐蚀/放射性
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS msds_summary     TEXT;                -- MSDS 要点文本（主理人 U-9：不做文件上传）
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS msds_url         VARCHAR(500);        -- MSDS 外部链接
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS storage_temp_min DECIMAL(6,2);        -- 储存温度下限(℃)，与 storage_cond 文本并存
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS storage_temp_max DECIMAL(6,2);        -- 储存温度上限(℃)

CREATE INDEX IF NOT EXISTS idx_lis_reagent_category ON lis_reagent (category_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_hazard   ON lis_reagent (hazard_flag);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_supplier ON lis_reagent (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_status   ON lis_reagent (status);           -- 缺陷 G 的 status 筛选

-- 15.1 三阈值字段统一（主理人 Q4：以 min_stock 为准）
--      幂等：只填空值，不覆盖已有 min_stock
UPDATE lis_reagent SET min_stock = safety_stock
 WHERE min_stock IS NULL AND safety_stock IS NOT NULL;
COMMENT ON COLUMN lis_reagent.safety_stock IS '【已废弃】请用 min_stock。保留仅为兼容存量数据，新代码不再写入';


-- 16. lis_reagent_stock 加仓库字段
ALTER TABLE lis_reagent_stock ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN lis_reagent_stock.warehouse_id IS '仓库ID，0=默认库房（待归集）。用哨兵值而非 NULL，见第 17 节说明';

-- 16.1 【缺陷 K 补全 —— 列约束归一，三步不可拆】
--
--  为什么第 16 节的 `ADD COLUMN IF NOT EXISTS ... NOT NULL DEFAULT 0` 不够：
--  `IF NOT EXISTS` 在列【已存在】时是【整句 no-op】——PostgreSQL 不会去补加 NOT NULL
--  或 DEFAULT。凡是列已被先前版本 / 半截执行 / legacy 库建成「可空无默认」的环境，
--  第 16 节形同虚设，warehouse_id 依旧 nullable。
--
--  后果（QA 真机实测）：第 17 节三列唯一索引建好后，插入 2 行相同 (reagent_id, lot_no)
--  且 warehouse_id = NULL 的记录，dup_rows_admitted = 2 —— 两行全部写入成功。
--  因为 SQL 语义里 NULL <> NULL，唯一索引对 NULL 值天然不去重，缺陷 K 被彻底击穿。
--
--  三步顺序不可颠倒，且【必须整体排在第 17 节建唯一索引之前】：
--    ① 先给列补上 DEFAULT，保证后续 INSERT 不显式传值时落 0 而非 NULL
--    ② 再把存量 NULL 行归一为 0（原 16.1，保留）
--    ③ 最后 SET NOT NULL，从数据库层面根除 NULL，唯一索引才真正生效
--  第 ③ 步会全表扫描校验，若仍有 NULL 会报错中断 —— 这正是我们要的强校验。
ALTER TABLE lis_reagent_stock ALTER COLUMN warehouse_id SET DEFAULT 0;
UPDATE      lis_reagent_stock SET warehouse_id = 0 WHERE warehouse_id IS NULL;
ALTER TABLE lis_reagent_stock ALTER COLUMN warehouse_id SET NOT NULL;


-- 17. 【⚠️ 破坏性变更 —— 执行前请备份 lis_reagent_stock】重建合批唯一索引（修复缺陷 K）
--
--  背景：init-schema.sql L666 已存在
--        CREATE UNIQUE INDEX uk_lis_reagent_stock_lot ON lis_reagent_stock (reagent_id, lot_no);
--  该索引在数据库层强制「同试剂同批号只能有一条库存记录」。
--  引入多仓库后，A 冰箱与 B 冰箱的同批号试剂必须是两条独立记录，
--  若不重建索引，第二次入库会抛 duplicate key 直接崩溃（详见设计文档 C-2）。
--
--  顺序不可颠倒：必须先 DROP 旧索引，再 CREATE 新索引。
DROP INDEX IF EXISTS uk_lis_reagent_stock_lot;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_lot_wh
    ON lis_reagent_stock (reagent_id, lot_no, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_warehouse ON lis_reagent_stock (warehouse_id, status);


-- 18. lis_reagent_inbound 关联订单与供应商
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS order_id    BIGINT;
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS order_no    VARCHAR(32);
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS supplier_id BIGINT;   -- 旧 supplier 字符串保留冗余
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_order    ON lis_reagent_inbound (order_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_supplier ON lis_reagent_inbound (supplier_id);


-- 19. lis_reagent_inbound_item 关联订单明细与仓库
--     【PRD 遗漏项，详见设计文档 C-4 / C-7】
--     · order_item_id：不加此字段，同一订单内同一试剂多行时 arrivedQty 无法精确回写
--     · warehouse_id ：不加此字段，采购验收入库会全部落到默认仓，R-09 对主入口失效
ALTER TABLE lis_reagent_inbound_item ADD COLUMN IF NOT EXISTS order_item_id BIGINT;
ALTER TABLE lis_reagent_inbound_item ADD COLUMN IF NOT EXISTS warehouse_id  BIGINT;
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_order_item ON lis_reagent_inbound_item (order_item_id);

-- 19.1 warehouse_id 列约束归一（与 16.1 同理，三步不可拆）
--      入库明细的 warehouse_id 决定库存落到哪个仓，若为 NULL 会在 R-09 主入口
--      产生「无归属库存」的脏数据，且与 lis_reagent_stock.warehouse_id 的
--      非空哨兵约定不一致（同一份数据两套空值语义）。统一为哨兵 0。
--      ⚠️ 对 Java 侧的硬性要求：写入前必须过 ReagentWarehouseGuard.normalize()
--         把 null 归一为 0L。MyBatis-Plus 默认 insertStrategy=NOT_NULL 会跳过
--         null 字段从而落 DEFAULT 0，但一旦显式传 null 仍会撞 NOT NULL 违例，
--         不能依赖 ORM 的默认行为兜底。
ALTER TABLE lis_reagent_inbound_item ALTER COLUMN warehouse_id SET DEFAULT 0;
UPDATE      lis_reagent_inbound_item SET warehouse_id = 0 WHERE warehouse_id IS NULL;
ALTER TABLE lis_reagent_inbound_item ALTER COLUMN warehouse_id SET NOT NULL;


-- 20. lis_reagent_stock_record 补科室归属
--     【注意】不新增 req_id。该表 init-schema.sql L682-683 已有 related_id / related_type
--     通用多态外键，复用即可（related_type 扩展枚举 REQ / CHECK / INBOUND），
--     再加 req_id 属于重复建模，违反 PRD 第 5 章第 4 条硬约束（详见设计文档 C-11）
ALTER TABLE lis_reagent_stock_record ADD COLUMN IF NOT EXISTS dept_id   BIGINT;
ALTER TABLE lis_reagent_stock_record ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_dept    ON lis_reagent_stock_record (dept_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_related ON lis_reagent_stock_record (related_type, related_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_type    ON lis_reagent_stock_record (record_type, create_time);
COMMENT ON COLUMN lis_reagent_stock_record.record_type IS
    '1入库 2出库(领用) 3报损/报废 4退库 5盘点调账 6消耗 7调拨 8借用 9归还';
COMMENT ON COLUMN lis_reagent_stock_record.related_type IS
    'RESULT 检验结果 / QC 质控 / REQ 出入库业务单 / CHECK 盘点单 / INBOUND 入库单';


-- 21. lis_reagent_stock_check 按仓库主数据盘点
--     主理人 U-10：warehouse_id 可空 = 全盘，非空 = 按仓盘
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS warehouse_id BIGINT;
COMMENT ON COLUMN lis_reagent_stock_check.warehouse_id IS '盘点仓库ID。NULL=全库盘点，非空=按仓库（含子树）盘点';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_wh ON lis_reagent_stock_check (warehouse_id);


-- =============================================================================
-- 六、存量数据迁移（幂等）
-- =============================================================================

-- 22. 供应商字符串 → 主数据草稿（R-04 验收 7）
--
--  本节修复 QA 真机实测出的 3 个缺陷，执行顺序 22.0 → 22.5 不可调整：
--    · 缺陷 1「名称清洗不足」：TRIM() 只剥 ASCII 空格，全角空格(U+3000)/不断行空格
--      (U+00A0)/Tab 的变体逃逸，「罗氏诊断」被切成 5 条独立供应商。
--    · 缺陷 2「大小写不折叠」：roche / Roche / ROCHE 被切成 3 条。
--    · 缺陷 3「supplier_name 无唯一约束」：22.4/22.5 回填时若存在重名，
--      优化器随机选一个赢家绑定，全程不报错 —— 静默错绑，最难排查。
--  三者同源，统一用规范化函数 fn_supplier_name_norm + 表达式唯一索引根治。

-- 22.0 供应商名规范化函数（唯一事实来源，22.1~22.5 与 Java 侧比对全部走它）
--
--   规则：① 所有空白类字符（含 U+00A0 不断行空格、U+3000 全角空格、Tab、换行）
--            统一折叠为【单个半角空格】；
--         ② 首尾空白剥除（BTRIM）；
--         ③ 整体转小写（LOWER）实现大小写不敏感。
--   为什么折叠为单空格而不是全部删除：全删会把「北京 生物」与「北京生物」误判为
--   同一家，属于过度合并，宁可漏合不可错合。
--   E'...' 转义串在【解析阶段】就把 \u00A0 / \u3000 变成真实字符再交给正则引擎，
--   不依赖正则引擎对 \uXXXX 的支持，也不依赖 [[:space:]] 在当前 locale 下
--   是否覆盖全角空格 —— 这两点都是跨环境不确定项，必须绕开。
--   IMMUTABLE 是建表达式索引的硬性前提，不可省。
CREATE OR REPLACE FUNCTION fn_supplier_name_norm(p_name TEXT)
RETURNS TEXT AS $$
    SELECT LOWER(BTRIM(REGEXP_REPLACE(
               COALESCE(p_name, ''),
               E'[[:space:]\u00A0\u3000]+',
               ' ',
               'g')))
$$ LANGUAGE SQL IMMUTABLE;

COMMENT ON FUNCTION fn_supplier_name_norm(TEXT) IS
    '供应商名称规范化：空白折叠为单空格 + 去首尾 + 转小写。用于唯一索引与迁移比对';


-- 22.0.1 【前置】人工重名守卫（P2-01：守卫前移 + 条件收窄）
--
--   ▍为什么必须前移
--     本守卫原先位于 22.1.4 之后（旧 22.2）。在脚本 autocommit 执行的年代，
--     22.1.1~22.1.4 的 UPDATE 改外键 / DELETE 删草稿已经【逐句提交落库】，
--     此时守卫再 RAISE EXCEPTION 只能中断后续语句，已提交的破坏性变更无法回滚，
--     数据库停在「外键已改指、草稿已删、唯一索引未建」的半迁移态，
--     且脚本此后每次重跑都在同一处失败，人工修复成本极高。
--   ▍修复：双保险
--     ① 结构层：全脚本已用 BEGIN;/COMMIT; 包成单事务，任何异常整体回滚（根因修复）；
--     ② 时序层：把守卫提到【一切破坏性语句之前】，让不可自动处理的场景在
--        「一条破坏性语句都还没执行」时就干净退出（快速失败，语义更清晰）。
--
--   ▍为什么条件要收窄为 source_type <> 2
--     旧条件 `GROUP BY norm HAVING COUNT(*) > 1` 不区分来源，直接前移会误杀
--     「1 条人工 + 1 条自动草稿」这种【本就应当由 22.1 自动归并】的合法场景。
--     收窄后语义精确 =「人工录入之间自己重名」，这才是脚本不敢自动删、必须人工介入的情形。
--
--   ▍场景验算（QA ④ 组用例）
--     · ④a  1 条人工 +  1 条草稿（规范名相同）
--          → 人工-only 分组仅 1 条，COUNT=1，守卫【不触发】
--          → 22.1 正常归并：外键改指人工记录，草稿被删。✔ 期望行为
--     · ④d  2 条人工（规范名相同）
--          → 人工-only 分组 2 条，COUNT=2，守卫【触发】RAISE EXCEPTION
--          → 此刻 22.1.1~22.1.4 尚未执行，破坏性语句【一条都没跑】；
--            叠加单事务，连本节之前的 DDL 一并回滚，库回到执行前状态。✔ 期望行为
--     · ④其它 纯草稿重名 → 人工-only 分组 0 条，不触发，22.1 自动归并。✔
DO $$
DECLARE
    v_dup TEXT;
BEGIN
    SELECT string_agg(t.norm || '(' || t.cnt || '条)', '; ')
      INTO v_dup
      FROM (SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) AS cnt
              FROM lis_supplier
             WHERE source_type <> 2                       -- 只看人工录入，草稿交给 22.1 自动归并
             GROUP BY fn_supplier_name_norm(supplier_name)
            HAVING COUNT(*) > 1) t;
    IF v_dup IS NOT NULL THEN
        RAISE EXCEPTION
            '存在人工录入的重名供应商，需先在页面人工归并后再执行本脚本：%', v_dup
            USING HINT = '这些记录 source_type <> 2，脚本不会自动删除，避免误删人工维护的证照数据；'
                         '本次已在任何破坏性语句执行前中断，且整个脚本单事务回滚，数据库无任何变更';
    END IF;
END $$;

-- 22.1 存量 lis_supplier 规范化去重（必须早于 22.3 建唯一索引，否则索引建不出来）
--
--   背景：本脚本先前版本已在真实库执行过，已经产生了「罗氏诊断」5 条、roche 3 条
--   这类规范化后重名的记录。若直接建唯一索引会报
--   `could not create unique index ... Key ... is duplicated` 而中断部署，
--   所以必须先归并。
--
--   赢家规则（PARTITION BY 规范名，ORDER BY 决定）：
--     ① 人工维护的记录（source_type <> 2）优先于自动迁移草稿 —— 前者有真实证照数据；
--     ② 同源时取最小 id（最早创建者胜出，结果稳定可复现）。
--   输家处理：先把所有外键引用改指向赢家，再【只删除自动迁移草稿(source_type=2)】。
--   人工录入的重名记录一律不自动删除，交由 22.3 的守卫显式报错、人工归并。

-- 22.1.1 外键改指向赢家 —— lis_reagent
UPDATE lis_reagent t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.2 外键改指向赢家 —— lis_reagent_inbound
UPDATE lis_reagent_inbound t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.3 外键改指向赢家 —— lis_reagent_purchase_order（新表，通常为空；幂等保险）
UPDATE lis_reagent_purchase_order t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.4 删除已无引用的自动迁移草稿输家
DELETE FROM lis_supplier a
 USING (SELECT id, source_type, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE a.id = m.id AND m.id <> m.keep_id AND m.source_type = 2;

-- 22.2 【后置】全量断言：归并完成后，对【全部记录（不限 source_type）】再查一次规范名重复。
--
--   与 22.0.1 的分工：
--     · 22.0.1 前置守卫 —— 拦【输入不合法】（人工之间重名），条件 source_type <> 2；
--     · 22.2   后置断言 —— 防【归并逻辑自身出错】，条件不限 source_type，是兜底防御。
--   正常情况下本断言【永不触发】：22.1 的窗口函数每个规范名分组只保留一个 keep_id，
--   输家若是草稿则被 22.1.4 删除，若是人工则早已被 22.0.1 拦在门外。
--   一旦触发，说明 22.1 的赢家选取或删除条件存在 bug（例如窗口分区键与索引表达式不一致），
--   此时宁可整体回滚也绝不能让下一句 CREATE UNIQUE INDEX 抛出一句
--   看不懂的 `Key (fn_supplier_name_norm(...)) is duplicated` 而中断部署。
DO $$
DECLARE
    v_dup TEXT;
BEGIN
    SELECT string_agg(t.norm || '(' || t.cnt || '条)', '; ')
      INTO v_dup
      FROM (SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) AS cnt
              FROM lis_supplier
             GROUP BY fn_supplier_name_norm(supplier_name)
            HAVING COUNT(*) > 1) t;
    IF v_dup IS NOT NULL THEN
        RAISE EXCEPTION
            '【内部断言失败】22.1 归并后仍存在规范名重复的供应商：%', v_dup
            USING HINT = '这不是数据问题而是脚本缺陷：22.0.1 前置守卫已放行，说明重复项含自动草稿，'
                         '但 22.1 未能正确归并。请检查 22.1 窗口分区键是否与 fn_supplier_name_norm 一致。'
                         '整个脚本为单事务，本次执行已全部回滚，数据库无任何变更';
    END IF;
END $$;

-- 22.3 供应商名唯一索引（规范化后唯一）—— 根治 22.4/22.5 的「静默错绑」
--      注意索引建在函数表达式上，而 supplier_name 列本身仍存【原始串】，
--      页面展示保持原样，不会变成小写。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_supplier_name_norm
    ON lis_supplier (fn_supplier_name_norm(supplier_name));

-- 22.4 供应商字符串 → 主数据草稿
--
--   从 lis_reagent.supplier 与 lis_reagent_inbound.supplier 提取，
--   按【规范名】分组去重，生成 source_type=2 / qual_status=0（待完善）的草稿。
--   组内显示名取 MIN(BTRIM(原始串))：存原值不存小写版，且 MIN 保证结果确定可复现。
--   P4-01 —— 为什么套 BTRIM 而不是裸 MIN(u.raw)：
--     文本排序中前导空格排在可见字符之前（' 罗氏诊断' < '罗氏诊断'），裸 MIN 会稳定
--     选中【带前导空格的脏串】作为该供应商的展示名，页面上表现为名称莫名缩进、
--     搜索框精确匹配失败。BTRIM 先剥首尾空白再参与 MIN 比较与落库，
--     既不影响分组（分组键始终是 fn_supplier_name_norm，与本改动无关），
--     又保证落库的 supplier_name 首尾干净。注意仅 BTRIM 不 LOWER：
--     中间空白与大小写属于「原始书写形态」，按既定策略保留原值。
--
--   编号幂等（原缺陷：ROW_NUMBER 每次从 1 重排，不感知库内已有 SUP_MIG_xxxx）：
--   QA 实测 pass1 插入 A、业务新增 B、pass2 时 B 又拿到 SUP_MIG_0001，
--   触发 `重复键违反唯一约束 uk_lis_supplier_code`，带 ON_ERROR_STOP=1 的部署整体中断。
--   改为「库内已有最大编号 + 行号」续号：
--     · 子查询不相关，PostgreSQL 作为 InitPlan 只求值一次，所有行共享同一基数；
--     · INSERT ... SELECT 使用语句开始前的快照，读不到本次自己插入的行，故需 ROW_NUMBER 递增；
--     · 窗口函数在 WHERE 之后计算，行号只覆盖真正要插入的行，不会产生编号空洞。
--   正则守卫 ^SUP_MIG_[0-9]+$ 不可省：库中若存在人工录入的 SUP_MIG_ABC 之类非规范编码，
--   CAST(... AS INT) 会直接抛异常中断整个脚本。
INSERT INTO lis_supplier (supplier_code, supplier_name, qual_status, source_type, status, remark)
SELECT 'SUP_MIG_' || LPAD(CAST(
           (SELECT COALESCE(MAX(CAST(SUBSTRING(supplier_code FROM 9) AS INT)), 0)
              FROM lis_supplier
             WHERE supplier_code ~ '^SUP_MIG_[0-9]+$')
           + ROW_NUMBER() OVER (ORDER BY s.norm)
       AS VARCHAR), 4, '0'),
       s.name, 0, 2, 1, '由存量 supplier 字符串自动迁移生成，请补录证照信息'
FROM (
    SELECT fn_supplier_name_norm(u.raw) AS norm, MIN(BTRIM(u.raw)) AS name
    FROM (
        SELECT supplier AS raw FROM lis_reagent
         WHERE supplier IS NOT NULL AND fn_supplier_name_norm(supplier) <> ''
        UNION ALL
        SELECT supplier AS raw FROM lis_reagent_inbound
         WHERE supplier IS NOT NULL AND fn_supplier_name_norm(supplier) <> ''
    ) u
    GROUP BY fn_supplier_name_norm(u.raw)
) s
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier x
                   WHERE fn_supplier_name_norm(x.supplier_name) = s.norm);

-- 22.5 回填 lis_reagent.supplier_id（按规范名匹配，幂等）
--      22.3 的唯一索引保证至多命中一行，不再出现「优化器随机选赢家」。
UPDATE lis_reagent r SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE r.supplier_id IS NULL
   AND r.supplier IS NOT NULL
   AND fn_supplier_name_norm(r.supplier) = fn_supplier_name_norm(sp.supplier_name);

-- 22.6 回填 lis_reagent_inbound.supplier_id
UPDATE lis_reagent_inbound i SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE i.supplier_id IS NULL
   AND i.supplier IS NOT NULL
   AND fn_supplier_name_norm(i.supplier) = fn_supplier_name_norm(sp.supplier_name);


-- 23. 试剂分类字典种子数据（幂等）
INSERT INTO lis_reagent_category (category_code, category_name, parent_id, path, level_no, hazard_category, sort_order, status)
SELECT v.code, v.name, v.pid, v.path, v.lv, v.hz, v.sort, 1
FROM (VALUES
    ('CAT_CHEM',   '化学试剂',      0::bigint, '/CAT_CHEM/',   1::smallint, 0::smallint, 10),
    ('CAT_BIO',    '生物试剂',      0::bigint, '/CAT_BIO/',    1::smallint, 0::smallint, 20),
    ('CAT_HAZARD', '危险品',        0::bigint, '/CAT_HAZARD/', 1::smallint, 1::smallint, 30),
    ('CAT_CONSUM', '耗材',          0::bigint, '/CAT_CONSUM/', 1::smallint, 0::smallint, 40),
    ('CAT_QC',     '质控品/校准品', 0::bigint, '/CAT_QC/',     1::smallint, 0::smallint, 50)
) AS v(code, name, pid, path, lv, hz, sort)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_category c WHERE c.category_code = v.code);


-- 24. reagent_code 存量重复检测（主理人 U-1：索引已存在，不新建索引，仅保留检测）
--     init-schema.sql L648 已有 uk_lis_reagent_code 唯一索引，理论上不可能存在重复。
--     以下为只读检测语句，正常应返回 0 行；若返回非 0 行说明索引曾被人工删除，
--     需由试剂管理员在页面人工归并后再补建索引，不在本脚本中自动处理。
-- SELECT reagent_code, COUNT(*) AS cnt
--   FROM lis_reagent
--  GROUP BY reagent_code
-- HAVING COUNT(*) > 1;


-- =============================================================================
-- 七、执行后自检（QA 可直接跑，全部应返回 0 行 / 期望值）
-- =============================================================================

-- 自检 1：库存唯一索引已切换为三列（应返回 uk_lis_reagent_stock_lot_wh 一行，且无 uk_lis_reagent_stock_lot）
-- SELECT indexname FROM pg_indexes WHERE tablename = 'lis_reagent_stock' AND indexname LIKE 'uk_%';

-- 自检 2：无 warehouse_id 为 NULL 的库存记录（应返回 0）
-- SELECT COUNT(*) FROM lis_reagent_stock WHERE warehouse_id IS NULL;

-- 自检 3：默认库房哨兵存在（应返回 1）
-- SELECT COUNT(*) FROM lis_reagent_warehouse WHERE id = 0;

-- 自检 4：审批节点已初始化（应返回 4）
-- SELECT COUNT(*) FROM lis_reagent_approval_node;

-- 自检 5：min_stock 已从 safety_stock 迁移（应返回 0）
-- SELECT COUNT(*) FROM lis_reagent WHERE min_stock IS NULL AND safety_stock IS NOT NULL;

-- 自检 6：新增表数量（应返回 14）
-- SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN (
--   'lis_reagent_category','lis_supplier','lis_reagent_warehouse',
--   'lis_reagent_budget','lis_reagent_budget_record','lis_reagent_approval_node',
--   'lis_reagent_purchase_apply','lis_reagent_purchase_apply_item','lis_reagent_purchase_approval',
--   'lis_reagent_purchase_order','lis_reagent_purchase_order_item',
--   'lis_reagent_requisition','lis_reagent_requisition_item','lis_reagent_scrap_disposal');

-- 自检 7：全部试剂相关表均无 deleted 列（应返回 0）—— 见附录 A
-- SELECT COUNT(*) FROM information_schema.columns
--  WHERE column_name = 'deleted' AND (table_name = 'lis_supplier' OR table_name LIKE 'lis_reagent%');

-- 自检 8：CHECK 约束存在（应返回 4）
-- SELECT COUNT(*) FROM pg_constraint WHERE conname IN (
--   'ck_lis_reagent_budget_amount','ck_lis_reagent_purchase_apply_item_qty',
--   'ck_lis_reagent_purchase_order_item_arrived','ck_lis_reagent_requisition_item_qty');


-- =============================================================================
-- 附录 A：C-9「logic-delete 地雷」复核结论 —— 本脚本不加 deleted 列的依据
-- =============================================================================
--
-- 设计文档 §6 T-01/T-02、§7 第 4 条、§8 U-2 主张给全部新表与 9 张现有试剂表补
-- `deleted BIGINT NOT NULL DEFAULT 0` + 实体 `@TableLogic`。经实测复核，该主张不成立，
-- 且与设计文档自身 §1 C-9 正文（第 277/281 行）相互矛盾。本脚本采纳 §1 C-9 正文结论。
--
-- 证据：
--   1) MyBatis-Plus 3.5.5 的 logic-delete-field 是「按实体字段名匹配」生效的：
--      TableInfoHelper 在解析实体时，只有发现同名字段才会标记 logicDelete 并注入
--      `AND deleted = 0` / 改写 deleteById 为 UPDATE。实体无该字段时配置完全惰性。
--   2) grep lis-service-reagent/src/main/java 中 `deleted` / `@TableLogic` —— 零命中，
--      9 个存量实体均未声明 deleted 字段。
--   3) 全项目仅 lis-service-sample 的 Sample.java 声明了 `private Integer deleted`，
--      且 lis_sample 表确有该列（init-schema.sql L113）；日志中也仅 lis_sample 的 SQL
--      带 `deleted = 0`，其余表（含全部 reagent 表）的 SQL 均不带。
--   4) 存量代码已在调用 removeById / deleteById（ReagentController L55、
--      ReagentStockController L63、ReagentStockRecordController L58），服务长期正常运行，
--      日志中不存在任何 `column "deleted" does not exist` 报错。
--
-- 结论与决策（经主理人裁决）：
--   · 不给 9 张现有试剂表加 deleted 列（避免无谓的破坏性变更）。
--   · 14 张新表同样不加 deleted 列，与存量保持一致，避免出现「一半表有逻辑删除、
--     一半没有」的半吊子状态。
--   · 新实体禁止声明名为 deleted 的字段（否则才会真正触发 C-9 描述的运行期报错）。
--     需要逻辑删除语义时一律使用既有约定的 status 字段（0停用 1启用）。
--
-- =============================================================================
-- 脚本结束。新增表 14 张，改造现有表 7 处，重建索引 1 处，数据迁移 4 项。
-- =============================================================================


-- ⚠️ P2-01：单事务提交。与文件头部的 BEGIN; 严格配对。
-- 若前文任一语句报错且 psql 带 ON_ERROR_STOP=1，将不会执行到此处，事务由 psql 退出时回滚；
-- 若未带 ON_ERROR_STOP，事务已处于 aborted 状态，此 COMMIT 等价 ROLLBACK，同样不会留下半迁移态。
COMMIT;


-- =============================================================================
-- ⑤ 批次追溯（add-reagent-lot-trace.sql 安全版）
-- =============================================================================

-- =============================================================================
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
END $$;

-- =============================================================================
-- 脚本结束
-- =============================================================================
