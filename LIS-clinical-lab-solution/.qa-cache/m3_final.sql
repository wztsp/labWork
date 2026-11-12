\pset pager off
\echo '=== FINAL: DB unchanged & clean ==='
SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') AS tables,
       (SELECT COUNT(*) FROM lis_reagent)             AS reagents,
       (SELECT COUNT(*) FROM lis_reagent_inbound)     AS inbounds,
       (SELECT COUNT(*) FROM lis_supplier)            AS suppliers,
       (SELECT COUNT(*) FROM lis_reagent_stock)       AS stock,
       (SELECT COUNT(*) FROM lis_reagent_warehouse)   AS warehouses,
       (SELECT COUNT(*) FROM lis_reagent_category)    AS categories,
       (SELECT COUNT(*) FROM lis_reagent_approval_node) AS nodes;

\echo ''
\echo '=== FINAL: no QA residue in any touched table ==='
SELECT (SELECT COUNT(*) FROM lis_reagent WHERE reagent_code LIKE 'QA_%')          AS qa_reagents,
       (SELECT COUNT(*) FROM lis_reagent_inbound WHERE inbound_no LIKE 'QA_%')    AS qa_inbounds,
       (SELECT COUNT(*) FROM lis_supplier WHERE supplier_code LIKE 'QA_%'
                                             OR supplier_code LIKE 'SUP_MIG_%')   AS qa_suppliers,
       (SELECT COUNT(*) FROM lis_reagent WHERE supplier IS NOT NULL)              AS reagents_with_supplier_str,
       (SELECT COUNT(*) FROM lis_reagent WHERE supplier_id IS NOT NULL)           AS reagents_with_supplier_id;

\echo ''
\echo '=== FINAL: real lis_reagent_stock.warehouse_id is in the GOOD state (not legacy) ==='
SELECT column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_name='lis_reagent_stock' AND column_name='warehouse_id';

\echo ''
\echo '=== FINAL: stock unique index intact ==='
SELECT indexname, indexdef FROM pg_indexes
WHERE tablename='lis_reagent_stock' AND indexname LIKE 'uk_%';
