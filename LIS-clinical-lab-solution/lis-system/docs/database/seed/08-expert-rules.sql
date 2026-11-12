-- ============================================================
-- 08-expert-rules.sql   内置专家/耐药规则种子 (可配置规则引擎)
-- 与「耐药规则」共用 lis_resistance_rule 表; is_builtin=1 标记系统内置。
-- 幂等: ON CONFLICT (rule_name) 需要唯一约束, 这里用 WHERE NOT EXISTS 保护。
-- 执行顺序: 依赖 01(加列) 与 02(细菌码)。
-- ============================================================

-- MRSA: 金黄色葡萄球菌(SAU) 对苯唑西林/头孢西丁 任一耐药
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, bacteria_codes, rule_name, rule_type, match_mode, is_critical, alert_message, description, is_builtin, status)
SELECT 'SAU', '["OXA","CFZ"]', '["SAU"]', 'MRSA', '特殊耐药', 'ANY_RESISTANT', 0,
       '检测到耐甲氧西林金黄色葡萄球菌(MRSA)：苯唑西林/头孢西丁耐药', '耐甲氧西林金黄色葡萄球菌', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lis_resistance_rule WHERE rule_name='MRSA');

-- VRE: 肠球菌(EFA/EFM) 对万古霉素 耐药
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, bacteria_codes, rule_name, rule_type, match_mode, is_critical, alert_message, description, is_builtin, status)
SELECT 'EFA', '["VAN"]', '["EFA","EFM"]', 'VRE', '特殊耐药', 'ANY_RESISTANT', 0,
       '检测到耐万古霉素肠球菌(VRE)：万古霉素耐药', '耐万古霉素肠球菌', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lis_resistance_rule WHERE rule_name='VRE');

-- CRE: 肠杆菌科(ECO/KPN/ECL/ARE/CFF/PMI/SMA) 对碳青霉烯类 任一耐药
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, bacteria_codes, rule_name, rule_type, match_mode, is_critical, alert_message, description, is_builtin, status)
SELECT 'ECO', '["IPM","MEM","ETP"]', '["ECO","KPN","ECL","ARE","CFF","PMI","SMA"]', 'CRE', '特殊耐药', 'ANY_RESISTANT', 1,
       '检测到耐碳青霉烯类肠杆菌科细菌(CRE)：碳青霉烯类耐药，请按院感流程处理', '耐碳青霉烯类肠杆菌科细菌', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lis_resistance_rule WHERE rule_name='CRE');

-- CRAB: 鲍曼不动杆菌(ABA) 对亚胺培南/美罗培南 任一耐药
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, bacteria_codes, rule_name, rule_type, match_mode, is_critical, alert_message, description, is_builtin, status)
SELECT 'ABA', '["IPM","MEM"]', '["ABA"]', 'CRAB', '特殊耐药', 'ANY_RESISTANT', 0,
       '检测到耐碳青霉烯类鲍曼不动杆菌(CRAB)', '耐碳青霉烯类鲍曼不动杆菌', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lis_resistance_rule WHERE rule_name='CRAB');

-- 最后防线耐药(危急值): 适用于所有细菌, 对多黏菌素/替加环素 任一耐药
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, bacteria_codes, rule_name, rule_type, match_mode, is_critical, alert_message, description, is_builtin, status)
SELECT NULL, '["POL","CST","TGC"]', '', '最后防线耐药(危急值)', '危急值', 'ANY_RESISTANT', 1,
       '危急值：对多黏菌素/替加环素等最后防线药物耐药，可能面临无药可治', '最后防线药物耐药', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lis_resistance_rule WHERE rule_name='最后防线耐药(危急值)');