-- 插入检验项目(is_panel=1)数据
-- 现有检验指标: 1=WBC 2=RBC 3=HGB 4=PLT 5=GLU 6=ALT 7=AST 8=TP

INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CBP',  '血常规',       'Complete Blood Count',     '血常规',  '血常规', '全血', NULL, NULL, 1, NULL, 55.00, 1),
('LFT',  '肝功能',       'Liver Function Test',       '肝功能',  '生化',   '血清', NULL, NULL, 1, NULL, 36.00, 1),
('GLU_T','血糖',         'Blood Glucose',             '血糖',    '生化',   '血清', NULL, NULL, 1, NULL, 12.00, 1),
('TP_T', '总蛋白测定',   'Total Protein Test',        '总蛋白',  '生化',   '血清', NULL, NULL, 1, NULL, 10.00, 1);

-- 关联检验指标到检验项目 (设置parent_id)
-- 血常规: WBC(1), RBC(2), HGB(3), PLT(4)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'CBP') WHERE id IN (1, 2, 3, 4);
-- 肝功能: ALT(6), AST(7)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'LFT') WHERE id IN (6, 7);
-- 血糖: GLU(5)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'GLU_T') WHERE id = 5;
-- 总蛋白: TP(8)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'TP_T') WHERE id = 8;
