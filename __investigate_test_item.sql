-- SCRIPT A: 调查 试管字典 + 采集方式字典 + 检验项目空值率
set client_encoding = 'UTF8';

-- 1. 试管字典全字段
SELECT * FROM lis_test_tube ORDER BY id;

-- 2. 查找 采集方式 字典
CREATE TEMP TABLE tbls AS
SELECT table_name FROM information_schema.tables WHERE table_schema='public';

SELECT table_name FROM tbls WHERE lower(table_name) LIKE '%collect%method%' OR lower(table_name) LIKE '%method%collect%' OR lower(table_name) LIKE '%collection%' ORDER BY table_name;

-- 3. lis_collection_method 是否存在
SELECT table_name FROM tbls WHERE lower(table_name) IN ('lis_collection_method','collection_method') ORDER BY table_name;

-- 4. 字典类型名含 采集 / method
SELECT dt.dict_type_code, dt.dict_type_name, de.entry_code, de.entry_name
FROM lis_dict_type dt LEFT JOIN lis_dict_entry de ON de.dict_type_code = dt.dict_type_code
WHERE lower(dt.dict_type_code) LIKE '%collect%' OR lower(dt.dict_type_code) LIKE '%method%'
   OR dt.dict_type_name LIKE '%采集%' OR dt.dict_type_name LIKE '%采血%' OR dt.dict_type_name LIKE '%方式%' OR de.entry_name LIKE '%采集%'
ORDER BY dt.dict_type_code, de.sort_no NULLS LAST, de.entry_code
LIMIT 200;

-- 5. 检验项目表: 统计 tube_id / collection_method_id 空值率 + 按标本类型, category 分组空值数量
SELECT '总体' AS scope,
  COUNT(*) AS total,
  COUNT(CASE WHEN tube_id IS NULL THEN 1 END) AS tube_null,
  COUNT(CASE WHEN collection_method_id IS NULL THEN 1 END) AS cm_null
FROM lis_test_item
UNION ALL
SELECT 'specimen_type=' || COALESCE(specimen_type,'(null)'),
  COUNT(*),
  COUNT(CASE WHEN tube_id IS NULL THEN 1 END),
  COUNT(CASE WHEN collection_method_id IS NULL THEN 1 END)
FROM lis_test_item
GROUP BY specimen_type
UNION ALL
SELECT 'category=' || COALESCE(category,'(null)'),
  COUNT(*),
  COUNT(CASE WHEN tube_id IS NULL THEN 1 END),
  COUNT(CASE WHEN collection_method_id IS NULL THEN 1 END)
FROM lis_test_item
GROUP BY category
ORDER BY scope;

-- 6. 列出部分项目 (前60条)
SELECT id, item_code, item_name, category, specimen_type, tube_id, collection_method_id
FROM lis_test_item
ORDER BY category, specimen_type, id
LIMIT 80;
