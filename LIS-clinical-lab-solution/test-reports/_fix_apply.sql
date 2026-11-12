\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';

--=========== 1. 备份受影响表 ===========
DROP TABLE IF EXISTS _bak_report;
CREATE TABLE _bak_report AS SELECT * FROM lis_report;
DROP TABLE IF EXISTS _bak_inbound;
CREATE TABLE _bak_inbound AS SELECT * FROM lis_reagent_inbound;
DROP TABLE IF EXISTS _bak_result;
CREATE TABLE _bak_result AS SELECT * FROM lis_result;

--=========== 2. 修复患者姓名乱码 (GBK双字节编码导致的mojibake, 人工映射+已验证) ===========
UPDATE lis_report SET patient_name = CASE patient_name
    WHEN '榛勫織寮' THEN '黄志强'
    WHEN '鏋楅潤'   THEN '林静'
    WHEN '寰愪附濞' THEN '徐丽娟'
    WHEN '楂樻枃鍗' THEN '高文博'
    WHEN '鍛ㄧ編鐜' THEN '周美玲'
    ELSE patient_name END
WHERE patient_name IN ('榛勫織寮','鏋楅潤','寰愪附濞','楂樻枃鍗','鍛ㄧ編鐜');

--=========== 3. 修复入库单供应商坏占位 '?????' -> 可读占位 ===========
UPDATE lis_reagent_inbound SET supplier = '未知供应商'
WHERE supplier = '?????' AND (supplier_id IS NULL OR supplier_id = 0);

--=========== 4. 修复异常时间: 危急值确认时间不得早于检测时间 ===========
UPDATE lis_result SET audit_time = test_time
WHERE id IN (SELECT id FROM lis_result WHERE is_critical=1 AND audit_time IS NOT NULL AND test_time > audit_time);

--=========== 5. 校验 ===========
\echo '== 修复后 lis_report 乱码应无残留, 显示正确姓名 =='
SELECT patient_name, count(*) FROM lis_report
WHERE patient_name = '高文博' OR patient_name='林静' OR patient_name='黄志强' OR patient_name='徐丽娟' OR patient_name='周美玲'
GROUP BY patient_name;
\echo '== 残留乱码(应为0) =='
SELECT count(*) AS mojibake_left FROM lis_report WHERE patient_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo '== 入库单 ???? 残留(应为0) =='
SELECT count(*) AS qmark_left FROM lis_reagent_inbound WHERE supplier = '?????';
\echo '== 异常时间残留(应为0) =='
SELECT count(*) AS time_left FROM lis_result WHERE is_critical=1 AND audit_time IS NOT NULL AND test_time > audit_time;