-- =============================================================================
-- 质控规则配置 & 质控预警表
-- =============================================================================

-- Westgard 规则配置表
CREATE TABLE IF NOT EXISTS lis_qc_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(20) NOT NULL UNIQUE,          -- 规则编码: 1-2s/1-3s/2-2s/R-4s/4-1s/10x
    rule_name       VARCHAR(50) NOT NULL,                 -- 规则名称
    rule_type       VARCHAR(20) DEFAULT 'REJECT',         -- WARNING/REJECT
    enabled         SMALLINT DEFAULT 1,                   -- 0禁用 1启用
    description     VARCHAR(200),                         -- 说明
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_rule IS 'Westgard 规则配置表';

INSERT INTO lis_qc_rule (rule_code, rule_name, rule_type, enabled, description) VALUES
('1-2s','1-2s警告规则','WARNING',1,'1个质控点超出均值±2SD (警告, 不拒绝)'),
('1-3s','1-3s失控规则','REJECT',1,'1个质控点超出均值±3SD (失控)'),
('2-2s','2-2s失控规则','REJECT',1,'连续2个质控点同侧超出均值±2SD (失控)'),
('R-4s','R-4s失控规则','REJECT',1,'连续2个质控点极差超过4SD (失控)'),
('4-1s','4-1s警告规则','WARNING',1,'连续4个质控点同侧超出均值±1SD (警告)'),
('10x','10x警告规则','WARNING',1,'连续10个质控点在均值同侧 (警告)')
ON CONFLICT (rule_code) DO NOTHING;

-- 质控偏差趋势预警表
CREATE TABLE IF NOT EXISTS lis_qc_alert (
    id              BIGSERIAL PRIMARY KEY,
    alert_type      VARCHAR(30),                         -- SAME_SIDE_BIAS/TREND/TREND_AND_SAME_SIDE
    lot_id          BIGINT,                               -- 质控品批号ID
    test_item_id    BIGINT,                               -- 检验项目ID
    instrument_id   BIGINT,                              -- 仪器ID
    last_measure_value DECIMAL(10,4),                     -- 最近测量值
    last_z_score    DECIMAL(5,2),                         -- 最近Z分数
    message         VARCHAR(255),                         -- 预警描述
    status          VARCHAR(20) DEFAULT 'PENDING',        -- PENDING待处理 / READ已读
    alert_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 预警时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_alert IS '质控偏差趋势预警表';
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_time ON lis_qc_alert (alert_time);
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_status ON lis_qc_alert (status);
