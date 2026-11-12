-- =============================================================================
-- Seed 30 Technician Workstation Business Data (PostgreSQL 13+)
-- 目标：在「贝克曼 AU5800 生化分析仪」(instrument_id=8) 下，生成 30 条
--       闭环真实业务数据，包含完整流程及 14 项肝功能实测结果。
--
-- 链路闭环：患者 → 就诊 → 检验申请 → 申请项目(肝功能组合)
--          → 标本(采集→送检→签收→核收 status=4)
--          → 14 项检验结果(正常值/高/低/危急各有分布)
--
-- 幂等：按「条码前缀 2608220000xx」先删除对应 6 张表数据再重插。
-- 执行：$env:PGPASSWORD='lis_dev_2026'
--       & 'D:\Program Files\PostgreSQL\13\bin\psql.exe' -h localhost -p 5432 `
--         -U lis_user -d lis_db -v ON_ERROR_STOP=1 -f seed_30_technician.sql
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 0. 引用常量
-- ---------------------------------------------------------------------------
-- 肝功能组合（完整 panel, 非空壳）
-- SELECT id FROM lis_test_item WHERE item_code='LIV_PANEL' AND is_panel=1 AND status=1;
\set LIVER_PANEL_ID 2089961667416928258
-- 贝克曼 AU5800
\set INSTR_ID 8
-- 操作员：张技师(id=2) 核收/检测，李主任(id=3) 审核
\set OP_ID 2
\set AUDITOR_ID 3
-- 标本状态：4=已核收(技师站可见)
\set STATUS_RECEIVED 4

-- ---------------------------------------------------------------------------
-- 1. 清理旧数据(幂等)：按当天日期条码前缀 260822 清理
-- ---------------------------------------------------------------------------
DELETE FROM lis_result
WHERE sample_id IN (
  SELECT id FROM lis_sample WHERE sample_barcode LIKE '2608220000%'
);

DELETE FROM lis_sample       WHERE sample_barcode LIKE '2608220000%';
DELETE FROM lis_order_item
WHERE order_id IN (
  SELECT lo.id FROM lis_lab_order lo WHERE lo.order_no LIKE 'ORD260822%'
);
DELETE FROM lis_lab_order    WHERE order_no LIKE 'ORD260822%';
DELETE FROM lis_visit        WHERE visit_no  LIKE 'V260822%';
DELETE FROM lis_patient      WHERE patient_no LIKE 'P260822%';

-- ---------------------------------------------------------------------------
-- 2. 生成 30 条 患者 / 就诊 / 申请 / 订单项目 / 标本 / 14 项结果
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  v_idx   integer;
  v_bar   varchar;
  v_dt0   timestamp;            -- 申请基准时间
  v_pid   bigint;
  v_vid   bigint;
  v_oid   bigint;
  v_sid   bigint;
  v_oiid  bigint;
  v_seed  numeric;
  v_age   int;
  v_gender int;
  v_name  varchar;
  v_dept  varchar;
  v_doctor varchar;
  v_diag   varchar;
  v_visit_type int;
  v_sample_no varchar;

  m       record;
  v_val   numeric;
  v_ab    varchar;
  v_cri   int;
  v_range varchar;
BEGIN
  -- 手工对齐「肝功能组合 2089961667416928258」下 14 个子项及其成人通用参考范围
  CREATE TEMP TABLE IF NOT EXISTS _liver_items (
    id       bigint,
    code     varchar,
    name     varchar,
    unit     varchar,
    ref_low  numeric,
    ref_high numeric,
    cri_high numeric
  ) ON COMMIT DROP;
  TRUNCATE _liver_items;
  INSERT INTO _liver_items(id, code, name, unit, ref_low, ref_high, cri_high) VALUES
    ( 6, 'ALT',  '丙氨酸氨基转移酶', 'U/L',    0,    40,   300),
    ( 7, 'AST',  '天门冬氨酸氨基转移酶', 'U/L', 0,   40,   300),
    ( 8, 'TP',   '总蛋白',           'g/L',   60,    80,   NULL),
    (49, 'TBIL', '总胆红素',         'umol/L', 5.1,  28.0, 85),
    (50, 'DBIL', '直接胆红素',       'umol/L', 0,     8.0, 50),
    (51, 'IBIL', '间接胆红素',       'umol/L', 0,    20.0, NULL),
    (52, 'ALP',  '碱性磷酸酶',       'U/L',   40,   150,   NULL),
    (53, 'GGT',  'γ-谷氨酰转移酶',   'U/L',   10,    60,   300),
    (54, 'ALB',  '白蛋白',           'g/L',   40,    55,   NULL),
    (55, 'GLO',  '球蛋白',           'g/L',   20,    40,   NULL),
    (56, 'A_G',  '白球比',           '',      1.2,   2.0,  NULL),
    (57, 'CHE',  '胆碱酯酶',         'U/L',   4000,  12000, NULL),
    (58, 'TBA',  '总胆汁酸',         'umol/L', 0,     10,   100),
    (59, 'LDH',  '乳酸脱氢酶',       'U/L',   120,   250,   800);

  -- 申请基准时间：2026-08-22 当天早 07:30 开始，每条 +3 分钟
  v_dt0 := '2026-08-22 07:30:00'::timestamp;

  FOR i IN 1..30 LOOP
    v_idx  := i;
    v_bar  := '260822' || to_char(v_idx, 'FM000000');   -- 260822000001..260822000030
    v_age  := 18 + (v_idx * 3 % 60);                    -- 18..77
    v_gender:= 1 + (v_idx % 2);                         -- 1男 2女 交替
    -- 中文姓名(常见百家姓+名)
    v_name := (ARRAY['张','王','李','赵','刘','陈','杨','黄','周','吴',
                     '徐','孙','朱','马','胡','郭','林','何','高','罗',
                     '郑','梁','谢','宋','唐','许','韩','冯','邓','曹'])[v_idx]
           || (ARRAY['伟','芳','娜','敏','静','丽','强','磊','军','洋',
                     '勇','艳','杰','娟','涛','明','超','霞','平',
                     '刚','英','文','华','玲','辉','鹏','建','斌','宇','峰'])[v_idx];
    v_dept   := (ARRAY['消化内科','感染科','普外科','肝胆外科','老年病科',
                       '心内科','内分泌科','肾内科','风湿免疫科','体检中心',
                       '消化内科','感染科','普外科','肝胆外科','老年病科',
                       '心内科','内分泌科','肾内科','风湿免疫科','体检中心',
                       '消化内科','感染科','普外科','肝胆外科','老年病科',
                       '心内科','内分泌科','肾内科','风湿免疫科','体检中心'])[v_idx];
    v_doctor := (ARRAY['赵医生','钱医生','孙医生','李医生','周医生',
                       '吴医生','郑医生','王医生','冯医生','陈医生',
                       '褚医生','卫医生','蒋医生','沈医生','韩医生',
                       '杨医生','朱医生','秦医生','尤医生','许医生',
                       '何医生','吕医生','施医生','张医生','孔医生',
                       '曹医生','严医生','华医生','金医生','魏医生'])[v_idx];
    v_diag   := (ARRAY['肝功能异常待查','慢性乙型肝炎','脂肪肝','肝硬化代偿期',
                       '药物性肝损害','体检','术后肝功能监测','黄疸待查',
                       '腹痛待查','慢性丙型肝炎','肝功能异常待查','慢性乙型肝炎',
                       '脂肪肝','肝硬化代偿期','药物性肝损害','体检',
                       '术后肝功能监测','黄疸待查','腹痛待查','慢性丙型肝炎',
                       '肝功能异常待查','慢性乙型肝炎','脂肪肝','肝硬化代偿期',
                       '药物性肝损害','体检','术后肝功能监测','黄疸待查',
                       '腹痛待查','慢性丙型肝炎'])[v_idx];
    v_visit_type := 1 + (v_idx % 3);                 -- 1门诊 / 2住院 / 3体检
    v_sample_no := 'S' || v_bar;

    -- 患者
    INSERT INTO lis_patient(patient_no, name, gender, age, birth_date, create_time, update_time)
    VALUES ('P' || v_bar, v_name, v_gender, v_age,
            ('2026-08-22'::date - (v_age || ' years')::interval)::date,
            v_dt0 + (v_idx-1)*interval '3 min', v_dt0 + (v_idx-1)*interval '3 min')
    RETURNING id INTO v_pid;

    -- 就诊
    INSERT INTO lis_visit(visit_no, patient_id, visit_type, department, doctor_name, visit_time, create_time)
    VALUES ('V' || v_bar, v_pid, v_visit_type, v_dept, v_doctor,
            v_dt0 + (v_idx-1)*interval '3 min', v_dt0 + (v_idx-1)*interval '3 min')
    RETURNING id INTO v_vid;

    -- 检验申请
    INSERT INTO lis_lab_order(order_no, visit_id, patient_id, order_type, status,
                              apply_doctor, apply_dept, clinical_diag, apply_time,
                              receive_time, collector_id, collect_time, create_time)
    VALUES ('ORD' || v_bar, v_vid, v_pid, 1, 1,
            v_doctor, v_dept, v_diag,
            v_dt0 + (v_idx-1)*interval '3 min',             -- apply
            v_dt0 + (v_idx-1)*interval '3 min' + interval '20 min', -- receive(检验科签收)
            2,                                               -- 采集护士
            v_dt0 + (v_idx-1)*interval '3 min' + interval '5 min', -- 采集
            v_dt0 + (v_idx-1)*interval '3 min')
    RETURNING id INTO v_oid;

    -- 申请项目(肝功能组合)
    INSERT INTO lis_order_item(order_id, test_item_id, test_item_code, test_item_name,
                               status, fee, create_time)
    VALUES (v_oid, 2089961667416928258, 'LIV_PANEL', '肝功能', 2, 98.00,
            v_dt0 + (v_idx-1)*interval '3 min')
    RETURNING id INTO v_oiid;

    -- 标本（贝克曼 AU5800 核收，status=4）
    INSERT INTO lis_sample(sample_barcode, sample_no, order_id, patient_id, visit_id,
                           visit_no, patient_name, sample_type, container_type,
                           status, is_urgent, instrument_id,
                           collect_time, deliver_time, deliver_person, deliver_person_id,
                           sign_off_time, sign_off_person, sign_off_person_id,
                           receive_time, operator_id, receiver, receiver_id,
                           test_start_time, report_time,
                           department, remark, deleted, create_time, update_time)
    VALUES (v_bar, v_sample_no, v_oid, v_pid, v_vid,
            'V' || v_bar, v_name, '血清', '红头管',
            4, CASE WHEN v_idx % 7 = 0 THEN 1 ELSE 0 END, 8,
            -- 流程时间按真实 30 分钟/节点走
            v_dt0 + (v_idx-1)*interval '3 min' + interval '5 min',   -- 采集
            v_dt0 + (v_idx-1)*interval '3 min' + interval '12 min',  -- 送检
            '刘护士', 1,
            v_dt0 + (v_idx-1)*interval '3 min' + interval '18 min',  -- 签收
            '陈护士', 1,
            v_dt0 + (v_idx-1)*interval '3 min' + interval '25 min',  -- 核收(技师站)
            2, '张技师', 2,
            v_dt0 + (v_idx-1)*interval '3 min' + interval '30 min',  -- 检测开始
            v_dt0 + (v_idx-1)*interval '3 min' + interval '1h 30min',-- 报告时间
            v_dept,
            CASE WHEN v_idx % 11 = 0 THEN '标本中度溶血' ELSE NULL END,
            0,
            v_dt0 + (v_idx-1)*interval '3 min',
            v_dt0 + (v_idx-1)*interval '3 min' + interval '25 min')
    RETURNING id INTO v_sid;

    -- ---------------------------------------------------------------
    -- 生成 14 个肝功能指标的结果
    --   分布：每条大约 11-12 条正常、1-2 条偏高或偏低、第 7/14/21/28 条含危急
    -- ---------------------------------------------------------------
    v_seed := (random() * 1000000)::int;
    FOR m IN SELECT * FROM _liver_items LOOP
      -- 以患者序号 + 指标序号 构造可预测但分布自然的结果
      v_val := m.ref_low + (m.ref_high - m.ref_low) * (0.65 + sin(v_idx*0.7 + m.id*0.13)*0.35)
                              + ((v_seed + m.id*17) % 100)::numeric/100 * (m.ref_high - m.ref_low)*0.15;

      -- 刻意偏离：按 v_idx%5 的规则让某 1-2 项偏高或偏低
      IF (v_idx % 5 = 1 AND m.code='ALT')  THEN v_val := m.ref_high * 1.7; END IF;
      IF (v_idx % 5 = 2 AND m.code='AST')  THEN v_val := m.ref_high * 1.5; END IF;
      IF (v_idx % 5 = 3 AND m.code='GGT')  THEN v_val := m.ref_high * 2.1; END IF;
      IF (v_idx % 5 = 4 AND m.code='ALB')  THEN v_val := m.ref_low * 0.85; END IF;
      IF (v_idx % 5 = 0 AND m.code='TBIL') THEN v_val := m.ref_high * 1.9; END IF;

      -- 危急样本：v_idx=7/14/21/28 → 特定危急值
      IF v_idx IN (7,21) AND m.code='TBIL'  THEN v_val := 112.0; END IF;  -- >85
      IF v_idx IN (14,28) AND m.code='ALT'  THEN v_val := 520.0; END IF;  -- >300

      -- 格式化数值(保留小数：整数单位 0 位，有小数 1-2 位)
      IF m.unit IN ('U/L','g/L','umol/L') AND m.ref_high < 100 THEN
        v_val := round(v_val, 1);
      ELSIF m.code='A_G' THEN
        v_val := round(v_val, 2);
      ELSIF m.code='CHE' THEN
        v_val := round(v_val, 0);
      ELSIF m.ref_high - m.ref_low >= 50 THEN
        v_val := round(v_val, 0);
      ELSE
        v_val := round(v_val, 2);
      END IF;

      -- 异常标志 / 危急
      v_ab  := 'N';
      v_cri := 0;
      IF v_val < m.ref_low  THEN v_ab := 'L'; END IF;
      IF v_val > m.ref_high THEN v_ab := 'H'; END IF;
      IF m.cri_high IS NOT NULL AND v_val > m.cri_high THEN
        v_ab  := 'H';
        v_cri := 1;
      END IF;

      -- 参考范围展示
      IF m.code = 'A_G' THEN
        v_range := '1.2-2.0';
      ELSE
        v_range := CASE WHEN m.ref_low = floor(m.ref_low) THEN trim(to_char(m.ref_low,'99999')) ELSE trim(to_char(m.ref_low,'99999.9')) END
                || '-'
                || CASE WHEN m.ref_high = floor(m.ref_high) THEN trim(to_char(m.ref_high,'99999')) ELSE trim(to_char(m.ref_high,'99999.9')) END;
        IF m.unit IS NOT NULL AND length(m.unit)>0 THEN
          v_range := v_range || ' ' || m.unit;
        END IF;
      END IF;

      INSERT INTO lis_result(sample_id, test_item_id, result_value, unit, ref_range,
                             abnormal_flag, is_critical, instrument_id,
                             operator_id, test_time, audit_status, auditor_id, audit_time,
                             create_time, update_time)
      VALUES (v_sid, m.id, v_val::varchar, m.unit, v_range,
              v_ab, v_cri, 8,
              2, v_dt0 + (v_idx-1)*interval '3 min' + interval '45 min',
              2, 3, v_dt0 + (v_idx-1)*interval '3 min' + interval '1h 20min',
              v_dt0 + (v_idx-1)*interval '3 min' + interval '45 min',
              v_dt0 + (v_idx-1)*interval '3 min' + interval '1h 20min');
    END LOOP;
  END LOOP;
END $$;

COMMIT;
