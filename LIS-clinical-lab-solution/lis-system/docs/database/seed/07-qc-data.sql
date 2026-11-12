-- ============================================================
-- 07-qc-data.sql   原 QCDataInitializer
-- 质控批号与质控记录示例数据。演示数据, 非生产必需。
-- 幂等保护: 仅当 lis_qc_lot 为空时创建批号; 记录按批号补充。
-- ============================================================
-- 确保基础仪器与检验项目存在 (仅当为空)
INSERT INTO lis_instrument (instrument_code, instrument_name, model, manufacturer, protocol, status) VALUES
('INST-QC-001','血细胞分析仪-QC','BC-5390','迈瑞','ASTM',1),
('INST-QC-002','生化分析仪-QC','BS-480','迈瑞','ASTM',1)
WHERE NOT EXISTS (SELECT 1 FROM lis_instrument);

INSERT INTO lis_test_item (item_code, item_name, category, unit, status) VALUES
('WBC','白细胞计数','血常规','10^9/L',1),('RBC','红细胞计数','血常规','10^12/L',1),
('HGB','血红蛋白','血常规','g/L',1),('PLT','血小板计数','血常规','10^9/L',1),
('GLU','葡萄糖','生化','mmol/L',1),('ALT','谷丙转氨酶','生化','U/L',1),
('TP','总蛋白','生化','g/L',1)
WHERE NOT EXISTS (SELECT 1 FROM lis_test_item);

-- 质控批号 (仅在 lis_qc_lot 为空时插入; 需先有仪器/项目才能解析)
INSERT INTO lis_qc_lot (lot_no, test_item_id, instrument_id, target_value, sd, cv, level, start_date, end_date, status, deleted)
SELECT v.lot_no, ti.id, i.id, v.target_value, v.sd,
       round((v.sd*100.0/v.target_value)::numeric,2), v.level, v.start_date, v.end_date, 1, 0
FROM (VALUES
 ('QC2026W001','WBC','血细胞分析仪-QC',6.50,0.20,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026W002','WBC','血细胞分析仪-QC',3.50,0.15,1, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026W003','WBC','血细胞分析仪-QC',15.00,0.50,3, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026R001','RBC','血细胞分析仪-QC',4.50,0.15,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026H001','HGB','血细胞分析仪-QC',130.00,3.00,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026P001','PLT','血细胞分析仪-QC',200.00,8.00,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026G001','GLU','生化分析仪-QC',5.00,0.15,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026A001','ALT','生化分析仪-QC',40.00,2.00,2, DATE '2026-01-01', DATE '2026-12-31'),
 ('QC2026T001','TP','生化分析仪-QC',65.00,1.50,2, DATE '2026-01-01', DATE '2026-12-31')
) AS v(lot_no, item_code, instrument_name, target_value, sd, level, start_date, end_date)
JOIN lis_test_item ti ON ti.item_code = v.item_code
JOIN lis_instrument i ON i.instrument_name = v.instrument_name
WHERE NOT EXISTS (SELECT 1 FROM lis_qc_lot WHERE deleted = 0);

-- 质控记录 (为尚无记录的批号补齐近 12 天, 含 3 条刻意失控点)
-- 规则: 相对批号 id 偏移 i=-11..0; i=5 → 1-3s 失控, i=2 → 2-2s 失控, i=8 → 1-2s 失控; 其余接受
INSERT INTO lis_qc_record (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, operator_id, z_score, rule_violation, qc_result, deleted, create_time)
SELECT l.id, l.test_item_id, l.instrument_id,
       round((l.target_value + l.sd * d.sd_mult)::numeric, 2),
       DATE_TRUNC('day', NOW()) + d.idx * interval '1 day',
       2,
       round((l.sd * d.sd_mult / l.sd)::numeric, 4),
       CASE d.idx WHEN 5 THEN '1-3s' WHEN 2 THEN '2-2s' WHEN 8 THEN '1-2s' ELSE NULL END,
       CASE WHEN d.idx IN (5,2,8) THEN 'REJECT' ELSE 'ACCEPT' END,
       0,
       DATE_TRUNC('day', NOW()) + d.idx * interval '1 day'
FROM lis_qc_lot l
CROSS JOIN (VALUES
  (-11, 0.0), (-10, 0.8), (-9, 0.0), (-8, 2.2), (-7, 0.0), (-6, -0.8),
  (-5, 3.5),  (-4, 0.8),  (-3, 0.0), (-2, -2.5), (-1, 0.8), ( 0, 0.0)
) d(idx, sd_mult)
WHERE l.deleted = 0 AND l.status = 1
  AND NOT EXISTS (SELECT 1 FROM lis_qc_record r WHERE r.qc_lot_id = l.id AND r.deleted = 0);