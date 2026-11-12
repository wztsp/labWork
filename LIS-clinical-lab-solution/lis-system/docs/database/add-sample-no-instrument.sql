-- 为 lis_sample 表添加标本号和仪器ID字段
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sample_no VARCHAR(32);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS instrument_id BIGINT;

COMMENT ON COLUMN lis_sample.sample_no IS '标本号 (按仪器序号生成)';
COMMENT ON COLUMN lis_sample.instrument_id IS '核收仪器ID';

-- 按仪器查询标本号索引
CREATE INDEX IF NOT EXISTS idx_lis_sample_instrument_no ON lis_sample (instrument_id, sample_no);
