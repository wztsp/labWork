-- 危急值处理记录表: 记录危急值通知人/通知时间/发送人/发送时间/备注及通知状态
-- 一个结果(result)对应一条处理记录, 通知->取消->再通知 均更新同一记录
CREATE TABLE IF NOT EXISTS lis_critical_record (
    id              BIGSERIAL PRIMARY KEY,
    result_id       BIGINT NOT NULL,                        -- 检验结果ID (lis_result.id)
    sample_id       BIGINT,                                 -- 标本ID
    test_item_id    BIGINT,                                 -- 检验指标ID
    patient_name    VARCHAR(60),                            -- 患者姓名(冗余)
    test_item_name  VARCHAR(100),                           -- 检验项目名称(冗余)
    result_value    VARCHAR(100),                           -- 结果值
    unit            VARCHAR(30),                            -- 单位
    critical_low    VARCHAR(30),                            -- 危急值下限
    critical_high   VARCHAR(30),                            -- 危急值上限
    notifier        VARCHAR(60),                            -- 危急值通知人
    notify_time     TIMESTAMP,                              -- 通知时间
    sender          VARCHAR(60),                            -- 发送人
    send_time       TIMESTAMP,                              -- 发送时间
    remark          TEXT,                                   -- 备注
    status          SMALLINT DEFAULT 0,                     -- 0待通知 1已通知 2已取消
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_critical_record IS '危急值处理记录表';
COMMENT ON COLUMN lis_critical_record.notifier IS '危急值通知人';
COMMENT ON COLUMN lis_critical_record.notify_time IS '通知时间';
COMMENT ON COLUMN lis_critical_record.sender IS '发送人';
COMMENT ON COLUMN lis_critical_record.send_time IS '发送时间';
COMMENT ON COLUMN lis_critical_record.remark IS '备注';
COMMENT ON COLUMN lis_critical_record.status IS '0待通知 1已通知 2已取消';

CREATE INDEX IF NOT EXISTS idx_lis_critical_record_result ON lis_critical_record (result_id);
CREATE INDEX IF NOT EXISTS idx_lis_critical_record_status ON lis_critical_record (status);