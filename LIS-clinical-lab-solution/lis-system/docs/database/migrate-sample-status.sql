-- ============================================================
-- 标本状态值迁移脚本
-- 旧状态: 0已申请 1已采集 2已核收 3检测中 4已报告 5已退费 6已作废 7已拒收
-- 新状态: 0已申请 1已采集 2已送检 3已签收 4已核收 5检测中 6已报告 7已退费 8已作废 9已拒收
--
-- 新增: 2=已送检, 3=已签收 (在已采集和已核收之间)
-- 顺延: 2→4, 3→5, 4→6, 5→7, 6→8, 7→9
--
-- 注意: 必须从高到低更新，避免值冲突
-- ============================================================

BEGIN;

-- 1. 已拒收: 7 → 9
UPDATE lis_sample SET status = 9 WHERE status = 7;

-- 2. 已作废: 6 → 8
UPDATE lis_sample SET status = 8 WHERE status = 6;

-- 3. 已退费: 5 → 7
UPDATE lis_sample SET status = 7 WHERE status = 5;

-- 4. 已报告: 4 → 6
UPDATE lis_sample SET status = 6 WHERE status = 4;

-- 5. 检测中: 3 → 5
UPDATE lis_sample SET status = 5 WHERE status = 3;

-- 6. 已核收: 2 → 4
UPDATE lis_sample SET status = 4 WHERE status = 2;

-- 7. 已采集(1)中已签收的: 1 → 3 (有签收时间但状态还是1的)
UPDATE lis_sample SET status = 3 WHERE status = 1 AND sign_off_time IS NOT NULL;

-- 8. 已采集(1)中已送检的: 1 → 2 (有送检时间但状态还是1的)
UPDATE lis_sample SET status = 2 WHERE status = 1 AND deliver_time IS NOT NULL;

-- 9. 已采集(1)未送检的: 保持1不变
-- 10. 已申请(0): 保持0不变

-- 更新字段注释
COMMENT ON COLUMN lis_sample.status IS '0已申请 1已采集 2已送检 3已签收 4已核收 5检测中 6已报告 7已退费 8已作废 9已拒收';

COMMIT;

-- 验证: 检查迁移结果
-- SELECT status, COUNT(*) FROM lis_sample GROUP BY status ORDER BY status;
