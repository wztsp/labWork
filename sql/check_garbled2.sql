-- 检查关联表乱码及完整乱码清单
\pset footer off
\echo === lis_order_item 乱码 ===
SELECT id, order_id, test_item_code, test_item_name FROM lis_order_item
WHERE test_item_name LIKE '%' || chr(65533) || '%'
   OR test_item_code LIKE '%' || chr(65533) || '%';

\echo === lis_sample_order_item 乱码 ===
SELECT id, sample_id, order_id, order_item_id, test_item_id FROM lis_sample_order_item;

\echo === lis_sample 其他字段乱码 (collector/deliver/signoff) ===
SELECT id, sample_barcode, deliver_person, sign_off_person FROM lis_sample
WHERE deliver_person LIKE '%' || chr(65533) || '%'
   OR sign_off_person LIKE '%' || chr(65533) || '%';

\echo === 全部乱码条码清单 ===
SELECT id, sample_barcode, status FROM lis_sample
WHERE patient_name LIKE '%' || chr(65533) || '%'
   OR department LIKE '%' || chr(65533) || '%'
   OR sample_type LIKE '%' || chr(65533) || '%'
ORDER BY id;