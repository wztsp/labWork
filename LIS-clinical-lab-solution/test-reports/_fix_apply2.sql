\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';
-- 其余4个乱码尾部带 U+FFFD, 按去掉尾部替换符后精确匹配
UPDATE lis_report
SET patient_name = sub.new_name
FROM (VALUES
  ('榛勫織寮','黄志强'),
  ('寰愪附濞','徐丽娟'),
  ('楂樻枃鍗','高文博'),
  ('鍛ㄧ編鐜','周美玲')
) AS sub(moj, new_name)
WHERE rtrim(patient_name, chr(65533)) = sub.moj;

\echo '== 修复后校验: 残留乱码(应为0) =='
SELECT count(*) AS mojibake_left
FROM lis_report
WHERE rtrim(patient_name, chr(65533)) IN ('榛勫織寮','寰愪附濞','楂樻枃鍗','鍛ㄧ編鐜');
\echo '== 修复后正确姓名清单 =='
SELECT patient_name, count(*) FROM lis_report
WHERE patient_name IN ('高文博','林静','黄志强','徐丽娟','周美玲')
GROUP BY patient_name ORDER BY patient_name;