-- 各主表完整字段 + 非空约束
\pset footer off
SELECT table_name, column_name, is_nullable, column_default, data_type
FROM information_schema.columns
WHERE table_name IN ('lis_reagent','lis_reagent_purchase_apply','lis_reagent_purchase_order',
                     'lis_reagent_inbound','lis_reagent_requisition','lis_reagent_stock_check')
ORDER BY table_name, ordinal_position;