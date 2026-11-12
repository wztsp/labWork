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
