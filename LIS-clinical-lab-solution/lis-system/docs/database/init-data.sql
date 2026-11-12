-- =============================================================================
-- LIS 临床检验系统 初始化数据脚本 (PostgreSQL 15+)
-- 依赖: init-schema.sql 已执行
-- 内容:
--   1. 默认角色 (5 个)
--   2. 默认管理员账号 (admin / 123456, BCrypt 加密)
--   3. 检验项目字典 (8 项: WBC/RBC/HGB/PLT/GLU/ALT/AST/TP)
--   4. 参考范围 (与检验项目对应, 含危急值)
--   5. 权限/菜单 (标本/报告/质控/仪器/基础数据/系统管理)
--   6. 仪器示例 (血细胞分析仪 / 生化分析仪)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 一、默认角色
-- -----------------------------------------------------------------------------
INSERT INTO sys_role (role_code, role_name, description, status) VALUES
('ADMIN',          '系统管理员',  '拥有系统全部权限, 负责系统配置与维护', 1),
('LAB_DIRECTOR',   '检验科主任',  '负责报告审核发布、统计查询、质控管理', 1),
('TECHNICIAN',     '检验技师',    '负责标本核收、结果录入、日常检测操作', 1),
('REPORT_VIEWER',  '报告查看员',  '仅可查看检验报告, 不可修改',          1),
('HIS_INTEGRATE',  'HIS对接账号', '用于 LIS 与 HIS 系统的数据对接',      1);


-- -----------------------------------------------------------------------------
-- 二、默认管理员账号
-- 用户名: admin  原始密码: 123456 (BCrypt 加密)
-- -----------------------------------------------------------------------------
INSERT INTO sys_user (username, password, real_name, phone, email, status) VALUES
('admin', '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6', '系统管理员', '13800000000', 'admin@lis.com', 1);

-- 绑定管理员角色 (admin 用户 id=1, ADMIN 角色 id=1)
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);


-- -----------------------------------------------------------------------------
-- 三、检验项目字典 (8 项)
-- 项目 ID 顺序: 1=WBC 2=RBC 3=HGB 4=PLT 5=GLU 6=ALT 7=AST 8=TP
-- -----------------------------------------------------------------------------
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('WBC', '白细胞计数',           'White Blood Cell',          'WBC', '血常规', '全血',   '10^9/L', '电阻抗法',     0, NULL, 15.00, 1),
('RBC', '红细胞计数',           'Red Blood Cell',            'RBC', '血常规', '全血',   '10^12/L','电阻抗法',     0, NULL, 15.00, 1),
('HGB', '血红蛋白',             'Hemoglobin',                'HGB', '血常规', '全血',   'g/L',    '比色法',       0, NULL, 10.00, 1),
('PLT', '血小板计数',           'Platelet',                  'PLT', '血常规', '全血',   '10^9/L', '电阻抗法',     0, NULL, 15.00, 1),
('GLU', '葡萄糖',               'Glucose',                   'GLU', '生化',   '血清',   'mmol/L', '己糖激酶法',   0, NULL, 12.00, 1),
('ALT', '丙氨酸氨基转移酶',     'Alanine Aminotransferase',  'ALT', '生化',   '血清',   'U/L',    '速率法',       0, NULL, 12.00, 1),
('AST', '天门冬氨酸氨基转移酶', 'Aspartate Aminotransferase','AST', '生化',   '血清',   'U/L',    '速率法',       0, NULL, 12.00, 1),
('TP',  '总蛋白',               'Total Protein',             'TP',  '生化',   '血清',   'g/L',    '双缩脲法',     0, NULL, 10.00, 1);


-- -----------------------------------------------------------------------------
-- 四、参考范围 (含危急值)
-- gender: NULL=全部 1=男 2=女 ; age 单位: 岁
-- -----------------------------------------------------------------------------
-- WBC (test_item_id=1)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(1, NULL, 0,    999, '3.5',  '9.5',  NULL, '0.5', '30.0', '10^9/L');

-- RBC (test_item_id=2) 男
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(2, 1,    0,    999, '4.3',  '5.8',  NULL, '2.0', '8.0',  '10^12/L');
-- RBC 女
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(2, 2,    0,    999, '3.8',  '5.1',  NULL, '2.0', '7.0',  '10^12/L');

