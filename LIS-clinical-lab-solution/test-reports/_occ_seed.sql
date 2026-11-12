-- OCC 演示种子数据（依据 occ-module-design.md 3.5）
INSERT INTO lis_qc_occ_event
(occ_no, project_name, group_name, trigger_rules, found_at, found_by, status, error_type, qc_values, target_value, sd, root_cause_category, root_cause_desc, material_snapshot, result_impact, prevention, current_step, create_time, update_time)
VALUES
('OCC-20260827-001','ALT 丙氨酸转氨酶','临床生化','2₂s','2026-08-27 09:12','张检验师','PENDING','SYSTEM','45.8,44.2',42.6,1.05,'','待排查','生化复合质控 L1 20260410','失控期间结果未发放','复溶SOP修订与培训',1,now(),now()),
('OCC-20260827-002','TSH 促甲状腺激素','免疫学','10x̄','2026-08-27 08:40','李检验师','INVESTIGATING','SYSTEM','2.5,2.4,2.5,2.6',2.18,0.09,'','试剂批号变更','免疫复合质控 L1','已评估','更换试剂批次并验证',4,now(),now()),
('OCC-20260826-001','WBC 白细胞计数','血液学','1₃s','2026-08-26 16:55','王检验师','CORRECTED','RANDOM','8.1,8.2',7.4,0.22,'仪器','比色杯污染','血液学质控 L1','已评估','清洗比色杯并复查',6,now(),now()),
('OCC-20260824-002','GLU 葡萄糖','临床生化','R₄s','2026-08-24 10:30','赵检验师','CLOSED','RANDOM','6.0,5.1',5.62,0.14,'质控品','质控品污染（瓶间差）','生化复合质控 L1','已评估','更换质控品批次',7,now(),now()),
('OCC-20260820-001','AFP 甲胎蛋白','免疫学','4₁s','2026-08-20 11:20','孙检验师','CLOSED','SYSTEM','13.5,13.6,13.8',12.4,0.6,'校准品','校准品更换','免疫复合质控 L2','已评估','校准后验证通过',7,now(),now());

-- 每个事件初始化七步法步骤（checklist用默认空项, 简单起见用空数组占位）
INSERT INTO lis_qc_occ_step (occ_id, step_no, name, status, checklist, operator, operated_at, conclusion)
SELECT e.id, s.n,
       (ARRAY['紧急暂停','全面回顾','保留现场','原因排查','纠正措施','验证在控','记录上报'])[s.n],
       CASE WHEN s.n <= e.current_step THEN CASE WHEN s.n = e.current_step THEN 'CURRENT' ELSE 'DONE' END ELSE 'PENDING' END,
       '[]', e.found_by, now(), ''
FROM lis_qc_occ_event e CROSS JOIN generate_series(1,7) s(n);

-- 每个事件初始化三级审批
INSERT INTO lis_qc_occ_approval (occ_id, level_no, level_name, status, approver, comment, approved_at)
SELECT e.id, l.n,
       (ARRAY['专业组组长','质控负责人','科主任'])[l.n],
       CASE WHEN e.status='CLOSED' AND l.n<3 THEN 'APPROVED' WHEN e.status='CLOSED' THEN 'PENDING' ELSE 'PENDING' END,
       CASE WHEN e.status='CLOSED' AND l.n<3 THEN (ARRAY['王组长','质控负责人'])[l.n] END,
       '', CASE WHEN e.status='CLOSED' AND l.n<3 THEN now() END
FROM lis_qc_occ_event e CROSS JOIN generate_series(1,3) l(n);