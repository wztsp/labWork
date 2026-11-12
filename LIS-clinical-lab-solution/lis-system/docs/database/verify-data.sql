-- 验证测试数据和基础数据
SELECT 'sys_role' AS table_name, COUNT(*) AS row_count FROM sys_role
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'sys_permission', COUNT(*) FROM sys_permission
UNION ALL SELECT 'lis_test_item', COUNT(*) FROM lis_test_item
UNION ALL SELECT 'lis_reference_range', COUNT(*) FROM lis_reference_range
UNION ALL SELECT 'lis_patient', COUNT(*) FROM lis_patient
UNION ALL SELECT 'lis_visit', COUNT(*) FROM lis_visit
UNION ALL SELECT 'lis_lab_order', COUNT(*) FROM lis_lab_order
UNION ALL SELECT 'lis_order_item', COUNT(*) FROM lis_order_item
UNION ALL SELECT 'lis_sample', COUNT(*) FROM lis_sample
UNION ALL SELECT 'lis_result', COUNT(*) FROM lis_result
UNION ALL SELECT 'lis_report', COUNT(*) FROM lis_report
UNION ALL SELECT 'lis_instrument', COUNT(*) FROM lis_instrument
UNION ALL SELECT 'lis_instrument_item_map', COUNT(*) FROM lis_instrument_item_map
UNION ALL SELECT 'lis_maintenance_template', COUNT(*) FROM lis_maintenance_template
UNION ALL SELECT 'lis_reagent', COUNT(*) FROM lis_reagent
UNION ALL SELECT 'lis_reagent_stock', COUNT(*) FROM lis_reagent_stock
UNION ALL SELECT 'lis_antibiotic', COUNT(*) FROM lis_antibiotic
UNION ALL SELECT 'lis_resistance_rule', COUNT(*) FROM lis_resistance_rule
UNION ALL SELECT 'lis_staff', COUNT(*) FROM lis_staff
ORDER BY table_name;
