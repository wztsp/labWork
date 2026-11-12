-- 试剂模块现有数据量概览
\pset footer off
\echo === 基础数据表 ===
SELECT 'lis_reagent_category' AS tbl, COUNT(*) FROM lis_reagent_category
UNION ALL SELECT 'lis_supplier', COUNT(*) FROM lis_supplier
UNION ALL SELECT 'lis_reagent_warehouse', COUNT(*) FROM lis_reagent_warehouse
UNION ALL SELECT 'lis_reagent', COUNT(*) FROM lis_reagent
UNION ALL SELECT 'lis_reagent_budget', COUNT(*) FROM lis_reagent_budget
UNION ALL SELECT 'lis_reagent_approval_node', COUNT(*) FROM lis_reagent_approval_node;

\echo === 流程表 ===
SELECT 'lis_reagent_purchase_apply' AS tbl, COUNT(*) FROM lis_reagent_purchase_apply
UNION ALL SELECT 'lis_reagent_purchase_apply_item', COUNT(*) FROM lis_reagent_purchase_apply_item
UNION ALL SELECT 'lis_reagent_purchase_approval', COUNT(*) FROM lis_reagent_purchase_approval
UNION ALL SELECT 'lis_reagent_purchase_order', COUNT(*) FROM lis_reagent_purchase_order
UNION ALL SELECT 'lis_reagent_purchase_order_item', COUNT(*) FROM lis_reagent_purchase_order_item
UNION ALL SELECT 'lis_reagent_inbound', COUNT(*) FROM lis_reagent_inbound
UNION ALL SELECT 'lis_reagent_inbound_item', COUNT(*) FROM lis_reagent_inbound_item
UNION ALL SELECT 'lis_reagent_requisition', COUNT(*) FROM lis_reagent_requisition
UNION ALL SELECT 'lis_reagent_requisition_item', COUNT(*) FROM lis_reagent_requisition_item
UNION ALL SELECT 'lis_reagent_stock_check', COUNT(*) FROM lis_reagent_stock_check
UNION ALL SELECT 'lis_reagent_stock_check_item', COUNT(*) FROM lis_reagent_stock_check_item
UNION ALL SELECT 'lis_reagent_consumption', COUNT(*) FROM lis_reagent_consumption
UNION ALL SELECT 'lis_reagent_opening', COUNT(*) FROM lis_reagent_opening
UNION ALL SELECT 'lis_reagent_stock', COUNT(*) FROM lis_reagent_stock
UNION ALL SELECT 'lis_reagent_stock_record', COUNT(*) FROM lis_reagent_stock_record
UNION ALL SELECT 'lis_reagent_budget_record', COUNT(*) FROM lis_reagent_budget_record;

\echo === 现有试剂字典(前若干) ===
SELECT id, reagent_code, reagent_name, category_id, manufacturer, supplier, unit FROM lis_reagent ORDER BY id LIMIT 20;