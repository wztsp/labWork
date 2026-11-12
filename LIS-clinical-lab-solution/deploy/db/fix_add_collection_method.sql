-- 添加 collection_method_id 和 collection_method_name 字段到 lis_lab_order 表
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_id BIGINT;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_name VARCHAR(100);

COMMENT ON COLUMN lis_lab_order.collection_method_id IS '采集方式ID (关联 lis_collection_method.id)';
COMMENT ON COLUMN lis_lab_order.collection_method_name IS '采集方式名称 (冗余, 便于直接展示)';
