-- 检测并列出所有乱码记录（GBK->UTF8 双重编码产生的乱码）
-- 乱码特征：包含 U+FFFD 替换符 或 典型的 mojibake 汉字
\pset footer off
\echo === lis_sample 乱码 ===
SELECT id, sample_barcode, patient_name, department FROM lis_sample
WHERE patient_name LIKE '%' || chr(65533) || '%'
   OR department LIKE '%' || chr(65533) || '%'
   OR sample_type LIKE '%' || chr(65533) || '%';

\echo === lis_patient 乱码 ===
SELECT id, patient_no, name FROM lis_patient
WHERE name LIKE '%' || chr(65533) || '%';

\echo === lis_visit 乱码 ===
SELECT id, visit_no, department, doctor_name FROM lis_visit
WHERE department LIKE '%' || chr(65533) || '%'
   OR doctor_name LIKE '%' || chr(65533) || '%';

\echo === lis_lab_order 乱码 ===
SELECT id, order_no, apply_doctor, apply_dept, clinical_diag FROM lis_lab_order
WHERE apply_doctor LIKE '%' || chr(65533) || '%'
   OR apply_dept LIKE '%' || chr(65533) || '%'
   OR clinical_diag LIKE '%' || chr(65533) || '%';