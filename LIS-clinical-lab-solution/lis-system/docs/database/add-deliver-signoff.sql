-- 标本送检/签收功能: 新增送检时间、送检人、签收时间、签收人字段
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_time TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_person VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sign_off_time TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sign_off_person VARCHAR(64);
-- 就诊号字段 (从 lis_visit 冗余, 便于按就诊号查询)
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS visit_no VARCHAR(64);

COMMENT ON COLUMN lis_sample.deliver_time IS '送检时间';
COMMENT ON COLUMN lis_sample.deliver_person IS '送检人';
COMMENT ON COLUMN lis_sample.sign_off_time IS '签收时间';
COMMENT ON COLUMN lis_sample.sign_off_person IS '签收人';
COMMENT ON COLUMN lis_sample.visit_no IS '就诊号';

-- 回填历史数据的 visit_no (从 lis_visit JOIN)
UPDATE lis_sample s
SET visit_no = v.visit_no
FROM lis_visit v
WHERE s.visit_id = v.id AND s.visit_no IS NULL;
