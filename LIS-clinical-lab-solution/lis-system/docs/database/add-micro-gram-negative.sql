-- 为 lis_micro_report 表添加革兰氏染色与阴性报告相关字段
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_result VARCHAR(200);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_remark VARCHAR(500);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_time TIMESTAMP;
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_operator VARCHAR(50);

ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS is_negative SMALLINT DEFAULT 0;
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_remark VARCHAR(500);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_operator VARCHAR(50);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_time TIMESTAMP;

COMMENT ON COLUMN lis_micro_report.gram_stain_result IS '革兰氏染色结果';
COMMENT ON COLUMN lis_micro_report.gram_stain_remark IS '革兰氏染色备注';
COMMENT ON COLUMN lis_micro_report.gram_stain_time IS '革兰氏染色时间';
COMMENT ON COLUMN lis_micro_report.gram_stain_operator IS '革兰氏染色操作人';

COMMENT ON COLUMN lis_micro_report.is_negative IS '是否阴性报告 0否 1是';
COMMENT ON COLUMN lis_micro_report.negative_remark IS '阴性报告备注';
COMMENT ON COLUMN lis_micro_report.negative_operator IS '阴性报告操作人';
COMMENT ON COLUMN lis_micro_report.negative_time IS '阴性报告时间';
