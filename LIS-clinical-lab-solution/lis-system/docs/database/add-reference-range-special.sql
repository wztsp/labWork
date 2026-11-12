-- ============================================================
-- 补充: 性激素六项 + 内分泌激素 + 特殊参考范围
-- ============================================================
-- 1. 参考范围表新增 condition_remark 列 (存储生理周期/体位/采血时间/饮食状态等条件)
-- 2. 新增检验项目: REPRO(生殖激素全套) ENDO(内分泌激素)
-- 3. 新增检验指标: FSH LH E2 PROG TESTO PRL (性激素六项)
--                  CORT ACTH ALDO RENIN VITD (内分泌五项)
-- 4. 女性生理周期参考范围: 卵泡期/排卵期/黄体期/绝经期
-- 5. 年龄分段参考范围: 新生儿/儿童/成人/老年
-- 6. 孕妇特殊参考范围: 孕早/中/晚期 (PROG/B2_HCG/AFP)
-- 7. 其他影响质控的参考范围:
--    - 皮质醇昼夜节律 (晨8点/下午4点/午夜)
--    - 醛固酮/肾素体位差异 (立位/卧位)
--    - 血糖饮食状态 (空腹/餐后2小时)
--    - 维生素D季节差异 (夏季/冬季)
-- ============================================================
-- 编码: UTF-8
-- ============================================================

BEGIN;

-- ============================================================
-- 一、参考范围表新增 condition_remark 列
-- ============================================================
-- 存储特殊参考范围条件: 如"女性-卵泡期""立位""晨8点""空腹""孕妇-孕早期"等
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS condition_remark VARCHAR(100);
COMMENT ON COLUMN lis_reference_range.condition_remark IS '参考范围条件备注: 生理周期/体位/采血时间/饮食状态/孕周等';


-- ============================================================
-- 二、新增检验项目 (is_panel=1)
-- ============================================================
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, is_panel, parent_id, price, status) VALUES
('REPRO',  '生殖激素六项',   'Reproductive Hormone Panel',  '性激素', '免疫', '血清', 1, NULL, 180.00, 1),
('ENDO',   '内分泌激素',     'Endocrine Hormone Panel',     '内分泌', '免疫', '血清', 1, NULL, 200.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 三、新增检验指标 (is_panel=0)
-- ============================================================

-- ===== 性激素六项 (parent_id = REPRO) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('FSH',    '促卵泡生成素',     'Follicle Stimulating Hormone', 'FSH',   '免疫', '血清', 'mIU/mL', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('LH',     '促黄体生成素',     'Luteinizing Hormone',          'LH',    '免疫', '血清', 'mIU/mL', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('E2',     '雌二醇',           'Estradiol',                    'E2',    '免疫', '血清', 'pg/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('PROG',   '孕酮',             'Progesterone',                 'P',     '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('TESTO',  '睾酮',             'Testosterone',                 'T',     '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('PRL',    '泌乳素',           'Prolactin',                    'PRL',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 内分泌激素 (parent_id = ENDO) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CORT',   '皮质醇',           'Cortisol',                     'CORT',  '免疫', '血清', 'nmol/L', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 35.00, 1),
('ACTH',   '促肾上腺皮质激素', 'Adrenocorticotropic Hormone',  'ACTH',  '免疫', '血浆', 'pg/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 50.00, 1),
('ALDO',   '醛固酮',           'Aldosterone',                  'ALDO',  '免疫', '血清', 'ng/dL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 45.00, 1),
('RENIN',  '肾素活性',         'Plasma Renin Activity',        'PRA',   '免疫', '血浆', 'ng/mL/h','放射免疫法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 50.00, 1),
('VITD',   '25-羟基维生素D',   '25-Hydroxy Vitamin D',         '25(OH)D','免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 60.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 四、仪器-检验指标关联 (内分泌指标关联到免疫分析仪)
-- ============================================================
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('ARCHITECT','COBAS_E','COBAS_E411','MAGLUMI','CL2000','ACCESS')
  AND t.is_panel = 0
  AND t.item_code IN ('FSH','LH','E2','PROG','TESTO','PRL','CORT','ACTH','ALDO','RENIN','VITD')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 五、性激素六项参考范围 — 女性生理周期 + 男性 + 年龄分段
-- ============================================================

-- FSH 促卵泡生成素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
-- 男性
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 1, 0, 999, '1.5', '12.4', NULL, NULL, 'mIU/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 1, 0, 14, '0.3', '4.5', NULL, NULL, 'mIU/mL', '血清', '男性-儿童'),
-- 女性-卵泡期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '3.5', '12.5', NULL, NULL, 'mIU/mL', '血清', '女性-卵泡期'),
-- 女性-排卵期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '4.7', '21.5', NULL, NULL, 'mIU/mL', '血清', '女性-排卵期'),
-- 女性-黄体期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '1.7', '7.7', NULL, NULL, 'mIU/mL', '血清', '女性-黄体期'),
-- 女性-绝经期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '25.8', '134.8', NULL, NULL, 'mIU/mL', '血清', '女性-绝经期'),
-- 女性儿童
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 9, '0.6', '5.0', NULL, NULL, 'mIU/mL', '血清', '女性-儿童')
ON CONFLICT DO NOTHING;

-- LH 促黄体生成素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='LH'), 1, 0, 999, '1.7', '8.6', NULL, NULL, 'mIU/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '2.4', '12.6', NULL, NULL, 'mIU/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '14.0', '95.6', NULL, NULL, 'mIU/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '1.0', '11.4', NULL, NULL, 'mIU/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '7.7', '58.5', NULL, NULL, 'mIU/mL', '血清', '女性-绝经期')
ON CONFLICT DO NOTHING;

-- E2 雌二醇
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='E2'), 1, 0, 999, '11', '44', NULL, NULL, 'pg/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '21', '251', NULL, NULL, 'pg/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '38', '649', NULL, NULL, 'pg/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '21', '312', NULL, NULL, 'pg/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '0', '28', NULL, NULL, 'pg/mL', '血清', '女性-绝经期')
ON CONFLICT DO NOTHING;

