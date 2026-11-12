-- =============================================================================
-- 微生物室内质控记录表
-- 支持: 生化反应试剂 / 抗菌药物 / 纸片法药敏 / 诊断血清 / 血培养瓶 / 染色液 / 标准菌株
-- 采用单表 + item_type 判别字段, 定性质控 (预期结果 vs 实际结果 -> 合格/不合格)
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_micro_qc_record (
    id              BIGSERIAL PRIMARY KEY,
    item_type       SMALLINT NOT NULL,                    -- 类别: 1生化反应试剂 2抗菌药物 3纸片法药敏 4诊断血清 5血培养瓶 6染色液 7标准菌株
    item_code       VARCHAR(64),                          -- 物品编码 (抗菌药物/标准菌株等关联字典编码)
    item_name       VARCHAR(128) NOT NULL,                -- 物品名称
    qc_lot_no       VARCHAR(64),                          -- 批号 / 质控编号
    control_strain  VARCHAR(64),                          -- 质控标准菌株 (如 ATCC25922 / 大肠埃希菌)
    expected_result VARCHAR(64),                          -- 预期结果 (如 阳性/阴性/S/I/R/生长/不生长)
    actual_result   VARCHAR(64),                          -- 实际结果
    result          VARCHAR(16) NOT NULL DEFAULT 'PASS',  -- 判定: PASS合格 FAIL不合格
    operator_id     BIGINT,                               -- 操作人ID
    operator_name   VARCHAR(64),                          -- 操作人姓名 (冗余)
    measure_time    TIMESTAMP,                            -- 质控操作时间
    remark          VARCHAR(255),                         -- 备注
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_qc_record IS '微生物室内质控记录表';
COMMENT ON COLUMN lis_micro_qc_record.item_type IS '类别: 1生化反应试剂 2抗菌药物 3纸片法药敏 4诊断血清 5血培养瓶 6染色液 7标准菌株';
COMMENT ON COLUMN lis_micro_qc_record.result IS '判定: PASS合格 FAIL不合格';
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_type ON lis_micro_qc_record (item_type);
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_time ON lis_micro_qc_record (measure_time);