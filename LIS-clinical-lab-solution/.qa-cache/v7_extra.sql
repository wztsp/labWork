\set ON_ERROR_STOP 0
\echo '=== E1: warehouse_id NULL-vs-DEFAULT asymmetry risk ==='
\echo '--- lis_reagent_stock.warehouse_id = NOT NULL DEFAULT 0 ; lis_reagent_inbound_item.warehouse_id = NULLABLE ---'
SELECT table_name, column_name, is_nullable, column_default
FROM information_schema.columns
WHERE column_name='warehouse_id' AND table_schema='public'
ORDER BY table_name;

BEGIN;
\echo '--- E1a: omitting warehouse_id (DEFAULT applies) should SUCCEED ---'
SAVEPOINT e1a;
INSERT INTO lis_reagent_stock(reagent_id, lot_no, expiry_date, quantity) VALUES (9911,'QA_E1A','2099-12-31',1);
SELECT reagent_id, lot_no, warehouse_id FROM lis_reagent_stock WHERE reagent_id=9911;
ROLLBACK TO e1a;

\echo '--- E1b: explicitly passing NULL warehouse_id should FAIL (Java null-passing hazard) ---'
SAVEPOINT e1b;
INSERT INTO lis_reagent_stock(reagent_id, lot_no, expiry_date, quantity, warehouse_id) VALUES (9912,'QA_E1B','2099-12-31',1,NULL);
\echo '--> E1b: if a not-null violation appeared, Java MUST coalesce null warehouseId to 0 before insert'
ROLLBACK TO e1b;
ROLLBACK;

\echo ''
\echo '=== E2: sequence state vs the id=0 sentinel (future collision check) ==='
SELECT 'lis_reagent_warehouse' AS tbl, last_value, is_called FROM lis_reagent_warehouse_id_seq;
BEGIN;
SAVEPOINT e2;
INSERT INTO lis_reagent_warehouse(wh_code, wh_name) VALUES ('QA_WH_NEXT','QA next warehouse');
SELECT id, wh_code FROM lis_reagent_warehouse WHERE wh_code='QA_WH_NEXT';
\echo '--> E2: new id must NOT be 0 (no collision with sentinel)'
ROLLBACK TO e2;
ROLLBACK;

\echo ''
\echo '=== E3: index inventory on the 14 new tables ==='
SELECT tablename, COUNT(*) AS idx_count
FROM pg_indexes
WHERE schemaname='public' AND tablename IN (
 'lis_reagent_category','lis_supplier','lis_reagent_warehouse','lis_reagent_budget',
 'lis_reagent_budget_record','lis_reagent_approval_node','lis_reagent_purchase_apply',
 'lis_reagent_purchase_apply_item','lis_reagent_purchase_approval','lis_reagent_purchase_order',
 'lis_reagent_purchase_order_item','lis_reagent_requisition','lis_reagent_requisition_item',
 'lis_reagent_scrap_disposal')
GROUP BY tablename ORDER BY tablename;

\echo ''
\echo '=== E4: all UNIQUE indexes created by this script ==='
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname='public' AND indexdef LIKE 'CREATE UNIQUE%'
  AND (tablename LIKE 'lis_reagent%' OR tablename='lis_supplier')
  AND indexname LIKE 'uk_%'
ORDER BY tablename, indexname;

\echo ''
\echo '=== E5: dead-code check - is section 16.1 UPDATE reachable? ==='
\echo '--- warehouse_id is NOT NULL so "WHERE warehouse_id IS NULL" can never match ---'
SELECT COUNT(*) AS rows_matching_dead_predicate FROM lis_reagent_stock WHERE warehouse_id IS NULL;

\echo ''
\echo '=== E6: safety_stock deprecation comment applied ==='
SELECT col_description('lis_reagent'::regclass, ordinal_position) AS safety_stock_comment
FROM information_schema.columns WHERE table_name='lis_reagent' AND column_name='safety_stock';

\echo ''
\echo '=== E7: min_stock migration correctness (values actually copied) ==='
SELECT id, reagent_code, min_stock, safety_stock,
       (min_stock = safety_stock) AS values_match
FROM lis_reagent ORDER BY id;
