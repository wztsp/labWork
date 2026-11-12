\echo '=== SELF-CHECK 1: stock unique index switched to 3-col (expect only uk_lis_reagent_stock_lot_wh) ==='
SELECT indexname FROM pg_indexes WHERE tablename = 'lis_reagent_stock' AND indexname LIKE 'uk_%';

\echo '=== SELF-CHECK 2: no NULL warehouse_id in stock (expect 0) ==='
SELECT COUNT(*) AS null_warehouse_cnt FROM lis_reagent_stock WHERE warehouse_id IS NULL;

\echo '=== SELF-CHECK 3: default warehouse sentinel id=0 exists (expect 1) ==='
SELECT COUNT(*) AS sentinel_cnt FROM lis_reagent_warehouse WHERE id = 0;

\echo '=== SELF-CHECK 4: approval nodes initialized (expect 4) ==='
SELECT COUNT(*) AS node_cnt FROM lis_reagent_approval_node;

\echo '=== SELF-CHECK 5: min_stock migrated from safety_stock (expect 0) ==='
SELECT COUNT(*) AS unmigrated_cnt FROM lis_reagent WHERE min_stock IS NULL AND safety_stock IS NOT NULL;

\echo '=== SELF-CHECK 6: new table count (expect 14) ==='
SELECT COUNT(*) AS new_table_cnt FROM information_schema.tables WHERE table_name IN (
  'lis_reagent_category','lis_supplier','lis_reagent_warehouse',
  'lis_reagent_budget','lis_reagent_budget_record','lis_reagent_approval_node',
  'lis_reagent_purchase_apply','lis_reagent_purchase_apply_item','lis_reagent_purchase_approval',
  'lis_reagent_purchase_order','lis_reagent_purchase_order_item',
  'lis_reagent_requisition','lis_reagent_requisition_item','lis_reagent_scrap_disposal');

\echo '=== SELF-CHECK 7: no deleted column on reagent tables (expect 0) ==='
SELECT COUNT(*) AS deleted_col_cnt FROM information_schema.columns
 WHERE column_name = 'deleted' AND (table_name = 'lis_supplier' OR table_name LIKE 'lis_reagent%');

\echo '=== SELF-CHECK 8: CHECK constraints exist (expect 4) ==='
SELECT COUNT(*) AS ck_cnt FROM pg_constraint WHERE conname IN (
  'ck_lis_reagent_budget_amount','ck_lis_reagent_purchase_apply_item_qty',
  'ck_lis_reagent_purchase_order_item_arrived','ck_lis_reagent_requisition_item_qty');
\echo '--- detail of the 4 CHECK constraints ---'
SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conname IN (
  'ck_lis_reagent_budget_amount','ck_lis_reagent_purchase_apply_item_qty',
  'ck_lis_reagent_purchase_order_item_arrived','ck_lis_reagent_requisition_item_qty') ORDER BY conname;

\echo '=== SELF-CHECK 24 (sec.24 dup reagent_code detection, expect 0 rows) ==='
SELECT reagent_code, COUNT(*) AS cnt FROM lis_reagent GROUP BY reagent_code HAVING COUNT(*) > 1;

\echo '=== ENCODING SANITY: stored Chinese COMMENTs must not be mojibake ==='
SELECT c.relname AS table_name, obj_description(c.oid) AS table_comment
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r'
  AND c.relname IN ('lis_reagent_category','lis_supplier','lis_reagent_warehouse','lis_reagent_budget','lis_reagent_scrap_disposal')
ORDER BY c.relname;

\echo '=== SEED DATA: warehouse sentinel row ==='
SELECT id, wh_code, wh_name, parent_id, path, wh_type, status FROM lis_reagent_warehouse ORDER BY id;

\echo '=== SEED DATA: approval nodes ==='
SELECT id, biz_type, node_level, node_name, trigger_cond, approver_role_code, status FROM lis_reagent_approval_node ORDER BY biz_type, node_level;

\echo '=== SEED DATA: reagent categories ==='
SELECT id, category_code, category_name, parent_id, path, level_no, hazard_category, sort_order, status FROM lis_reagent_category ORDER BY sort_order;

\echo '=== SEED DATA: suppliers migrated from legacy strings ==='
SELECT COUNT(*) AS supplier_cnt FROM lis_supplier;
\echo '--- legacy supplier strings present in source tables (drives migration coverage) ---'
SELECT 'lis_reagent.supplier' AS src, COUNT(*) AS non_empty FROM lis_reagent WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
UNION ALL
SELECT 'lis_reagent_inbound.supplier', COUNT(*) FROM lis_reagent_inbound WHERE supplier IS NOT NULL AND TRIM(supplier) <> '';
