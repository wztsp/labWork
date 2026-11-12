-- Add basic data to empty LIS tables (corrected column names)
-- Run with: psql -U lis_user -d lis_db -f add-test-data.sql

-- 1. Microbiology report (linked to blood culture sample id=3)
INSERT INTO lis_micro_report (report_no, sample_id, patient_id, patient_name, specimen_type, culture_site, report_status, create_time)
VALUES ('MIC20260728001', 3, 3, '王丽', '血液', '静脉血', 0, NOW())
ON CONFLICT DO NOTHING;

-- 2. Microbiology organism (linked to the micro report)
INSERT INTO lis_micro_organism (micro_report_id, organism_code, organism_name, quantity, culture_method, create_time)
VALUES
(currval('lis_micro_report_id_seq'), 'ECOLI', '大肠埃希菌', 'MODERATE', '需氧培养', NOW()),
(currval('lis_micro_report_id_seq'), 'SALEP', '表皮葡萄球菌', 'LIGHT', '厌氧培养', NOW())
ON CONFLICT DO NOTHING;

-- 3. Antibiotic sensitivity results for the micro report
INSERT INTO lis_antibiotic_sensitivity (micro_report_id, organism_id, antibiotic_code, antibiotic_name, mic_value, result, breakpoint, create_time)
VALUES
(currval('lis_micro_report_id_seq'), 2, 'PEN', '青霉素', '8.0', 'R', '<=0.25', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'AMP', '氨苄西林', '16.0', 'R', '<=2', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'OXA', '苯唑西林', '4.0', 'R', '<=0.25', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'GEN', '庆大霉素', '2.0', 'S', '<=1', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'CIP', '环丙沙星', '1.0', 'S', '<=0.5', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'IPM', '亚胺培南', '0.5', 'S', '<=1', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'AK', '阿米卡星', '4.0', 'S', '<=8', NOW()),
(currval('lis_micro_report_id_seq'), 2, 'CAZ', '头孢他啶', '1.0', 'S', '<=4', NOW())
ON CONFLICT DO NOTHING;

-- 4. EQA records (external quality assessment)
INSERT INTO lis_eqa_record (eqa_batch_no, test_item_id, instrument_id, target_value, measure_value, sd, cv, score, grade, passed, measure_time, result_time, create_time)
VALUES
('EQA-2026-01', 1, 1, 6.50, 6.45, 0.20, 3.08, 95.0, 'A', 1, '2026-03-15 10:00:00', '2026-03-15 14:00:00', NOW()),
('EQA-2026-01', 2, 1, 4.50, 4.55, 0.15, 3.33, 92.0, 'A', 1, '2026-03-15 10:00:00', '2026-03-15 14:00:00', NOW()),
('EQA-2026-02', 1, 1, 7.20, 7.35, 0.20, 2.78, 88.0, 'B', 1, '2026-06-15 10:00:00', '2026-06-15 14:00:00', NOW()),
('EQA-2026-02', 1, 1, 5.80, 6.10, 0.20, 3.45, 75.0, 'C', 0, '2026-06-15 10:00:00', '2026-06-15 14:00:00', NOW())
ON CONFLICT DO NOTHING;

-- 5. Staff schedule (basic weekly schedule)
INSERT INTO lis_staff_schedule (staff_id, schedule_date, shift_type, duty_type, create_time)
VALUES
(1, '2026-07-29', 'DAY', 'NORMAL', NOW()),
(2, '2026-07-29', 'DAY', 'NORMAL', NOW()),
(2, '2026-07-30', 'DAY', 'NORMAL', NOW()),
(3, '2026-07-29', 'NIGHT', 'ON_CALL', NOW()),
(3, '2026-07-30', 'NIGHT', 'ON_CALL', NOW())
ON CONFLICT DO NOTHING;

