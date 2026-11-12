-- 查看乱码记录的 order_item 正确名称映射及患者信息
\pset footer off
\echo === 乱码 order_item + 正确名称 ===
SELECT oi.id, oi.order_id, oi.test_item_code, oi.test_item_id, tt.item_name AS correct_name
FROM lis_order_item oi
LEFT JOIN lis_test_item tt ON tt.item_code = oi.test_item_code
WHERE oi.order_id IN (200,201,202,203,300)
ORDER BY oi.order_id, oi.id;

\echo === 患者 200-300 name ===
SELECT id, patient_no, name FROM lis_patient WHERE id BETWEEN 200 AND 300 ORDER BY id;

\echo === 标本 200-300 ===
SELECT id, sample_barcode, patient_name, department, sample_type, status FROM lis_sample WHERE id BETWEEN 200 AND 300 ORDER BY id;