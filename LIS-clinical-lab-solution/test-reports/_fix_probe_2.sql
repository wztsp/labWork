SET client_encoding TO 'UTF8';
\echo === 1. lis_sample 乱码候选 ===
SELECT patient_name AS name, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_sample) t
WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 2. lis_report 乱码候选 ===
SELECT patient_name AS name, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_report) t
WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 3. lis_critical_record 乱码候选 ===
SELECT patient_name AS name, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_critical_record) t
WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 4. lis_tat_monitor 乱码候选 ===
SELECT patient_name AS name, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_tat_monitor) t
WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 5. lis_micro_report 乱码候选 ===
SELECT patient_name AS name, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_micro_report) t
WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 6. lis_reagent_inbound 供应商乱码 ===
SELECT DISTINCT supplier_name FROM lis_reagent_inbound WHERE supplier_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 7. lis_supplier 供应商乱码 ===
SELECT supplier_id, supplier_name FROM lis_supplier WHERE supplier_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === 8. lis_result 异常时间 ===
SELECT id, test_time, audit_time FROM lis_result WHERE is_critical=1 AND audit_time IS NOT NULL AND test_time > audit_time LIMIT 20;
\echo === 9. lis_sample 异常时间参考 ===
SELECT count(*) AS test_gt_audit FROM lis_result WHERE is_critical=1 AND audit_time IS NOT NULL AND test_time > audit_time;