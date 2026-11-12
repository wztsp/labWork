-- ============================================================
-- 技师工作站批量操作功能 - 新增字段与收藏夹表
-- 执行顺序: 在 init-schema.sql 之后执行
-- ============================================================

-- 1. lis_sample 新增让步核收/拆分/销毁/检验人相关字段
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS waive_flag       SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS waive_reason     VARCHAR(500);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS split_flag       SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS parent_id        BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_reason   VARCHAR(500);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_operator VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_time     TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS inspector        VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS inspector_user_id BIGINT;

COMMENT ON COLUMN lis_sample.waive_flag        IS '让步核收标志: 0否 1是';
COMMENT ON COLUMN lis_sample.waive_reason      IS '让步核收原因';
COMMENT ON COLUMN lis_sample.split_flag        IS '拆分标志: 0未拆分 1已拆分';
COMMENT ON COLUMN lis_sample.parent_id         IS '拆分子标本的父标本ID';
COMMENT ON COLUMN lis_sample.destroy_reason    IS '销毁原因';
COMMENT ON COLUMN lis_sample.destroy_operator  IS '销毁操作人';
COMMENT ON COLUMN lis_sample.destroy_time      IS '销毁时间';
COMMENT ON COLUMN lis_sample.inspector         IS '检验人姓名';
COMMENT ON COLUMN lis_sample.inspector_user_id IS '检验人用户ID';

CREATE INDEX IF NOT EXISTS idx_lis_sample_parent ON lis_sample (parent_id);

-- 2. 技师工作站收藏夹表
CREATE TABLE IF NOT EXISTS lis_technician_favorite (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20) NOT NULL DEFAULT 'sample',
    payload      JSON,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_technician_favorite IS '技师工作站收藏夹';
COMMENT ON COLUMN lis_technician_favorite.name  IS '收藏名称';
COMMENT ON COLUMN lis_technician_favorite.type  IS '收藏类型: sample=标本 keyword=搜索条件 panel=检验项目';
COMMENT ON COLUMN lis_technician_favorite.payload IS '收藏内容JSON';

-- 3. lis_result 表增加唯一约束 (sample_id + test_item_id) 用于 upsert
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_result_sample_item ON lis_result (sample_id, test_item_id);
