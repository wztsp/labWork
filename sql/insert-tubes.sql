-- 1. 插入检验试管数据 (如果已存在则跳过)
INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, specimen_type, description, status, create_time)
SELECT 'EDTA', 'EDTA抗凝管', '紫色', '全血', '血常规专用抗凝管', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM lis_test_tube WHERE tube_code = 'EDTA');

INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, specimen_type, description, status, create_time)
SELECT 'RED', '红头促凝管', '红色', '血清', '生化/免疫专用促凝管', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM lis_test_tube WHERE tube_code = 'RED');

INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, specimen_type, description, status, create_time)
SELECT 'CIT', '枸橼酸钠管', '蓝色', '血浆', '凝血检测专用抗凝管', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM lis_test_tube WHERE tube_code = 'CIT');

INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, specimen_type, description, status, create_time)
SELECT 'HEP', '肝素钠管', '绿色', '血浆', '肝素抗凝管', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM lis_test_tube WHERE tube_code = 'HEP');

-- 2. 给检验项目(is_panel=1)绑定试管
-- 血常规(CBP) -> EDTA抗凝管(紫色)
UPDATE lis_test_item SET tube_id = (SELECT id FROM lis_test_tube WHERE tube_code = 'EDTA')
WHERE item_code = 'CBP' AND is_panel = 1;

-- 肝功能(LFT) -> 红头促凝管(红色)
UPDATE lis_test_item SET tube_id = (SELECT id FROM lis_test_tube WHERE tube_code = 'RED')
WHERE item_code = 'LFT' AND is_panel = 1;

-- 血糖(GLU_T) -> 红头促凝管(红色)
UPDATE lis_test_item SET tube_id = (SELECT id FROM lis_test_tube WHERE tube_code = 'RED')
WHERE item_code = 'GLU_T' AND is_panel = 1;

-- 总蛋白测定(TP_T) -> 红头促凝管(红色)
UPDATE lis_test_item SET tube_id = (SELECT id FROM lis_test_tube WHERE tube_code = 'RED')
WHERE item_code = 'TP_T' AND is_panel = 1;
