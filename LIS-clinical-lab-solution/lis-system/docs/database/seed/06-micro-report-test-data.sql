-- ============================================================
-- 06-micro-report-test-data.sql   原 MicroReportTestDataInitializer
-- 微生物报告示例数据: 革兰氏染色 / 血培养 / 细菌药敏 (待签收状态)。
-- 演示数据, 非生产必需。幂等: 先清理旧示例再重建; 仅当无"微生物"项目或从未建立时执行。
-- ============================================================
-- 先清理旧的 MIC-TEST 示例, 保证可重复执行
DELETE FROM lis_micro_report_subculture WHERE micro_report_id IN (SELECT id FROM lis_micro_report WHERE report_no LIKE 'MIC-TEST-%');
DELETE FROM lis_antibiotic_sensitivity WHERE micro_report_id IN (SELECT id FROM lis_micro_report WHERE report_no LIKE 'MIC-TEST-%');
DELETE FROM lis_micro_organism WHERE micro_report_id IN (SELECT id FROM lis_micro_report WHERE report_no LIKE 'MIC-TEST-%');
DELETE FROM lis_micro_report WHERE report_no LIKE 'MIC-TEST-%';
DELETE FROM lis_sample WHERE sample_barcode LIKE 'SM-GS-%' OR sample_barcode LIKE 'SM-BC-%' OR sample_barcode LIKE 'SM-ST-%';
DELETE FROM lis_lab_order WHERE order_no LIKE 'ORD-TEST-%';

-- 使用已有的患者(缺则插入测试患者)
INSERT INTO lis_patient (patient_no, name, gender, age, is_test) SELECT 'P-TEST-1','张三',1,35,1 WHERE NOT EXISTS (SELECT 1 FROM lis_patient WHERE name='张三');
INSERT INTO lis_patient (patient_no, name, gender, age, is_test) SELECT 'P-TEST-2','李四',1,42,1 WHERE NOT EXISTS (SELECT 1 FROM lis_patient WHERE name='李四');
INSERT INTO lis_patient (patient_no, name, gender, age, is_test) SELECT 'P-TEST-3','王丽',2,28,1 WHERE NOT EXISTS (SELECT 1 FROM lis_patient WHERE name='王丽');

-- 申请单 (ORD-TEST-{patientId}-{seq})
INSERT INTO lis_lab_order (order_no, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time)
SELECT 'ORD-TEST-1-001', p.id, 1, 1, '测试医生', '测试科室', '微生物检查', NOW() FROM lis_patient p WHERE p.name='张三';
INSERT INTO lis_lab_order (order_no, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time)
SELECT 'ORD-TEST-2-002', p.id, 1, 1, '测试医生', '测试科室', '微生物检查', NOW() FROM lis_patient p WHERE p.name='李四';
INSERT INTO lis_lab_order (order_no, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time)
SELECT 'ORD-TEST-3-003', p.id, 1, 1, '测试医生', '测试科室', '微生物检查', NOW() FROM lis_patient p WHERE p.name='王丽';

-- ========== 1. 革兰氏染色报告 ==========
INSERT INTO lis_sample (sample_barcode, sample_no, order_id, patient_id, patient_name, sample_type, container_type, status, is_urgent, collect_time, deliver_time, deliver_person, department, deleted)
SELECT 'SM-GS-001','GS001', o.id, p.id, p.name, '呼吸道分泌物','痰瓶', 2, 0, NOW() - interval '2 hour', NOW() - interval '1 hour', '送检员A','呼吸内科',0
FROM lis_lab_order o JOIN lis_patient p ON p.id=o.patient_id WHERE o.order_no='ORD-TEST-1-001';
INSERT INTO lis_micro_report (report_no, sample_id, patient_id, patient_name, specimen_type, culture_site, report_status, create_time, update_time)
SELECT 'MIC-TEST-GS-001', s.id, s.patient_id, s.patient_name, '呼吸道分泌物', '下呼吸道', 1, NOW(), NOW()
FROM lis_sample s WHERE s.sample_barcode='SM-GS-001';
UPDATE lis_micro_report SET gram_stain_result='G+ 球菌，成双排列', gram_stain_remark='可见革兰氏阳性双球菌，疑似肺炎链球菌', gram_stain_time=NOW(), gram_stain_operator='张技师' WHERE report_no='MIC-TEST-GS-001';
INSERT INTO lis_micro_organism (micro_report_id, organism_code, organism_name, quantity, culture_method, create_time)
SELECT r.id,'SPN','肺炎链球菌','中等量','需氧培养',NOW() FROM lis_micro_report r WHERE r.report_no='MIC-TEST-GS-001';
INSERT INTO lis_antibiotic_sensitivity (micro_report_id, organism_id, antibiotic_name, antibiotic_code, mic_value, result, breakpoint, create_time)
SELECT r.id, o.id, v.antibiotic_name, v.antibiotic_code, v.mic_value, v.result, v.breakpoint, NOW()
FROM ((SELECT r.id FROM lis_micro_report r WHERE r.report_no='MIC-TEST-GS-001') r
  JOIN (SELECT o.id FROM lis_micro_organism o WHERE o.organism_code='SPN') o ON true)
