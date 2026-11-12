-- =============================================================================
-- 修复 1: 创建微生物耐药预警表 lis_micro_alert (如不存在)
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_micro_alert (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT,
    organism_id     BIGINT,
    alert_type      VARCHAR(32),
    alert_level     VARCHAR(16),
    organism_name   VARCHAR(100),
    message         TEXT,
    status          VARCHAR(16),
    handler         VARCHAR(50),
    handle_remark   VARCHAR(500),
    handle_time     TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_alert IS '微生物特殊耐药/危急值预警表';
CREATE INDEX IF NOT EXISTS idx_micro_alert_report ON lis_micro_alert (micro_report_id);
CREATE INDEX IF NOT EXISTS idx_micro_alert_status ON lis_micro_alert (status);

-- =============================================================================
-- 修复 2: 扩展仪器通讯日志表 parse_result 字段长度 (实际库中是 VARCHAR(10))
-- =============================================================================
ALTER TABLE lis_instrument_comm_log
    ALTER COLUMN parse_result TYPE TEXT;

-- 同时确保其他可能被压缩过的字段也足够长
ALTER TABLE lis_instrument_comm_log
    ALTER COLUMN direction TYPE VARCHAR(16);
ALTER TABLE lis_instrument_comm_log
    ALTER COLUMN parse_status TYPE VARCHAR(16);
ALTER TABLE lis_instrument_comm_log
    ALTER COLUMN protocol_type TYPE VARCHAR(32);
