-- 技师工作站：手工标本转质控标本
-- 质控记录增加来源标本ID，用于取消质控时定位并删除该标本生成的质控记录
ALTER TABLE lis_qc_record ADD COLUMN IF NOT EXISTS sample_id BIGINT;
COMMENT ON COLUMN lis_qc_record.sample_id IS '来源标本ID (技师工作站手工标本转质控时记录, 用于取消质控时定位)';
CREATE INDEX IF NOT EXISTS idx_lis_qc_record_sample ON lis_qc_record (sample_id);