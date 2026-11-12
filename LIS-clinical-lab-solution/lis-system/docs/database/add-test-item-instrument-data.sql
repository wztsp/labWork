-- ============================================================
-- 检验项目/指标 + 仪器字典 + 仪器指标关联 + 参考范围 完整基础数据
-- ============================================================
-- 检验项目(is_panel=1): 20个面板项目
-- 检验指标(is_panel=0): 120+个检验指标
-- 仪器字典: 30台
-- 仪器指标关联: 覆盖所有仪器支持的指标
-- 参考范围: 覆盖所有检验指标
-- ============================================================
-- 编码: UTF-8
-- ============================================================

BEGIN;

-- ============================================================
-- 一、清理旧数据 (保留已有ID, 追加新数据)
-- ============================================================
DELETE FROM lis_reference_range;
DELETE FROM lis_instrument_item_map;
-- 不删除 lis_test_item 和 lis_instrument, 使用追加方式

-- ============================================================
-- 二、检验项目 (is_panel=1) — 20个检验项目面板
-- ============================================================
-- 已有: 9=血常规 10=肝功能 11=血糖 12=总蛋白测定
-- 新增从 ID=13 开始

INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, is_panel, parent_id, price, status) VALUES
-- 肝功能组 (扩展)
('LFT_FULL',  '肝功能全套',     'Liver Function Test Full',   '肝全',   '生化', '血清', 1, NULL, 80.00, 1),  -- 13
('TP_T2',      '总胆红素测定',   'Total Bilirubin Test',       'TBIL',   '生化', '血清', 1, NULL, 15.00, 1),  -- 14
-- 肾功能组
('RFT',        '肾功能',         'Renal Function Test',        '肾功',   '生化', '血清', 1, NULL, 35.00, 1),  -- 15
-- 血脂组
('LIPID',      '血脂全套',       'Lipid Profile',              '血脂',   '生化', '血清', 1, NULL, 60.00, 1),  -- 16
-- 心肌酶谱
('CK_PANEL',   '心肌酶谱',       'Cardiac Enzyme Panel',       '心肌酶', '生化', '血清', 1, NULL, 50.00, 1),  -- 17
-- 电解质
('ELECTRO',    '电解质',         'Electrolytes',               '电解质', '生化', '血清', 1, NULL, 30.00, 1),  -- 18
-- 胰腺功能
('PANC',       '胰腺功能',       'Pancreatic Function',        '胰腺',   '生化', '血清', 1, NULL, 40.00, 1),  -- 19
-- 凝血功能
('COAG',       '凝血功能',       'Coagulation Panel',          '凝血',   '凝血', '血浆', 1, NULL, 45.00, 1),  -- 20
-- 尿液分析
('UA_PANEL',   '尿液分析',       'Urinalysis Panel',           '尿常规', '尿液', '尿液', 1, NULL, 20.00, 1),  -- 21
-- 甲状腺功能
('THYROID',    '甲状腺功能',     'Thyroid Function',           '甲功',   '免疫', '血清', 1, NULL, 120.00, 1), -- 22
-- 肿瘤标志物
('TUMOR',      '肿瘤标志物',     'Tumor Markers',              '肿瘤',   '免疫', '血清', 1, NULL, 200.00, 1), -- 23
-- 糖代谢
('GLU_META',   '糖代谢',         'Glucose Metabolism',         '糖代谢', '生化', '血清', 1, NULL, 55.00, 1),  -- 24
-- 炎症标志物
('INFLAM',     '炎症标志物',     'Inflammation Markers',       '炎症',   '免疫', '血清', 1, NULL, 50.00, 1),  -- 25
-- 免疫球蛋白
('IG_PANEL',   '免疫球蛋白',     'Immunoglobulin Panel',       '免疫球蛋白', '免疫', '血清', 1, NULL, 80.00, 1), -- 26
-- 血气分析
('BLOOD_GAS',  '血气分析',       'Blood Gas Analysis',         '血气',   '生化', '全血', 1, NULL, 60.00, 1),  -- 27
-- 骨代谢
('BONE_META',  '骨代谢标志物',   'Bone Metabolism Markers',    '骨代谢', '生化', '血清', 1, NULL, 90.00, 1),  -- 28
-- 铁代谢
('IRON_META',  '铁代谢',         'Iron Metabolism',            '铁代谢', '生化', '血清', 1, NULL, 40.00, 1),  -- 29
-- 自身免疫
('AUTO_IMM',   '自身免疫抗体',   'Autoimmune Antibodies',      '自免',   '免疫', '血清', 1, NULL, 150.00, 1), -- 30
-- 肝纤维化
('LIVER_FIB',  '肝纤维化指标',   'Liver Fibrosis Markers',     '肝纤',   '免疫', '血清', 1, NULL, 100.00, 1), -- 31
-- 血红蛋白电泳
('HGB_ELEC',   '血红蛋白电泳',   'Hemoglobin Electrophoresis', '电泳',   '生化', '全血', 1, NULL, 70.00, 1)   -- 32
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 三、检验指标 (is_panel=0) — 120+个指标
-- ============================================================

