-- ④d 回滚断言：种子 9003/9004 应仍完好（事务整体回滚，破坏性语句一条未落库）
SELECT COUNT(*) AS cnt_9003_9004 FROM lis_supplier WHERE id IN (9003,9004);
SELECT id, supplier_code, supplier_name, source_type FROM lis_supplier WHERE id IN (9003,9004) ORDER BY id;
