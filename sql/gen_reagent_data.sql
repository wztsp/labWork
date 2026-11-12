-- ============================================================
-- 试剂管理测试数据生成
-- 一、基础数据：分类子类 / 供应商 / 仓库 / 试剂字典(30) / 预算
-- 二、流程数据（每个流程 30 条）：库存 / 采购申请 / 采购订单 /
--     入库 / 请领业务单 / 盘点 / 消耗 / 开瓶
-- 全部使用显式高位ID(100000起)，UTF-8编码执行，中文无乱码
-- ============================================================
BEGIN;

-- ============================================================
-- 一、基础数据
-- ============================================================

-- 1. 试剂分类子类（挂到现有5个根分类下）
INSERT INTO lis_reagent_category (id, category_code, category_name, parent_id, path, level_no, hazard_category, sort_order, status)
SELECT v.id, v.code, v.name, v.pid, v.path, 2, v.hz, v.id, 1
FROM (VALUES
    (100, 'CAT_CHEM_BIO',   '生化试剂',   1, '/CAT_CHEM/BIO/',     0),
    (101, 'CAT_CHEM_IMMU',  '免疫试剂',   1, '/CAT_CHEM/IMMU/',    0),
    (102, 'CAT_BIO_BLD',    '血液/凝血试剂', 2, '/CAT_BIO/BLD/',    0),
    (103, 'CAT_BIO_MOL',    '分子诊断试剂', 2, '/CAT_BIO/MOL/',     0),
    (104, 'CAT_BIO_URINE',  '尿液试剂',   2, '/CAT_BIO/URINE/',    0),
    (105, 'CAT_HAZARD_FLAM','易燃试剂',   3, '/CAT_HAZARD/FLAM/',  1),
    (106, 'CAT_HAZARD_COR', '腐蚀试剂',   3, '/CAT_HAZARD/COR/',   1),
    (107, 'CAT_CONSUM_DISP','一次性耗材', 4, '/CAT_CONSUM/DISP/',  0),
    (108, 'CAT_QC_QC',      '质控品',     5, '/CAT_QC/QC/',        0),
    (109, 'CAT_QC_CAL',     '校准品',     5, '/CAT_QC/CAL/',       0)
) AS v(id, code, name, pid, path, hz)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_category c WHERE c.category_code = v.code);

-- 2. 供应商主数据（10家）
INSERT INTO lis_supplier (id, supplier_code, supplier_name, short_name, credit_code, contact_person, phone, email, address, qual_status, score, source_type, status, remark)
SELECT
  g.id,
  'SUP2026S' || lpad((g.n)::text, 3, '0'),
  (ARRAY['罗氏诊断(中国)有限公司','迈瑞医疗科技股份有限公司','西门子医学诊断产品公司','雅培诊断产品公司','贝克曼库尔特贸易有限公司','希森美康医用电子有限公司','生物梅里埃诊断有限公司','北京世纪沃德生物科技有限公司','深圳普门科技股份有限公司','上海科华生物工程股份有限公司'])[g.n],
  (ARRAY['罗氏','迈瑞','西门子','雅培','贝克曼','希森美康','梅里埃','沃德','普门','科华'])[g.n],
  '91' || lpad((100000000000 + g.n*100)::text, 16, '0'),
  (ARRAY['李先生','陈女士','王经理','刘主管','张先生','赵女士','孙经理','钱主管','周先生','吴女士'])[g.n],
  '138' || lpad((g.n * 137)::text, 8, '0'),
  'contact' || g.n || '@example.com',
  '北京市' || '经济技术开发区' || lpad(g.n::text, 3, '0') || '号',
  1,
  (85 + g.n)::numeric,
  1, 1,
  '2026年度测试供应商'
FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 10) AS n) g
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier s WHERE s.supplier_code = 'SUP2026S' || lpad(g.n::text, 3, '0'));

