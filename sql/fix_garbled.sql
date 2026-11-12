-- ============================================================
-- 修正乱码测试数据 (ID 200/201/202/203/300)
-- 1) lis_order_item 指标名通过 test_item_code 关联 lis_test_item 恢复
-- 2) 患者/科室/诊断等原值已损坏, 替换为干净中文
-- 以 UTF-8 编码执行
-- ============================================================
BEGIN;

-- ---------- 1. 修正申请单明细指标名 (可恢复) ----------
UPDATE lis_order_item oi
SET test_item_name = tt.item_name
FROM lis_test_item tt
WHERE tt.item_code = oi.test_item_code
  AND oi.order_id IN (200,201,202,203,300);

-- ---------- 2. 修正患者姓名 ----------
UPDATE lis_patient SET name = CASE id
  WHEN 200 THEN '赵建国'
  WHEN 201 THEN '林芳'
  WHEN 202 THEN '高文华'
  WHEN 203 THEN '徐丽娜'
  WHEN 300 THEN '周美玲'
END
WHERE id IN (200,201,202,203,300);

-- ---------- 3. 修正就诊 ----------
UPDATE lis_visit SET department = CASE id
  WHEN 200 THEN '门诊内科'
  WHEN 201 THEN '门诊妇科'
  WHEN 202 THEN '住院部'
  WHEN 203 THEN '门诊儿科'
  WHEN 300 THEN '住院部心内'
END,
doctor_name = CASE id
  WHEN 200 THEN '王医生'
  WHEN 201 THEN '何医生'
  WHEN 202 THEN '罗医生'
  WHEN 203 THEN '梁医生'
  WHEN 300 THEN '王医生'
END
WHERE id IN (200,201,202,203,300);

-- ---------- 4. 修正申请单 ----------
UPDATE lis_lab_order SET apply_doctor = CASE id
  WHEN 200 THEN '王医生'
  WHEN 201 THEN '何医生'
  WHEN 202 THEN '罗医生'
  WHEN 203 THEN '梁医生'
  WHEN 300 THEN '王医生'
END,
apply_dept = CASE id
  WHEN 200 THEN '门诊内科'
  WHEN 201 THEN '门诊妇科'
  WHEN 202 THEN '住院部'
  WHEN 203 THEN '门诊儿科'
  WHEN 300 THEN '住院部心内'
END,
clinical_diag = CASE id
  WHEN 200 THEN '肝功能异常'
  WHEN 201 THEN '妇科常规检查'
  WHEN 202 THEN '心脏病随访'
  WHEN 203 THEN '发热待查'
  WHEN 300 THEN '心肌酶谱检查'
END
WHERE id IN (200,201,202,203,300);

-- ---------- 5. 修正标本 (同步患者姓名/科室/标本类型) ----------
UPDATE lis_sample SET patient_name = CASE id
  WHEN 200 THEN '赵建国'
  WHEN 201 THEN '林芳'
  WHEN 202 THEN '高文华'
  WHEN 203 THEN '徐丽娜'
  WHEN 300 THEN '周美玲'
END,
department = CASE id
  WHEN 200 THEN '门诊内科'
  WHEN 201 THEN '门诊妇科'
  WHEN 202 THEN '住院部'
  WHEN 203 THEN '门诊儿科'
  WHEN 300 THEN '住院部心内'
END,
sample_type = CASE id
  WHEN 202 THEN '全血'
  WHEN 203 THEN '全血'
  ELSE '血清'
END
WHERE id IN (200,201,202,203,300);

COMMIT;