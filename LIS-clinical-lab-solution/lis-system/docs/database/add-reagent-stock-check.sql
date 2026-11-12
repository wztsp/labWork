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