-- 3. 仓库/冰箱/层架（挂在现有二级节点下）
-- 3.1 一级库房
INSERT INTO lis_reagent_warehouse (id, wh_code, wh_name, parent_id, path, wh_type, dept_name, temp_min, temp_max, location_desc, sort_order, status)
SELECT v.id, v.wh_code, v.wh_name, v.parent_id, v.path, 1, '检验科', v.tmin, v.tmax, v.ldesc, v.id, 1
FROM (VALUES
    (100000, 'WH_RT',  '常温库房', 0, '/100000/',   15, 30, '一楼常温区'),
    (100001, 'WH_HAZ', '危险品库', 0, '/100001/',   10, 25, '独立防火区'),
    (100002, 'WH_FR1', '冷冻库',   0, '/100002/',  -25, -15, '负二十度冷冻区')
) AS v(id, wh_code, wh_name, parent_id, path, tmin, tmax, ldesc)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_warehouse w WHERE w.wh_code = v.wh_code);

-- 3.2 冷藏库(2)下的冰箱与层架
INSERT INTO lis_reagent_warehouse (id, wh_code, wh_name, parent_id, path, wh_type, dept_name, temp_min, temp_max, location_desc, sort_order, status)
SELECT v.id, v.wh_code, v.wh_name, v.parent_id, v.path, 2, '检验科', 2, 8, v.ldesc, v.id, 1
FROM (VALUES
    (100003, 'FRI_1', '冷藏冰箱1号', 2, '/2/100003/',  '冷藏库A区'),
    (100004, 'FRI_2', '冷藏冰箱2号', 2, '/2/100004/',  '冷藏库B区')
) AS v(id, wh_code, wh_name, parent_id, path, ldesc)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_warehouse w WHERE w.wh_code = v.wh_code);

-- 3.3 层架
INSERT INTO lis_reagent_warehouse (id, wh_code, wh_name, parent_id, path, wh_type, dept_name, temp_min, temp_max, location_desc, sort_order, status)
SELECT v.id, v.wh_code, v.wh_name, v.parent_id, v.path, 3, '检验科', 2, 8, v.ldesc, v.id, 1
FROM (VALUES
    (100005, 'SHELF_RT_A1', '常温仓A1层架', 100000, '/100000/100005/', 'A1-01'),
    (100006, 'SHELF_FR1_B1', '冷冻库B1层架', 100002, '/100002/100006/', 'B1-01')
) AS v(id, wh_code, wh_name, parent_id, path, ldesc)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_warehouse w WHERE w.wh_code = v.wh_code);

-- 4. 试剂字典（30 种）
INSERT INTO lis_reagent (
  id, reagent_code, reagent_name, specification, manufacturer, category_id,
  supplier_id, unit, price_purchase, price_selling, storage_cond,
  min_stock, max_stock, hazard_flag, hazard_class, status, create_time
)
SELECT
  g.id,
  g.code, g.name, g.spec, g.mfr, g.cat,
  (100000 + (g.n - 1) % 10),
  g.unit, g.pp, g.ps, g.storage,
  g.minstock, g.maxstock, g.hazard, g.hzclass, 1, now()
