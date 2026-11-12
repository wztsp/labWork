-- ④d 清理：删除种子，恢复 0 供应商
DELETE FROM lis_supplier WHERE id IN (9003,9004);
SELECT COUNT(*) AS remaining FROM lis_supplier;
