-- =====================================================================
-- 10-antibiotic-template-seed.sql
-- 抗生素模板 内置种子数据：6 套系统内置模板 + 模板-细菌绑定 + 模板抗生素明细
-- 依赖 09-antibiotic-tables.sql 已执行；细菌/抗生素字典取 02/03。
-- 幂等 PostgreSQL：模板按 code、明细按 (template,antibiotic) 唯一键 ON CONFLICT。
-- =====================================================================

-- ---------- 1. 模板主表(系统内置) ----------
INSERT INTO lis_antibiotic_template
    (code, name, template_category, default_method, description, status, is_system, sort_no)
VALUES
    ('ABX_GP_STAPH',   'G+葡萄球菌常规药敏板',    'G+', 'MIC',
     '葡萄球菌属(含凝固酶阴性)常规药敏；含OXA用于MRSA判读', 1, 1, 1),
    ('ABX_GP_STREP',   'G+链球菌药敏板',          'G+', 'MIC',
     '化脓/无乳/肺炎/草绿色链球菌常规药敏', 1, 1, 2),
    ('ABX_GP_ENTERO',  'G+肠球菌药敏板',          'G+', 'MIC',
     '粪肠球菌/屎肠球菌常规药敏，含高浓度氨基糖苷(HLAR)判读参考', 1, 1, 3),
    ('ABX_GN_ENTERO',  '肠杆菌科常规药敏板',      'G-', 'MIC',
     '肠杆菌科(含ESBL/碳青霉烯)常规药敏', 1, 1, 4),
    ('ABX_GN_NONFER',  '非发酵菌常规药敏板',      'G-', 'MIC',
     '铜绿假单胞/不动杆菌/嗜麦芽窄食单胞菌等非发酵菌常规药敏', 1, 1, 5),
    ('ABX_GN_HAEMO',   '苛养菌(G-嗜血/奈瑟)药敏板', 'G-', 'MIC',
     '流感嗜血杆菌/淋球菌等苛养菌常规药敏(HTM/GC琼脂)', 1, 1, 6)
ON CONFLICT (code) DO NOTHING;

-- ---------- 2. 模板-细菌绑定 ----------
INSERT INTO lis_antibiotic_template_bacteria (template_id, bacteria_id)
SELECT t.id, b.id
FROM lis_antibiotic_template t
JOIN (VALUES
    -- 葡萄球菌板：葡萄球菌属(含凝固酶阴性)
    ('ABX_GP_STAPH','SAU'),('ABX_GP_STAPH','SEP'),('ABX_GP_STAPH','SHA'),
    ('ABX_GP_STAPH','SHO'),('ABX_GP_STAPH','SSA'),('ABX_GP_STAPH','SCN'),
    -- 链球菌板
    ('ABX_GP_STREP','SPY'),('ABX_GP_STREP','SAG'),('ABX_GP_STREP','SPN'),
    ('ABX_GP_STREP','SVI'),('ABX_GP_STREP','SBO'),
    -- 肠球菌板
    ('ABX_GP_ENTERO','EFA'),('ABX_GP_ENTERO','EFM'),
    -- 肠杆菌科板
    ('ABX_GN_ENTERO','ECO'),('ABX_GN_ENTERO','KPN'),('ABX_GN_ENTERO','KOX'),
    ('ABX_GN_ENTERO','ECL'),('ABX_GN_ENTERO','PMI'),('ABX_GN_ENTERO','MOR'),
    ('ABX_GN_ENTERO','SMA'),('ABX_GN_ENTERO','SHF'),('ABX_GN_ENTERO','STM'),
    ('ABX_GN_ENTERO','CFF'),
    -- 非发酵菌板
    ('ABX_GN_NONFER','PAE'),('ABX_GN_NONFER','ABA'),('ABX_GN_NONFER','SMT'),
    ('ABX_GN_NONFER','BCE'),('ABX_GN_NONFER','AXY'),('ABX_GN_NONFER','MCA'),
    -- 苛养菌板
    ('ABX_GN_HAEMO','HIN'),('ABX_GN_HAEMO','HPA'),
    ('ABX_GN_HAEMO','NGO'),('ABX_GN_HAEMO','NME')
) AS v(tpl_code, bac_code) ON t.code = v.tpl_code
JOIN lis_bacteria b ON b.bacteria_code = v.bac_code
ON CONFLICT (template_id, bacteria_id) DO NOTHING;

