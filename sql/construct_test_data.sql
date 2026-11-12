-- 测试标本: 含手写签名预览用
INSERT INTO lis_sample (id, sample_barcode, sample_no, order_id, patient_id, visit_id, patient_name,
  sample_type, status, is_urgent, department, instrument_id,
  collect_time, receive_time, test_start_time, report_time, create_time, update_time, deleted, visit_no)
VALUES (9002000100010000001, 'TEST-SIG-001', 'TS001', 300, 300, 300, '测试患者',
  '血清', 4, 0, '验收检验科', 8,
  now(), now(), now(), NULL, now(), now(), 0, 'V-TEST-001');

-- 报告(含签名人员: 检验人=张技师, 报告医生=李主任)
INSERT INTO lis_report (id, report_no, sample_id, patient_id, patient_name, report_type, status,
  checker, report_doctor, create_time, update_time, patient_gender, patient_age)
VALUES (9002000100010000002, 'RPT-TEST-001', 9002000100010000001, 300, '测试患者', '常规', 6,
  '张技师', '李主任', now(), now(), 2, 40);

-- 检验结果(跨 3 个检验项目: 血常规/肝功能/血糖)
INSERT INTO lis_result (sample_id, test_item_id, result_value, unit, ref_range, abnormal_flag, is_critical, test_time, create_time, update_time) VALUES
(9002000100010000001, 1, '7.2', '10^9/L', '3.5-9.5', '', 0, now(), now(), now()),
(9002000100010000001, 2, '4.8', '10^12/L', '4.3-5.8', '', 0, now(), now(), now()),
(9002000100010000001, 3, '152', 'g/L', '130-175', '', 0, now(), now(), now()),
(9002000100010000001, 5, '6.8', 'mmol/L', '3.9-6.1', 'H', 0, now(), now(), now()),
(9002000100010000001, 6, '45', 'U/L', '7-40', 'H', 0, now(), now(), now());