-- ===== 血常规指标 (parent_id=9, 已有1-4) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HCT',     '红细胞压积',           'Hematocrit',                'HCT',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('MCV',     '平均红细胞体积',       'Mean Corpuscular Volume',   'MCV',   '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('MCH',     '平均红细胞血红蛋白量', 'Mean Corpuscular Hemoglobin','MCH',  '血常规', '全血', 'pg',    '仪器法', 0, 9, 5.00, 1),
('MCHC',    '平均红细胞血红蛋白浓度','Mean Corpuscular Hb Conc.', 'MCHC',  '血常规', '全血', 'g/L',   '仪器法', 0, 9, 5.00, 1),
('RDW',     '红细胞分布宽度',       'RBC Distribution Width',    'RDW',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('PDW',     '血小板分布宽度',       'Platelet Distribution Width','PDW',  '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('MPV',     '平均血小板体积',       'Mean Platelet Volume',      'MPV',   '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('PCT',     '血小板压积',           'Plateletcrit',              'PCT',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('NEUT',    '中性粒细胞计数',       'Neutrophil Count',          'NEUT',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('LYMPH',   '淋巴细胞计数',         'Lymphocyte Count',          'LYMPH', '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('MONO',    '单核细胞计数',         'Monocyte Count',            'MONO',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('EOS',     '嗜酸性粒细胞计数',     'Eosinophil Count',          'EOS',   '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('BASO',    '嗜碱性粒细胞计数',     'Basophil Count',            'BASO',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('NEUT_P',  '中性粒细胞百分比',     'Neutrophil Percentage',     'NEUT%', '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('LYMPH_P', '淋巴细胞百分比',       'Lymphocyte Percentage',     'LYMPH%','血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('MONO_P',  '单核细胞百分比',       'Monocyte Percentage',       'MONO%', '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肝功能指标 (parent_id=10 已有ALT/AST, parent_id=13 肝功能全套) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TBIL',    '总胆红素',             'Total Bilirubin',           'TBIL',  '生化', '血清', 'umol/L', '钒酸盐氧化法', 0, 10, 10.00, 1),
('DBIL',    '直接胆红素',           'Direct Bilirubin',          'DBIL',  '生化', '血清', 'umol/L', '钒酸盐氧化法', 0, 10, 10.00, 1),
('IBIL',    '间接胆红素',           'Indirect Bilirubin',        'IBIL',  '生化', '血清', 'umol/L', '计算法',       0, 10, 10.00, 1),
('ALP',     '碱性磷酸酶',           'Alkaline Phosphatase',      'ALP',   '生化', '血清', 'U/L',    'AMP缓冲液法',  0, 10, 10.00, 1),
('GGT',     'γ-谷氨酰转移酶',       'Gamma-Glutamyl Transferase','GGT',   '生化', '血清', 'U/L',    '酶法',         0, 10, 10.00, 1),
('ALB',     '白蛋白',               'Albumin',                   'ALB',   '生化', '血清', 'g/L',    '溴甲酚绿法',   0, 10, 10.00, 1),
('GLO',     '球蛋白',               'Globulin',                  'GLO',   '生化', '血清', 'g/L',    '计算法',       0, 10, 10.00, 1),
('A_G',     '白球比',               'Albumin/Globulin Ratio',    'A/G',   '生化', '血清', '',       '计算法',       0, 10, 5.00, 1),
('CHE',     '胆碱酯酶',             'Cholinesterase',            'CHE',   '生化', '血清', 'U/L',    '酶法',         0, 10, 15.00, 1),
('TBA',     '总胆汁酸',             'Total Bile Acid',           'TBA',   '生化', '血清', 'umol/L', '酶法',         0, 10, 15.00, 1),
('LDH',     '乳酸脱氢酶',           'Lactate Dehydrogenase',     'LDH',   '生化', '血清', 'U/L',    '酶法',         0, 10, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肾功能指标 (parent_id=15) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('BUN',     '尿素氮',               'Blood Urea Nitrogen',       'BUN',   '生化', '血清', 'mmol/L', '酶偶联法', 0, 15, 10.00, 1),
('CRE',     '肌酐',                 'Creatinine',                'CRE',   '生化', '血清', 'umol/L', '酶法',     0, 15, 10.00, 1),
('UA',      '尿酸',                 'Uric Acid',                 'UA',    '生化', '血清', 'umol/L', '酶法',     0, 15, 10.00, 1),
('CYS_C',   '胱抑素C',              'Cystatin C',                'Cys-C', '生化', '血清', 'mg/L',   '免疫比浊法',0, 15, 30.00, 1),
('B2_MG',   'β2-微球蛋白',          'Beta-2 Microglobulin',      'β2MG',  '生化', '血清', 'mg/L',   '免疫比浊法',0, 15, 25.00, 1),
('EGFR',    '估算肾小球滤过率',     'Estimated GFR',             'eGFR',  '生化', '血清', 'mL/min', '计算法',   0, 15, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 血脂指标 (parent_id=16) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TC',      '总胆固醇',             'Total Cholesterol',         'TC',    '生化', '血清', 'mmol/L', '酶法',     0, 16, 10.00, 1),
('TG',      '甘油三酯',             'Triglycerides',             'TG',    '生化', '血清', 'mmol/L', '酶法',     0, 16, 10.00, 1),
('HDL_C',   '高密度脂蛋白胆固醇',   'HDL Cholesterol',           'HDL-C', '生化', '血清', 'mmol/L', '直接法',   0, 16, 15.00, 1),
('LDL_C',   '低密度脂蛋白胆固醇',   'LDL Cholesterol',           'LDL-C', '生化', '血清', 'mmol/L', '直接法',   0, 16, 15.00, 1),
('APO_A1',  '载脂蛋白A1',           'Apolipoprotein A1',         'ApoA1', '生化', '血清', 'g/L',    '免疫比浊法',0, 16, 20.00, 1),
('APO_B',   '载脂蛋白B',            'Apolipoprotein B',          'ApoB',  '生化', '血清', 'g/L',    '免疫比浊法',0, 16, 20.00, 1),
('LPA',     '脂蛋白a',              'Lipoprotein(a)',            'Lp(a)', '生化', '血清', 'mg/L',   '免疫比浊法',0, 16, 25.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 心肌酶谱指标 (parent_id=17) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CK',      '肌酸激酶',             'Creatine Kinase',           'CK',    '生化', '血清', 'U/L',    '酶法',     0, 17, 15.00, 1),
('CK_MB',   '肌酸激酶同工酶',       'CK-MB',                     'CK-MB', '生化', '血清', 'U/L',    '免疫抑制法',0, 17, 25.00, 1),
('MB',      '肌红蛋白',             'Myoglobin',                 'Mb',    '生化', '血清', 'ng/mL',  '化学发光法',0, 17, 40.00, 1),
('CTNI',    '肌钙蛋白I',            'Cardiac Troponin I',        'cTnI',  '生化', '血清', 'ng/mL',  '化学发光法',0, 17, 50.00, 1),
('BNP',     'B型钠尿肽',            'BNP',                       'BNP',   '生化', '血清', 'pg/mL',  '化学发光法',0, 17, 60.00, 1),
('NT_PROBNP','N末端B型钠尿肽原',    'NT-proBNP',                 'NT-proBNP','生化','血清','pg/mL', '化学发光法',0, 17, 60.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 电解质指标 (parent_id=18) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('K',       '钾',                   'Potassium',                 'K',     '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('NA',      '钠',                   'Sodium',                    'Na',    '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('CL',      '氯',                   'Chloride',                  'Cl',    '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('CA',      '钙',                   'Calcium',                   'Ca',    '生化', '血清', 'mmol/L', '偶氮砷III法',    0, 18, 8.00, 1),
('P',       '磷',                   'Phosphorus',                'P',     '生化', '血清', 'mmol/L', '紫外法',         0, 18, 8.00, 1),
('MG',      '镁',                   'Magnesium',                 'Mg',    '生化', '血清', 'mmol/L', '二甲苯蓝法',     0, 18, 8.00, 1),
('FE',      '铁',                   'Iron',                      'Fe',    '生化', '血清', 'umol/L', '比色法',         0, 18, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 胰腺功能指标 (parent_id=19) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('AMY',     '淀粉酶',               'Amylase',                   'AMY',   '生化', '血清', 'U/L',    '酶法', 0, 19, 15.00, 1),
('LPS',     '脂肪酶',               'Lipase',                    'LPS',   '生化', '血清', 'U/L',    '酶法', 0, 19, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 凝血功能指标 (parent_id=20) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('PT',      '凝血酶原时间',         'Prothrombin Time',          'PT',    '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('INR',     '国际标准化比值',       'INR',                       'INR',   '凝血', '血浆', '',      '计算法', 0, 20, 5.00, 1),
('APTT',    '活化部分凝血活酶时间', 'APTT',                      'APTT',  '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('FIB',     '纤维蛋白原',           'Fibrinogen',                'FIB',   '凝血', '血浆', 'g/L',   '凝固法', 0, 20, 15.00, 1),
('TT',      '凝血酶时间',           'Thrombin Time',             'TT',    '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('D_DIMER', 'D-二聚体',             'D-Dimer',                   'D-D',   '凝血', '血浆', 'mg/L',  '免疫比浊法', 0, 20, 30.00, 1),
('FDP',     '纤维蛋白降解产物',     'Fibrin Degradation Products','FDP',  '凝血', '血浆', 'mg/L',  '免疫比浊法', 0, 20, 30.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 尿液分析指标 (parent_id=21) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('U_PRO',   '尿蛋白',               'Urine Protein',             'U-Pro', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_GLU',   '尿糖',                 'Urine Glucose',             'U-Glu', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_OB',    '尿潜血',               'Urine Occult Blood',        'U-OB',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_LEU',   '尿白细胞',             'Urine Leukocyte',           'U-LEU', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_PH',    '尿pH值',              'Urine pH',                  'U-pH',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_SG',    '尿比重',               'Urine Specific Gravity',    'U-SG',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_KET',   '尿酮体',               'Urine Ketone',              'U-KET', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_NIT',   '尿亚硝酸盐',           'Urine Nitrite',             'U-NIT', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_BIL',   '尿胆红素',             'Urine Bilirubin',           'U-BIL', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_UBG',   '尿胆原',               'Urine Urobilinogen',        'U-UBG', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 甲状腺功能指标 (parent_id=22) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TSH',     '促甲状腺激素',         'Thyroid Stimulating Hormone','TSH',   '免疫', '血清', 'uIU/mL','化学发光法', 0, 22, 30.00, 1),
('FT3',     '游离三碘甲状腺原氨酸', 'Free T3',                   'FT3',   '免疫', '血清', 'pmol/L','化学发光法', 0, 22, 35.00, 1),
('FT4',     '游离甲状腺素',         'Free T4',                   'FT4',   '免疫', '血清', 'pmol/L','化学发光法', 0, 22, 35.00, 1),
('TT3',     '总三碘甲状腺原氨酸',   'Total T3',                  'TT3',   '免疫', '血清', 'nmol/L','化学发光法', 0, 22, 30.00, 1),
('TT4',     '总甲状腺素',           'Total T4',                  'TT4',   '免疫', '血清', 'nmol/L','化学发光法', 0, 22, 30.00, 1),
('TPO_AB',  '抗甲状腺过氧化物酶抗体','Anti-TPO Antibody',        'TPOAb', '免疫', '血清', 'IU/mL', '化学发光法', 0, 22, 40.00, 1),
('TG_AB',   '抗甲状腺球蛋白抗体',   'Anti-TG Antibody',          'TGAb',  '免疫', '血清', 'IU/mL', '化学发光法', 0, 22, 40.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肿瘤标志物指标 (parent_id=23) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('AFP',     '甲胎蛋白',             'Alpha-Fetoprotein',         'AFP',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('CEA',     '癌胚抗原',             'Carcinoembryonic Antigen',  'CEA',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('CA125',   '糖类抗原125',          'CA125',                     'CA125', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA153',   '糖类抗原15-3',         'CA15-3',                    'CA153', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA199',   '糖类抗原19-9',         'CA19-9',                    'CA199', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA724',   '糖类抗原72-4',         'CA72-4',                    'CA724', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('PSA',     '前列腺特异性抗原',     'PSA',                       'PSA',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('FPSA',    '游离前列腺特异性抗原', 'Free PSA',                  'fPSA',  '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('NSE',     '神经元特异性烯醇化酶', 'NSE',                       'NSE',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 50.00, 1),
('CYFRA211','细胞角蛋白19片段',     'CYFRA21-1',                 'CYFRA', '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 50.00, 1),
('SCC',     '鳞状细胞癌抗原',       'SCC Antigen',               'SCC',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 45.00, 1),
('B2_HCG',  '人绒毛膜促性腺激素',   'Beta-hCG',                  'β-HCG', '免疫', '血清', 'mIU/mL', '化学发光法', 0, 23, 40.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 糖代谢指标 (parent_id=24, 已有GLU parent_id=11) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HBA1C',   '糖化血红蛋白',         'HbA1c',                     'HbA1c', '生化', '全血', '%',     'HPLC法', 0, 24, 40.00, 1),
('GA',      '糖化白蛋白',           'Glycated Albumin',          'GA',    '生化', '血清', '%',     '酶法',   0, 24, 35.00, 1),
('INS',     '胰岛素',               'Insulin',                   'INS',   '免疫', '血清', 'uIU/mL','化学发光法', 0, 24, 35.00, 1),
('CPEP',    'C肽',                  'C-Peptide',                 'C-P',   '免疫', '血清', 'ng/mL', '化学发光法', 0, 24, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 炎症标志物指标 (parent_id=25) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CRP',     'C反应蛋白',            'C-Reactive Protein',        'CRP',   '免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 15.00, 1),
('HSCRP',   '超敏C反应蛋白',        'High-Sensitivity CRP',      'hs-CRP','免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 20.00, 1),
('PCT_INF', '降钙素原',             'Procalcitonin',             'PCT',   '免疫', '血清', 'ng/mL', '化学发光法', 0, 25, 40.00, 1),
('SAA',     '血清淀粉样蛋白A',      'Serum Amyloid A',           'SAA',   '免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 25.00, 1),
('IL6',     '白细胞介素6',          'Interleukin-6',             'IL-6',  '免疫', '血清', 'pg/mL', '化学发光法', 0, 25, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 免疫球蛋白指标 (parent_id=26) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('IGG',     '免疫球蛋白G',          'IgG',                       'IgG',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGA',     '免疫球蛋白A',          'IgA',                       'IgA',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGM',     '免疫球蛋白M',          'IgM',                       'IgM',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGE',     '免疫球蛋白E',          'IgE',                       'IgE',   '免疫', '血清', 'IU/mL', '化学发光法', 0, 26, 30.00, 1),
('C3',      '补体C3',               'Complement C3',             'C3',    '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('C4',      '补体C4',               'Complement C4',             'C4',    '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 血气分析指标 (parent_id=27) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('PH',      'pH值',                 'pH',                        'pH',    '生化', '全血', '',      '电极法', 0, 27, 10.00, 1),
('PCO2',    '二氧化碳分压',         'PCO2',                      'pCO2',  '生化', '全血', 'mmHg',  '电极法', 0, 27, 10.00, 1),
('PO2',     '氧分压',               'PO2',                       'pO2',   '生化', '全血', 'mmHg',  '电极法', 0, 27, 10.00, 1),
('HCO3',    '碳酸氢根',             'HCO3',                      'HCO3-', '生化', '全血', 'mmol/L','计算法', 0, 27, 10.00, 1),
('BE',      '碱剩余',               'Base Excess',               'BE',    '生化', '全血', 'mmol/L','计算法', 0, 27, 10.00, 1),
('SO2',     '血氧饱和度',           'Oxygen Saturation',         'sO2',   '生化', '全血', '%',     '计算法', 0, 27, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 铁代谢指标 (parent_id=29) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TIBC',    '总铁结合力',           'Total Iron Binding Capacity','TIBC',  '生化', '血清', 'umol/L','比色法', 0, 29, 15.00, 1),
('UIBC',    '不饱和铁结合力',       'Unsaturated Iron Binding Capacity','UIBC','生化','血清','umol/L','比色法',0, 29, 15.00, 1),
('FERR',    '铁蛋白',               'Ferritin',                  'SF',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 29, 30.00, 1),
('TS',      '转铁蛋白饱和度',       'Transferrin Saturation',    'TS',    '生化', '血清', '%',     '计算法', 0, 29, 5.00, 1),
('TRF',     '转铁蛋白',             'Transferrin',               'TRF',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 29, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肝纤维化指标 (parent_id=31) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HA',      '透明质酸',             'Hyaluronic Acid',           'HA',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1),
('LN',      '层粘连蛋白',           'Laminin',                   'LN',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1),
('PIIINP',  'III型前胶原N端肽',     'PIIINP',                    'PIIINP','免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 40.00, 1),
('CIV',     'IV型胶原',             'Collagen IV',               'C-IV',  '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 四、仪器字典 — 30台仪器 (已有2台, 新增28台)
-- ============================================================
-- ID 1-2 已存在
-- 新增从 ID=3 开始

INSERT INTO lis_instrument (instrument_code, instrument_name, brand, model, protocol_type, connect_type, ip_address, port, status) VALUES
-- 血细胞分析仪 (ID 3-7)
('BC6800',     '迈瑞 BC-6800 血细胞分析仪',    'Mindray',  'BC-6800',    'ASTM', 'TCP', '192.168.1.11', 9100, 1),
('BC5390',     '迈瑞 BC-5390 血细胞分析仪',    'Mindray',  'BC-5390',    'ASTM', 'TCP', '192.168.1.12', 9100, 1),
('XN1000',     '希森美康 XN-1000 血细胞分析仪', 'Sysmex',   'XN-1000',    'HL7',  'TCP', '192.168.1.13', 9100, 1),
('XN2000',     '希森美康 XN-2000 血细胞分析仪', 'Sysmex',   'XN-2000',    'HL7',  'TCP', '192.168.1.14', 9100, 1),
('LH750',      '贝克曼 LH 750 血细胞分析仪',    'Beckman',  'LH 750',     'ASTM', 'TCP', '192.168.1.15', 9100, 1),
-- 生化分析仪 (ID 8-14)
('AU5800',     '贝克曼 AU5800 生化分析仪',      'Beckman',  'AU5800',     'ASTM', 'TCP', '192.168.1.21', 9100, 1),
('AU680',      '贝克曼 AU680 生化分析仪',       'Beckman',  'AU680',      'ASTM', 'TCP', '192.168.1.22', 9100, 1),
('BS2000M',    '迈瑞 BS-2000M 生化分析仪',     'Mindray',  'BS-2000M',   'ASTM', 'TCP', '192.168.1.23', 9100, 1),
('BS380',      '迈瑞 BS-380 生化分析仪',        'Mindray',  'BS-380',     'ASTM', 'TCP', '192.168.1.24', 9100, 1),
('7180',       '日立 7180 生化分析仪',          'Hitachi',  '7180',       'ASTM', 'TCP', '192.168.1.25', 9100, 1),
('7600',       '日立 7600 生化分析仪',          'Hitachi',  '7600',       'ASTM', 'TCP', '192.168.1.26', 9100, 1),
('ABBOTT_C',   '雅培 Architect c16000 生化仪',  'Abbott',   'c16000',     'HL7',  'TCP', '192.168.1.27', 9100, 1),
-- 免疫分析仪 (ID 15-20)
('ARCHITECT',  '雅培 Architect i2000 免疫分析仪','Abbott',  'i2000',      'HL7',  'TCP', '192.168.1.31', 9100, 1),
('COBAS_E',    '罗氏 cobas e601 免疫分析仪',    'Roche',    'cobas e601', 'HL7',  'TCP', '192.168.1.32', 9100, 1),
('COBAS_E411', '罗氏 cobas e411 免疫分析仪',    'Roche',    'cobas e411', 'HL7',  'TCP', '192.168.1.33', 9100, 1),
('MAGLUMI',    '新产业 Maglumi 4000 免疫分析仪','Snibe',    'Maglumi 4000','HL7', 'TCP', '192.168.1.34', 9100, 1),
('CL2000',     '安图 CL2000i 免疫分析仪',       'Autobio',  'CL2000i',    'HL7',  'TCP', '192.168.1.35', 9100, 1),
('ACCESS',     '贝克曼 Access 2 免疫分析仪',    'Beckman',  'Access 2',   'ASTM', 'TCP', '192.168.1.36', 9100, 1),
-- 凝血分析仪 (ID 21-22)
('CS5100',     '希森美康 CS-5100 凝血分析仪',   'Sysmex',   'CS-5100',    'HL7',  'TCP', '192.168.1.41', 9100, 1),
('STAGO',      '法国思塔高 STA-R 凝血分析仪',   'Stago',    'STA-R',      'ASTM', 'TCP', '192.168.1.42', 9100, 1),
-- 尿液分析仪 (ID 23-24)
('UX2000',     '希森美康 UX-2000 尿液分析仪',   'Sysmex',   'UX-2000',    'HL7',  'TCP', '192.168.1.51', 9100, 1),
('H800',       '迪瑞 H-800 尿液分析仪',         'Dirui',    'H-800',      'ASTM', 'TCP', '192.168.1.52', 9100, 1),
-- 血气分析仪 (ID 25-26)
('RAPIDPOINT', '西门子 RAPIDPoint 500 血气仪',  'Siemens',  'RAPIDPoint 500','HL7','TCP','192.168.1.61',9100, 1),
('ABL90',      '雷度 ABL90 血气分析仪',         'Radiometer','ABL90',     'ASTM', 'TCP', '192.168.1.62', 9100, 1),
-- 糖化血红蛋白分析仪 (ID 27-28)
('D100',       '伯乐 D-100 糖化血红蛋白仪',     'Bio-Rad',  'D-100',      'HL7',  'TCP', '192.168.1.71', 9100, 1),
('HLC723',     '东曹 HLC-723 G11 糖化仪',      'Tosoh',    'HLC-723 G11','HL7',  'TCP', '192.168.1.72', 9100, 1),
-- 电解质分析仪 (ID 29-30)
('CARESURE',   '康宇 Caresure 电解质分析仪',    'Konsung',  'Caresure',   'ASTM', 'TCP', '192.168.1.81', 9100, 1),
('AVL9180',    '罗氏 AVL 9180 电解质分析仪',    'Roche',    'AVL 9180',   'ASTM', 'TCP', '192.168.1.82', 9100, 1)
ON CONFLICT (instrument_code) DO NOTHING;


-- ============================================================
-- 五、仪器-检验指标关联 (lis_instrument_item_map)
-- ============================================================

-- 清理旧关联后重建全部
-- 仪器ID范围: 1-30, 检验指标ID范围: 1(已有) + 33(新增)
-- 使用子查询获取检验指标ID

-- 血细胞分析仪 (1-7) → 血常规指标 (WBC/RBC/HGB/PLT/HCT/MCV/MCH/MCHC/RDW/PDW/MPV/PCT/NEUT/LYMPH/MONO/EOS/BASO/NEUT_P/LYMPH_P/MONO_P)
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('BC7500','BC6800','BC5390','XN1000','XN2000','LH750')
  AND t.is_panel = 0 AND t.category = '血常规';

-- 生化分析仪 (2, 8-14) → 生化类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('COBAS8000','AU5800','AU680','BS2000M','BS380','7180','7600','ABBOTT_C')
  AND t.is_panel = 0 AND t.category = '生化';

-- 免疫分析仪 (15-20) → 免疫类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('ARCHITECT','COBAS_E','COBAS_E411','MAGLUMI','CL2000','ACCESS')
  AND t.is_panel = 0 AND t.category = '免疫';

-- 凝血分析仪 (21-22) → 凝血类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '血浆'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('CS5100','STAGO')
  AND t.is_panel = 0 AND t.category = '凝血';

-- 尿液分析仪 (23-24) → 尿液类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '尿液'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('UX2000','H800')
  AND t.is_panel = 0 AND t.category = '尿液';

-- 血气分析仪 (25-26) → 血气指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('RAPIDPOINT','ABL90')
  AND t.is_panel = 0 AND t.category = '生化' AND t.specimen_type = '全血';

-- 糖化血红蛋白仪 (27-28) → 糖化血红蛋白指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('D100','HLC723')
  AND t.item_code = 'HBA1C';

-- 电解质分析仪 (29-30) → 电解质指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '血清'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('CARESURE','AVL9180')
  AND t.item_code IN ('K','NA','CL','CA');


-- ============================================================
-- 六、参考范围 (lis_reference_range)
-- ============================================================
-- gender: NULL=通用 1=男 2=女
-- 为所有检验指标(is_panel=0)生成参考范围

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit, specimen_type)
SELECT t.id, NULL, 0, 999, NULL, NULL, NULL, NULL, NULL, t.unit, t.specimen_type
FROM lis_test_item t WHERE t.is_panel = 0 AND t.id > 12
ON CONFLICT DO NOTHING;

-- 逐项补充具体参考值
-- 血常规 (已有1-10, 补充新增指标)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HCT'), 1, 0, 999, '40', '50', '20', '60', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='HCT'), 2, 0, 999, '35', '45', '15', '55', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCV'), NULL, 0, 999, '80', '100', '60', '120', 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCH'), NULL, 0, 999, '27', '34', '20', '40', 'pg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCHC'), NULL, 0, 999, '320', '360', '250', '400', 'g/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='RDW'), NULL, 0, 999, '11.5', '14.5', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PDW'), NULL, 0, 999, '9', '17', NULL, NULL, 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MPV'), NULL, 0, 999, '7', '13', NULL, NULL, 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PCT'), NULL, 0, 999, '0.1', '0.3', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='NEUT'), NULL, 0, 999, '1.8', '6.3', '0.5', '15', '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='LYMPH'), NULL, 0, 999, '1.1', '3.2', '0.3', '8', '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MONO'), NULL, 0, 999, '0.1', '0.6', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='EOS'), NULL, 0, 999, '0.02', '0.52', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='BASO'), NULL, 0, 999, '0', '0.06', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='NEUT_P'), NULL, 0, 999, '40', '75', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='LYMPH_P'), NULL, 0, 999, '20', '50', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MONO_P'), NULL, 0, 999, '3', '10', NULL, NULL, '%', '全血')
ON CONFLICT DO NOTHING;

-- 肝功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TBIL'), NULL, 0, 999, '5.1', '28.0', NULL, '85', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='DBIL'), NULL, 0, 999, '0', '8.0', NULL, '50', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IBIL'), NULL, 0, 999, '0', '20.0', NULL, NULL, 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0, 999, '40', '150', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GGT'), 1, 0, 999, '10', '60', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GGT'), 2, 0, 999, '7', '45', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='ALB'), NULL, 0, 999, '40', '55', '20', NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GLO'), NULL, 0, 999, '20', '40', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='A_G'), NULL, 0, 999, '1.2', '2.0', NULL, NULL, '', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CHE'), NULL, 0, 999, '4000', '12000', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TBA'), NULL, 0, 999, '0', '10', NULL, NULL, 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LDH'), NULL, 0, 999, '120', '250', NULL, NULL, 'U/L', '血清')
ON CONFLICT DO NOTHING;

-- 肾功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='BUN'), NULL, 0, 999, '2.9', '8.2', '1.0', '25', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CRE'), 1, 0, 999, '57', '97', '30', '500', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CRE'), 2, 0, 999, '44', '80', '25', '400', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UA'), 1, 0, 999, '208', '428', '120', '700', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UA'), 2, 0, 999, '155', '357', '90', '600', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CYS_C'), NULL, 0, 999, '0.5', '1.0', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='B2_MG'), NULL, 0, 999, '0.7', '1.8', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='EGFR'), NULL, 0, 999, '90', NULL, NULL, NULL, 'mL/min', '血清')
ON CONFLICT DO NOTHING;

-- 血脂
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TC'), NULL, 0, 999, '3.1', '5.7', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TG'), NULL, 0, 999, '0.4', '1.7', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='HDL_C'), NULL, 0, 999, '0.9', '1.6', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LDL_C'), NULL, 0, 999, '1.9', '3.6', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='APO_A1'), NULL, 0, 999, '1.0', '1.6', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='APO_B'), NULL, 0, 999, '0.6', '1.1', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LPA'), NULL, 0, 999, '0', '300', NULL, NULL, 'mg/L', '血清')
ON CONFLICT DO NOTHING;

-- 心肌酶谱
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CK'), NULL, 0, 999, '25', '200', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CK_MB'), NULL, 0, 999, '0', '25', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='MB'), NULL, 0, 999, '0', '70', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CTNI'), NULL, 0, 999, '0', '0.4', NULL, '2.0', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='BNP'), NULL, 0, 999, '0', '100', NULL, '500', 'pg/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NT_PROBNP'), NULL, 0, 999, '0', '125', NULL, '450', 'pg/mL', '血清')
ON CONFLICT DO NOTHING;

-- 电解质
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='K'), NULL, 0, 999, '3.5', '5.3', '2.5', '7.0', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NA'), NULL, 0, 999, '136', '146', '120', '160', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CL'), NULL, 0, 999, '98', '107', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA'), NULL, 0, 999, '2.1', '2.6', '1.5', '3.5', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='P'), NULL, 0, 999, '0.8', '1.5', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='MG'), NULL, 0, 999, '0.7', '1.1', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FE'), NULL, 0, 999, '7', '30', NULL, NULL, 'umol/L', '血清')
ON CONFLICT DO NOTHING;

-- 胰腺功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='AMY'), NULL, 0, 999, '30', '110', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LPS'), NULL, 0, 999, '0', '60', NULL, NULL, 'U/L', '血清')
ON CONFLICT DO NOTHING;

-- 凝血功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PT'), NULL, 0, 999, '9.4', '12.5', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='INR'), NULL, 0, 999, '0.8', '1.2', NULL, NULL, '', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='APTT'), NULL, 0, 999, '25', '37', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='FIB'), NULL, 0, 999, '2', '4', '1', '8', 'g/L', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='TT'), NULL, 0, 999, '14', '21', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='D_DIMER'), NULL, 0, 999, '0', '0.5', NULL, NULL, 'mg/L', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='FDP'), NULL, 0, 999, '0', '5', NULL, NULL, 'mg/L', '血浆')
ON CONFLICT DO NOTHING;

-- 尿液分析 (文本型参考值)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='U_PRO'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_GLU'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_OB'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_LEU'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_PH'), NULL, 0, 999, '5.0', '8.0', NULL, '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_SG'), NULL, 0, 999, '1.003', '1.030', NULL, '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_KET'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_NIT'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_BIL'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_UBG'), NULL, 0, 999, NULL, NULL, '阴性或弱阳性', '', '尿液')
ON CONFLICT DO NOTHING;

-- 甲状腺功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TSH'), NULL, 0, 999, '0.4', '4.0', NULL, NULL, 'uIU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FT3'), NULL, 0, 999, '3.5', '6.5', NULL, NULL, 'pmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FT4'), NULL, 0, 999, '11.5', '22.7', NULL, NULL, 'pmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TT3'), NULL, 0, 999, '1.0', '3.0', NULL, NULL, 'nmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TT4'), NULL, 0, 999, '60', '160', NULL, NULL, 'nmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TPO_AB'), NULL, 0, 999, '0', '34', NULL, NULL, 'IU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TG_AB'), NULL, 0, 999, '0', '115', NULL, NULL, 'IU/mL', '血清')
ON CONFLICT DO NOTHING;

-- 肿瘤标志物
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='AFP'), NULL, 0, 999, '0', '7', NULL, '400', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CEA'), NULL, 0, 999, '0', '5', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA125'), NULL, 0, 999, '0', '35', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA153'), NULL, 0, 999, '0', '25', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA199'), NULL, 0, 999, '0', '37', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA724'), NULL, 0, 999, '0', '6.9', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PSA'), NULL, 0, 999, '0', '4', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FPSA'), NULL, 0, 999, '0', '1', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NSE'), NULL, 0, 999, '0', '16.3', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CYFRA211'), NULL, 0, 999, '0', '3.3', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='SCC'), NULL, 0, 999, '0', '1.5', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), NULL, 0, 999, '0', '5', NULL, NULL, 'mIU/mL', '血清')
ON CONFLICT DO NOTHING;

