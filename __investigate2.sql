set client_encoding = 'UTF8';

SELECT * FROM lis_collection_method ORDER BY id;

-- 项目中已有设置的分布
SELECT tube_id, collection_method_id, COUNT(*) cnt
FROM lis_test_item
WHERE tube_id IS NOT NULL OR collection_method_id IS NOT NULL
GROUP BY tube_id, collection_method_id
ORDER BY cnt DESC;

-- 项目总数
SELECT COUNT(*) AS total_items,
  COUNT(DISTINCT specimen_type) specimen_type_cnt,
  COUNT(DISTINCT category) category_cnt
FROM lis_test_item;

-- 全部 category + specimen_type 组合列表
SELECT category, specimen_type, COUNT(*) cnt
FROM lis_test_item
GROUP BY category, specimen_type
ORDER BY category, specimen_type;