-- 6. Staff training records
INSERT INTO lis_staff_training (staff_id, training_name, training_type, training_date, trainer, result, attachment_url, remark, create_time)
VALUES
(1, 'LIS系统操作培训', 'SYSTEM', '2026-01-15', '系统管理员', 'PASS', '/docs/training/lis-system.pdf', '全员LIS系统操作培训', NOW()),
(2, '生化分析仪操作规范', 'TECHNICAL', '2026-02-20', '李主任', 'PASS', '/docs/training/biochem-analyzer.pdf', '迈瑞BC-7500操作规范', NOW()),
(2, '质控管理培训', 'QC', '2026-03-10', '质控组长', 'PASS', '/docs/training/qc-management.pdf', 'Westgard多规则质控培训', NOW()),
(3, '生物安全培训', 'SAFETY', '2026-01-20', '安全员', 'PASS', '/docs/training/biosafety.pdf', '实验室生物安全规范', NOW()),
(3, '微生物检验技术', 'TECHNICAL', '2026-04-15', '李主任', 'PASS', '/docs/training/micro-tech.pdf', '微生物检验技术进阶培训', NOW())
ON CONFLICT DO NOTHING;

-- 7. Instrument maintenance log (completed maintenance)
INSERT INTO lis_instrument_maintenance_log (instrument_id, template_id, maintenance_date, maintenance_type, operator_id, operator_name, reviewer_id, reviewer_name, result, remark, create_time)
VALUES
(1, 1, '2026-07-22', 'DAILY', 2, '张技师', 3, '李主任', 1, 'BC-7500日维护：清洁进样针、检查液路、校准光路', NOW()),
(2, 2, '2026-07-22', 'DAILY', 2, '张技师', 3, '李主任', 1, 'cobas 8000日维护：更换反应杯、清洁搅拌棒、检查试剂仓温度', NOW()),
(1, 1, '2026-07-15', 'WEEKLY', 2, '张技师', 3, '李主任', 1, 'BC-7500周维护：深度清洁、校准、性能验证', NOW())
ON CONFLICT DO NOTHING;

-- 8. Instrument communication log (simulated comm events)
INSERT INTO lis_instrument_comm_log (instrument_id, direction, raw_message, protocol_type, parse_status, parse_result, message_time, create_time)
VALUES
(1, 'RECEIVE', 'H|\^&|||Mindray^BC-7500|||||LIS||P|1.0', 'ASTM', 'SUCCESS', 'Header frame from BC-7500', NOW() - INTERVAL '1 hour', NOW()),
(1, 'SEND', 'Q|1|^BC202607280001||ALL', 'ASTM', 'SUCCESS', 'Query for sample BC202607280001', NOW() - INTERVAL '59 minutes', NOW()),
(1, 'RECEIVE', 'R|1|^WBC^8.5|8.5|10^9/L|N|||F', 'ASTM', 'SUCCESS', 'WBC result: 8.5 10^9/L', NOW() - INTERVAL '58 minutes', NOW()),
(2, 'RECEIVE', 'MSH|^~\&|cobas8000|Roche|LIS|HOSP|202607291000||ORU^R01|MSG001|P|2.5', 'HL7', 'SUCCESS', 'HL7 ORU message from cobas 8000', NOW() - INTERVAL '30 minutes', NOW()),
(2, 'RECEIVE', 'OBX|1|NM|GLU^^^||7.2|mmol/L|3.9-6.1|N|||F', 'HL7', 'SUCCESS', 'GLU result: 7.2 mmol/L', NOW() - INTERVAL '29 minutes', NOW())
ON CONFLICT DO NOTHING;

-- 9. Report modify log (audit trail)
INSERT INTO lis_report_modify_log (report_id, item_id, old_value, new_value, modify_reason, operator_id, operator_name, modify_time)
VALUES
(1, 1, 'WBC: 8.2', 'WBC: 8.5', '结果复核修正-原值录入错误', 3, '李主任', '2026-07-28T11:00:00'),
(1, NULL, NULL, NULL, '报告补打', 3, '李主任', '2026-07-28T11:30:00')
ON CONFLICT DO NOTHING;
