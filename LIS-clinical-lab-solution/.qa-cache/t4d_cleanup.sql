\echo '=== 4d CLEANUP: remove all QA rows, then re-run script to restore uk_lis_supplier_name_norm ==='
DELETE FROM lis_reagent WHERE reagent_code LIKE 'QA_%';
DELETE FROM lis_supplier WHERE supplier_code LIKE 'QA_%';
\echo '--- residue after delete (expect 0 / 0) ---'
SELECT (SELECT COUNT(*) FROM lis_supplier WHERE supplier_code LIKE 'QA_%') AS qa_sup,
       (SELECT COUNT(*) FROM lis_reagent  WHERE reagent_code  LIKE 'QA_%') AS qa_rea;
