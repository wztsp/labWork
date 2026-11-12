-- 标本采集工作站: 添加采集人字段
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS collector_id BIGINT;
COMMENT ON COLUMN lis_sample.collector_id IS '采集人ID';

-- 标本申请表 (lis_lab_order) 也添加采集人和采集时间字段 (如果存在该表)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'lis_lab_order') THEN
        ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collector_id BIGINT;
        ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collect_time TIMESTAMP;
        COMMENT ON COLUMN lis_lab_order.collector_id IS '采集人ID';
        COMMENT ON COLUMN lis_lab_order.collect_time IS '采集时间';
    END IF;
END $$;
