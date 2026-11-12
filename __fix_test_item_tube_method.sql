-- 为 lis_test_item 全部项目补齐 tube_id + collection_method_id
-- 规则原则:
--   1. 按 (category, specimen_type) 组合匹配最合适的试管 (保留已设置的 tube_id 不覆盖)
--   2. 采集方式: 尿液/粪便类 -> 1005 病人自采; 其他 -> 1001 门诊采集
--   3. 对已有 tube_id 的项目不再覆盖 (保留历史配置); 对已有 collection_method_id 项目也不覆盖
set client_encoding = 'UTF8';

-- ===== 规则 1: 试管 (tube_id) 映射 =====
-- 免疫-血清  -> 红头管 1 (无促凝/血清学) 生化免疫共用
-- 免疫-血浆  -> 绿头管 5 (肝素钠/血浆)
-- 生化-血清  -> 黄头管 2 (分离胶+促凝,生化首选);  GLU(血糖) 特殊 -> 灰头管 7 (氟化钠)
-- 生化-全血  -> 绿头管 5 (肝素锂/钠, 血气等)
-- 血常规-全血 -> 紫头管 3 (EDTA-K2)
-- 凝血-血浆   -> 蓝头管 4 (枸橼酸钠)
-- 尿常规-尿液 -> 尿杯 8
-- 尿液-尿液   -> 尿杯 8
-- 其他-粪便   -> 粪盒 9
-- 微生物:
--     血液    -> 培养管 10 (血培养瓶)
--     分泌物  -> 培养管 10
--     痰液    -> 培养管 10
--     脑脊液  -> 培养管 10
--     胸腹水  -> 培养管 10
--     尿液    -> 尿杯 8 (清洁中段尿)
--     粪便    -> 粪盒 9
-- null/未知标本类型 (AFP) 属于免疫/血清 -> 红头管 1
-- 注意: 血糖 (GLU) 若为生化-血清, 应强制灰头管 7 (草酸钾/氟化钠, 防止糖酵解)

-- 1. 先将 NULL specimen_type 归类 (AFP 属于 免疫-血清)
UPDATE lis_test_item
SET specimen_type = CASE
      WHEN specimen_type IS NOT NULL THEN specimen_type
      WHEN category = '免疫' AND item_code IN ('AFP') THEN '血清'
      ELSE specimen_type END
WHERE specimen_type IS NULL;

-- 2. 写入 tube_id (仅 NULL)
UPDATE lis_test_item SET tube_id = (CASE
  -- 特殊: 血糖类 灰头管
  WHEN item_code IN ('GLU','GLU2','FBG','PBG','OGTT_GLU','RBS','尿糖') OR item_name LIKE '%血糖%' OR item_name LIKE '%葡萄糖%' THEN 7
  -- 特殊: 糖化血红蛋白 紫头管 (EDTA全血)
  WHEN item_code IN ('HBA1C','GHb','HbA1c') OR item_name LIKE '%糖化血红蛋白%' OR item_name LIKE '%糖化%' THEN 3
  -- 特殊: 血沉 黑头管
  WHEN item_code IN ('ESR') OR item_name LIKE '%血沉%' THEN 6
  -- 特殊: 血型 紫头管
  WHEN item_name LIKE '%血型%' OR item_code IN ('ABO','RH','BLOOD_TYPE') THEN 3
  -- category + specimen_type 分类
  WHEN category = '血常规' AND specimen_type = '全血' THEN 3
  WHEN category = '凝血'   AND specimen_type = '血浆' THEN 4
  WHEN category = '尿常规' AND specimen_type = '尿液' THEN 8
  WHEN category = '尿液'   AND specimen_type = '尿液' THEN 8
  WHEN category = '其他'   AND specimen_type = '粪便' THEN 9
  WHEN category = '生化'   AND specimen_type = '血清'  THEN 2
  WHEN category = '生化'   AND specimen_type = '全血'  THEN 5
  WHEN category = '免疫'   AND specimen_type = '血清'  THEN 1
  WHEN category = '免疫'   AND specimen_type = '血浆'  THEN 5
  -- 微生物分类
  WHEN category = '微生物' AND specimen_type IN ('血液','分泌物','痰液','脑脊液','胸腹水') THEN 10
  WHEN category = '微生物' AND specimen_type = '尿液' THEN 8
  WHEN category = '微生物' AND specimen_type = '粪便' THEN 9
  -- 其余: 血液类/全血 -> 紫头管 3, 血浆 -> 绿头管 5, 血清 -> 黄头管 2, 其余(如分泌物标本痰液等) -> 培养管 10
  WHEN specimen_type IN ('全血','血液') THEN 3
  WHEN specimen_type = '血浆' THEN 5
  WHEN specimen_type = '血清' THEN 2
  WHEN specimen_type = '尿液' THEN 8
  WHEN specimen_type = '粪便' THEN 9
  WHEN specimen_type IN ('分泌物','痰液','脑脊液','胸腹水') THEN 10
  ELSE 1  -- 兜底: 红头管
END)::bigint
WHERE tube_id IS NULL;

-- ===== 规则 2: 采集方式 (collection_method_id) 映射 =====
-- 尿液/粪便 -> 1005 病人自采; 微生物的 血液/分泌物/痰液/脑脊液/胸腹水 -> 1001 门诊采集; 其他 -> 1001 门诊采集
-- 特别: 尿常规组合/尿液组合/粪便/尿沉渣/便隐血 都是患者自采
UPDATE lis_test_item SET collection_method_id = (CASE
  WHEN category IN ('尿常规','尿液','其他') AND specimen_type IN ('尿液','粪便') THEN 1005
  WHEN specimen_type IN ('尿液','粪便') THEN 1005
  WHEN category = '微生物' AND specimen_type IN ('尿液','粪便') THEN 1005
  ELSE 1001  -- 默认门诊采集
END)::bigint
WHERE collection_method_id IS NULL;

-- ===== 结果验证 =====
SELECT '总体' scope, COUNT(*) total,
  COUNT(CASE WHEN tube_id IS NULL THEN 1 END) tube_null_after,
  COUNT(CASE WHEN collection_method_id IS NULL THEN 1 END) cm_null_after
FROM lis_test_item
UNION ALL
SELECT 'category=' || COALESCE(category,'?'), COUNT(*),
  COUNT(CASE WHEN tube_id IS NULL THEN 1 END),
  COUNT(CASE WHEN collection_method_id IS NULL THEN 1 END)
FROM lis_test_item
GROUP BY category
ORDER BY scope;

-- 各类样本的 tube/method 分布抽样
SELECT category, specimen_type, tube_id, collection_method_id, COUNT(*) cnt
FROM lis_test_item
GROUP BY category, specimen_type, tube_id, collection_method_id
HAVING COUNT(*) > 0
ORDER BY category, specimen_type, tube_id, collection_method_id
LIMIT 40;

-- 抽样 20 条项目 (覆盖各类)
SELECT item_code, item_name, category, specimen_type, tube_id,
  (SELECT tube_name FROM lis_test_tube t WHERE t.id = i.tube_id) AS tube_name,
  collection_method_id,
  (SELECT method_name FROM lis_collection_method m WHERE m.id = i.collection_method_id) AS method_name
FROM lis_test_item i
ORDER BY category, specimen_type, id
LIMIT 25;