JOIN (VALUES ('青霉素','PEN','<=0.06','S','≤0.06'),('阿莫西林','AMP','<=0.25','S','≤0.25'),('头孢曲松','CRO','<=0.25','S','≤0.25'),('左氧氟沙星','LVX','1.0','S','≤2'),('万古霉素','VAN','0.5','S','≤2'))
 AS v(antibiotic_name, antibiotic_code, mic_value, result, breakpoint) ON true;

-- ========== 2. 血培养报告 ==========
INSERT INTO lis_sample (sample_barcode, sample_no, order_id, patient_id, patient_name, sample_type, container_type, status, is_urgent, collect_time, deliver_time, deliver_person, department, deleted)
SELECT 'SM-BC-001','BC001', o.id, p.id, p.name, '血液','血培养瓶', 2, 1, NOW() - interval '4 hour', NOW() - interval '3 hour', '送检员B','急诊科',0
FROM lis_lab_order o JOIN lis_patient p ON p.id=o.patient_id WHERE o.order_no='ORD-TEST-2-002';
INSERT INTO lis_micro_report (report_no, sample_id, patient_id, patient_name, specimen_type, culture_site, report_status, create_time, update_time)
SELECT 'MIC-TEST-BC-001', s.id, s.patient_id, s.patient_name, '血液','静脉血', 1, NOW(), NOW()
FROM lis_sample s WHERE s.sample_barcode='SM-BC-001';
UPDATE lis_micro_report SET bottle_no='BC202608001', bottle_type='成人需氧瓶', volume='8ml', inoculate_time=NOW()-interval '4 hour', alarm_time=NOW()-interval '1 hour', ttp_hours=3.0, blood_culture_result='POSITIVE', gram_stain_result='G- 杆菌', gram_stain_remark='血培养阳性，涂片见革兰氏阴性杆菌', gram_stain_time=NOW()-interval '1 hour', gram_stain_operator='李技师' WHERE report_no='MIC-TEST-BC-001';
INSERT INTO lis_micro_organism (micro_report_id, organism_code, organism_name, quantity, culture_method, create_time)
SELECT r.id,'ECO','大肠埃希菌','大量','血培养仪',NOW() FROM lis_micro_report r WHERE r.report_no='MIC-TEST-BC-001';
INSERT INTO lis_antibiotic_sensitivity (micro_report_id, organism_id, antibiotic_name, antibiotic_code, mic_value, result, breakpoint, create_time)
SELECT r.id, o.id, v.antibiotic_name, v.antibiotic_code, v.mic_value, v.result, v.breakpoint, NOW()
FROM ((SELECT r.id FROM lis_micro_report r WHERE r.report_no='MIC-TEST-BC-001') r
  JOIN (SELECT o.id FROM lis_micro_organism o WHERE o.organism_code='ECO') o ON true)
JOIN (VALUES ('头孢他啶','CAZ','0.5','S','≤2'),('亚胺培南','IPM','0.25','S','≤1'),('庆大霉素','GEN','1.0','S','≤1'),('环丙沙星','CIP','0.5','S','≤0.5'),('氨苄西林','AMP','>32','R','≤2'),('头孢曲松','CRO','2.0','I','≤1'))
 AS v(antibiotic_name, antibiotic_code, mic_value, result, breakpoint) ON true;
