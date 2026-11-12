-- ============================================================
-- 检验试管管理 + 检验项目绑定试管 + 仪器项目映射扩展(标本类型/操作人员)
-- ============================================================

-- 1. 检验试管字典表
CREATE TABLE IF NOT EXISTS lis_test_tube (
    id              BIGSERIAL PRIMARY KEY,
    tube_code       VARCHAR(32) NOT NULL,                  -- 管编码
    tube_name       VARCHAR(100) NOT NULL,                 -- 管名称
    tube_color      VARCHAR(20),                           -- 管颜色: 红/紫/蓝/绿/黑/黄/灰
    tube_type       VARCHAR(20),                           -- 管类型: 采血管/尿杯/粪盒/培养管
    volume          VARCHAR(20),                           -- 容量
    additive        VARCHAR(100),                          -- 抗凝剂/添加剂
    sort_order      INT DEFAULT 0,                         -- 排序
    status          SMALLINT DEFAULT 1,                    -- 0停用 1启用
    remark          VARCHAR(200),                          -- 备注
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_test_tube IS '检验试管字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_test_tube_code ON lis_test_tube (tube_code);
CREATE INDEX IF NOT EXISTS idx_lis_test_tube_type ON lis_test_tube (tube_type);
CREATE INDEX IF NOT EXISTS idx_lis_test_tube_status ON lis_test_tube (status);

-- 2. 检验项目(指标/组合) 绑定试管: 增加 tube_id 字段 (NULL表示未绑定)
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS tube_id BIGINT;
COMMENT ON COLUMN lis_test_item.tube_id IS '绑定的检验试管ID (NULL表示未绑定)';
CREATE INDEX IF NOT EXISTS idx_lis_test_item_tube ON lis_test_item (tube_id);

-- 3. 仪器项目映射表扩展: 增加标本类型和操作人员字段
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS specimen_type VARCHAR(20);
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS operator_id BIGINT;
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS operator_name VARCHAR(50);
COMMENT ON COLUMN lis_instrument_item_map.specimen_type IS '适用标本类型 (NULL表示全部)';
COMMENT ON COLUMN lis_instrument_item_map.operator_id IS '操作人员ID';
COMMENT ON COLUMN lis_instrument_item_map.operator_name IS '操作人员姓名';
CREATE INDEX IF NOT EXISTS idx_lis_instrument_item_map_specimen ON lis_instrument_item_map (specimen_type);

-- 4. 初始化检验试管数据
INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, tube_type, volume, additive, sort_order, status, remark) VALUES
('RED_TUBE',   '红头管',   '红', '采血管', '5ml',  '无(促凝)',         1, 1, '生化/免疫/血清学'),
('PST_TUBE',   '黄头管',   '黄', '采血管', '5ml',  '分离胶+促凝',      2, 1, '生化/免疫'),
('EDTA_TUBE',  '紫头管',   '紫', '采血管', '2ml',  'EDTA-K2',          3, 1, '血常规/血型/糖化血红蛋白'),
('CITRATE_TUBE','蓝头管',  '蓝', '采血管', '2.7ml','枸橼酸钠(1:9)',     4, 1, '凝血功能'),
('HEPARIN_TUBE','绿头管',  '绿', '采血管', '5ml',  '肝素钠',           5, 1, '生化/血流变'),
('OXALATE_TUBE','黑头管',  '黑', '采血管', '2ml',  '草酸钾/氟化钠',     6, 1, '血沉'),
('FLUORIDE_TUBE','灰头管', '灰', '采血管', '2ml',  '草酸钾/氟化钠',     7, 1, '血糖'),
('URINE_CUP',   '尿杯',    '',  '尿杯',   '50ml', '无',               10, 1, '尿常规'),
('STOOL_BOX',   '粪盒',    '',  '粪盒',   '15ml', '无',               11, 1, '粪便常规'),
('CULTURE_TUBE','培养管',  '',  '培养管', '10ml', '无菌',             12, 1, '微生物培养')
ON CONFLICT DO NOTHING;

-- 5. 授权给应用数据库用户
GRANT SELECT, INSERT, UPDATE, DELETE ON lis_test_tube TO lis_user;
GRANT USAGE, SELECT ON SEQUENCE lis_test_tube_id_seq TO lis_user;
