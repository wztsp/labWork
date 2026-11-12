-- ============================================================
-- 申请单增加患者生理条件字段
-- 用于与参考范围 condition_remark 匹配, 实现按生理周期/体位/采血时间/饮食状态精确匹配参考范围
-- ============================================================

BEGIN;

-- lis_lab_order 增加 patient_condition 列
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS patient_condition VARCHAR(100);
COMMENT ON COLUMN lis_lab_order.patient_condition IS '患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等, 用于匹配参考范围';

COMMIT;
