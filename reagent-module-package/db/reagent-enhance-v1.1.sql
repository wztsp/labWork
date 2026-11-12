-- ============================================================================
-- 试剂管理增强脚本 v1.1
-- 对应《试剂管理系统》需求文档缺口补齐：
--   1) 系统参数配置        2) 试剂字典扩展（最佳库存/采购周期/条码别名/质控）
--   3) 采购订单审批与审核   4) 退供应商业务单   5) 入库方式
--   6) 盘点差异原因与审核   7) 消耗仪器关联     8) 开封/分包/危化品
--   9) 供应商发票
-- 全部语句幂等，可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 系统参数
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_sys_config (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(255),
    config_name  VARCHAR(100),
    remark       VARCHAR(255),
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_sys_config_key ON lis_sys_config (config_key);

INSERT INTO lis_sys_config (config_key, config_value, config_name, remark) VALUES
    ('reagent.purchase_order_need_approval', '0', '采购订单是否需要审批', '1=需组长/主任审批通过后才能审核；0=可直接审核'),
    ('reagent.scrap_need_approval',          '1', '报损/报废是否需要审批', '1=需审批；0=可直接审核'),
    ('reagent.high_stock_warning_enabled',   '1', '是否启用高库存预警', '1=启用；0=关闭')
ON CONFLICT (config_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. 试剂字典扩展
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS optimal_stock      DECIMAL(10,2);  -- 最佳库存
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS purchase_cycle_days INT;            -- 采购周期(天)
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS barcode_alias      VARCHAR(64);     -- 条码别名(扫码选试剂)
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS qc_flag            SMALLINT DEFAULT 0; -- 是否质控品 0否 1是
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS qc_pack_count      INT;             -- 质控品分包份数
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_barcode_alias
    ON lis_reagent (barcode_alias) WHERE barcode_alias IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 采购订单：合并来源、审批、审核
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS apply_ids        TEXT;                  -- 合并的申请ID列表(逗号分隔)
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_status  SMALLINT DEFAULT 0;   -- 0无需/待审 1通过 2驳回
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_time    TIMESTAMP;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_opinion VARCHAR(255);
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS audit_status     SMALLINT DEFAULT 0;   -- 0未审核 1已审核 2已取消审核
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS auditor_id       BIGINT;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS auditor_name     VARCHAR(50);
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS audit_time       TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 4. 业务单：退供应商出库
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_requisition ADD COLUMN IF NOT EXISTS supplier_id   BIGINT;
ALTER TABLE lis_reagent_requisition ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(100);

-- ---------------------------------------------------------------------------
-- 5. 入库单：入库方式（RULE_SCAN/SINGLE_SCAN/ALIAS_SCAN/ORDER/MANUAL/GIVEBACK）
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS inbound_mode VARCHAR(20);

-- ---------------------------------------------------------------------------
-- 6. 库存盘点：差异原因与审核
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_stock_check_item ADD COLUMN IF NOT EXISTS diff_reason VARCHAR(100);
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS audit_status  SMALLINT DEFAULT 0; -- 0未审核 1已审核
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS auditor_id    BIGINT;
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS auditor_name  VARCHAR(50);
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS audit_time    TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 7. 消耗登记：关联仪器与项目数
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_consumption ADD COLUMN IF NOT EXISTS instrument_id BIGINT;
ALTER TABLE lis_reagent_consumption ADD COLUMN IF NOT EXISTS test_count    INT;

-- ---------------------------------------------------------------------------
-- 8. 开封记录：二次开封 / 分包 / 危化品审批 / 闭封 / 耗尽
-- ---------------------------------------------------------------------------
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS opening_type    SMALLINT DEFAULT 1; -- 1首次 2二次 3分包 4危化品
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS parent_id       BIGINT;            -- 分包/二次开封来源
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS pack_no         INT;               -- 分包序号
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS pack_total      INT;               -- 分包总份数
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS demand_qty      DECIMAL(10,2);     -- 危化品需求量
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approval_status SMALLINT DEFAULT 1;-- 0待审 1通过 2驳回
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approver_id     BIGINT;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approver_name   VARCHAR(50);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approve_time    TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approve_opinion VARCHAR(255);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_time       TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_operator   VARCHAR(50);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS exhaust_time    TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_status     SMALLINT DEFAULT 0; -- 0未闭封 1已闭封

-- 状态口径：lis_reagent_opening.status —— 1正常 0过期 2已闭封 3已耗尽

-- ---------------------------------------------------------------------------
-- 9. 供应商发票
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_reagent_invoice (
    id            BIGSERIAL PRIMARY KEY,
    invoice_no    VARCHAR(64) NOT NULL,          -- 发票号
    supplier_id   BIGINT,
    supplier_name VARCHAR(100),
    invoice_date  DATE,
    amount        DECIMAL(12,2),                 -- 价税合计
    tax_amount    DECIMAL(12,2),                 -- 税额
    order_id      BIGINT,
    order_no      VARCHAR(32),
    status        SMALLINT DEFAULT 1,            -- 1已登记 0已作废
    remark        VARCHAR(255),
    operator_name VARCHAR(50),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_invoice_no ON lis_reagent_invoice (invoice_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_invoice_supplier ON lis_reagent_invoice (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_invoice_date ON lis_reagent_invoice (invoice_date);

-- ---------------------------------------------------------------------------
-- 10. 统计索引（提升台账/汇总查询）
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_lis_reagent_record_time ON lis_reagent_stock_record (create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_record_type ON lis_reagent_stock_record (record_type);
