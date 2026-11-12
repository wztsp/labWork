-- ============================================================
-- 生成技师工作站测试标本数据
-- 每类 50 条，共 200 条，每条数据独立关联: 患者 -> 就诊 -> 申请单 -> 标的
-- 状态: 0待采集 / 1已采集 / 3已签收 / 4已核收(待审核)
-- 本脚本以 UTF-8 编码执行，保证中文无乱码
-- ============================================================
BEGIN;

-- ---------- 1. 患者 (id 1000~1199) ----------
INSERT INTO lis_patient (id, patient_no, name, gender, age, birth_date, create_time, update_time)
SELECT
  g.n,
  'P20260805' || lpad((g.n - 1000)::text, 4, '0'),
  (ARRAY['张','李','王','刘','陈','杨','赵','黄','周','吴','徐','孙','马','朱','胡','郭','何','高','林','罗','郑','梁','谢','宋','唐','许','韩','冯','邓','曹','彭','曾','肖','田','董','袁','潘','蒋','蔡','余','杜','叶','程','苏','魏','吕','丁','任','卢','沈'])[ ((g.n - 1000) % 50) + 1 ] ||
  (ARRAY['伟','芳','娜','敏','静','丽','强','磊','军','洋','勇','艳','杰','涛','明','超','玉兰','秀英','霞','平','刚'])[ ((g.n - 1000) % 20) + 1 ],
  (g.n % 2) + 1,
  20 + (g.n % 60),
  (date '2026-08-05' - (20 + (g.n % 60)) * interval '1 year')::date,
  now(), now()
FROM generate_series(1000, 1199) AS g(n);

-- ---------- 2. 就诊 (id 1000~1199) ----------
INSERT INTO lis_visit (id, visit_no, patient_id, visit_type, department, doctor_id, doctor_name, visit_time, create_time)
SELECT
  g.n,
  'V20260805' || lpad((g.n - 1000)::text, 4, '0'),
  g.n,
  (ARRAY[1,1,2,3,1])[ ((g.n - 1000) % 5) + 1 ],
  (ARRAY['门诊内科','门诊外科','住院部','急诊科','呼吸内科','消化内科','心内科','内分泌科'])[ ((g.n - 1000) % 8) + 1 ],
  NULL,
  (ARRAY['王医生','刘医生','陈医生','张医生','李医生','孙医生','周医生','吴医生'])[ ((g.n - 1000) % 8) + 1 ],
  (timestamp '2026-08-05 08:00:00' + ((g.n - 1000) % 480) * interval '1 minute'),
  now()
FROM generate_series(1000, 1199) AS g(n);

-- ---------- 3. 申请单 (id 1000~1199) ----------
INSERT INTO lis_lab_order (id, order_no, visit_id, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time, create_time)
SELECT
  g.n,
  'ORD20260805' || lpad((g.n - 1000)::text, 4, '0'),
  g.n, g.n,
  (ARRAY[1,1,2,3,1])[ ((g.n - 1000) % 5) + 1 ],
  1,
  (ARRAY['王医生','刘医生','陈医生','张医生','李医生','孙医生','周医生','吴医生'])[ ((g.n - 1000) % 8) + 1 ],
  (ARRAY['门诊内科','门诊外科','住院部','急诊科','呼吸内科','消化内科','心内科','内分泌科'])[ ((g.n - 1000) % 8) + 1 ],
  (ARRAY['健康体检','发热待查','高血压病','糖尿病','咳嗽待查','腹痛待查','胸闷待查','贫血待查'])[ ((g.n - 1000) % 8) + 1 ],
  (timestamp '2026-08-05 08:00:00' + ((g.n - 1000) % 480) * interval '1 minute'),
  now()
FROM generate_series(1000, 1199) AS g(n);

-- ---------- 4. 申请单明细 (id 1000~1199), 每条关联一个检验项目 ----------
INSERT INTO lis_order_item (id, order_id, test_item_id, test_item_code, test_item_name, status, fee, create_time)
SELECT
  g.n,
  g.n,
  g.panel_id,
  (SELECT item_code FROM lis_test_item WHERE id = g.panel_id),
  (SELECT item_name FROM lis_test_item WHERE id = g.panel_id),
  1,
  35.00,
  now()
