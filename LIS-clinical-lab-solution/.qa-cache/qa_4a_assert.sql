-- ④a 归并断言
SELECT COUNT(*) AS cnt_9001_9002 FROM lis_supplier WHERE id IN (9001,9002);
SELECT id, supplier_code, supplier_name, source_type FROM lis_supplier WHERE id IN (9001,9002) ORDER BY id;
SELECT supplier_id AS reagent_90001_supplier_id FROM lis_reagent WHERE id=90001;
