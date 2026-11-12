-- =============================================================================
-- 标本图像表 (独立存储, 一个标本对应多个图像)
-- 数据库: PostgreSQL
-- 说明: 血常规/尿常规等检验项目的形态图像(细胞形态、尿沉渣等)属于整个标本,
--       而非每个检验指标。该表以标本为单位存储, 一个标本可关联多张图像。
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_sample_image (
    id             BIGSERIAL PRIMARY KEY,
    sample_id      BIGINT NOT NULL,                        -- 标本ID
    image_url      VARCHAR(1000) NOT NULL,                 -- 图像URL
    image_name     VARCHAR(200),                           -- 图像名称/说明 (如 图1-细胞形态)
    image_type     VARCHAR(50),                            -- 图像类型 (如 CELL_MORPHOLOGY/URINE_SEDIMENT)
    sort_no        SMALLINT DEFAULT 0,                     -- 排序号
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_sample_image IS '标本图像表(一个标本对应多个图像, 血常规/尿常规等形态图像)';
COMMENT ON COLUMN lis_sample_image.sample_id IS '标本ID (关联 lis_sample.id)';
COMMENT ON COLUMN lis_sample_image.image_url IS '图像URL';
COMMENT ON COLUMN lis_sample_image.image_name IS '图像名称/说明';
COMMENT ON COLUMN lis_sample_image.image_type IS '图像类型';
COMMENT ON COLUMN lis_sample_image.sort_no IS '排序号';
CREATE INDEX IF NOT EXISTS idx_lis_sample_image_sample ON lis_sample_image (sample_id);