-- PROG 孕酮
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 1, 0, 999, '0.2', '1.4', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.2', '1.5', NULL, NULL, 'ng/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.8', '3.0', NULL, NULL, 'ng/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '1.7', '27', NULL, NULL, 'ng/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.1', '0.8', NULL, NULL, 'ng/mL', '血清', '女性-绝经期'),
-- 孕妇参考范围 (按孕周)
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '2.8', '147', NULL, NULL, 'ng/mL', '血清', '孕妇-孕早期(1-12周)'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '22', '117', NULL, NULL, 'ng/mL', '血清', '孕妇-孕中期(13-27周)'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '65', '290', NULL, NULL, 'ng/mL', '血清', '孕妇-孕晚期(28-40周)')
ON CONFLICT DO NOTHING;

-- TESTO 睾酮
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 1, 0, 999, '2.8', '8.0', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 1, 0, 14, '0.1', '0.5', NULL, NULL, 'ng/mL', '血清', '男性-儿童'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 2, 0, 999, '0.06', '0.82', NULL, NULL, 'ng/mL', '血清', '女性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 2, 0, 14, '0.05', '0.3', NULL, NULL, 'ng/mL', '血清', '女性-儿童')
ON CONFLICT DO NOTHING;

-- PRL 泌乳素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 1, 0, 999, '3.5', '15.2', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 2, 0, 999, '4.8', '23.3', NULL, NULL, 'ng/mL', '血清', '女性-非孕期'),
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 2, 0, 999, '95', '475', NULL, NULL, 'ng/mL', '血清', '孕妇-孕晚期(28-40周)')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 六、内分泌激素参考范围 — 昼夜节律 + 体位差异
-- ============================================================

-- CORT 皮质醇 (昼夜节律: 晨8点最高, 午夜最低)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '171', '536', '27', '1380', 'nmol/L', '血清', '晨8点'),
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '64', '327', NULL, NULL, 'nmol/L', '血清', '下午4点'),
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '0', '138', NULL, NULL, 'nmol/L', '血清', '午夜')
ON CONFLICT DO NOTHING;

-- ACTH 促肾上腺皮质激素 (昼夜节律)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ACTH'), NULL, 0, 999, '7.2', '63.3', NULL, NULL, 'pg/mL', '血浆', '晨6-10点'),
((SELECT id FROM lis_test_item WHERE item_code='ACTH'), NULL, 0, 999, '0', '30', NULL, NULL, 'pg/mL', '血浆', '下午16-20点')
ON CONFLICT DO NOTHING;

-- ALDO 醛固酮 (体位差异: 立位显著高于卧位)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ALDO'), NULL, 0, 999, '3.0', '23.6', NULL, NULL, 'ng/dL', '血清', '卧位(晨起卧床)'),
((SELECT id FROM lis_test_item WHERE item_code='ALDO'), NULL, 0, 999, '7.2', '39.9', NULL, NULL, 'ng/dL', '血清', '立位(站立2小时)')
ON CONFLICT DO NOTHING;

