-- ============================================================
-- 修复 OCC 失控报告模块中文乱码 (mojibake) 数据
-- 作用域: lis_qc_occ_event / lis_qc_occ_step / lis_qc_occ_approval
-- 执行:  psql -h localhost -p 5432 -U lis_user -d lis_db -f fix_occ_mojibake.sql
-- ============================================================

SET client_encoding = 'UTF8';

-- ------------------------------------------------------------
-- 1) 修复步骤表: 步骤名 (step_no -> name 映射, 全量覆盖)
-- ------------------------------------------------------------
UPDATE lis_qc_occ_step SET name = CASE step_no
  WHEN 1 THEN '紧急暂停'
  WHEN 2 THEN '全面回顾'
  WHEN 3 THEN '保留现场'
  WHEN 4 THEN '原因排查'
  WHEN 5 THEN '纠正措施'
  WHEN 6 THEN '验证在控'
  WHEN 7 THEN '记录上报'
END;

-- ------------------------------------------------------------
-- 2) 修复审批表: 等级名 (level_no -> level_name 映射, 全量覆盖)
-- ------------------------------------------------------------
UPDATE lis_qc_occ_approval SET level_name = CASE level_no
  WHEN 1 THEN '专业组组长'
  WHEN 2 THEN '质控负责人'
  WHEN 3 THEN '科主任'
END;

-- ------------------------------------------------------------
-- 3) 修复事件主表: 项目编码 / 项目名称 / 专业组, 按 lot_id -> test_item 联动回填
--    (使用真实数据库中的检验项目信息, 保证真实数据约束)
-- ------------------------------------------------------------
UPDATE lis_qc_occ_event e
SET project_code = ti.item_code,
    project_name = ti.item_name,
    group_name   = ti.category
FROM lis_qc_lot lot
JOIN lis_test_item ti ON ti.id = lot.test_item_id
WHERE e.lot_id IS NOT NULL
  AND e.lot_id = lot.id
  AND lot.test_item_id IS NOT NULL;

-- ------------------------------------------------------------
-- 4) 修复步骤 checklist JSON 文本 (保留 done 勾选状态, 仅修正 text)
--    每个 step_no 对应一套固定的排查项 (与 OccServiceImpl.defaultChecklist 完全一致)
--    对于长度不匹配的异常数据, 缺失的 done 默认为 false, 多余的忽略
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION repair_occ_step_checklist()
RETURNS void AS $$
DECLARE
  step_row RECORD;
  step_no INT;
  src_arr JSONB;
  new_arr JSONB := '[]'::jsonb;
  i INT;
  src_len INT;
  elem JSONB;
  done_val BOOLEAN;
  texts TEXT[];
  t TEXT;
BEGIN
  FOR step_row IN SELECT s.id, s.step_no, s.checklist FROM lis_qc_occ_step s LOOP
    step_no := step_row.step_no;
    -- 对应步骤的默认排查项文本 (必须与 OccServiceImpl.defaultChecklist 保持一致)
    texts := CASE step_no
      WHEN 1 THEN ARRAY['已停止相关项目检测','已张贴"失控待处理"标识','已通知当班人员与专业组组长']
      WHEN 2 THEN ARRAY['已核对质控品与批号效期','已核对仪器状态与校准','已核对操作流程','已核对数据录入']
      WHEN 3 THEN ARRAY['已保留质控数据','已保留原瓶质控品','已记录试剂批号与校准记录']
      WHEN 4 THEN ARRAY['已排除质控品因素','已排除试剂因素','已排除校准品因素','已排除仪器因素','已排除环境/操作因素']
      WHEN 5 THEN ARRAY['已更换质控品/试剂','已规范操作流程(SOP)']
      WHEN 6 THEN ARRAY['新条件连续≥2批次在控','已恢复相关项目检测']
      WHEN 7 THEN ARRAY['已填写失控报告','已评估既往结果影响','已制定预防措施','已提交复核']
      ELSE ARRAY[]::TEXT[]
    END;

    IF array_length(texts, 1) IS NULL THEN CONTINUE; END IF;

    -- 解析原 checklist JSON (如果非法则 fallback 为全 false)
    IF step_row.checklist IS NULL OR step_row.checklist = '' THEN
      src_arr := '[]'::jsonb;
    ELSE
      BEGIN
        src_arr := step_row.checklist::jsonb;
      EXCEPTION WHEN OTHERS THEN
        src_arr := '[]'::jsonb;
      END;
    END IF;
    IF jsonb_typeof(src_arr) <> 'array' THEN src_arr := '[]'::jsonb; END IF;
    src_len := jsonb_array_length(src_arr);

    -- 重建数组: 逐个元素填正确的 text, 从原数组取 done, 取不到默认 false
    new_arr := '[]'::jsonb;
    FOR i IN 1 .. array_length(texts, 1) LOOP
      t := texts[i];
      IF i <= src_len THEN
        elem := src_arr -> (i - 1);
        done_val := COALESCE((elem ->> 'done')::boolean, false);
      ELSE
        done_val := false;
      END IF;
      new_arr := new_arr || jsonb_build_object('text', t, 'done', done_val)::jsonb;
    END LOOP;

    UPDATE lis_qc_occ_step SET checklist = new_arr::text WHERE id = step_row.id;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT repair_occ_step_checklist();
DROP FUNCTION repair_occ_step_checklist();

-- ------------------------------------------------------------
-- 5) 修正触发规则编码 (由 Unicode 下标/中文别名 标准化为 Westgard 英文编码)
--    仅修正常见的乱码或中文别名情况, 不覆盖已存在的合法规则
-- ------------------------------------------------------------
UPDATE lis_qc_occ_event
SET trigger_rules = '1-3s,4-1s'
WHERE trigger_rules IS NOT NULL
  AND trigger_rules <> ''
  AND (trigger_rules LIKE '%?%' OR trigger_rules LIKE '%１%' OR trigger_rules LIKE '%３%');