FROM (
  SELECT n, id,
    (ARRAY['R26_GLU','R26_ALT','R26_AST','R26_TP','R26_ALB','R26_ALP','R26_CREA','R26_UREA','R26_UA','R26_BUN',
           'R26_TC','R26_TG','R26_HDL','R26_LDL','R26_WBCDIL','R26_HGB','R26_PLTDIL','R26_RETSTAIN','R26_HBSAG','R26_HCV',
           'R26_HIV','R26_AFP','R26_CEA','R26_PT','R26_APTT','R26_FIB','R26_UASTRIP','R26_UPRO','R26_QCCBC','R26_QCCHEM'])[n] AS code,
    (ARRAY['葡萄糖试剂','ALT试剂','AST试剂','总蛋白试剂','白蛋白试剂','碱性磷酸酶试剂','肌酐试剂','尿素试剂','尿酸试剂','尿素氮试剂',
           '总胆固醇试剂','甘油三酯试剂','高密度脂蛋白试剂','低密度脂蛋白试剂','白细胞稀释液','血红蛋白溶血剂','血小板稀释液','网织红细胞染液','HBsAg试剂','抗HCV试剂',
           '抗HIV试剂','甲胎蛋白试剂','癌胚抗原试剂','凝血酶原时间试剂','活化部分凝血活酶试剂','纤维蛋白原试剂','尿液分析试纸条','尿蛋白试剂','血常规质控品','生化质控品'])[n] AS name,
    (ARRAY['R1 500mL','R2 200mL','R3 200mL','R4 200mL','R5 200mL','R6 500mL','R7 200mL','R8 200mL','R9 200mL','R10 200mL',
           'R11 200mL','R12 200mL','R13 200mL','R14 200mL','R15 5L','R16 5L','R17 1L','R18 1L','R19 100T','R20 100T',
           'R21 100T','R22 200T','R23 200T','R24 100T','R25 100T','R26 100T','R27 100P','R28 100T','R29 1盒','R30 1盒'])[n] AS spec,
    (ARRAY['罗氏','罗氏','罗氏','罗氏','罗氏','罗氏','贝克曼','贝克曼','贝克曼','贝克曼',
           '罗氏','罗氏','罗氏','罗氏','迈瑞','迈瑞','迈瑞','迈瑞','雅培','雅培',
           '雅培','雅培','雅培','希森美康','希森美康','希森美康','希森美康','希森美康','迈瑞','罗氏'])[n] AS mfr,
    (ARRAY[100,100,100,100,100,100,100,100,100,100,
           100,100,100,100,102,102,102,102,101,101,
           101,101,101,102,102,102,104,104,108,108])[n] AS cat,
    (ARRAY['mL','mL','mL','mL','mL','mL','mL','mL','mL','mL',
           'mL','mL','mL','mL','瓶','瓶','瓶','瓶','盒','盒',
           '盒','盒','盒','盒','盒','盒','人份','瓶','瓶','瓶'])[n] AS unit,
    (ARRAY[35.0,120.0,130.0,60.0,65.0,140.0,95.0,88.0,70.0,80.0,
           85.0,95.0,120.0,140.0,200.0,180.0,150.0,260.0,350.0,320.0,
           380.0,420.0,450.0,300.0,310.0,290.0,180.0,120.0,500.0,480.0])[n] AS pp,
    (ARRAY[45.0,150.0,160.0,75.0,80.0,175.0,120.0,110.0,88.0,100.0,
           105.0,120.0,150.0,175.0,250.0,225.0,190.0,325.0,440.0,400.0,
           475.0,525.0,560.0,375.0,390.0,360.0,225.0,150.0,625.0,600.0])[n] AS ps,
    (ARRAY[365,365,365,365,365,365,365,365,365,365,
           365,365,365,365,540,540,365,365,360,360,
           360,360,360,365,365,365,365,365,540,540])[n] AS exp,
    (ARRAY['2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏',
           '2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','常温','常温','常温','常温','2-8℃冷藏','2-8℃冷藏',
           '2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏','常温','2-8℃冷藏','2-8℃冷藏','2-8℃冷藏'])[n] AS storage,
    (ARRAY[20,20,20,20,20,20,20,20,20,20,
           20,20,20,20,30,30,30,30,15,15,
           15,15,15,20,20,20,30,20,10,10])[n] AS minstock,
    (ARRAY[200,200,200,200,200,200,200,200,200,200,
           200,200,200,200,300,300,300,300,150,150,
           150,150,150,200,200,200,300,200,100,100])[n] AS maxstock,
    (ARRAY[0,0,0,0,0,0,0,0,0,0,
           0,0,0,0,0,0,0,0,0,0,
           1,1,1,0,0,0,0,0,0,0])[n] AS hazard,
    (ARRAY['','','','','','','','','','',
           '','','','','','','','','','',
           '剧毒','剧毒','剧毒','','','','','','',''])[n] AS hzclass
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent r WHERE r.id = g.id);

-- 5. 预算（.年度+科室+类别 唯一，共 12 条）
INSERT INTO lis_reagent_budget (id, budget_no, budget_year, dept_id, dept_name, category_id, category_name, total_amount, frozen_amount, used_amount, warn_ratio, version, status)
SELECT
  g.id, 'BUD2026-' || lpad(g.n::text, 3, '0'), 2026,
  100 + g.n, g.dept, g.cat, g.catname, g.total, 0, 0, 0.8000, 0, 1