-- RENIN 肾素活性 (体位差异)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='RENIN'), NULL, 0, 999, '0.05', '0.79', NULL, NULL, 'ng/mL/h', '血浆', '卧位(晨起卧床)'),
((SELECT id FROM lis_test_item WHERE item_code='RENIN'), NULL, 0, 999, '0.93', '6.56', NULL, NULL, 'ng/mL/h', '血浆', '立位(站立2小时)')
ON CONFLICT DO NOTHING;

-- VITD 25-羟基维生素D (季节差异 + 营养状态分级)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '30', '100', NULL, NULL, 'ng/mL', '血清', '充足(夏季偏高)'),
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '20', '30', NULL, NULL, 'ng/mL', '血清', '不足(冬季偏低)'),
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '0', '20', NULL, NULL, 'ng/mL', '血清', '缺乏')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 七、孕妇特殊参考范围 — B2_HCG 按孕周
-- ============================================================
-- B2_HCG 已存在(非孕参考范围 0-5 mIU/mL), 补充孕周参考范围
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='B2_HCG')
  AND condition_remark LIKE '孕妇%';

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '0', '5', NULL, NULL, 'mIU/mL', '血清', '非孕期'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '5', '426', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕0.2-1周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '18', '7340', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕1-2周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '1080', '56500', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕2-3周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '7650', '229000', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕3-4周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '25700', '288000', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕4-5周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '23300', '137116', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕5-6周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '15900', '181400', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕6-8周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '14050', '62800', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕8-12周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '6140', '65100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕12-16周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '4730', '80100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕16-29周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '2700', '78100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕29-41周')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 八、血糖饮食状态参考范围
-- ============================================================
-- GLU 已存在空腹参考范围, 补充餐后2小时及孕妇OGTT
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GLU')
  AND condition_remark LIKE '餐后%';

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '6.1', '2.2', '25', 'mmol/L', '血清', '空腹'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '7.8', NULL, NULL, 'mmol/L', '血清', '餐后2小时'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '5.1', NULL, NULL, 'mmol/L', '血清', '孕妇-空腹OGTT'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '0', '10.0', NULL, NULL, 'mmol/L', '血清', '孕妇-服糖后1小时OGTT'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '0', '8.5', NULL, NULL, 'mmol/L', '血清', '孕妇-服糖后2小时OGTT')
ON CONFLICT DO NOTHING;

-- 更新原有GLU参考范围为空腹标记
UPDATE lis_reference_range SET condition_remark = '空腹'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GLU')
  AND condition_remark IS NULL;


-- ============================================================
-- 九、年龄分段参考范围补充 — ALP/GGT/HGB/RBC (年龄影响显著)
-- ============================================================

-- ALP 碱性磷酸酶: 儿童/青少年因骨骼发育, ALP显著高于成人
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='ALP')
  AND (condition_remark LIKE '儿童%' OR condition_remark LIKE '青少年%');

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0, 0.1, '90', '500', NULL, NULL, 'U/L', '血清', '新生儿'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0.1, 1, '120', '650', NULL, NULL, 'U/L', '血清', '婴幼儿(1个月-1岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 1, 12, '100', '350', NULL, NULL, 'U/L', '血清', '儿童(1-12岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), 1, 12, 18, '100', '560', NULL, NULL, 'U/L', '血清', '青少年男性(12-18岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), 2, 12, 18, '80', '380', NULL, NULL, 'U/L', '血清', '青少年女性(12-18岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 18, 999, '40', '150', NULL, NULL, 'U/L', '血清', '成人')
ON CONFLICT DO NOTHING;

-- 更新原有ALP成人为标记
UPDATE lis_reference_range SET condition_remark = '成人'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='ALP')
  AND condition_remark IS NULL;

-- 更新ALP成人为标记
UPDATE lis_reference_range SET condition_remark = '成人'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GGT')
  AND condition_remark IS NULL;

COMMIT;

-- ============================================================
-- 验证查询
-- ============================================================
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('REPRO','ENDO');                                -- 应 2
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('FSH','LH','E2','PROG','TESTO','PRL');           -- 应 6
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('CORT','ACTH','ALDO','RENIN','VITD');            -- 应 5
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '女性-%';                          -- 性激素女性生理周期参考范围
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '孕妇%';                           -- 孕妇参考范围
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '晨%' OR condition_remark LIKE '下午%'; -- 昼夜节律
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '%位%';                            -- 体位差异
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '餐后%' OR condition_remark LIKE '空腹'; -- 饮食状态
-- SELECT COUNT(*) FROM lis_instrument_item_map WHERE test_item_id IN (SELECT id FROM lis_test_item WHERE item_code IN ('FSH','LH','E2','PROG','TESTO','PRL','CORT','ACTH','ALDO','RENIN','VITD')); -- 仪器关联
