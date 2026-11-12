\echo '=== [V3-1] lis_reagent : 7 new columns expected ==='
WITH want(c, expect_type) AS (VALUES
    ('supplier_id','bigint'),('hazard_flag','smallint'),('hazard_class','character varying'),
    ('msds_summary','text'),('msds_url','character varying'),
    ('storage_temp_min','numeric'),('storage_temp_max','numeric'))
SELECT w.c AS column_name,
       w.expect_type AS expected,
       COALESCE(i.data_type,'*** MISSING ***') AS actual,
       CASE WHEN i.data_type IS NULL THEN 'FAIL-MISSING'
            WHEN i.data_type = w.expect_type THEN 'OK'
            ELSE 'FAIL-TYPE' END AS verdict,
       i.numeric_precision, i.numeric_scale, i.character_maximum_length AS varchar_len
FROM want w LEFT JOIN information_schema.columns i
  ON i.table_name='lis_reagent' AND i.column_name=w.c
ORDER BY w.c;

\echo '=== [V3-2] lis_reagent_stock.warehouse_id ==='
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name='lis_reagent_stock' AND column_name='warehouse_id';

\echo '=== [V3-3] lis_reagent_inbound : order_id/order_no/supplier_id ==='
SELECT column_name, data_type, character_maximum_length AS varchar_len
FROM information_schema.columns
WHERE table_name='lis_reagent_inbound' AND column_name IN ('order_id','order_no','supplier_id')
ORDER BY column_name;

\echo '=== [V3-4] lis_reagent_inbound_item : order_item_id/warehouse_id ==='
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name='lis_reagent_inbound_item' AND column_name IN ('order_item_id','warehouse_id')
ORDER BY column_name;

\echo '=== [V3-5] lis_reagent_stock_record : dept_id/dept_name ==='
SELECT column_name, data_type, character_maximum_length AS varchar_len
FROM information_schema.columns
WHERE table_name='lis_reagent_stock_record' AND column_name IN ('dept_id','dept_name')
ORDER BY column_name;

\echo '=== [V3-6] lis_reagent_stock_check.warehouse_id (6th altered table, not in brief) ==='
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name='lis_reagent_stock_check' AND column_name='warehouse_id';

\echo '=== [V3-7] CRITICAL: FLOAT/REAL/DOUBLE audit across ALL 14 new tables + 6 altered tables ==='
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema='public'
  AND data_type IN ('double precision','real','float','money')
  AND (table_name LIKE 'lis_reagent%' OR table_name='lis_supplier')
ORDER BY table_name, column_name;

\echo '--- (0 rows above = PASS, no floating point money/qty) ---'

\echo '=== [V3-8] All money/qty/amount/price/ratio columns in the 14 NEW tables with exact types ==='
SELECT table_name, column_name, data_type,
       numeric_precision AS prec, numeric_scale AS scale
FROM information_schema.columns
WHERE table_schema='public'
  AND table_name IN ('lis_reagent_category','lis_supplier','lis_reagent_warehouse',
      'lis_reagent_budget','lis_reagent_budget_record','lis_reagent_approval_node',
      'lis_reagent_purchase_apply','lis_reagent_purchase_apply_item','lis_reagent_purchase_approval',
      'lis_reagent_purchase_order','lis_reagent_purchase_order_item',
      'lis_reagent_requisition','lis_reagent_requisition_item','lis_reagent_scrap_disposal')
  AND (column_name ~ '(amount|price|quantity|qty|score|ratio|temp_|total)')
ORDER BY table_name, column_name;