FROM (
  SELECT n, id,
    (ARRAY['检验科','生化组','免疫组','血液组','尿液组','门诊部','住院部','急诊科','心内科','呼吸内科','消化内科','内分泌科'])[n] AS dept,
    (ARRAY[NULL,NULL,NULL,NULL,NULL,1,2,3,4,5,7,8])[n] AS cat,
    (ARRAY['通用预算','通用预算','通用预算','通用预算','通用预算','化学试剂','生物试剂','危险品','耗材','质控品/校准品','分子诊断试剂','尿液试剂'])[n] AS catname,
    (ARRAY[500000,300000,250000,200000,150000,120000,180000,90000,70000,60000,80000,50000])[n] AS total
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 12) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_budget b WHERE b.id = g.id);

-- ============================================================
-- 二、流程数据（每个流程 30 条）
-- ============================================================

-- 6. 库存（30 条，reagent 一一对应，唯一 (reagent,lot,wh)）
INSERT INTO lis_reagent_stock (id, reagent_id, lot_no, barcode, expiry_date, quantity, location, status, in_stock_time, warehouse_id, create_time)
SELECT
  g.id, g.reagent_id, g.lot, 'BC' || g.lot, g.expiry, g.qty, g.loc, 1, now(), 2, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS reagent_id,
         'LOT2026' || LPAD(n::text, 3, '0') AS lot,
         (date '2026-12-31' + (n % 90)) AS expiry,
         100 + n * 10 AS qty,
         (ARRAY['冷藏库A1-01','冷藏库A1-02','冷藏库A1-03','冷藏库B1-01','冷藏库B1-02','常温A1-01'])[ ((n-1) % 6) + 1 ] AS loc
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_stock s WHERE s.id = g.id);

-- 7. 采购申请（30 条）
INSERT INTO lis_reagent_purchase_apply (
  id, apply_no, apply_dept_id, apply_dept_name, apply_user_id, apply_user_name,
  budget_id, budget_year, category_id, total_amount, status, current_node_level, total_node_count,
  over_budget_flag, urgent_flag, expect_date, submit_time, finish_time, remark, create_time, update_time
)
SELECT
  g.id, 'PA2026' || LPAD(g.n::text, 4, '0'),
  (g.n % 12) + 1, (ARRAY['检验科','生化组','免疫组','血液组','尿液组','门诊部','住院部','急诊科','心内科','呼吸内科','消化内科','内分泌科'])[ ((g.n-1) % 12) + 1 ],
  1, (ARRAY['王采购员','李采购员','张采购员','刘采购员','陈采购员'])[ ((g.n-1) % 5) + 1 ],
  100000 + ((g.n - 1) % 12), 2026, (ARRAY[100,101,102,104,105,108])[ ((g.n-1) % 6) + 1 ],
  g.total, g.status, g.node, g.nodes, 0, g.urgent,
  (date '2026-08-05' + (g.n % 15)),
  CASE WHEN g.status >= 1 THEN (timestamp '2026-08-01 09:00:00' + g.n * interval '1 hour') ELSE NULL END,
  CASE WHEN g.status IN (3,5) THEN (timestamp '2026-08-02 10:00:00' + g.n * interval '1 hour') ELSE NULL END,
  '采购申请测试数据', now(), now()
FROM (
  SELECT n, id, 5000 + n * 1200 AS total,
    CASE
      WHEN n % 7 = 0 THEN 0      -- 草稿
      WHEN n % 7 = 1 THEN 1      -- 待审批
      WHEN n % 7 = 2 THEN 2      -- 审批中
      WHEN n % 7 = 3 THEN 3      -- 已批准
      WHEN n % 7 = 4 THEN 4      -- 已驳回
      WHEN n % 7 = 5 THEN 5      -- 已冻结
      ELSE 6                     -- 已作废
    END AS status,
    CASE WHEN n % 7 IN (2,3) THEN 1 ELSE 0 END AS node,
    CASE WHEN n % 7 IN (2,3) THEN 2 ELSE 0 END AS nodes,
    CASE WHEN n % 5 = 0 THEN 1 ELSE 0 END AS urgent
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_purchase_apply a WHERE a.id = g.id);

