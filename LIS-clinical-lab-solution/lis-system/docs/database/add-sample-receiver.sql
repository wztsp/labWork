-- 技师工作站: 标本核收人字段 (姓名冗余 + 员工ID), 与送检人/签收人/拒收人模式一致
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver_id BIGINT;

COMMENT ON COLUMN lis_sample.receiver IS '核收人(姓名冗余)';
COMMENT ON COLUMN lis_sample.receiver_id IS '核收人ID(关联人事员工 lis_staff.id)';