-- ---------- 3. 模板抗生素明细 ----------
INSERT INTO lis_antibiotic_template_item (template_id, antibiotic_id, default_method, sort_no)
SELECT t.id, ab.id, v.method, v.sort_no
FROM lis_antibiotic_template t
JOIN (VALUES
    -- 葡萄球菌板(含耐甲氧西林判读)
    ('ABX_GP_STAPH','OXA','MIC',1),('ABX_GP_STAPH','PEN','MIC',2),
    ('ABX_GP_STAPH','CFZ','MIC',3),('ABX_GP_STAPH','CIP','MIC',4),
    ('ABX_GP_STAPH','LVX','MIC',5),('ABX_GP_STAPH','GEN','MIC',6),
    ('ABX_GP_STAPH','AMK','MIC',7),('ABX_GP_STAPH','ERY','MIC',8),
    ('ABX_GP_STAPH','CLI','MIC',9),('ABX_GP_STAPH','VAN','MIC',10),
    ('ABX_GP_STAPH','TEC','MIC',11),('ABX_GP_STAPH','DAP','MIC',12),
    ('ABX_GP_STAPH','LZD','MIC',13),('ABX_GP_STAPH','TET','MIC',14),
    ('ABX_GP_STAPH','DOX','MIC',15),('ABX_GP_STAPH','MIN','MIC',16),
    ('ABX_GP_STAPH','SXT','MIC',17),('ABX_GP_STAPH','RIF','MIC',18),
    ('ABX_GP_STAPH','CHL','MIC',19),('ABX_GP_STAPH','FOS','MIC',20),
    -- 链球菌板
    ('ABX_GP_STREP','PEN','MIC',1),('ABX_GP_STREP','AMP','MIC',2),
    ('ABX_GP_STREP','AMX','MIC',3),('ABX_GP_STREP','CXM','MIC',4),
    ('ABX_GP_STREP','CRO','MIC',5),('ABX_GP_STREP','CTX','MIC',6),
    ('ABX_GP_STREP','FEP','MIC',7),('ABX_GP_STREP','ERY','MIC',8),
    ('ABX_GP_STREP','CLR','MIC',9),('ABX_GP_STREP','AZM','MIC',10),
    ('ABX_GP_STREP','CLI','MIC',11),('ABX_GP_STREP','VAN','MIC',12),
    ('ABX_GP_STREP','LZD','MIC',13),('ABX_GP_STREP','DAP','MIC',14),
    ('ABX_GP_STREP','LVX','MIC',15),('ABX_GP_STREP','MXF','MIC',16),
    ('ABX_GP_STREP','TET','MIC',17),('ABX_GP_STREP','CHL','MIC',18),
    ('ABX_GP_STREP','SXT','MIC',19),
    -- 肠球菌板(含 HLAR 参考)
    ('ABX_GP_ENTERO','PEN','MIC',1),('ABX_GP_ENTERO','AMP','MIC',2),
    ('ABX_GP_ENTERO','VAN','MIC',3),('ABX_GP_ENTERO','TEC','MIC',4),
    ('ABX_GP_ENTERO','LZD','MIC',5),('ABX_GP_ENTERO','DAP','MIC',6),
    ('ABX_GP_ENTERO','QDA','MIC',7),('ABX_GP_ENTERO','CIP','MIC',8),
    ('ABX_GP_ENTERO','NOR','MIC',9),('ABX_GP_ENTERO','GEN','MIC',10),
    ('ABX_GP_ENTERO','NIT','MIC',11),('ABX_GP_ENTERO','TET','MIC',12),
    ('ABX_GP_ENTERO','CHL','MIC',13),
    -- 肠杆菌科板
    ('ABX_GN_ENTERO','AMP','MIC',1),('ABX_GN_ENTERO','AMC','MIC',2),
    ('ABX_GN_ENTERO','SAM','MIC',3),('ABX_GN_ENTERO','TZP','MIC',4),
    ('ABX_GN_ENTERO','CFZ','MIC',5),('ABX_GN_ENTERO','CXM','MIC',6),
    ('ABX_GN_ENTERO','FOX','MIC',7),('ABX_GN_ENTERO','CTX','MIC',8),
    ('ABX_GN_ENTERO','CRO','MIC',9),('ABX_GN_ENTERO','CAZ','MIC',10),
    ('ABX_GN_ENTERO','CFP','MIC',11),('ABX_GN_ENTERO','SCF','MIC',12),
    ('ABX_GN_ENTERO','FEP','MIC',13),('ABX_GN_ENTERO','ATM','MIC',14),
    ('ABX_GN_ENTERO','IPM','MIC',15),('ABX_GN_ENTERO','MEM','MIC',16),
    ('ABX_GN_ENTERO','ETP','MIC',17),('ABX_GN_ENTERO','GEN','MIC',18),
    ('ABX_GN_ENTERO','TOB','MIC',19),('ABX_GN_ENTERO','AMK','MIC',20),
    ('ABX_GN_ENTERO','CIP','MIC',21),('ABX_GN_ENTERO','LVX','MIC',22),
    ('ABX_GN_ENTERO','MXF','MIC',23),('ABX_GN_ENTERO','TET','MIC',24),
    ('ABX_GN_ENTERO','MIN','MIC',25),('ABX_GN_ENTERO','TGC','MIC',26),
    ('ABX_GN_ENTERO','SXT','MIC',27),('ABX_GN_ENTERO','CHL','MIC',28),
    ('ABX_GN_ENTERO','CST','MIC',29),
    -- 非发酵菌板
    ('ABX_GN_NONFER','PIP','MIC',1),('ABX_GN_NONFER','TZP','MIC',2),
    ('ABX_GN_NONFER','TCC','MIC',3),('ABX_GN_NONFER','CAZ','MIC',4),
    ('ABX_GN_NONFER','CFP','MIC',5),('ABX_GN_NONFER','SCF','MIC',6),
    ('ABX_GN_NONFER','FEP','MIC',7),('ABX_GN_NONFER','ATM','MIC',8),
    ('ABX_GN_NONFER','IPM','MIC',9),('ABX_GN_NONFER','MEM','MIC',10),
    ('ABX_GN_NONFER','GEN','MIC',11),('ABX_GN_NONFER','TOB','MIC',12),
    ('ABX_GN_NONFER','AMK','MIC',13),('ABX_GN_NONFER','CIP','MIC',14),
    ('ABX_GN_NONFER','LVX','MIC',15),('ABX_GN_NONFER','MXF','MIC',16),
    ('ABX_GN_NONFER','MIN','MIC',17),('ABX_GN_NONFER','TGC','MIC',18),
    ('ABX_GN_NONFER','SXT','MIC',19),('ABX_GN_NONFER','CST','MIC',20),
    ('ABX_GN_NONFER','POL','MIC',21),('ABX_GN_NONFER','TET','MIC',22),
    -- 苛养菌板
    ('ABX_GN_HAEMO','AMP','MIC',1),('ABX_GN_HAEMO','AMC','MIC',2),
    ('ABX_GN_HAEMO','SAM','MIC',3),('ABX_GN_HAEMO','CXM','MIC',4),
    ('ABX_GN_HAEMO','CTX','MIC',5),('ABX_GN_HAEMO','CRO','MIC',6),
    ('ABX_GN_HAEMO','CAZ','MIC',7),('ABX_GN_HAEMO','FEP','MIC',8),
    ('ABX_GN_HAEMO','AZM','MIC',9),('ABX_GN_HAEMO','CLR','MIC',10),
    ('ABX_GN_HAEMO','LVX','MIC',11),('ABX_GN_HAEMO','MXF','MIC',12),
    ('ABX_GN_HAEMO','CIP','MIC',13),('ABX_GN_HAEMO','TET','MIC',14),
    ('ABX_GN_HAEMO','CHL','MIC',15),('ABX_GN_HAEMO','SXT','MIC',16),
    ('ABX_GN_HAEMO','MEM','MIC',17)
) AS v(tpl_code, ab_code, method, sort_no) ON t.code = v.tpl_code
JOIN lis_antibiotic ab ON ab.antibiotic_code = v.ab_code
ON CONFLICT (template_id, antibiotic_id) DO NOTHING;