-- HGB (test_item_id=3) 男
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(3, 1,    0,    999, '130',  '175',  NULL, '60',  '230',  'g/L');
-- HGB 女
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(3, 2,    0,    999, '115',  '150',  NULL, '50',  '200',  'g/L');

-- PLT (test_item_id=4)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(4, NULL, 0,    999, '125',  '350',  NULL, '30',  '800',  '10^9/L');

-- GLU (test_item_id=5)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(5, NULL, 0,    999, '3.9',  '6.1',  NULL, '2.2', '25.0', 'mmol/L');

-- ALT (test_item_id=6)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(6, NULL, 0,    999, '7',    '40',   NULL, '5',   '300',  'U/L');

-- AST (test_item_id=7)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(7, NULL, 0,    999, '13',   '35',   NULL, '5',   '300',  'U/L');

-- TP (test_item_id=8)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit) VALUES
(8, NULL, 0,    999, '65',   '85',   NULL, '40',  '120',  'g/L');


-- -----------------------------------------------------------------------------
-- 五、权限 / 菜单数据
-- perm_type: 1=菜单 2=按钮 3=接口
-- 一级菜单 id: 1=标本管理 2=报告管理 3=质控管理 4=仪器管理 5=基础数据 6=系统管理
-- -----------------------------------------------------------------------------
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, status) VALUES
-- 一级菜单
(0, 'sample',       '标本管理', 1, '/sample',       'sample/index',       'sample',      10, 1),
(0, 'report',       '报告管理', 1, '/report',       'report/index',       'report',      20, 1),
(0, 'qc',           '质控管理', 1, '/qc',           'qc/index',           'qc',          30, 1),
(0, 'instrument',   '仪器管理', 1, '/instrument',   'instrument/index',   'instrument',  40, 1),
(0, 'basedata',     '基础数据', 1, '/basedata',     'basedata/index',     'basedata',    50, 1),
(0, 'system',       '系统管理', 1, '/system',       'system/index',       'system',      60, 1),

-- 标本管理子菜单/按钮 (parent_id=1)
(1, 'sample:list',       '标本列表',   2, NULL, NULL, NULL, 1, 1),
(1, 'sample:receive',    '标本核收',   2, NULL, NULL, NULL, 2, 1),
(1, 'sample:reject',     '标本拒收',   2, NULL, NULL, NULL, 3, 1),
(1, 'sample:query',      '标本查询',   2, NULL, NULL, NULL, 4, 1),

-- 报告管理子菜单/按钮 (parent_id=2)
(2, 'report:list',       '报告列表',   2, NULL, NULL, NULL, 1, 1),
(2, 'report:audit',      '报告审核',   2, NULL, NULL, NULL, 2, 1),
(2, 'report:publish',    '报告发布',   2, NULL, NULL, NULL, 3, 1),
(2, 'report:modify',     '报告修改',   2, NULL, NULL, NULL, 4, 1),
(2, 'report:print',      '报告打印',   2, NULL, NULL, NULL, 5, 1),

-- 质控管理子菜单/按钮 (parent_id=3)
(3, 'qc:lot',            '质控批号',   2, NULL, NULL, NULL, 1, 1),
(3, 'qc:record',         '质控记录',   2, NULL, NULL, NULL, 2, 1),
(3, 'qc:rule',           '质控规则',   2, NULL, NULL, NULL, 3, 1),
(3, 'qc:chart',          '质控图表',   2, NULL, NULL, NULL, 4, 1),

-- 仪器管理子菜单/按钮 (parent_id=4)
(4, 'instrument:list',   '仪器列表',       2, NULL, NULL, NULL, 1, 1),
(4, 'instrument:connect','仪器连接管理',   2, NULL, NULL, NULL, 2, 1),
(4, 'instrument:mapping','仪器项目映射',   2, NULL, NULL, NULL, 3, 1),
(4, 'instrument:maint',  '仪器维护',       2, NULL, NULL, NULL, 4, 1),

