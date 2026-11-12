-- ==============================================================================
-- 初始化检验项目数据（依据《检验类医疗服务价格项目立项指南（试行）》）
-- ==============================================================================
-- 说明：
-- 1. 本脚本用于为系统初始化一套标准的检验项目（含价格）字典数据。
-- 2. 项目编码 (item_code) 采用拼音首字母缩写，价格 (price) 以人民币(元)为单位。
-- 3. is_panel = 1 表示这是一个“检验项目”（组合开单项），它可以包含多个“检验指标”。
-- ==============================================================================

-- 1. 血清学检查
INSERT INTO lis_test_item (id, item_code, item_name, item_name_en, category, specimen_type, is_panel, price, status, create_time) VALUES
(9000000000000000001, 'LIV_PANEL', '肝功能全套', 'Liver Function Panel', '血清学检查', '血清', 1, 80.00, 1, now()),
(9000000000000000002, 'KID_PANEL', '肾功能全套', 'Kidney Function Panel', '血清学检查', '血清', 1, 75.00, 1, now()),
(9000000000000000003, 'LIPID_PANEL', '血脂全套', 'Lipid Panel', '血清学检查', '血清', 1, 65.00, 1, now()),
(9000000000000000004, 'GLU_T', '葡萄糖测定', 'Glucose, Total', '血清学检查', '血清', 1, 6.00, 1, now()),
(9000000000000000005, 'TP', '血清总蛋白测定', 'Total Protein', '血清学检查', '血清', 1, 10.00, 1, now()),
(9000000000000000006, 'ALB', '血清白蛋白测定', 'Albumin', '血清学检查', '血清', 1, 10.00, 1, now());

-- 2. 血液学检查
INSERT INTO lis_test_item (id, item_code, item_name, item_name_en, category, specimen_type, is_panel, price, status, create_time) VALUES
(9000000000000000010, 'CBC', '血常规', 'Complete Blood Count (CBC)', '血液学检查', 'EDTA抗凝血', 1, 25.00, 1, now()),
(9000000000000000011, 'PT', '凝血酶原时间测定', 'Prothrombin Time (PT)', '血液学检查', '枸橼酸钠抗凝血', 1, 15.00, 1, now()),
(9000000000000000012, 'APTT', '活化部分凝血活酶时间', 'Activated Partial Thromboplastin Time (APTT)', '血液学检查', '枸橼酸钠抗凝血', 1, 15.00, 1, now()),
(9000000000000000013, 'FIB', '纤维蛋白原测定', 'Fibrinogen', '血液学检查', '枸橼酸钠抗凝血', 1, 20.00, 1, now());

-- 3. 免疫学检查 (肿瘤标志物等)
INSERT INTO lis_test_item (id, item_code, item_name, item_name_en, category, specimen_type, is_panel, price, status, create_time) VALUES
(9000000000000000020, 'AFP', '甲胎蛋白(AFP)', 'Alpha-Fetoprotein (AFP)', '免疫学检查', '血清', 1, 35.00, 1, now()),
(9000000000000000021, 'CEA', '癌胚抗原(CEA)', 'Carcinoembryonic Antigen (CEA)', '免疫学检查', '血清', 1, 35.00, 1, now()),
(9000000000000000022, 'CA125', '癌抗原125(CA125)', 'Cancer Antigen 125 (CA125)', '免疫学检查', '血清', 1, 40.00, 1, now()),
(9000000000000000023, 'CA199', '癌抗原19-9(CA19-9)', 'Cancer Antigen 19-9 (CA19-9)', '免疫学检查', '血清', 1, 40.00, 1, now()),
(9000000000000000024, 'PSA', '前列腺特异性抗原(PSA)', 'Prostate-Specific Antigen (PSA)', '免疫学检查', '血清', 1, 40.00, 1, now()),
(9000000000000000025, 'HBV', '乙型肝炎五项', 'Hepatitis B Virus (HBV) Panel', '免疫学检查', '血清', 1, 50.00, 1, now());