-- 7.1 采购申请明细（每条申请 1 条）
INSERT INTO lis_reagent_purchase_apply_item (id, apply_id, reagent_id, reagent_code, reagent_name, specification, category_id, quantity, unit, estimated_price, amount, remark, create_time)
SELECT
  g.id, g.apply_id, g.reagent_id, (SELECT reagent_code FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT reagent_name FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT specification FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT category_id FROM lis_reagent WHERE id = g.reagent_id),
  5 + (g.n % 10), '瓶', 1200, (5 + (g.n % 10)) * 1200, NULL, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS apply_id,
         100000 + ((n - 1) % 30) AS reagent_id
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_purchase_apply_item i WHERE i.id = g.id);

-- 8. 采购订单（30 条）
INSERT INTO lis_reagent_purchase_order (
  id, order_no, apply_id, apply_no, supplier_id, supplier_name, dept_id, dept_name,
  total_amount, expect_date, order_date, status, operator_id, operator_name, remark, create_time, update_time
)
SELECT
  g.id, 'PO2026' || LPAD(g.n::text, 4, '0'),
  g.apply_id, 'PA2026' || LPAD(g.n::text, 4, '0'),
  g.supplier_id, (SELECT supplier_name FROM lis_supplier WHERE id = g.supplier_id),
  (g.n % 12) + 1, (ARRAY['检验科','生化组','免疫组','血液组','尿液组','门诊部','住院部','急诊科','心内科','呼吸内科','消化内科','内分泌科'])[ ((g.n-1) % 12) + 1 ],
  5000 + g.n * 1200, (date '2026-08-05' + (g.n % 15)), (date '2026-08-03' + (g.n % 3)),
  g.status, 1, (ARRAY['王采购员','李采购员','张采购员','刘采购员','陈采购员'])[ ((g.n-1) % 5) + 1 ],
  '采购订单测试数据', now(), now()
FROM (
  SELECT n, id, 100000 + (n - 1) AS apply_id, 100000 + ((n - 1) % 10) AS supplier_id,
    CASE WHEN n % 5 = 0 THEN 0      -- 待下单
         WHEN n % 5 = 1 THEN 1      -- 已下单
         WHEN n % 5 = 2 THEN 2      -- 部分到货
         WHEN n % 5 = 3 THEN 3      -- 已到货
         ELSE 4                     -- 已取消
    END AS status
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_purchase_order o WHERE o.id = g.id);

-- 8.1 采购订单明细（每条订单 1 条）
INSERT INTO lis_reagent_purchase_order_item (id, order_id, apply_item_id, reagent_id, reagent_code, reagent_name, specification, quantity, arrived_qty, unit, unit_price, amount, remark, create_time)
SELECT
  g.id, g.order_id, g.id, g.reagent_id, (SELECT reagent_code FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT reagent_name FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT specification FROM lis_reagent WHERE id = g.reagent_id),
  g.qty, g.qty, '瓶', 1200, g.qty * 1200, NULL, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS order_id,
         100000 + ((n - 1) % 30) AS reagent_id, 5 + (n % 10) AS qty
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_purchase_order_item i WHERE i.id = g.id);

-- 9. 入库单（30 条）
INSERT INTO lis_reagent_inbound (id, inbound_no, supplier, total_amount, verify_status, verifier_id, verifier_name, verify_time, remark, create_time, order_id, order_no, supplier_id)
SELECT
  g.id, 'IN2026' || LPAD(g.n::text, 4, '0'),
  (SELECT supplier_name FROM lis_supplier WHERE id = g.supplier_id),
  5000 + g.n * 1200, g.vstatus, 1, (ARRAY['陈验收员','刘验收员','王验收员'])[ ((g.n-1) % 3) + 1 ],
  CASE WHEN g.vstatus >= 1 THEN (timestamp '2026-08-04 14:00:00' + g.n * interval '1 hour') ELSE NULL END,
  '入库验收测试数据', now(), 100000 + (g.n - 1), 'PO2026' || LPAD(g.n::text, 4, '0'), g.supplier_id
