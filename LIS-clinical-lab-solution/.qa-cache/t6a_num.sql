\echo '=== ⑥a numbering two-phase + regex guard ==='
BEGIN;
SAVEPOINT s6;
-- pass1: business has supplier A
INSERT INTO lis_reagent (reagent_code, reagent_name, supplier) VALUES ('QA_A','试剂A','A公司');
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql
\echo '--- after pass1: A should be SUP_MIG_0001 ---'
SELECT supplier_code, supplier_name FROM lis_supplier WHERE source_type=2 ORDER BY supplier_code;
-- pass2: business adds supplier B
INSERT INTO lis_reagent (reagent_code, reagent_name, supplier) VALUES ('QA_B','试剂B','B公司');
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql
\echo '--- after pass2: B should be SUP_MIG_0002 (NOT 0001), no dup violation ---'
SELECT supplier_code, supplier_name FROM lis_supplier WHERE source_type=2 ORDER BY supplier_code;
\echo '--- regex guard: inject human SUP_MIG_ABC (non-numeric), then migrate C ---'
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('SUP_MIG_ABC','ZZTOP唯一公司',1,1);
INSERT INTO lis_reagent (reagent_code, reagent_name, supplier) VALUES ('QA_C','试剂C','C公司');
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql
\echo '--- after pass3: C should be SUP_MIG_0003, no CAST crash, SUP_MIG_ABC untouched ---'
SELECT supplier_code, supplier_name, source_type FROM lis_supplier WHERE supplier_code LIKE 'SUP_MIG%' ORDER BY supplier_code;
ROLLBACK;
