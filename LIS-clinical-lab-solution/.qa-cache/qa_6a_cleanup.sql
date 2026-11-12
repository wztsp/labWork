-- ⑥a 清理：删除种子与本次生成的草稿，恢复 0 供应商
DELETE FROM lis_supplier WHERE id = 9010 OR supplier_name='Brand New Supplier XYZ';
DELETE FROM lis_reagent WHERE id = 90002;
SELECT COUNT(*) AS remaining FROM lis_supplier;
