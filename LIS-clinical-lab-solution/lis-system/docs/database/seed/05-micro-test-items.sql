-- ============================================================
-- 05-micro-test-items.sql   原 MicroTestItemInitializer
-- 内置微生物检验项目 (分类=微生物)。幂等: 仅当"微生物"分类为空时灌入。
-- ============================================================
INSERT INTO lis_test_item (item_code, item_name, category, specimen_type, is_panel, status)
SELECT * FROM (VALUES
 ('MIC_GEN','一般细菌培养','微生物','血液',1),
 ('MIC_BLD','血培养','微生物','血液',1),
 ('MIC_URINE','尿液培养','微生物','尿液',1),
 ('MIC_SPUTUM','痰液培养','微生物','痰液',1),
 ('MIC_STOOL','粪便培养','微生物','粪便',1),
 ('MIC_SECR','分泌物培养','微生物','分泌物',1),
 ('MIC_CSF','脑脊液培养','微生物','脑脊液',1),
 ('MIC_FLUID','胸腹水培养','微生物','胸腹水',1),
 ('MIC_FUNGUS','真菌培养','微生物','分泌物',1),
 ('MIC_ANA','厌氧菌培养','微生物','分泌物',1),
 ('MIC_GRAM','革兰氏染色','微生物','分泌物',1),
 ('MIC_ACID','抗酸杆菌涂片','微生物','痰液',1),
 ('MIC_SENS','药敏试验','微生物','血液',1)
) AS v(item_code, item_name, category, specimen_type, is_panel, status)
WHERE NOT EXISTS (SELECT 1 FROM lis_test_item WHERE category = '微生物');