INSERT INTO lis_micro_report_subculture (id, micro_report_id, subculture_time, medium, incubator_no, result, operator, remark, create_time)
SELECT (SELECT COALESCE(MAX(id),0)+1 FROM lis_micro_report_subculture), r.id, NOW()-interval '1 hour', 'MAC平板', '培养箱1号', '转种生长良好', '李技师', '血培养阳性转种', NOW()
FROM lis_micro_report r WHERE r.report_no='MIC-TEST-BC-001';

-- ========== 3. 细菌药敏报告 (双细菌) ==========
INSERT INTO lis_sample (sample_barcode, sample_no, order_id, patient_id, patient_name, sample_type, container_type, status, is_urgent, collect_time, deliver_time, deliver_person, department, deleted)
SELECT 'SM-ST-001','ST001', o.id, p.id, p.name, '尿液','尿杯', 2, 0, NOW() - interval '3 hour', NOW() - interval '2 hour', '送检员C','泌尿外科',0
FROM lis_lab_order o JOIN lis_patient p ON p.id=o.patient_id WHERE o.order_no='ORD-TEST-3-003';
INSERT INTO lis_micro_report (report_no, sample_id, patient_id, patient_name, specimen_type, culture_site, report_status, create_time, update_time)
SELECT 'MIC-TEST-ST-001', s.id, s.patient_id, s.patient_name, '尿液','膀胱穿刺', 2, NOW(), NOW()
FROM lis_sample s WHERE s.sample_barcode='SM-ST-001';
UPDATE lis_micro_report SET gram_stain_result='G+ 球菌，成堆排列', gram_stain_remark='尿沉渣涂片见革兰氏阳性球菌成堆排列', gram_stain_time=NOW()-interval '2 hour', gram_stain_operator='王技师' WHERE report_no='MIC-TEST-ST-001';
-- 细菌1 金黄色葡萄球菌
INSERT INTO lis_micro_organism (micro_report_id, organism_code, organism_name, quantity, culture_method, create_time)
SELECT r.id,'SAU','金黄色葡萄球菌','大量','需氧培养',NOW() FROM lis_micro_report r WHERE r.report_no='MIC-TEST-ST-001';
INSERT INTO lis_antibiotic_sensitivity (micro_report_id, organism_id, antibiotic_name, antibiotic_code, mic_value, result, breakpoint, create_time)
SELECT r.id, o.id, v.antibiotic_name, v.antibiotic_code, v.mic_value, v.result, v.breakpoint, NOW()
FROM ((SELECT r.id FROM lis_micro_report r WHERE r.report_no='MIC-TEST-ST-001') r
  JOIN (SELECT o.id FROM lis_micro_organism o WHERE o.organism_code='SAU') o ON true)
JOIN (VALUES ('苯唑西林','OXA','0.25','S','≤2'),('头孢唑林','CFZ','0.5','S','≤2'),('万古霉素','VAN','1.0','S','≤2'),('利福平','RIF','0.5','S','≤1'),('环丙沙星','CIP','2.0','I','≤0.5'),('青霉素','PEN','>0.5','R','≤0.12'))
 AS v(antibiotic_name, antibiotic_code, mic_value, result, breakpoint) ON true;
-- 细菌2 大肠埃希菌
INSERT INTO lis_micro_organism (micro_report_id, organism_code, organism_name, quantity, culture_method, create_time)
SELECT r.id,'ECO','大肠埃希菌','少量','需氧培养',NOW() FROM lis_micro_report r WHERE r.report_no='MIC-TEST-ST-001';
INSERT INTO lis_antibiotic_sensitivity (micro_report_id, organism_id, antibiotic_name, antibiotic_code, mic_value, result, breakpoint, create_time)
SELECT r.id, o.id, v.antibiotic_name, v.antibiotic_code, v.mic_value, v.result, v.breakpoint, NOW()
FROM ((SELECT r.id FROM lis_micro_report r WHERE r.report_no='MIC-TEST-ST-001') r
  JOIN (SELECT o.id, ROW_NUMBER() OVER (ORDER BY o.id) rn FROM lis_micro_organism o WHERE o.organism_code='ECO') o ON true)
JOIN (VALUES ('头孢噻肟','CTX','0.25','S','≤1'),('头孢他啶','CAZ','0.5','S','≤2'),('亚胺培南','IPM','0.25','S','≤1'),('庆大霉素','GEN','1.0','S','≤1'),('氨苄西林','AMP','>32','R','≤2'))
 AS v(antibiotic_name, antibiotic_code, mic_value, result, breakpoint) ON true;