\set ON_ERROR_STOP on
\echo '######## C1: 人工记录 + 自动草稿 规范名重复 → 人工必须存活 ########'
BEGIN;

-- 先摘掉唯一索引，才能构造出「升级前的脏库」状态
DROP INDEX uk_lis_supplier_name_norm;

-- 人工录入（带真实证照数据，绝不能被删）
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status, license_no, contact_person)
VALUES ('QA_MAN_01', '安捷伦科技', 1, 1, 'LIC-REAL-001', '张三');

-- 自动迁移草稿（规范名与上面相同，只差一个全角空格）
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status)
VALUES ('SUP_MIG_9001', '安捷伦科技' || chr(12288), 2, 1);

-- 让一条试剂指向【草稿】，验证归并后外键会不会改指到人工那条
UPDATE lis_reagent
   SET supplier_id = (SELECT id FROM lis_supplier WHERE supplier_code = 'SUP_MIG_9001')
 WHERE id = (SELECT MIN(id) FROM lis_reagent);

\echo '--- 归并前 ---'
SELECT id, supplier_code, source_type, license_no FROM lis_supplier ORDER BY id;
SELECT id AS reagent_id, supplier_id FROM lis_reagent WHERE supplier_id IS NOT NULL;

\echo '--- 执行完整脚本 ---'
\i lis-system/docs/database/add-reagent-enhance.sql

\echo '--- 归并后：人工记录应存活、草稿应消失 ---'
SELECT id, supplier_code, source_type, license_no FROM lis_supplier ORDER BY id;

\echo '--- 断言1：人工记录 QA_MAN_01 仍在（期望 1） ---'
SELECT COUNT(*) AS manual_survived FROM lis_supplier WHERE supplier_code = 'QA_MAN_01';

\echo '--- 断言2：草稿 SUP_MIG_9001 已删（期望 0） ---'
SELECT COUNT(*) AS draft_deleted FROM lis_supplier WHERE supplier_code = 'SUP_MIG_9001';

\echo '--- 断言3：试剂外键已改指人工记录（期望 t） ---'
SELECT r.supplier_id = (SELECT id FROM lis_supplier WHERE supplier_code='QA_MAN_01') AS fk_repointed
  FROM lis_reagent r WHERE r.supplier_id IS NOT NULL;

\echo '--- 断言4：无悬空 supplier_id（期望 0） ---'
SELECT COUNT(*) AS dangling FROM lis_reagent r
 WHERE r.supplier_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lis_supplier s WHERE s.id = r.supplier_id);

ROLLBACK;

\echo '--- 回滚后零残留确认（期望 0 / 0） ---'
SELECT COUNT(*) AS suppliers_left FROM lis_supplier;
SELECT COUNT(*) AS reagents_with_supplier FROM lis_reagent WHERE supplier_id IS NOT NULL;