-- 糖代谢
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HBA1C'), NULL, 0, 999, '4.0', '6.0', '3.0', '10.0', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='GA'), NULL, 0, 999, '11', '16', NULL, NULL, '%', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='INS'), NULL, 0, 999, '2.6', '24.9', NULL, NULL, 'uIU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CPEP'), NULL, 0, 999, '1.1', '4.4', NULL, NULL, 'ng/mL', '血清')
ON CONFLICT DO NOTHING;

-- 炎症标志物
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CRP'), NULL, 0, 999, '0', '10', NULL, '100', 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='HSCRP'), NULL, 0, 999, '0', '3', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PCT_INF'), NULL, 0, 999, '0', '0.05', NULL, '10', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='SAA'), NULL, 0, 999, '0', '10', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IL6'), NULL, 0, 999, '0', '7', NULL, NULL, 'pg/mL', '血清')
ON CONFLICT DO NOTHING;

-- 免疫球蛋白
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='IGG'), NULL, 0, 999, '7', '16', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGA'), NULL, 0, 999, '0.7', '4.0', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGM'), NULL, 0, 999, '0.4', '2.3', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGE'), NULL, 0, 999, '0', '100', 'IU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='C3'), NULL, 0, 999, '0.9', '1.8', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='C4'), NULL, 0, 999, '0.1', '0.4', 'g/L', '血清')
ON CONFLICT DO NOTHING;