-- 基础数据子菜单/按钮 (parent_id=5)
(5, 'basedata:item',     '检验项目',   2, NULL, NULL, NULL, 1, 1),
(5, 'basedata:range',    '参考范围',   2, NULL, NULL, NULL, 2, 1),
(5, 'basedata:patient',  '患者档案',   2, NULL, NULL, NULL, 3, 1),

-- 系统管理子菜单/按钮 (parent_id=6)
(6, 'system:user',       '用户管理',   2, NULL, NULL, NULL, 1, 1),
(6, 'system:role',       '角色管理',   2, NULL, NULL, NULL, 2, 1),
(6, 'system:perm',       '权限管理',   2, NULL, NULL, NULL, 3, 1),
(6, 'system:log',        '操作日志',   2, NULL, NULL, NULL, 4, 1),
(6, 'system:dict',       '字典管理',   2, NULL, NULL, NULL, 5, 1);

-- 给 ADMIN 角色(role_id=1) 绑定全部权限
-- 一级菜单 id: 1..6, 子菜单 id: 7..34 (共 28 个二级权限, 总计 34 条权限)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 给 LAB_DIRECTOR 角色(role_id=2) 绑定: 标本管理、报告管理、质控管理、仪器管理 (查看+审核)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission
WHERE perm_code IN (
    'sample', 'sample:list', 'sample:query',
    'report', 'report:list', 'report:audit', 'report:publish', 'report:print',
    'qc', 'qc:lot', 'qc:record', 'qc:rule', 'qc:chart',
    'instrument', 'instrument:list', 'instrument:mapping', 'instrument:maint',
    'basedata', 'basedata:item', 'basedata:range', 'basedata:patient'
);

-- 给 TECHNICIAN 角色(role_id=3) 绑定: 标本核收、结果录入相关
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission
WHERE perm_code IN (
    'sample', 'sample:list', 'sample:receive', 'sample:reject', 'sample:query',
    'report', 'report:list',
    'qc', 'qc:lot', 'qc:record', 'qc:chart',
    'instrument', 'instrument:list', 'instrument:connect',
    'basedata', 'basedata:item', 'basedata:range', 'basedata:patient'
);

-- 给 REPORT_VIEWER 角色(role_id=4) 绑定: 报告查看、患者档案查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission
WHERE perm_code IN (
    'report', 'report:list', 'report:print',
    'basedata', 'basedata:patient'
);

-- 给 HIS_INTEGRATE 角色(role_id=5) 绑定: 仅接口相关权限 (这里给报告与标本查看)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 5, id FROM sys_permission
WHERE perm_code IN (
    'sample', 'sample:list', 'sample:query',
    'report', 'report:list'
);


-- -----------------------------------------------------------------------------
-- 六、仪器示例
-- -----------------------------------------------------------------------------
-- 1. 血细胞分析仪 (Mindray BC-7500, ASTM 协议, TCP 连接)
INSERT INTO lis_instrument (instrument_code, instrument_name, brand, model, protocol_type, connect_type, serial_port, baud_rate, ip_address, port, status) VALUES
('BC7500', '迈瑞 BC-7500 血细胞分析仪', 'Mindray', 'BC-7500', 'ASTM', 'TCP', NULL, NULL, '192.168.1.101', 9001, 1);

-- 2. 生化分析仪 (Roche cobas 8000, HL7 协议, TCP 连接)
INSERT INTO lis_instrument (instrument_code, instrument_name, brand, model, protocol_type, connect_type, serial_port, baud_rate, ip_address, port, status) VALUES
('COBAS8000', '罗氏 cobas 8000 生化分析仪', 'Roche', 'cobas 8000', 'HL7', 'TCP', NULL, NULL, '192.168.1.102', 9002, 1);

-- 仪器项目映射 (instrument_id=1 血细胞分析仪 → 血常规项目; instrument_id=2 生化分析仪 → 生化项目)
-- 血细胞分析仪 (id=1) 关联 WBC/RBC/HGB/PLT (项目 id 1..4)
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name) VALUES
(1, 1, 'WBC', 'WBC'),
(1, 2, 'RBC', 'RBC'),
(1, 3, 'HGB', 'HGB'),
(1, 4, 'PLT', 'PLT');

