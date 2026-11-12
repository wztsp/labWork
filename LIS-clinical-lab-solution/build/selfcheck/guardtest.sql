-- 前置守卫 (22.0.1) 判定验算。模拟「唯一索引尚未建立的存量库」：
-- 在事务内 DROP INDEX -> 造重名 -> 跑守卫逻辑 -> ROLLBACK（索引与数据均还原，库无污染）。
\set ON_ERROR_STOP 0

-- === 场景 4a: 1 人工 + 1 草稿，规范名相同 -> 新守卫【不应】触发，旧条件【会误杀】 ===
BEGIN;
DROP INDEX uk_lis_supplier_name_norm;
INSERT INTO lis_supplier(supplier_code,supplier_name,qual_status,source_type,status)
VALUES ('T_M1','  Roche  Diagnostics ',1,1,1),('T_D1','roche diagnostics',0,2,1);
SELECT '4a_NEW_cond(source_type<>2)' AS cond, COUNT(*) AS dup_groups
  FROM (SELECT 1 FROM lis_supplier WHERE source_type<>2
        GROUP BY fn_supplier_name_norm(supplier_name) HAVING COUNT(*)>1) t;
SELECT '4a_OLD_cond(all rows)' AS cond, COUNT(*) AS dup_groups
  FROM (SELECT 1 FROM lis_supplier
        GROUP BY fn_supplier_name_norm(supplier_name) HAVING COUNT(*)>1) t;
-- 验证 22.1 能自动归并该场景：赢家应是人工那条(T_M1)
SELECT '4a_winner_is_manual' AS chk, (SELECT supplier_code FROM lis_supplier WHERE id = m.keep_id) AS keep_code
  FROM (SELECT DISTINCT FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE (SELECT supplier_code FROM lis_supplier WHERE id = m.keep_id) LIKE 'T\_%';
ROLLBACK;

-- === 场景 4d: 2 条人工，规范名相同 -> 守卫【必须】触发并 RAISE EXCEPTION ===
BEGIN;
DROP INDEX uk_lis_supplier_name_norm;
INSERT INTO lis_supplier(supplier_code,supplier_name,qual_status,source_type,status)
VALUES ('T_M2','ROCHE',1,1,1),('T_M3','  roche ',1,1,1);
SELECT '4d_NEW_cond(source_type<>2)' AS cond, COUNT(*) AS dup_groups
  FROM (SELECT 1 FROM lis_supplier WHERE source_type<>2
        GROUP BY fn_supplier_name_norm(supplier_name) HAVING COUNT(*)>1) t;
-- 原样跑 22.0.1 的 DO 块，期望 RAISE EXCEPTION
DO $$
DECLARE
    v_dup TEXT;
BEGIN
    SELECT string_agg(t.norm || '(' || t.cnt || '条)', '; ')
      INTO v_dup
      FROM (SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) AS cnt
              FROM lis_supplier
             WHERE source_type <> 2
             GROUP BY fn_supplier_name_norm(supplier_name)
            HAVING COUNT(*) > 1) t;
    IF v_dup IS NOT NULL THEN
        RAISE EXCEPTION '存在人工录入的重名供应商，需先在页面人工归并后再执行本脚本：%', v_dup;
    END IF;
END $$;
ROLLBACK;

-- === 收尾：确认库未被污染，索引仍在 ===
SELECT COUNT(*) AS supplier_rows FROM lis_supplier;
SELECT COUNT(*) AS uk_index_still_present FROM pg_indexes
 WHERE tablename='lis_supplier' AND indexname='uk_lis_supplier_name_norm';