-- 4. 尿常规
INSERT INTO lis_test_item (id, item_code, item_name, item_name_en, category, specimen_type, is_panel, price, status, create_time) VALUES
(9000000000000000030, 'URINE_ROUTINE', '尿常规', 'Urinalysis, Routine', '尿液分析', '尿液', 1, 10.00, 1, now());

-- 5. 微生物检查
INSERT INTO lis_test_item (id, item_code, item_name, item_name_en, category, specimen_type, is_panel, price, status, create_time) VALUES
(9000000000000000040, 'BLOOD_CULTURE', '血培养', 'Blood Culture', '微生物检查', '血液', 1, 150.00, 1, now()),
(9000000000000000041, 'URINE_CULTURE', '尿培养', 'Urine Culture', '微生物检查', '尿液', 1, 80.00, 1, now());

-- ==============================================================================
-- 6. 设置项目-指标关联关系 (通过 parent_id 字段)
-- ==============================================================================
-- 说明: 
-- 1. is_panel = 1 的项目是组合项目，is_panel = 0 的项目是原子指标
-- 2. 通过 parent_id 字段将原子指标关联到组合项目
-- ==============================================================================

-- LIV_PANEL (肝功能全套) -> ALT, AST, TBIL, DBIL, IBIL, ALB, GLO, A_G, ALP, GGT, TBA, CHE, LDH, TP
UPDATE lis_test_item SET parent_id = 9000000000000000001 WHERE item_code IN ('ALT', 'AST', 'TBIL', 'DBIL', 'IBIL', 'ALB', 'GLO', 'A_G', 'ALP', 'GGT', 'TBA', 'CHE', 'LDH', 'TP');

-- KID_PANEL (肾功能全套) -> BUN, CRE, UA, CYS_C, EGFR, K, NA, CL, CA, P, MG, B2_MG
UPDATE lis_test_item SET parent_id = 9000000000000000002 WHERE item_code IN ('BUN', 'CRE', 'UA', 'CYS_C', 'EGFR', 'K', 'NA', 'CL', 'CA', 'P', 'MG', 'B2_MG');

-- LIPID_PANEL (血脂全套) -> TC, TG, HDL_C, LDL_C, APO_A1, APO_B, LPA
UPDATE lis_test_item SET parent_id = 9000000000000000003 WHERE item_code IN ('TC', 'TG', 'HDL_C', 'LDL_C', 'APO_A1', 'APO_B', 'LPA');

-- CBC (血常规) -> 所有血常规指标
UPDATE lis_test_item SET parent_id = 9000000000000000010 WHERE item_code IN (
    'RBC', 'WBC', 'HGB', 'HCT', 'MCV', 'MCH', 'MCHC', 'RDW', 'PLT', 'MPV', 'PDW', 'PCT',
    'NEUT', 'NEUT_P', 'LYMPH', 'LYMPH_P', 'MONO', 'MONO_P', 'EOS', 'EOS_P', 'BASO', 'BASO_P'
);

-- GLU_T (葡萄糖测定) -> GLU, HBA1C, GA
UPDATE lis_test_item SET parent_id = 9000000000000000004 WHERE item_code IN ('GLU', 'HBA1C', 'GA');

-- HBV (乙型肝炎五项) -> HBSAG, HBSAB, HBEAG, HBEAB, HBCAB
UPDATE lis_test_item SET parent_id = 9000000000000000025 WHERE item_code IN ('HBSAG', 'HBSAB', 'HBEAG', 'HBEAB', 'HBCAB');

-- URINE_ROUTINE (尿常规) -> 尿液分析指标
UPDATE lis_test_item SET parent_id = 9000000000000000030 WHERE item_code IN (
    'U_PRO', 'U_GLU', 'U_OB', 'U_LEU', 'U_PH', 'U_SG', 'U_KET', 'U_NIT', 'U_BIL', 'U_UBG'
);