-- 生化分析仪 (id=2) 关联 GLU/ALT/AST/TP (项目 id 5..8)
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name) VALUES
(2, 5, 'GLU', 'GLU'),
(2, 6, 'ALT', 'ALT'),
(2, 7, 'AST', 'AST'),
(2, 8, 'TP',  'TP');


-- =============================================================================
-- 七、仪器管理模块初始数据
-- =============================================================================

-- 维护模板
INSERT INTO lis_maintenance_template (template_code, template_name, instrument_type, cycle_type, status) VALUES
('DAILY_HEMATOLOGY', '血细胞分析仪日维护', 'HEMATOLOGY', 'DAILY', 1),
('DAILY_BIOCHEM',    '生化分析仪日维护',   'BIOCHEM',    'DAILY', 1),
('WEEKLY_PM',        '周保养模板',         NULL,         'WEEKLY', 1),
('MONTHLY_PM',       '月保养模板',         NULL,         'MONTHLY', 1);

-- 维护模板明细 (template_id=1 血细胞分析仪日维护)
INSERT INTO lis_maintenance_template_item (template_id, item_code, item_name, item_type, expected_value, sort_order) VALUES
(1, 'POWER_ON',  '开机自检', 1, '通过', 1),
(1, 'CLEAN_PROBE','清洗探针', 2, '清洁', 2),
(1, 'CHECK_REAGENT','试剂余量检查', 3, '充足', 3),
(1, 'RECORD_TEMP','记录温湿度', 5, '温度20-25℃ 湿度30-70%', 4);

-- 维护计划 (instrument_id=1, PM 周保养)
INSERT INTO lis_instrument_maintenance_plan (instrument_id, plan_type, cycle_type, first_date, next_date, status) VALUES
(1, 'PM', 'WEEKLY', '2026-01-05', '2026-07-29', 1),
(2, 'PM', 'WEEKLY', '2026-01-05', '2026-07-29', 1),
(1, 'CALIBRATION', 'MONTHLY', '2026-01-15', '2026-08-15', 1);


-- =============================================================================
-- 八、试剂管理模块初始数据
-- =============================================================================

-- 试剂字典 (instrument_id=1 关联 WBC, instrument_id=2 关联 GLU)
INSERT INTO lis_reagent (reagent_code, reagent_name, specification, manufacturer, cas_no, storage_cond, unit, instrument_id, test_item_id, safety_stock, status) VALUES
('R_WBC_DIL',  '白细胞稀释液', '500mL/瓶', '迈瑞',   NULL, '2-8℃避光', 'mL', 1, 1, 100.00, 1),
('R_GLU_REA',  '葡萄糖试剂',   '100mL/瓶', '罗氏',   NULL, '2-8℃避光', 'mL', 2, 5, 50.00, 1),
('R_ALT_REA',  'ALT试剂',      '100mL/瓶', '罗氏',   NULL, '2-8℃避光', 'mL', 2, 6, 50.00, 1),
('R_HGB_DRUG', '血红蛋白溶血剂','100mL/瓶', '迈瑞',   NULL, '室温',     'mL', 1, 3, 80.00, 1);

-- 试剂库存 (按批次)
INSERT INTO lis_reagent_stock (reagent_id, lot_no, barcode, expiry_date, quantity, location, status, in_stock_time) VALUES
(1, 'LOT20260101', 'BC001', '2026-12-31', 350.00, '冰箱A-1', 1, '2026-01-10 09:00:00'),
(2, 'LOT20260201', 'BC002', '2026-10-31', 200.00, '冰箱B-2', 1, '2026-02-15 10:30:00'),
(4, 'LOT20260301', 'BC003', '2026-09-30', 120.00, '室温架-3', 1, '2026-03-20 14:00:00');


-- =============================================================================
-- 九、微生物模块扩展数据
-- =============================================================================

