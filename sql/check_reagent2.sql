\pset footer off
\echo === 现有分类 ===
SELECT id, category_code, category_name, parent_id, path, level_no, hazard_category FROM lis_reagent_category ORDER BY id;
\echo === 现有供应商 ===
SELECT id, supplier_code, supplier_name, qual_status, status FROM lis_supplier ORDER BY id;
\echo === 现有仓库 ===
SELECT id, wh_code, wh_name, parent_id, path, wh_type FROM lis_reagent_warehouse ORDER BY id;
\echo === 现有预算 ===
SELECT id, budget_no, budget_year, dept_name, category_id, total_amount FROM lis_reagent_budget ORDER BY id;
\echo === 盘点明细表结构 ===
SELECT column_name, data_type FROM information_schema.columns WHERE table_name='lis_reagent_stock_check_item' ORDER BY ordinal_position;
\echo === 消耗/开瓶/库存 表结构字段 ===
SELECT table_name, column_name FROM information_schema.columns
WHERE table_name IN ('lis_reagent_consumption','lis_reagent_opening','lis_reagent_stock','lis_reagent_stock_record')
ORDER BY table_name, ordinal_position;