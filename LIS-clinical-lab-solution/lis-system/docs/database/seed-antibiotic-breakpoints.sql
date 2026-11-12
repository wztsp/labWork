-- ============================================================
-- 抗生素折点种子数据 (CLSI M100 标准)
-- 用于支撑 CLSI 药敏判读引擎 (lis_antibiotic_breakpoint 表)
-- 幂等: 按 (antibiotic_id, bacteria_id, method, standard_version) 唯一化, 可安全重跑
-- 执行: PGPASSWORD=xxx psql -f seed-antibiotic-breakpoints.sql
-- ============================================================

-- 先清理旧数据, 保证种子幂等
DELETE FROM lis_antibiotic_breakpoint;

-- ============ 大肠埃希菌 (id=1) / 肠杆菌目 ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(2, 1, 'MIC', 'CLSI', 'M100-S33', '≤8',  '≥32', '16',  NULL, NULL, 1),  -- 氨苄西林
(2, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥17', '≤13', 1),  -- 氨苄西林
(4, 1, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',   NULL, NULL, 1),   -- 庆大霉素
(4, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥15', '≤12', 1),  -- 庆大霉素
(5, 1, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',   NULL, NULL, 1),   -- 环丙沙星
(5, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥21', '≤15', 1),  -- 环丙沙星
(7, 1, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',   NULL, NULL, 1),   -- 亚胺培南
(7, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥23', '≤19', 1),  -- 亚胺培南
(8, 1, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32',  NULL, NULL, 1),   -- 阿米卡星
(8, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥17', '≤14', 1),  -- 阿米卡星
(9, 1, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',   NULL, NULL, 1),   -- 头孢他啶
(9, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥21', '≤17', 1),  -- 头孢他啶
(10,1, 'MIC', 'CLSI', 'M100-S33', '≤2',  '≥4',  NULL,  NULL, NULL, 1),    -- 复方新诺明
(10,1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥16', '≤10', 1); -- 复方新诺明

-- ============ 肺炎克雷伯菌 (id=2) / 肠杆菌目 ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(2, 2, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(2, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤13', 1),
(4, 2, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 2, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 2, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(7, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤19', 1),
(8, 2, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 2, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(9, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤17', 1),
(10,2, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),
(10,2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥16', '≤10', 1);

-- ============ 金黄色葡萄球菌 (id=77) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(3, 77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),  -- 苯唑西林
(3, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥13', '≤10', 1), -- 苯唑西林
(4, 77, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),   -- 庆大霉素
(4, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 77, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),   -- 环丙沙星
(5, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(6, 77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥16', '4',  NULL, NULL, 1),   -- 万古霉素
(8, 77, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),   -- 阿米卡星
(8, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(10,77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),   -- 复方新诺明
(10,77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥16', '≤10', 1);

-- ============ 铜绿假单胞菌 (id=28) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(4, 28, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 28, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 28, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥8',  '4',  NULL, NULL, 1),
(7, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤14', 1),
(8, 28, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 28, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(9, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥18', '≤14', 1);

-- ============ 鲍曼不动杆菌 (id=33) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(4, 33, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 33, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 33, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥8',  '4',  NULL, NULL, 1),
(7, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤14', 1),
(8, 33, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 33, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(9, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥18', '≤14', 1);

SELECT count(*) AS seeded_breakpoints FROM lis_antibiotic_breakpoint;