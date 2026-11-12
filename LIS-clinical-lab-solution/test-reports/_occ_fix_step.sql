-- 重置全部 OCC 步骤的 checklist 为正确默认项（done:false），修复被污染/为空的种子数据
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已停止相关项目检测","done":false},{"text":"已张贴“失控待处理”标识","done":false},{"text":"已通知当班人员与专业组组长","done":false}]'::text WHERE step_no=1;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已核对质控品与批号效期","done":false},{"text":"已核对仪器状态与校准","done":false},{"text":"已核对操作流程","done":false},{"text":"已核对数据录入","done":false}]'::text WHERE step_no=2;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已保留质控数据","done":false},{"text":"已保留原瓶质控品","done":false},{"text":"已记录试剂批号与校准记录","done":false}]'::text WHERE step_no=3;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已排除质控品因素","done":false},{"text":"已排除试剂因素","done":false},{"text":"已排除校准品因素","done":false},{"text":"已排除仪器因素","done":false},{"text":"已排除环境/操作因素","done":false}]'::text WHERE step_no=4;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已更换质控品/试剂","done":false},{"text":"已规范操作流程(SOP)","done":false}]'::text WHERE step_no=5;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"新条件连续≥2批次在控","done":false},{"text":"已恢复相关项目检测","done":false}]'::text WHERE step_no=6;
UPDATE lis_qc_occ_step SET checklist = '[{"text":"已填写失控报告","done":false},{"text":"已评估既往结果影响","done":false},{"text":"已制定预防措施","done":false},{"text":"已提交复核","done":false}]'::text WHERE step_no=7;
SELECT step_no, checklist FROM lis_qc_occ_step WHERE occ_id=(SELECT id FROM lis_qc_occ_event WHERE occ_no='OCC-20260827-001') ORDER BY step_no;