\echo '=== POST regression cleanliness (must be ZERO QA residue) ==='
SELECT (SELECT COUNT(*) FROM lis_supplier WHERE supplier_code LIKE 'QA_%' OR supplier_code LIKE 'SUP_MIG%') AS qa_suppliers,
       (SELECT COUNT(*) FROM lis_reagent WHERE reagent_code LIKE 'QA_%') AS qa_reagents,
       (SELECT COUNT(*) FROM lis_reagent WHERE supplier_id IS NOT NULL) AS reagents_with_supplier_id,
       (SELECT COUNT(*) FROM lis_reagent WHERE supplier IS NOT NULL AND fn_supplier_name_norm(supplier) <> '') AS reagents_with_supplier_str;
SELECT COUNT(*) AS suppliers_total FROM lis_supplier;
SELECT COUNT(*) AS reagents_total FROM lis_reagent;
SELECT COUNT(*) AS stock_total FROM lis_reagent_stock;
\echo '--- fix still present ---'
SELECT COUNT(*) AS fn_exists FROM pg_proc WHERE proname='fn_supplier_name_norm';
SELECT COUNT(*) AS idx_exists FROM pg_indexes WHERE tablename='lis_supplier' AND indexname='uk_lis_supplier_name_norm';
SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name='lis_reagent_stock' AND column_name='warehouse_id';