-- 抗生素字典
INSERT INTO lis_antibiotic (antibiotic_code, antibiotic_name, category, unit, status) VALUES
('PEN',   '青霉素',        'β-内酰胺类', NULL, 1),
('AMP',   '氨苄西林',      'β-内酰胺类', NULL, 1),
('OXA',   '苯唑西林',      'β-内酰胺类', NULL, 1),
('GEN',   '庆大霉素',      '氨基糖苷类', NULL, 1),
('CIP',   '环丙沙星',      '喹诺酮类',   NULL, 1),
('VAN',   '万古霉素',      '糖肽类',     NULL, 1),
('IPM',   '亚胺培南',      '碳青霉烯类', NULL, 1),
('AK',    '阿米卡星',      '氨基糖苷类', NULL, 1),
('CAZ',   '头孢他啶',      '头孢菌素类', NULL, 1),
('SXT',   '复方新诺明',    '磺胺类',     NULL, 1);

-- 特殊耐药规则
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, rule_name, is_critical, alert_message, status) VALUES
('STAUR', '["OXA"]', 'MRSA', 1, '检出耐甲氧西林金黄色葡萄球菌(MRSA)，请按院感流程处理', 1),
('ECOLI', '["IPM"]', 'CRE',  1, '检出碳青霉烯耐药肠杆菌科细菌(CRE)，请按院感流程处理', 1),
('ACIBA', '["IPM"]', 'CRAB', 1, '检出碳青霉烯耐药鲍曼不动杆菌(CRAB)，请按院感流程处理', 1);

-- 微生物打印模板默认配置
INSERT INTO lis_micro_report_print_config (config_name, show_mic, show_kb, show_breakpoint, show_method, breakpoint_version, paper_size, is_default) VALUES
('默认A4模板', 1, 0, 0, 0, 'CLSI 2024', 'A4', 1);


-- =============================================================================
-- 十、人事管理模块初始数据
-- =============================================================================

-- 小组/科室字典
INSERT INTO lis_lab_group (group_code, group_name, parent_id, sort_order, status) VALUES
('BIOCHEM',   '生化组',     0, 1, 1),
('IMMUNE',    '免疫组',     0, 2, 1),
('MICRO',     '微生物组',   0, 3, 1),
('BLOOD',     '血液体液组', 0, 4, 1),
('MOLECULAR', '分子诊断组', 0, 5, 1),
('QC',        '质控组',     0, 6, 1);

-- 小组与仪器关联 (生化组 id=2 → 生化分析仪 id=2; 血液组 id=4 → 血细胞分析仪 id=1)
INSERT INTO lis_group_instrument (group_id, instrument_id) VALUES
(4, 1),
(2, 2);

-- 职称字典
INSERT INTO lis_title_dict (title_code, title_name, title_level, sort_order, status) VALUES
('TECH_JUNIOR',     '检验技士',      1, 1, 1),
('TECHNICIAN',       '检验技师',      2, 2, 1),
('SENIOR_TECH',      '主管技师',      3, 3, 1),
('ASSOC_CHIEF_TECH', '副主任技师',    4, 4, 1),
('CHIEF_TECH',       '主任技师',      5, 5, 1);

-- 人员档案 (admin 用户已绑定 id=1)
INSERT INTO lis_staff (staff_no, name, phone, email, group_id, user_id, education, major, entry_date, status) VALUES
('S001', '系统管理员', '13800000000', 'admin@lis.com', 1, 1, '本科', '医学检验', '2020-01-01', 1),
('S002', '张技师',     '13800000001', 'zhang@lis.com', 2, NULL, '本科', '医学检验', '2021-06-01', 1),
('S003', '李主任',     '13800000002', 'li@lis.com',    4, NULL, '博士', '临床检验诊断学', '2018-03-01', 1);

-- 当前职称记录 (staff_id=3 是主任技师)
INSERT INTO lis_staff_title (staff_id, title_code, title_name, acquire_date, expiry_date, certificate_no, is_current) VALUES
(3, 'CHIEF_TECH', '主任技师', '2022-12-01', NULL, 'CERT-2022-001', 1),
(2, 'TECHNICIAN', '检验技师', '2021-08-01', NULL, 'CERT-2021-008', 1);

-- 资质证书 (staff_id=2 PCR 上岗证)
INSERT INTO lis_staff_certificate (staff_id, cert_type, cert_name, cert_no, issue_date, expiry_date, issue_org, alert_days, status) VALUES
(2, 'QUAL_PCR', 'PCR 上岗证', 'PCR-2023-002', '2023-05-10', '2026-08-10', '省临检中心', 30, 1),
(3, 'QUAL_PRACTICE', '执业资格证', 'PRAC-2018-009', '2018-06-01', '2027-06-01', '卫健委', 60, 1);

