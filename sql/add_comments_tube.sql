-- lis_test_tube 表备注 (由 postgres 属主执行)
COMMENT ON TABLE lis_test_tube IS '检验试管字典表, 定义试管类型与采血管';
COMMENT ON COLUMN lis_test_tube.id IS '主键ID';
COMMENT ON COLUMN lis_test_tube.tube_code IS '试管编码';
COMMENT ON COLUMN lis_test_tube.tube_name IS '试管名称';
COMMENT ON COLUMN lis_test_tube.tube_color IS '试管颜色';
COMMENT ON COLUMN lis_test_tube.tube_type IS '试管类型';
COMMENT ON COLUMN lis_test_tube.volume IS '采血量';
COMMENT ON COLUMN lis_test_tube.additive IS '添加剂';
COMMENT ON COLUMN lis_test_tube.sort_order IS '排序号';
COMMENT ON COLUMN lis_test_tube.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_test_tube.remark IS '备注';
COMMENT ON COLUMN lis_test_tube.create_time IS '创建时间';