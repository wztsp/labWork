-- =============================================================================
-- 技师工作站检验结果列扩展字段
-- 数据库: PostgreSQL
-- =============================================================================

-- 1) 上次结果值 / 上次结果时间 / 上次结果异常标志
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_result_value VARCHAR(100);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_result_time TIMESTAMP;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_abnormal_flag VARCHAR(10);
COMMENT ON COLUMN lis_result.last_result_value IS '上次结果值 (历史记录, 患者同一指标的上一次检验结果)';
COMMENT ON COLUMN lis_result.last_result_time IS '上次结果时间';
COMMENT ON COLUMN lis_result.last_abnormal_flag IS '上次结果异常标志';

-- 2) 仪器计算相关: OD值 / CUTOFF值 / S/CO值 (常用于酶标仪/化学发光)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS od_value DECIMAL(12, 4);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS cutoff_value DECIMAL(12, 4);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS sc_ratio DECIMAL(12, 4);
COMMENT ON COLUMN lis_result.od_value IS 'OD值 (光密度, 仪器原始输出)';
COMMENT ON COLUMN lis_result.cutoff_value IS 'CUTOFF值 (仪器计算阈值)';
COMMENT ON COLUMN lis_result.sc_ratio IS 'S/CO值 (样本信号/ cutoff 比值)';

-- 3) 自动审核相关 (扩展 audit_status 语义)
-- auto_audit: 0=未做自动审核 1=通过自动审核 2=自动审核异常 (即触发规则)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS auto_audit SMALLINT DEFAULT 0;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS auto_audit_message VARCHAR(500);
COMMENT ON COLUMN lis_result.auto_audit IS '自动审核标志: 0未做 1通过 2异常';
COMMENT ON COLUMN lis_result.auto_audit_message IS '自动审核异常信息';

-- 4) 项目/指标知识备注 (技师工作站 "项目知识" 提示)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS item_knowledge VARCHAR(1000);
COMMENT ON COLUMN lis_result.item_knowledge IS '项目知识 / 临床提示 (存储为结果级别的备注, 可由自动审核/规则引擎回填)';

-- 5) 索引 (按仪器/时间筛选)
CREATE INDEX IF NOT EXISTS idx_lis_result_audit ON lis_result (auto_audit, audit_status);