-- 岗位授权 (staff_id=3 报告审核权)
INSERT INTO lis_staff_authorization (staff_id, auth_type, auth_scope, start_date, end_date, grantor_id, grantor_name, status) VALUES
(3, 'REPORT_AUDIT', 'ALL', '2022-12-01', '2027-12-01', 1, '系统管理员', 1),
(2, 'INSTRUMENT_OP', 'BC7500,COBAS8000', '2021-08-01', '2026-08-01', 3, '李主任', 1);

-- 文档资料示例
INSERT INTO lis_lab_document (doc_code, doc_name, doc_type, version, group_id, file_url, uploader_id, status) VALUES
('SOP-WBC-001', '白细胞计数标准操作规程', 'SOP', '2.0', 4, '/docs/sop/wbc-v2.pdf', 1, 1),
('MANUAL-QC-001','质量控制手册',           'MANUAL', '1.0', 6, '/docs/manual/qc-v1.pdf', 1, 1);


-- =============================================================================
-- 十一、测试数据 - 患者、就诊、检验申请、标本、结果、报告
-- =============================================================================

-- 患者信息 (3 名测试患者)
INSERT INTO lis_patient (patient_no, name, gender, birth_date, age, phone_hash, address) VALUES
('P20260001', '张三', 1, '1985-03-15', 41, 'a1b2c3d4e5f6', '北京市朝阳区'),
('P20260002', '李四', 1, '1990-07-20', 36, 'b2c3d4e5f6a1', '北京市海淀区'),
('P20260003', '王丽', 2, '1978-11-08', 47, 'c3d4e5f6a1b2', '北京市西城区');

-- 就诊记录 (3 名患者各 1 次门诊就诊)
INSERT INTO lis_visit (visit_no, patient_id, visit_type, department, doctor_name, visit_time) VALUES
('V20260001', 1, 1, '门诊内科', '王医生', '2026-07-28 08:30:00'),
('V20260002', 2, 1, '门诊内科', '王医生', '2026-07-28 09:00:00'),
('V20260003', 3, 2, '心内科',   '刘医生', '2026-07-28 09:30:00');

-- 检验申请单 (3 张申请单)
INSERT INTO lis_lab_order (order_no, visit_id, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time, receive_time) VALUES
('ORD20260728001', 1, 1, 1, 1, '王医生', '门诊内科', '体检',         '2026-07-28 08:35:00', '2026-07-28 08:40:00'),
('ORD20260728002', 2, 2, 2, 1, '王医生', '门诊内科', '糖尿病复查',   '2026-07-28 09:05:00', '2026-07-28 09:10:00'),
('ORD20260728003', 3, 3, 1, 1, '刘医生', '心内科',   '冠心病复查',   '2026-07-28 09:35:00', '2026-07-28 09:40:00');

-- 申请项目明细 (申请单1: 血常规 WBC/RBC/HGB/PLT; 申请单2: 生化 GLU/ALT/AST/TP; 申请单3: 血常规+生化)
INSERT INTO lis_order_item (order_id, test_item_id, test_item_code, test_item_name, status, fee) VALUES
(1, 1, 'WBC', '白细胞计数', 2, 15.00),
(1, 2, 'RBC', '红细胞计数', 2, 15.00),
(1, 3, 'HGB', '血红蛋白',   2, 10.00),
(1, 4, 'PLT', '血小板计数', 2, 15.00),
(2, 5, 'GLU', '葡萄糖',     2, 12.00),
(2, 6, 'ALT', '丙氨酸氨基转移酶', 2, 12.00),
(2, 7, 'AST', '天门冬氨酸氨基转移酶', 2, 12.00),
(2, 8, 'TP',  '总蛋白',     2, 10.00),
(3, 1, 'WBC', '白细胞计数', 1, 15.00),
(3, 3, 'HGB', '血红蛋白',   1, 10.00),
(3, 5, 'GLU', '葡萄糖',     1, 12.00);

