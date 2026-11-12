\echo '######## C2: 两条人工重名 → 脚本必须中断，且人工记录一条都不能少 ########'
BEGIN;

DROP INDEX uk_lis_supplier_name_norm;

-- 两条都是人工录入（source_type=1），规范名相同，脚本无权自动删除任何一条
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status, license_no)
VALUES ('QA_MAN_A', '安捷伦科技', 1, 1, 'LIC-A-001');
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status, license_no)
VALUES ('QA_MAN_B', '安捷伦科技' || chr(12288), 1, 1, 'LIC-B-002');

\echo '--- 执行前：两条人工记录并存 ---'
SELECT id, supplier_code, source_type, license_no FROM lis_supplier ORDER BY id;

\echo '--- 执行完整脚本，期望在 22.2 守卫处 RAISE EXCEPTION 中断 ---'
\i lis-system/docs/database/add-reagent-enhance.sql

\echo '!!!! 如果这行被打印出来，说明守卫没生效，属严重缺陷 !!!!'
SELECT id, supplier_code, source_type FROM lis_supplier ORDER BY id;

ROLLBACK;