FROM (
  SELECT n, id, 100000 + ((n - 1) % 10) AS supplier_id,
    CASE WHEN n % 3 = 0 THEN 0     -- 待验收
         WHEN n % 3 = 1 THEN 1     -- 已验收
         ELSE 2                    -- 拒收
    END AS vstatus
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_inbound i WHERE i.id = g.id);

-- 9.1 入库明细（每条入库单 1 条）
INSERT INTO lis_reagent_inbound_item (id, inbound_id, reagent_id, lot_no, barcode, quantity, unit, purchase_price, amount, expiry_date, location, warehouse_id, order_item_id, remark, create_time)
SELECT
  g.id, g.inbound_id, g.reagent_id, g.lot, 'BC' || g.lot, g.qty, '瓶', 1200, g.qty * 1200,
  (date '2026-12-31' + (g.n % 90)), '冷藏库A1-01', 2, g.id, NULL, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS inbound_id,
         100000 + ((n - 1) % 30) AS reagent_id,
         'LOT2026' || LPAD(n::text, 3, '0') AS lot, 5 + (n % 10) AS qty
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_inbound_item i WHERE i.id = g.id);

-- 10. 请领业务单（30 条，req_type 1领用 2退库 3报废 4调拨 5借用 6归还）
INSERT INTO lis_reagent_requisition (
  id, req_no, req_type, dept_id, dept_name, apply_user_id, apply_user_name, status,
  approver_id, approver_name, approve_time, executor_id, executor_name, execute_time,
  from_warehouse_id, to_warehouse_id, hazard_flag, reason, total_amount, remark, create_time, update_time
)
SELECT
  g.id, 'RQ2026' || LPAD(g.n::text, 4, '0'), g.req_type,
  (g.n % 12) + 1, (ARRAY['检验科','生化组','免疫组','血液组','尿液组','门诊部','住院部','急诊科','心内科','呼吸内科','消化内科','内分泌科'])[ ((g.n-1) % 12) + 1 ],
  1, '张技师', g.status,
  CASE WHEN g.status >= 2 THEN 1 ELSE NULL END, CASE WHEN g.status >= 2 THEN '李主任' ELSE NULL END,
  CASE WHEN g.status >= 2 THEN (timestamp '2026-08-05 09:30:00' + g.n * interval '1 hour') ELSE NULL END,
  CASE WHEN g.status >= 3 THEN 1 ELSE NULL END, CASE WHEN g.status >= 3 THEN '王技师' ELSE NULL END,
  CASE WHEN g.status >= 3 THEN (timestamp '2026-08-05 10:30:00' + g.n * interval '1 hour') ELSE NULL END,
  g.from_wh, g.to_wh, 0, g.reason, 3000 + g.n * 150, '请领业务单测试数据', now(), now()
FROM (
  SELECT n, id,
    (ARRAY[1,1,1,2,3,4,4,5,6,1,2,3])[ ((n-1) % 12) + 1 ] AS req_type,
    (ARRAY[2,2,2,0,0,2,100000,2,2,2,0,0])[ ((n-1) % 12) + 1 ] AS from_wh,
    (ARRAY[0,0,0,2,0,100001,2,0,0,0,2,2])[ ((n-1) % 12) + 1 ] AS to_wh,
    CASE WHEN n % 6 = 4 THEN '试剂过期报废' ELSE NULL END AS reason,
    CASE WHEN n % 6 = 0 THEN 0      -- 草稿
         WHEN n % 6 = 1 THEN 1      -- 待审
         WHEN n % 6 = 2 THEN 2      -- 已审
         WHEN n % 6 = 3 THEN 3      -- 已执行
         WHEN n % 6 = 4 THEN 4      -- 已驳回
         ELSE 5                     -- 已作废
    END AS status
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_requisition r WHERE r.id = g.id);

