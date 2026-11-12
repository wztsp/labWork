-- ============================================================
-- 标本类型管理 + 参考范围扩展 (标本类型/仪器)
-- ============================================================

-- 1. 标本类型字典表
CREATE TABLE IF NOT EXISTS lis_specimen_type (
    id              BIGSERIAL PRIMARY KEY,
    type_code       VARCHAR(32) NOT NULL,                 -- 类型编码
    type_name       VARCHAR(100) NOT NULL,                -- 类型名称
    description     VARCHAR(200),                         -- 描述
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    sort_order      INT DEFAULT 0,                        -- 排序
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_specimen_type IS '标本类型字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_specimen_type_code ON lis_specimen_type (type_code);
CREATE INDEX IF NOT EXISTS idx_lis_specimen_type_status ON lis_specimen_type (status);

-- 2. 参考范围表扩展: 增加标本类型和仪器字段 (NULL 表示全部适用)
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS specimen_type VARCHAR(20);
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS instrument_id BIGINT;
COMMENT ON COLUMN lis_reference_range.specimen_type IS '标本类型 (NULL表示全部适用)';
COMMENT ON COLUMN lis_reference_range.instrument_id IS '仪器ID (NULL表示全部适用)';
CREATE INDEX IF NOT EXISTS idx_lis_reference_range_specimen ON lis_reference_range (specimen_type);
CREATE INDEX IF NOT EXISTS idx_lis_reference_range_instrument ON lis_reference_range (instrument_id);

-- 3. 初始化标本类型数据
INSERT INTO lis_specimen_type (type_code, type_name, description, status, sort_order) VALUES
('WHOLE_BLOOD', '全血', '全血标本', 1, 1),
('SERUM',       '血清', '血清标本', 1, 2),
('PLASMA',      '血浆', '血浆标本', 1, 3),
('URINE',       '尿液', '尿液标本', 1, 4),
('STOOL',       '粪便', '粪便标本', 1, 5),
('SPUTUM',      '痰液', '痰液标本', 1, 6),
('CSF',         '脑脊液', '脑脊液标本', 1, 7),
('PLEURAL',     '胸腹水', '胸腹水标本', 1, 8),
('SECRETION',   '分泌物', '分泌物标本', 1, 9),
('OTHER',       '其他', '其他标本类型', 1, 99)
ON CONFLICT DO NOTHING;
