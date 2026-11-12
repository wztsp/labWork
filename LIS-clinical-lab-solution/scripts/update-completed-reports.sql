-- 更新现有几条报告状态为已审核(status=2)和已发布(status=3)，并补充审核/发布信息
UPDATE lis_report SET status = 3, auditor_id = 2, audit_time = '2026-08-02 10:30:00', publisher_id = 2, publish_time = '2026-08-02 10:35:00', checker = '李审核', report_doctor = '张技师' WHERE id = 100;
UPDATE lis_report SET status = 2, auditor_id = 2, audit_time = '2026-08-02 10:40:00', checker = '李审核', report_doctor = '张技师' WHERE id = 101;
UPDATE lis_report SET status = 3, auditor_id = 2, audit_time = '2026-08-02 10:50:00', publisher_id = 2, publish_time = '2026-08-02 10:55:00', checker = '李审核', report_doctor = '张技师' WHERE id = 102;
UPDATE lis_report SET status = 3, auditor_id = 2, audit_time = '2026-08-10 09:20:00', publisher_id = 2, publish_time = '2026-08-10 09:25:00', checker = '李审核', report_doctor = '张技师' WHERE id = 200;
UPDATE lis_report SET status = 2, auditor_id = 2, audit_time = '2026-08-10 10:20:00', checker = '李审核', report_doctor = '张技师' WHERE id = 201;

-- 同步更新对应结果状态
UPDATE lis_result SET audit_status = 2 WHERE sample_id IN (SELECT sample_id FROM lis_report WHERE status >= 2);

-- 新增一条 8月3日(今天) 已发布的报告，确保今日查询有结果
INSERT INTO lis_patient (id, patient_no, name, gender, age, phone_enc, phone_hash, address) VALUES
(300, 'P20260300', '周美玲', 2, 52, '13801000020', NULL, '北京市丰台区');

INSERT INTO lis_visit (id, visit_no, patient_id, visit_type, department, doctor_name, visit_time) VALUES
(300, 'V20260300', 300, 2, '住院部心内', '王医生', '2026-08-03 08:00:00');

INSERT INTO lis_lab_order (id, order_no, visit_id, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time, receive_time) VALUES
(300, 'ORD20260803300', 300, 300, 2, 1, '王医生', '住院部心内', '心肌酶谱检查', '2026-08-03 08:05:00', '2026-08-03 08:10:00');

INSERT INTO lis_order_item (id, order_id, test_item_id, test_item_code, test_item_name, status, fee) VALUES
(300, 300, 5,  'GLU', '葡萄糖',       2, 12.00),
(301, 300, 6,  'ALT', '丙氨酸氨基转移酶', 2, 12.00),
(302, 300, 1,  'WBC', '白细胞计数',     2, 15.00),
(303, 300, 2,  'RBC', '红细胞计数',     2, 15.00),
(304, 300, 3,  'HGB', '血红蛋白',       2, 10.00);

INSERT INTO lis_sample (id, sample_barcode, order_id, patient_id, visit_id, patient_name, visit_no, sample_type, container_type, status, is_urgent, collect_time, receive_time, report_time, operator_id, department) VALUES
(300, 'BC20260803300', 300, 300, 300, '周美玲', 'V20260300', '血清', '促凝管', 4, 0, '2026-08-03 08:15:00', '2026-08-03 08:30:00', '2026-08-03 09:00:00', 2, '住院部心内');

INSERT INTO lis_result (id, sample_id, test_item_id, result_value, unit, ref_range, abnormal_flag, is_critical, instrument_id, operator_id, test_time, audit_status) VALUES
(300, 300, 5, '5.8',  'mmol/L', '3.9-6.1',  'N', 0, 2, 2, '2026-08-03 08:50:00', 2),
(301, 300, 6, '32',   'U/L',    '7-40',     'N', 0, 2, 2, '2026-08-03 08:50:00', 2),
(302, 300, 1, '6.9',  '10^9/L', '3.5-9.5',  'N', 0, 1, 2, '2026-08-03 08:50:00', 2),
(303, 300, 2, '4.6',  '10^12/L','4.3-5.8',  'N', 0, 1, 2, '2026-08-03 08:50:00', 2),
(304, 300, 3, '148',  'g/L',    '130-175',  'N', 0, 1, 2, '2026-08-03 08:50:00', 2);

INSERT INTO lis_report (id, report_no, sample_id, patient_id, patient_name, patient_gender, patient_age, report_type, status, report_doctor, checker, auditor_id, audit_time, publisher_id, publish_time, create_time) VALUES
(300, 'RPT20260803300', 300, 300, '周美玲', 2, 52, 1, 3, '张技师', '李审核', 2, '2026-08-03 09:05:00', 2, '2026-08-03 09:10:00', '2026-08-03 09:00:00');