FROM (
  SELECT n,
         (ARRAY[9,10,11,15,16,21])[ ((n - 1000) % 6) + 1 ] AS panel_id
  FROM generate_series(1000, 1199) AS t(n)
) AS g;

-- ---------- 5. 标本 (id 1000~1199) ----------
INSERT INTO lis_sample (
  id, sample_barcode, order_id, patient_id, visit_id, patient_name,
  sample_type, container_type, status, is_urgent, department,
  sample_no, instrument_id,
  collect_time, deliver_time, sign_off_time, receive_time,
  collector_id, deliver_person, sign_off_person, deliver_person_id, sign_off_person_id,
  create_time, update_time, deleted
)
SELECT
  g.n,
  'BC20260805' || lpad((g.n - 1000)::text, 4, '0'),
  g.n, g.n, g.n,
  (SELECT name FROM lis_patient WHERE id = g.n),
  (ARRAY['全血','血清','血清','血清','血清','尿液'])[ ((g.n - 1000) % 6) + 1 ],
  (ARRAY['紫头管','促凝管','促凝管','促凝管','促凝管','尿杯'])[ ((g.n - 1000) % 6) + 1 ],
  g.status,
  CASE WHEN (g.n - 1000) % 25 = 0 THEN 1 ELSE 0 END,
  (ARRAY['门诊内科','门诊外科','住院部','急诊科','呼吸内科','消化内科','心内科','内分泌科'])[ ((g.n - 1000) % 8) + 1 ],
  CASE WHEN g.status >= 1 THEN 'SN' || lpad((g.n - 1000)::text, 4, '0') ELSE NULL END,
  CASE WHEN g.status >= 4 THEN 1 ELSE NULL END,
  CASE WHEN g.status >= 1 THEN (timestamp '2026-08-05 08:00:00' + ((g.n - 1000) % 480) * interval '1 minute') ELSE NULL END,
  CASE WHEN g.status >= 3 THEN (timestamp '2026-08-05 09:00:00' + ((g.n - 1000) % 480) * interval '1 minute') ELSE NULL END,
  CASE WHEN g.status >= 3 THEN (timestamp '2026-08-05 10:00:00' + ((g.n - 1000) % 480) * interval '1 minute') ELSE NULL END,
  CASE WHEN g.status >= 4 THEN (timestamp '2026-08-05 11:00:00' + ((g.n - 1000) % 480) * interval '1 minute') ELSE NULL END,
  1,
  CASE WHEN g.status >= 3 THEN '送检员' ELSE NULL END,
  CASE WHEN g.status >= 3 THEN '签收员' ELSE NULL END,
  1, 1,
  now(), now(), 0
FROM (
  SELECT n,
    CASE
      WHEN (n - 1000) < 50  THEN 0   -- 待采集
      WHEN (n - 1000) < 100 THEN 1   -- 已采集
      WHEN (n - 1000) < 150 THEN 3   -- 已签收
      ELSE 4                          -- 已核收(待审核)
    END AS status
  FROM generate_series(1000, 1199) AS t(n)
) AS g;

-- ---------- 6. 标本-申请明细 关联 (id 1000~1199) ----------
INSERT INTO lis_sample_order_item (id, sample_id, order_id, order_item_id, test_item_id, create_time)
SELECT
  g.n, g.n, g.n, g.n, g.panel_id, now()
FROM (
  SELECT n,
         (ARRAY[9,10,11,15,16,21])[ ((n - 1000) % 6) + 1 ] AS panel_id
  FROM generate_series(1000, 1199) AS t(n)
) AS g;

-- ---------- 7. 校正序列, 避免后续插入主键冲突 ----------
SELECT setval('lis_patient_id_seq', 1199, true);
SELECT setval('lis_visit_id_seq', 1199, true);
SELECT setval('lis_lab_order_id_seq', 1199, true);
SELECT setval('lis_order_item_id_seq', 1199, true);
SELECT setval('lis_sample_id_seq', 1199, true);

COMMIT;