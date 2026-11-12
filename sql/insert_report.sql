-- 报告(含签名人员: 检验人=张技师, 报告医生=李主任), report_type 为数值
INSERT INTO lis_report (id, report_no, sample_id, patient_id, patient_name, report_type, status,
  checker, report_doctor, create_time, update_time, patient_gender, patient_age)
VALUES (9002000100010000002, 'RPT-TEST-001', 9002000100010000001, 300, '测试患者', 1, 6,
  '张技师', '李主任', now(), now(), 2, 40);