-- 标本 (3 个标本，对应 3 张申请单)
-- 状态: 0申请 1采样 2核收 3检测中 4已报告
INSERT INTO lis_sample (sample_barcode, order_id, patient_id, visit_id, patient_name, sample_type, container_type, status, is_urgent, collect_time, receive_time, report_time, operator_id, department) VALUES
('BC20260728001', 1, 1, 1, '张三', '全血', 'EDTA管',  4, 0, '2026-07-28 08:45:00', '2026-07-28 09:00:00', '2026-07-28 10:30:00', 2, '门诊内科'),
('BC20260728002', 2, 2, 2, '李四', '血清', '促凝管',  4, 0, '2026-07-28 09:15:00', '2026-07-28 09:30:00', '2026-07-28 11:00:00', 2, '门诊内科'),
('BC20260728003', 3, 3, 3, '王丽', '全血', 'EDTA管',  2, 1, '2026-07-28 09:45:00', '2026-07-28 10:00:00', NULL,                  2, '心内科');

-- 检验结果 (sample_id=1: 血常规4项 已审核; sample_id=2: 生化4项 已审核; sample_id=3: 暂无结果)
-- abnormal_flag: N正常 H高 L低
INSERT INTO lis_result (sample_id, test_item_id, result_value, unit, ref_range, abnormal_flag, is_critical, instrument_id, operator_id, test_time, audit_status, auditor_id, audit_time) VALUES
(1, 1, '6.5', '10^9/L', '3.5-9.5',  'N', 0, 1, 2, '2026-07-28 10:00:00', 2, 3, '2026-07-28 10:15:00'),
(1, 2, '4.5', '10^12/L','4.3-5.8',  'N', 0, 1, 2, '2026-07-28 10:00:00', 2, 3, '2026-07-28 10:15:00'),
(1, 3, '142', 'g/L',    '130-175',  'N', 0, 1, 2, '2026-07-28 10:00:00', 2, 3, '2026-07-28 10:15:00'),
(1, 4, '220', '10^9/L', '125-350',  'N', 0, 1, 2, '2026-07-28 10:00:00', 2, 3, '2026-07-28 10:15:00'),
(2, 5, '8.2', 'mmol/L', '3.9-6.1',  'H', 0, 2, 2, '2026-07-28 10:30:00', 2, 3, '2026-07-28 10:45:00'),
(2, 6, '35',  'U/L',    '7-40',     'N', 0, 2, 2, '2026-07-28 10:30:00', 2, 3, '2026-07-28 10:45:00'),
(2, 7, '28',  'U/L',    '13-35',    'N', 0, 2, 2, '2026-07-28 10:30:00', 2, 3, '2026-07-28 10:45:00'),
(2, 8, '72',  'g/L',    '65-85',    'N', 0, 2, 2, '2026-07-28 10:30:00', 2, 3, '2026-07-28 10:45:00');

-- 报告 (sample_id=1,2 已发布报告)
INSERT INTO lis_report (report_no, sample_id, patient_id, patient_name, report_type, status, auditor_id, audit_time, publisher_id, publish_time, report_doctor, checker) VALUES
('RPT20260728001', 1, 1, '张三', 1, 2, 3, '2026-07-28 10:20:00', 3, '2026-07-28 10:30:00', '张技师', '李主任'),
('RPT20260728002', 2, 2, '李四', 1, 2, 3, '2026-07-28 10:50:00', 3, '2026-07-28 11:00:00', '张技师', '李主任');


-- =============================================================================
-- 十二、质控测试数据
-- =============================================================================

-- 质控品批号 (WBC 低中高 3 个批号)
INSERT INTO lis_qc_lot (lot_no, test_item_id, instrument_id, target_value, sd, cv, level, start_date, end_date, status) VALUES
('QC2026L001', 1, 1, 6.50, 0.20, 3.08, 1, '2026-01-01', '2026-12-31', 1),
('QC2026L002', 1, 1, 12.00,0.40, 3.33, 3, '2026-01-01', '2026-12-31', 1);

