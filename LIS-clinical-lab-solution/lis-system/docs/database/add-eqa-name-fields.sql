-- 为 lis_eqa_record 表添加检验项目名称和仪器名称字段
-- 室间质控可能包含外部项目，直接记录名称更灵活，同时保留 ID 关联
ALTER TABLE lis_eqa_record ADD COLUMN IF NOT EXISTS test_item_name VARCHAR(100);
ALTER TABLE lis_eqa_record ADD COLUMN IF NOT EXISTS instrument_name VARCHAR(100);

COMMENT ON COLUMN lis_eqa_record.test_item_name IS '检验项目名称(室间质控允许直接记录名称)';
COMMENT ON COLUMN lis_eqa_record.instrument_name IS '仪器名称';

-- 回填已有数据：根据 test_item_id / instrument_id 关联填充名称
UPDATE lis_eqa_record r
SET test_item_name = t.item_name
FROM lis_test_item t
WHERE r.test_item_id = t.id AND r.test_item_name IS NULL;

UPDATE lis_eqa_record r
SET instrument_name = i.instrument_name
FROM lis_instrument i
WHERE r.instrument_id = i.id AND r.instrument_name IS NULL;
