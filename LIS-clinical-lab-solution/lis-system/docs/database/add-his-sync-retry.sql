-- =============================================================================
-- HIS 同步重试表
-- 当 HIS 不可用时, 将危急值/标本状态回传任务持久化到此表, 定时重试。
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_his_sync_retry (
    id              BIGSERIAL PRIMARY KEY,
    notify_type     VARCHAR(32) NOT NULL,                 -- 通知类型: critical=危急值 / sample-status=标本状态
    payload         TEXT NOT NULL,                        -- JSON 序列化的消息体
    retry_count     INTEGER DEFAULT 0,                    -- 已重试次数
    max_retry       INTEGER DEFAULT 6,                    -- 最大重试次数 (超过则放弃)
    status          SMALLINT DEFAULT 0,                   -- 0=待重试 1=成功 2=已放弃
    error_msg       TEXT,                                 -- 最后一次错误信息
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_his_sync_retry IS 'HIS 同步重试表 (危急值/标本状态回传失败重试)';
COMMENT ON COLUMN lis_his_sync_retry.notify_type IS '通知类型: critical=危急值 / sample-status=标本状态';
COMMENT ON COLUMN lis_his_sync_retry.payload IS 'JSON 序列化的消息体';
COMMENT ON COLUMN lis_his_sync_retry.retry_count IS '已重试次数';
COMMENT ON COLUMN lis_his_sync_retry.max_retry IS '最大重试次数, 超过则放弃';
COMMENT ON COLUMN lis_his_sync_retry.status IS '0=待重试 1=成功 2=已放弃';

CREATE INDEX IF NOT EXISTS idx_lis_his_sync_retry_status ON lis_his_sync_retry (status);
CREATE INDEX IF NOT EXISTS idx_lis_his_sync_retry_create_time ON lis_his_sync_retry (create_time);