-- 血气分析
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PH'), NULL, 0, 999, '7.35', '7.45', '7.0', '7.6', '', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PCO2'), NULL, 0, 999, '35', '45', '20', '70', 'mmHg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PO2'), NULL, 0, 999, '80', '100', '40', NULL, 'mmHg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='HCO3'), NULL, 0, 999, '21', '28', '10', '40', 'mmol/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='BE'), NULL, 0, 999, '-3', '3', '-10', '10', 'mmol/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='SO2'), NULL, 0, 999, '95', '100', '85', NULL, '%', '全血')
ON CONFLICT DO NOTHING;

-- 铁代谢
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TIBC'), NULL, 0, 999, '50', '77', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UIBC'), NULL, 0, 999, '25', '50', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FERR'), 1, 0, 999, '30', '400', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FERR'), 2, 0, 999, '13', '150', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TS'), NULL, 0, 999, '20', '50', '%', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TRF'), NULL, 0, 999, '2.0', '3.6', 'g/L', '血清')
ON CONFLICT DO NOTHING;

-- 肝纤维化
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HA'), NULL, 0, 999, '0', '100', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LN'), NULL, 0, 999, '0', '130', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PIIINP'), NULL, 0, 999, '0', '15', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CIV'), NULL, 0, 999, '0', '95', 'ng/mL', '血清')
ON CONFLICT DO NOTHING;