-- 10.1 请领明细（每条业务单 1 条）
INSERT INTO lis_reagent_requisition_item (id, req_id, reagent_id, reagent_code, reagent_name, stock_id, lot_no, expiry_date, quantity, unit, unit_price, amount, hazard_flag, remark, create_time)
SELECT
  g.id, g.req_id, g.reagent_id, (SELECT reagent_code FROM lis_reagent WHERE id = g.reagent_id),
  (SELECT reagent_name FROM lis_reagent WHERE id = g.reagent_id),
  g.stock_id, (SELECT lot_no FROM lis_reagent_stock WHERE id = g.stock_id),
  (SELECT expiry_date FROM lis_reagent_stock WHERE id = g.stock_id),
  g.qty, '瓶', 1200, g.qty * 1200, 0, NULL, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS req_id,
         100000 + ((n - 1) % 30) AS reagent_id, 100000 + ((n - 1) % 30) AS stock_id,
         1 + (n % 5) AS qty
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_requisition_item i WHERE i.id = g.id);

-- 11. 盘点单（30 条）
INSERT INTO lis_reagent_stock_check (id, check_no, warehouse, check_date, status, operator_id, operator_name, remark, create_time, warehouse_id)
SELECT
  g.id, 'CK2026' || LPAD(g.n::text, 4, '0'),
  (ARRAY['冷藏库','常温库房','冷冻库','危险品库'])[ ((g.n-1) % 4) + 1 ],
  (date '2026-08-05' - (g.n % 7)), g.status, 1, '周盘点员',
  '库存盘点测试数据', now(),
  (ARRAY[2,100000,100002,100001])[ ((g.n-1) % 4) + 1 ]
FROM (
  SELECT n, id,
    CASE WHEN n % 3 = 0 THEN 0     -- 进行中
         WHEN n % 3 = 1 THEN 1     -- 已完成
         ELSE 2                    -- 已作废
    END AS status
  FROM (SELECT n, 100000 + (n - 1) AS id FROM generate_series(1, 30) AS n) s
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_stock_check c WHERE c.id = g.id);

-- 11.1 盘点明细（每条盘点单 1 条）
INSERT INTO lis_reagent_stock_check_item (id, check_id, stock_id, reagent_id, lot_no, book_qty, actual_qty, diff_qty, remark, create_time)
SELECT
  g.id, g.check_id, g.stock_id, g.reagent_id, (SELECT lot_no FROM lis_reagent_stock WHERE id = g.stock_id),
  g.qty, g.actual, g.actual - g.qty, NULL, now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + (n - 1) AS check_id,
         100000 + ((n - 1) % 30) AS stock_id, 100000 + ((n - 1) % 30) AS reagent_id,
         100 + n * 10 AS qty, 100 + n * 10 + (CASE WHEN n % 5 = 0 THEN 1 ELSE 0 END) AS actual
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_stock_check_item i WHERE i.id = g.id);

-- 12. 消耗登记（30 条）
INSERT INTO lis_reagent_consumption (id, consumption_no, reagent_id, stock_id, lot_no, quantity, unit, consume_type, related_no, operator_id, operator_name, remark, create_time)
SELECT
  g.id, 'CON2026' || LPAD(g.n::text, 4, '0'),
  g.reagent_id, g.stock_id, (SELECT lot_no FROM lis_reagent_stock WHERE id = g.stock_id),
  g.qty, 'mL', g.ctype, g.related, 1, '王操作员',
  '消耗登记测试数据', (timestamp '2026-08-05 08:00:00' + g.n * interval '30 minute')
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + ((n - 1) % 30) AS reagent_id,
         100000 + ((n - 1) % 30) AS stock_id,
         (ARRAY[1,2,3,4,5])[ ((n-1) % 5) + 1 ] AS ctype,
         'WO2026' || LPAD(n::text, 4, '0') AS related,
         1 + (n % 3) AS qty
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_consumption c WHERE c.id = g.id);