-- 质控记录 (近5天的质控记录，全部在控)
INSERT INTO lis_qc_record (qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, operator_id, z_score, rule_violation, qc_result) VALUES
(1, 1, 1, 6.55, '2026-07-25 08:00:00', 2, 0.25, NULL, 1),
(1, 1, 1, 6.45, '2026-07-26 08:00:00', 2, -0.25, NULL, 1),
(1, 1, 1, 6.62, '2026-07-27 08:00:00', 2, 0.60, NULL, 1),
(1, 1, 1, 6.40, '2026-07-28 08:00:00', 2, -0.50, NULL, 1),
(1, 1, 1, 6.58, '2026-07-29 08:00:00', 2, 0.40, NULL, 1);


-- =============================================================================
-- 十三、血培养测试数据
-- =============================================================================

-- 血培养记录 (1 个培养中, 1 个阳性已报警)
INSERT INTO lis_blood_culture (sample_id, bottle_no, medium_type, instrument_id, inoculation_time, alarm_time, final_result, final_time, ttp_hours, operator_id, status) VALUES
(3, 'BC-BOT-001', '需氧瓶', NULL, '2026-07-28 10:30:00', NULL,             NULL,        NULL, NULL,    2, 0),
(3, 'BC-BOT-002', '厌氧瓶', NULL, '2026-07-28 10:30:00', '2026-07-29 02:30:00', 'POSITIVE', NULL, 16.0, 2, 1);

-- 血培养转种记录 (BC-BOT-002 已转种)
INSERT INTO lis_blood_culture_subculture (blood_culture_id, subculture_time, medium, incubator_no, operator_id, result, remark) VALUES
(2, '2026-07-29 03:00:00', '血琼脂平板', 'INC-001', 2, '生长菌落', '疑似金黄色葡萄球菌');


-- =============================================================================
-- 十四、系统配置与科室数据
-- =============================================================================

-- 科室数据
INSERT INTO sys_department (dept_code, dept_name, parent_id, sort_order, status) VALUES
('LAB',       '检验科',     0, 1, 1),
('LAB_BIO',   '生化组',     1, 1, 1),
('LAB_IMM',   '免疫组',     1, 2, 1),
('LAB_MIC',   '微生物组',   1, 3, 1),
('LAB_BL',    '血液体液组', 1, 4, 1),
('OPD',       '门诊部',     0, 2, 1),
('IPD',       '住院部',     0, 3, 1);

-- 系统参数
INSERT INTO sys_config (config_key, config_value, config_name, remark) VALUES
('critical_value_notify_interval', '300',   '危急值通知间隔(秒)',     '同一危急值重复通知间隔'),
('auto_audit_enabled',             'true',  '自动审核开关',           '是否开启自动审核'),
('report_print_copies',            '1',     '报告打印份数',           '默认打印份数'),
('sample_barcode_prefix',          'BC',    '标本条码前缀',           '标本条码前缀字符'),
('qc_warning_days',                '7',     '质控预警天数',           '质控未做预警天数');


-- =============================================================================
-- 十五、测试用户 (与人员档案对应)
-- =============================================================================
-- 密码均为 123456 (BCrypt)
INSERT INTO sys_user (username, password, real_name, phone, email, department_id, status) VALUES
('tech',     '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6', '张技师', '13800000001', 'zhang@lis.com', 4, 1),
('director', '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6', '李主任', '13800000002', 'li@lis.com',    1, 1);

-- 绑定角色 (tech 用户 id=2 绑定 TECHNICIAN 角色 id=3; director 用户 id=3 绑定 LAB_DIRECTOR 角色 id=2)
INSERT INTO sys_user_role (user_id, role_id) VALUES
(2, 3),
(3, 2);


-- =============================================================================
-- 十六、操作日志测试数据
-- =============================================================================
INSERT INTO sys_audit_log (user_id, username, module, operation, method, ip, cost_time, create_time) VALUES
(1, 'admin',    '系统管理', '用户登录',     'POST /api/auth/login',           '127.0.0.1', 120, '2026-07-28 08:00:00'),
(2, 'tech',     '标本管理', '标本核收',     'POST /api/sample/receive',       '127.0.0.1', 80,  '2026-07-28 09:00:00'),
(3, 'director', '报告管理', '报告审核发布', 'POST /api/report/audit',         '127.0.0.1', 150, '2026-07-28 10:20:00');
