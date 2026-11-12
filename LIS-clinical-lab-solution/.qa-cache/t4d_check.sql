\echo '=== 4d CHECK after script aborted at 22.2 guard: did 22.1 destructive work already COMMIT? ==='
\echo '--- QA_PD (draft loser) still there? expect GONE if 22.1.4 committed before RAISE ---'
SELECT supplier_code, supplier_name, source_type, id FROM lis_supplier WHERE supplier_code LIKE 'QA_P%' ORDER BY id;
\echo '--- QA_PFK now points to? expect repointed to QA_PH1 (winner) if 22.1.1 committed ---'
SELECT r.reagent_code, r.supplier_id, s.supplier_code, s.supplier_name, s.source_type
  FROM lis_reagent r LEFT JOIN lis_supplier s ON s.id=r.supplier_id WHERE r.reagent_code='QA_PFK';
\echo '--- unique index rebuilt? expect 0 (script aborted before 22.3) ---'
SELECT COUNT(*) AS idx_present FROM pg_indexes WHERE indexname='uk_lis_supplier_name_norm';
\echo '--- human duplicates still present? expect 2 (script refuses to touch them) ---'
SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) FROM lis_supplier GROUP BY 1 HAVING COUNT(*)>1;