-- 已有指标补充危急值 (WBC/RBC/HGB/PLT/GLU/ALT/AST/TP)
UPDATE lis_reference_range SET critical_low='1.0', critical_high='30' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='WBC') AND ref_low='3.5';
UPDATE lis_reference_range SET critical_low='2.0', critical_high='8.0' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='RBC');
UPDATE lis_reference_range SET critical_low='60', critical_high='200' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='HGB');
UPDATE lis_reference_range SET critical_low='20', critical_high='1000' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='PLT');
UPDATE lis_reference_range SET critical_low='2.2', critical_high='25' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='GLU');
UPDATE lis_reference_range SET critical_low='2.1', critical_high='3.5' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='CA');

COMMIT;

-- ============================================================
-- 验证
-- ============================================================
-- SELECT COUNT(*) FROM lis_test_item;              -- 应 132+ (12原有 + 120新增)
-- SELECT COUNT(*) FROM lis_test_item WHERE is_panel=0; -- 应 120+ 检验指标
-- SELECT COUNT(*) FROM lis_test_item WHERE is_panel=1; -- 应 20+ 检验项目
-- SELECT COUNT(*) FROM lis_instrument;             -- 应 30
-- SELECT COUNT(*) FROM lis_instrument_item_map;    -- 应 200+ 关联
-- SELECT COUNT(*) FROM lis_reference_range;        -- 应 150+ 参考范围
