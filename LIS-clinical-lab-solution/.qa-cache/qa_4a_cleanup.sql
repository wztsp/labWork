-- ④a + ③ 清理：删除种子人工/草稿/试剂，及可能误插入的 9099
DELETE FROM lis_supplier WHERE id IN (9001,9002,9099);
DELETE FROM lis_reagent WHERE id = 90001;
SELECT COUNT(*) AS remaining FROM lis_supplier;
