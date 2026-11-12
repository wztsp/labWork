-- =============================================================================
-- 仪器台账字段补全 - 数据库变更脚本
-- 1. lis_instrument 新增: 序列号/安装日期/保修到期/使用科室/工作状态/通道类型
-- =============================================================================

ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS serial_no        VARCHAR(64);     -- 序列号
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS install_date     DATE;            -- 安装日期
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS warranty_expiry  DATE;            -- 保修到期
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS department       VARCHAR(50);     -- 使用科室
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS work_status      SMALLINT DEFAULT 1; -- 工作状态: 1正常 2维护中 3故障中
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS channel_type     VARCHAR(20);     -- 通道类型: TCP / TCP_SERIAL(双通道热备)

COMMENT ON COLUMN lis_instrument.serial_no       IS '仪器序列号';
COMMENT ON COLUMN lis_instrument.install_date    IS '安装日期';
COMMENT ON COLUMN lis_instrument.warranty_expiry IS '保修到期日期';
COMMENT ON COLUMN lis_instrument.department      IS '使用科室';
COMMENT ON COLUMN lis_instrument.work_status     IS '工作状态: 1正常 2维护中 3故障中';
COMMENT ON COLUMN lis_instrument.channel_type    IS '通道类型: TCP(仅主通道) / TCP_SERIAL(主TCP+备串口双通道热备)';

-- =============================================================================
-- 试剂批次追溯 - 数据库变更脚本
-- lis_result 新增试剂批次字段, 用于试剂批次追溯
-- =============================================================================
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;       -- 试剂批次ID
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);  -- 试剂批号

COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次ID (试剂追溯)';
COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号 (试剂追溯)';
CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot ON lis_result (reagent_lot_no);

-- =============================================================================
-- 试剂库存盘点 - 数据库变更脚本
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id              BIGSERIAL PRIMARY KEY,
    check_no        VARCHAR(32) NOT NULL,                  -- 盘点单号
    warehouse       VARCHAR(100),                          -- 仓库/位置
    check_date      DATE NOT NULL,                         -- 盘点日期
    status          SMALLINT DEFAULT 0,                    -- 0进行中 1已完成 2已作废
    operator_id     BIGINT,                                -- 盘点人ID
    operator_name   VARCHAR(50),                           -- 盘点人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check IS '试剂库存盘点单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_check_no ON lis_reagent_stock_check (check_no);

CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL,                       -- 盘点单ID
    stock_id        BIGINT,                                -- 库存批次ID
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    lot_no          VARCHAR(32),                           -- 批号
    book_qty        DECIMAL(10,2) NOT NULL,               -- 账面数量
    actual_qty      DECIMAL(10,2),                         -- 实盘数量
    diff_qty        DECIMAL(10,2),                         -- 差异 (实盘-账面)
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check_item IS '试剂库存盘点明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_check ON lis_reagent_stock_check_item (check_id);
