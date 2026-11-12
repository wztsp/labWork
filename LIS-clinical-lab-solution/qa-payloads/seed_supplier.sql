INSERT INTO lis_supplier
(supplier_code, supplier_name, short_name, credit_code, contact_person, phone, email, address,
 license_no, license_expiry, medical_cert_no, medical_cert_expiry,
 qual_status, score, source_type, status, remark, create_time, update_time)
VALUES
('SUP-QA-001', 'QA试剂供应有限公司', 'QA试剂', '91310000QA0001X', '张三', '13800000001', 'qa@example.com', '测试市测试路1号',
 'LIC-QA-001', '2030-12-31', 'MED-QA-001', '2030-12-31',
 1, 100.00, 1, 1, 'QA闭环验证数据准备(API创建因Bug S-1被阻断)', now(), now())
RETURNING id, supplier_code, supplier_name, qual_status, status;