-- 13. 开瓶记录（30 条）
INSERT INTO lis_reagent_opening (id, reagent_id, stock_id, lot_no, open_time, opened_expiry, status, operator_id, operator_name, remark, create_time)
SELECT
  g.id, g.reagent_id, g.stock_id, (SELECT lot_no FROM lis_reagent_stock WHERE id = g.stock_id),
  (timestamp '2026-08-05 08:00:00' + g.n * interval '20 minute'),
  (date '2026-09-01' + (g.n % 30)), g.status, 1, '王操作员',
  '开瓶记录测试数据', now()
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + ((n - 1) % 30) AS reagent_id,
         100000 + ((n - 1) % 30) AS stock_id,
         CASE WHEN n % 10 = 0 THEN 0 ELSE 1 END AS status
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_opening o WHERE o.id = g.id);

-- 14. 库存流水（30 条，入库记录，还原库存来源）
INSERT INTO lis_reagent_stock_record (id, stock_id, reagent_id, record_type, quantity, before_qty, after_qty, operator_id, operator_name, source_no, related_id, related_type, remark, create_time, dept_name)
SELECT
  g.id, g.stock_id, g.reagent_id, 1, g.qty, 0, g.qty, 1, '陈验收员',
  'IN2026' || LPAD(g.n::text, 4, '0'), 100000 + (g.n - 1), 'INBOUND',
  '入库测试数据', (timestamp '2026-08-04 14:00:00' + g.n * interval '30 minute'), '检验科'
FROM (
  SELECT n, 100000 + (n - 1) AS id, 100000 + ((n - 1) % 30) AS stock_id,
         100000 + ((n - 1) % 30) AS reagent_id, 100 + n * 10 AS qty
  FROM generate_series(1, 30) AS n
) g
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_stock_record r WHERE r.id = g.id);

-- ============================================================
-- 三、校正序列避免后续主键冲突
-- ============================================================
SELECT setval('lis_reagent_category_id_seq',      (SELECT MAX(id) FROM lis_reagent_category),      true);
SELECT setval('lis_supplier_id_seq',               (SELECT MAX(id) FROM lis_supplier),               true);
SELECT setval('lis_reagent_warehouse_id_seq',      (SELECT MAX(id) FROM lis_reagent_warehouse),      true);
SELECT setval('lis_reagent_id_seq',                (SELECT MAX(id) FROM lis_reagent),                true);
SELECT setval('lis_reagent_budget_id_seq',         (SELECT MAX(id) FROM lis_reagent_budget),         true);
SELECT setval('lis_reagent_stock_id_seq',          (SELECT MAX(id) FROM lis_reagent_stock),          true);
SELECT setval('lis_reagent_purchase_apply_id_seq', (SELECT MAX(id) FROM lis_reagent_purchase_apply), true);
SELECT setval('lis_reagent_purchase_apply_item_id_seq', (SELECT MAX(id) FROM lis_reagent_purchase_apply_item), true);
SELECT setval('lis_reagent_purchase_order_id_seq', (SELECT MAX(id) FROM lis_reagent_purchase_order), true);
SELECT setval('lis_reagent_purchase_order_item_id_seq', (SELECT MAX(id) FROM lis_reagent_purchase_order_item), true);
SELECT setval('lis_reagent_inbound_id_seq',        (SELECT MAX(id) FROM lis_reagent_inbound),        true);
SELECT setval('lis_reagent_inbound_item_id_seq',   (SELECT MAX(id) FROM lis_reagent_inbound_item),   true);
SELECT setval('lis_reagent_requisition_id_seq',    (SELECT MAX(id) FROM lis_reagent_requisition),    true);
SELECT setval('lis_reagent_requisition_item_id_seq',(SELECT MAX(id) FROM lis_reagent_requisition_item),true);
SELECT setval('lis_reagent_stock_check_id_seq',    (SELECT MAX(id) FROM lis_reagent_stock_check),    true);
SELECT setval('lis_reagent_stock_check_item_id_seq',(SELECT MAX(id) FROM lis_reagent_stock_check_item),true);
SELECT setval('lis_reagent_consumption_id_seq',    (SELECT MAX(id) FROM lis_reagent_consumption),    true);
SELECT setval('lis_reagent_opening_id_seq',        (SELECT MAX(id) FROM lis_reagent_opening),        true);
SELECT setval('lis_reagent_stock_record_id_seq',   (SELECT MAX(id) FROM lis_reagent_stock_record),   true);

COMMIT;