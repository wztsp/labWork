\echo '=== A. 规范化函数实效（期望全部 t） ==='
SELECT
  fn_supplier_name_norm('罗氏诊断') = fn_supplier_name_norm('罗氏诊断' || chr(12288)) AS a1_全角空格合并,
  fn_supplier_name_norm('罗氏诊断') = fn_supplier_name_norm('罗氏诊断' || chr(9))     AS a2_制表符合并,
  fn_supplier_name_norm('罗氏诊断') = fn_supplier_name_norm('  罗氏诊断  ')            AS a3_半角空格合并,
  fn_supplier_name_norm('Roche')    = fn_supplier_name_norm('ROCHE')                   AS a4_大小写合并,
  fn_supplier_name_norm('Roche')    = fn_supplier_name_norm('roche')                   AS a5_大小写合并2,
  fn_supplier_name_norm('北京 生物') <> fn_supplier_name_norm('北京生物')              AS a6_内部空格不误合;

\echo '=== A2. 规范化结果原文（观察是否折叠为单空格） ==='
SELECT '[' || fn_supplier_name_norm('  北京   生物  ' || chr(12288) || '科技 ') || ']' AS 折叠结果;

\echo '=== B. 表达式唯一索引真实拦截测试 ==='
BEGIN;
SAVEPOINT sp0;
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status)
VALUES ('QA_LEAD_01', '安捷伦科技', 1, 1);
\echo '--- B1 首条插入应成功 ---'
SELECT COUNT(*) AS b1_inserted FROM lis_supplier WHERE supplier_code='QA_LEAD_01';

\echo '--- B2 尾部全角空格变体，应被拒 ---'
SAVEPOINT sp1;
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status)
VALUES ('QA_LEAD_02', '安捷伦科技' || chr(12288), 1, 1);
ROLLBACK TO sp1;

\echo '--- B3 制表符变体，应被拒 ---'
SAVEPOINT sp2;
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status)
VALUES ('QA_LEAD_03', '安捷伦科技' || chr(9), 1, 1);
ROLLBACK TO sp2;

\echo '--- B4 真正不同的名称，应放行 ---'
SAVEPOINT sp3;
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, status)
VALUES ('QA_LEAD_04', '安捷伦生物', 1, 1);
SELECT COUNT(*) AS b4_inserted FROM lis_supplier WHERE supplier_code='QA_LEAD_04';
ROLLBACK TO sp3;

ROLLBACK;

\echo '=== C. 库内零残留确认 ==='
SELECT COUNT(*) AS 残留_QA_LEAD FROM lis_supplier WHERE supplier_code LIKE 'QA_LEAD%';
SELECT COUNT(*) AS 供应商总数 FROM lis_supplier;
