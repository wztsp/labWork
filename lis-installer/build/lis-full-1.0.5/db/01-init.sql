-- =============================================================================
-- LIS 临床检验系统 - PostgreSQL 一键初始化脚本 (正式部署)
-- =============================================================================
-- 本脚本供 Docker 的 postgres 镜像在 docker-entrypoint-initdb.d 中一次性执行。
-- 它由以下脚本按依赖顺序合并而成:
--   1. init-schema.sql                    全量表结构
--   2. init-data.sql                      基础字典数据(角色/管理员/检验项目/参考范围/权限菜单/仪器示例等)
--   3. 增量脚本(结构 + 字典): add-*.sql / create-*.sql / reagent-core-tables.sql /
--      seed-antibiotic-breakpoints.sql / insert-test-panels.sql 等
--
-- 面向正式部署, 不含演示/测试业务数据(患者/标本/报告/检验申请/质控明细记录等)。
-- 被合并的脚本均以幂等方式编写(IF NOT EXISTS / ON CONFLICT DO NOTHING), 重复执行安全。
-- 字符集: UTF-8 ; 目标 PostgreSQL 15+
-- =============================================================================


-- =============================================================================
-- 【脚本 1/2】init-schema.sql —— 全量建表(54 张表)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、患者与就诊
-- -----------------------------------------------------------------------------

-- 患者信息表
CREATE TABLE lis_patient (
    id              BIGSERIAL PRIMARY KEY,
    patient_no      VARCHAR(32) NOT NULL,                 -- 患者编号 (HIS 同步)
    id_card_enc     VARCHAR(255),                         -- 身份证号 (AES 加密密文)
    id_card_hash    CHAR(64),                             -- 身份证号 SHA-256 哈希 (盲索引)
    name            VARCHAR(50) NOT NULL,
    gender          SMALLINT,                             -- 0未知 1男 2女
    birth_date      DATE,
    age             INT,
    phone_enc       VARCHAR(255),                         -- 手机号 (AES 加密密文)
    phone_hash      CHAR(64),                             -- 手机号 SHA-256 哈希 (盲索引)
    address         VARCHAR(255),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_patient IS '患者信息表';
CREATE UNIQUE INDEX uk_lis_patient_patient_no ON lis_patient (patient_no);
CREATE INDEX idx_lis_patient_name ON lis_patient (name);
CREATE INDEX idx_lis_patient_id_card_hash ON lis_patient (id_card_hash);
CREATE INDEX idx_lis_patient_phone_hash ON lis_patient (phone_hash);

-- 就诊记录表
CREATE TABLE lis_visit (
    id              BIGSERIAL PRIMARY KEY,
    visit_no        VARCHAR(32) NOT NULL,                 -- 就诊号
    patient_id      BIGINT NOT NULL,
    visit_type      SMALLINT,                             -- 1门诊 2住院 3体检
    department      VARCHAR(50),                          -- 就诊科室
    bed_no          VARCHAR(20),                          -- 床位号 (住院)
    doctor_id       BIGINT,                               -- 开单医生
    doctor_name     VARCHAR(50),
    visit_time      TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_visit IS '就诊记录表';
CREATE UNIQUE INDEX uk_lis_visit_visit_no ON lis_visit (visit_no);
CREATE INDEX idx_lis_visit_patient ON lis_visit (patient_id);
CREATE INDEX idx_lis_visit_visit_time ON lis_visit (visit_time);

-- -----------------------------------------------------------------------------
-- 二、检验申请与标本
-- -----------------------------------------------------------------------------

-- 检验申请表
CREATE TABLE lis_lab_order (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL,                 -- 申请单号
    visit_id        BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    order_type      SMALLINT,                             -- 1常规 2急诊 3体检
    status          SMALLINT DEFAULT 0,                   -- 0待处理 1已接收 2已作废
    apply_doctor    VARCHAR(50),                          -- 申请医生
    apply_dept      VARCHAR(50),                          -- 申请科室
    clinical_diag   TEXT,                                 -- 临床诊断
    apply_time      TIMESTAMP,                            -- 申请时间
    receive_time    TIMESTAMP,                            -- 接收时间
    cancel_time     TIMESTAMP,                            -- 作废时间
    his_sync_status SMALLINT DEFAULT 0,                   -- HIS 同步状态
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_lab_order IS '检验申请表';
CREATE UNIQUE INDEX uk_lis_lab_order_order_no ON lis_lab_order (order_no);
CREATE INDEX idx_lis_lab_order_visit ON lis_lab_order (visit_id);
CREATE INDEX idx_lis_lab_order_patient ON lis_lab_order (patient_id);
CREATE INDEX idx_lis_lab_order_status_time ON lis_lab_order (status, apply_time);

-- 申请项目明细表
CREATE TABLE lis_order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,                      -- 检验项目 ID
    test_item_code  VARCHAR(32),                          -- 项目代码
    test_item_name  VARCHAR(100),                         -- 项目名称
    status          SMALLINT DEFAULT 0,                   -- 0待检 1已检 2已报告
    fee             DECIMAL(10,2),                        -- 费用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_order_item IS '申请项目明细表';
CREATE INDEX idx_lis_order_item_order ON lis_order_item (order_id);
CREATE INDEX idx_lis_order_item_item ON lis_order_item (test_item_id);

-- 标本表
CREATE TABLE lis_sample (
    id              BIGSERIAL PRIMARY KEY,
    sample_barcode  VARCHAR(32) NOT NULL,                 -- 标本条码
    order_id        BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    visit_id        BIGINT,
    patient_name    VARCHAR(50),                          -- 患者姓名 (冗余便于查询)
    sample_type     VARCHAR(20) NOT NULL,                 -- 标本类型
    container_type  VARCHAR(20),                          -- 容器类型
    status          SMALLINT DEFAULT 0,                   -- 0已申请 1已采集 2已送检 3已签收 4已核收 5检测中 6已报告 7已退费 8已作废 9已拒收
    is_urgent       SMALLINT DEFAULT 0,                   -- 急诊标志
    collect_time    TIMESTAMP,                            -- 采集时间
    receive_time    TIMESTAMP,                            -- 核收时间
    test_start_time TIMESTAMP,                            -- 检测开始
    report_time     TIMESTAMP,                            -- 报告时间
    operator_id     BIGINT,                               -- 核收操作员
    department      VARCHAR(50),                          -- 送检科室
    remark          TEXT,
    deleted         SMALLINT DEFAULT 0,                   -- 逻辑删除: 0未删除 1已删除
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_sample IS '标本表';
CREATE UNIQUE INDEX uk_lis_sample_barcode ON lis_sample (sample_barcode);
CREATE INDEX idx_lis_sample_order ON lis_sample (order_id);
CREATE INDEX idx_lis_sample_patient ON lis_sample (patient_id);
CREATE INDEX idx_lis_sample_status ON lis_sample (status);
CREATE INDEX idx_lis_sample_urgent_status ON lis_sample (is_urgent, status);
CREATE INDEX idx_lis_sample_receive_time ON lis_sample (receive_time);

-- -----------------------------------------------------------------------------
-- 三、检验项目与结果
-- -----------------------------------------------------------------------------

-- 检验项目字典表
CREATE TABLE lis_test_item (
    id              BIGSERIAL PRIMARY KEY,
    item_code       VARCHAR(32) NOT NULL,                 -- 项目代码
    item_name       VARCHAR(100) NOT NULL,                -- 项目中文名
    item_name_en    VARCHAR(100),                         -- 项目英文名
    short_name      VARCHAR(50),                          -- 简称/缩写
    category        VARCHAR(50),                          -- 分类: 血常规/生化/免疫...
    specimen_type   VARCHAR(20),                          -- 适用标本类型
    unit            VARCHAR(30),                          -- 单位
    method          VARCHAR(50),                          -- 检测方法
    is_panel        SMALLINT DEFAULT 0,                   -- 是否组合项目
    parent_id       BIGINT,                               -- 父项目 (组合项目)
    price           DECIMAL(10,2),
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_test_item IS '检验项目字典表';
CREATE UNIQUE INDEX uk_lis_test_item_code ON lis_test_item (item_code);
CREATE INDEX idx_lis_test_item_category ON lis_test_item (category);
CREATE INDEX idx_lis_test_item_parent ON lis_test_item (parent_id);

-- 参考范围表
CREATE TABLE lis_reference_range (
    id              BIGSERIAL PRIMARY KEY,
    test_item_id    BIGINT NOT NULL,
    gender          SMALLINT,                             -- 性别限制: null全部 1男 2女
    age_min         DECIMAL(5,1),                         -- 最小年龄
    age_max         DECIMAL(5,1),                         -- 最大年龄
    ref_low         VARCHAR(30),                          -- 参考下限
    ref_high        VARCHAR(30),                          -- 参考上限
    ref_text        VARCHAR(100),                         -- 文字参考范围 (如阴性)
    critical_low    VARCHAR(30),                          -- 危急值下限
    critical_high   VARCHAR(30),                          -- 危急值上限
    unit            VARCHAR(30)
);
COMMENT ON TABLE lis_reference_range IS '参考范围表';
CREATE INDEX idx_lis_reference_range_item ON lis_reference_range (test_item_id);

-- 检验结果表
CREATE TABLE lis_result (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,
    result_value    VARCHAR(100),                         -- 结果值
    result_text     TEXT,                                 -- 文字结果
    unit            VARCHAR(30),                          -- 单位
    ref_range       VARCHAR(100),                         -- 参考范围
    abnormal_flag   VARCHAR(10),                          -- 异常标志: N正常 H高 L低
    is_critical     SMALLINT DEFAULT 0,                   -- 是否危急值
    instrument_id   BIGINT,                               -- 检测仪器
    operator_id     BIGINT,                               -- 操作员
    test_time       TIMESTAMP,                            -- 检测时间
    audit_status    SMALLINT DEFAULT 0,                   -- 0未审核 1自动审核 2人工审核
    auditor_id      BIGINT,                               -- 审核人
    audit_time      TIMESTAMP,                            -- 审核时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_result IS '检验结果表';
CREATE INDEX idx_lis_result_sample ON lis_result (sample_id);
CREATE INDEX idx_lis_result_item ON lis_result (test_item_id);
CREATE INDEX idx_lis_result_critical ON lis_result (is_critical, audit_status);
CREATE INDEX idx_lis_result_patient_item ON lis_result (sample_id, test_item_id, test_time);

-- -----------------------------------------------------------------------------
-- 四、报告管理
-- -----------------------------------------------------------------------------

-- 检验报告表
CREATE TABLE lis_report (
    id              BIGSERIAL PRIMARY KEY,
    report_no       VARCHAR(32) NOT NULL,                 -- 报告编号
    sample_id       BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    patient_name    VARCHAR(50),                          -- 患者姓名 (冗余)
    report_type     SMALLINT,                             -- 1常规 2急诊 3微生物
    status          SMALLINT DEFAULT 0,                   -- 0草稿 1已审核 2已发布 3已修改
    auditor_id      BIGINT,                               -- 审核人
    audit_time      TIMESTAMP,                            -- 审核时间
    publisher_id    BIGINT,                               -- 发布人
    publish_time    TIMESTAMP,                            -- 发布时间
    report_doctor   VARCHAR(50),                          -- 报告医生
    checker         VARCHAR(50),                          -- 检验者
    pdf_path        VARCHAR(255),                         -- PDF 文件路径
    his_sync_status SMALLINT DEFAULT 0,                   -- HIS 同步状态
    his_sync_time   TIMESTAMP,                            -- HIS 同步时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_report IS '检验报告表';
CREATE UNIQUE INDEX uk_lis_report_report_no ON lis_report (report_no);
CREATE INDEX idx_lis_report_sample ON lis_report (sample_id);
CREATE INDEX idx_lis_report_patient ON lis_report (patient_id);
CREATE INDEX idx_lis_report_status ON lis_report (status);
CREATE INDEX idx_lis_report_publish_time ON lis_report (publish_time);

-- 报告修改记录表 (留痕)
CREATE TABLE lis_report_modify_log (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL,
    item_id         BIGINT,                               -- 修改的项目
    old_value       TEXT,                                 -- 修改前值
    new_value       TEXT,                                 -- 修改后值
    modify_reason   VARCHAR(255),                         -- 修改原因
    operator_id     BIGINT,                               -- 操作人
    operator_name   VARCHAR(50),
    modify_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_report_modify_log IS '报告修改记录表';
CREATE INDEX idx_lis_report_modify_log_report ON lis_report_modify_log (report_id);
CREATE INDEX idx_lis_report_modify_log_operator ON lis_report_modify_log (operator_id);

-- -----------------------------------------------------------------------------
-- 五、仪器与质控
-- -----------------------------------------------------------------------------

-- 仪器字典表
CREATE TABLE lis_instrument (
    id              BIGSERIAL PRIMARY KEY,
    instrument_code VARCHAR(32) NOT NULL,                 -- 仪器编码
    instrument_name VARCHAR(100) NOT NULL,                -- 仪器名称
    brand           VARCHAR(50),                          -- 品牌
    model           VARCHAR(50),                          -- 型号
    protocol_type   VARCHAR(20),                          -- 协议类型: ASTM/HL7/LIS01
    connect_type    VARCHAR(20),                          -- 连接方式: SERIAL/TCP
    serial_port     VARCHAR(20),                          -- 串口号 (SERIAL)
    baud_rate       INT,                                  -- 波特率
    ip_address      VARCHAR(50),                          -- IP (TCP)
    port            INT,                                  -- 端口 (TCP)
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    last_online_time TIMESTAMP,                           -- 最后在线时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument IS '仪器字典表';
CREATE UNIQUE INDEX uk_lis_instrument_code ON lis_instrument (instrument_code);
CREATE INDEX idx_lis_instrument_status ON lis_instrument (status);

-- 仪器项目映射表
CREATE TABLE lis_instrument_item_map (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,                      -- LIS 项目 ID
    instrument_item_code VARCHAR(32),                     -- 仪器项目代码
    instrument_item_name VARCHAR(100)                     -- 仪器项目名称
);
COMMENT ON TABLE lis_instrument_item_map IS '仪器项目映射表';
CREATE UNIQUE INDEX uk_lis_instrument_item_map ON lis_instrument_item_map (instrument_id, test_item_id);
CREATE INDEX idx_lis_instrument_item_map_instrument ON lis_instrument_item_map (instrument_id);
CREATE INDEX idx_lis_instrument_item_map_item ON lis_instrument_item_map (test_item_id);

-- 质控品批号表
CREATE TABLE lis_qc_lot (
    id              BIGSERIAL PRIMARY KEY,
    lot_no          VARCHAR(32) NOT NULL,                 -- 批号
    test_item_id    BIGINT NOT NULL,                      -- 检验项目
    instrument_id   BIGINT,                               -- 仪器
    target_value    DECIMAL(10,4),                        -- 靶值
    sd              DECIMAL(10,4),                        -- 标准差
    cv              DECIMAL(5,2),                         -- 变异系数
    level           SMALLINT,                             -- 质控级别: 1低 2中 3高
    start_date      DATE,                                 -- 启用日期
    end_date        DATE,                                 -- 失效日期
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_lot IS '质控品批号表';
CREATE INDEX idx_lis_qc_lot_item_lot ON lis_qc_lot (test_item_id, lot_no);
CREATE INDEX idx_lis_qc_lot_instrument ON lis_qc_lot (instrument_id);

-- 质控记录表
CREATE TABLE lis_qc_record (
    id              BIGSERIAL PRIMARY KEY,
    qc_lot_id       BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,
    instrument_id   BIGINT,
    measure_value   DECIMAL(10,4),                        -- 测量值
    measure_time    TIMESTAMP,                            -- 测量时间
    operator_id     BIGINT,
    z_score         DECIMAL(5,2),                         -- Z 分数
    rule_violation  VARCHAR(50),                          -- 违反规则 (如 1-3s)
    qc_result       VARCHAR(20),                          -- ACCEPT在控 / REJECT失控
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_record IS '质控记录表';
CREATE INDEX idx_lis_qc_record_lot_time ON lis_qc_record (qc_lot_id, measure_time);
CREATE INDEX idx_lis_qc_record_item ON lis_qc_record (test_item_id);
CREATE INDEX idx_lis_qc_record_instrument ON lis_qc_record (instrument_id);

-- -----------------------------------------------------------------------------
-- 六、系统管理
-- -----------------------------------------------------------------------------

-- 系统用户表
CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL,                 -- 登录用户名
    password        VARCHAR(100) NOT NULL,                -- BCrypt 加密密码
    real_name       VARCHAR(50),                          -- 真实姓名
    phone           VARCHAR(20),
    email           VARCHAR(100),
    department_id   BIGINT,
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    last_login_time TIMESTAMP,                            -- 最后登录时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_user IS '系统用户表';
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
CREATE INDEX idx_sys_user_dept ON sys_user (department_id);

-- 角色表
CREATE TABLE sys_role (
    id              BIGSERIAL PRIMARY KEY,
    role_code       VARCHAR(50) NOT NULL,                 -- 角色编码
    role_name       VARCHAR(50) NOT NULL,                 -- 角色名称
    description     VARCHAR(255),                         -- 描述
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_role IS '系统角色表';
CREATE UNIQUE INDEX uk_sys_role_code ON sys_role (role_code);

-- 权限表
CREATE TABLE sys_permission (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       BIGINT DEFAULT 0,                     -- 父级权限 ID
    perm_code       VARCHAR(100) NOT NULL,                -- 权限编码
    perm_name       VARCHAR(100) NOT NULL,                -- 权限名称
    perm_type       SMALLINT,                             -- 1菜单 2按钮 3接口
    path            VARCHAR(255),                         -- 路由路径
    component       VARCHAR(255),                         -- 前端组件
    icon            VARCHAR(50),                          -- 图标
    sort_order      INT DEFAULT 0,                        -- 排序
    status          SMALLINT DEFAULT 1                    -- 0停用 1启用
);
COMMENT ON TABLE sys_permission IS '系统权限表';
CREATE UNIQUE INDEX uk_sys_permission_code ON sys_permission (perm_code);
CREATE INDEX idx_sys_permission_parent ON sys_permission (parent_id);

-- 用户角色关联表
CREATE TABLE sys_user_role (
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户角色关联表';
CREATE INDEX idx_sys_user_role_role ON sys_user_role (role_id);

-- 角色权限关联表
CREATE TABLE sys_role_permission (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
COMMENT ON TABLE sys_role_permission IS '角色权限关联表';
CREATE INDEX idx_sys_role_permission_permission ON sys_role_permission (permission_id);

-- 操作日志表
CREATE TABLE sys_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(50),
    module          VARCHAR(50),                          -- 模块
    operation       VARCHAR(100),                         -- 操作描述
    method          VARCHAR(200),                         -- 请求方法
    params          TEXT,                                 -- 请求参数
    result          TEXT,                                 -- 返回结果
    ip              VARCHAR(50),                          -- IP 地址
    cost_time       INT,                                  -- 耗时(ms)
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_audit_log IS '操作日志表';
CREATE INDEX idx_sys_audit_log_user_time ON sys_audit_log (user_id, create_time);
CREATE INDEX idx_sys_audit_log_create_time ON sys_audit_log (create_time);
CREATE INDEX idx_sys_audit_log_module ON sys_audit_log (module);

-- 室间质控记录表
CREATE TABLE lis_eqa_record (
    id              BIGSERIAL PRIMARY KEY,
    eqa_batch_no    VARCHAR(64),
    test_item_id    BIGINT,
    instrument_id   BIGINT,
    target_value    DECIMAL(10,4),
    measure_value   DECIMAL(10,4),
    sd              DECIMAL(10,4),
    cv              DECIMAL(5,2),
    score           DECIMAL(5,2),
    grade           VARCHAR(2),
    passed          SMALLINT DEFAULT 0,
    measure_time    TIMESTAMP,
    result_time     TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_eqa_record IS '室间质控记录表';

-- 仪器通讯日志表
CREATE TABLE lis_instrument_comm_log (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    direction       VARCHAR(10),
    raw_message     TEXT,
    protocol_type   VARCHAR(20),
    parse_status    VARCHAR(10),
    parse_result    TEXT,
    message_time    TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument_comm_log IS '仪器通讯日志表';
CREATE INDEX idx_instrument_comm_log_instrument ON lis_instrument_comm_log (instrument_id, create_time);

-- 科室表
CREATE TABLE sys_department (
    id              BIGSERIAL PRIMARY KEY,
    dept_code       VARCHAR(50) NOT NULL UNIQUE,
    dept_name       VARCHAR(100) NOT NULL,
    parent_id       BIGINT DEFAULT 0,
    sort_order      INT DEFAULT 0,
    status          SMALLINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_department IS '科室表';

-- 系统参数表
CREATE TABLE sys_config (
    id              BIGSERIAL PRIMARY KEY,
    config_key      VARCHAR(100) NOT NULL UNIQUE,
    config_value    TEXT,
    config_name     VARCHAR(100),
    remark          VARCHAR(255),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_config IS '系统参数表';

-- 微生物报告表
CREATE TABLE lis_micro_report (
    id              BIGSERIAL PRIMARY KEY,
    report_no       VARCHAR(32) NOT NULL UNIQUE,
    sample_id       BIGINT NOT NULL,
    patient_id      BIGINT,
    patient_name    VARCHAR(50),
    specimen_type   VARCHAR(50),
    culture_site    VARCHAR(100),
    report_status   SMALLINT DEFAULT 1,
    auditor_id      BIGINT,
    audit_time      TIMESTAMP,
    publish_time    TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_report IS '微生物报告表';

-- 血培养专用字段扩展
ALTER TABLE lis_micro_report ADD COLUMN bottle_no            VARCHAR(32);   -- 培养瓶号
ALTER TABLE lis_micro_report ADD COLUMN bottle_type         VARCHAR(50);   -- 瓶类型: 成人瓶/儿童瓶/厌氧瓶/需氧瓶
ALTER TABLE lis_micro_report ADD COLUMN volume              VARCHAR(50);   -- 血量
ALTER TABLE lis_micro_report ADD COLUMN inoculate_time      TIMESTAMP;     -- 接种时间
ALTER TABLE lis_micro_report ADD COLUMN alarm_time          TIMESTAMP;     -- 阳性报警时间
ALTER TABLE lis_micro_report ADD COLUMN ttp_hours           DECIMAL(5,1);  -- TTP小时数(报警-接种)
ALTER TABLE lis_micro_report ADD COLUMN blood_culture_id    BIGINT;        -- 关联血培养仪记录ID
ALTER TABLE lis_micro_report ADD COLUMN blood_culture_result VARCHAR(20);  -- 血培养结果: POSITIVE/NEGATIVE/PENDING
CREATE INDEX idx_lis_micro_report_bottle_no ON lis_micro_report (bottle_no);
CREATE INDEX idx_lis_micro_report_bc_result ON lis_micro_report (blood_culture_result);

-- 细菌鉴定表
CREATE TABLE lis_micro_organism (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT NOT NULL,
    organism_name   VARCHAR(100),
    organism_code   VARCHAR(50),
    quantity        VARCHAR(50),
    culture_method  VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_organism IS '细菌鉴定表';

-- 药敏结果表
CREATE TABLE lis_antibiotic_sensitivity (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT NOT NULL,
    organism_id     BIGINT,
    antibiotic_name VARCHAR(100),
    antibiotic_code VARCHAR(50),
    mic_value       VARCHAR(30),
    result          VARCHAR(2),
    breakpoint      VARCHAR(30),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_antibiotic_sensitivity IS '药敏结果表';

-- 微生物报告转种记录表
CREATE TABLE lis_micro_report_subculture (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT NOT NULL,                         -- 微生物报告ID
    subculture_time TIMESTAMP,                               -- 转种时间
    medium          VARCHAR(50),                             -- 转种培养基
    incubator_no    VARCHAR(32),                             -- 培养箱号
    result          VARCHAR(255),                            -- 转种结果
    operator        VARCHAR(50),                             -- 操作人
    remark          TEXT,                                    -- 备注
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_report_subculture IS '微生物报告转种记录表';
CREATE INDEX idx_lis_micro_report_subculture_report ON lis_micro_report_subculture (micro_report_id);

-- =============================================================================
-- 七、仪器管理模块 (设备全生命周期与运维)
-- =============================================================================

-- 仪器维护模板表
CREATE TABLE lis_maintenance_template (
    id              BIGSERIAL PRIMARY KEY,
    template_code   VARCHAR(32) NOT NULL,                 -- 模板编码
    template_name   VARCHAR(100) NOT NULL,                -- 模板名称
    instrument_type VARCHAR(50),                          -- 适用仪器类型
    cycle_type      VARCHAR(20),                          -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_maintenance_template IS '仪器维护模板表';
CREATE UNIQUE INDEX uk_lis_maintenance_template_code ON lis_maintenance_template (template_code);

-- 仪器维护模板明细表
CREATE TABLE lis_maintenance_template_item (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    item_code       VARCHAR(32) NOT NULL,                 -- 维护项编码
    item_name       VARCHAR(100) NOT NULL,                -- 维护项名称
    item_type       SMALLINT,                             -- 1开关机 2清洗 3耗材 4质控 5其他
    expected_value  VARCHAR(100),                         -- 预期结果
    sort_order      INT DEFAULT 0
);
COMMENT ON TABLE lis_maintenance_template_item IS '仪器维护模板明细表';
CREATE INDEX idx_lis_maint_template_item_tpl ON lis_maintenance_template_item (template_id);

-- 仪器维护记录表
CREATE TABLE lis_instrument_maintenance_log (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    template_id     BIGINT,                               -- 关联模板
    maintenance_date DATE NOT NULL,                       -- 维护日期
    maintenance_type VARCHAR(20),                         -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    operator_id     BIGINT,                               -- 执行人
    operator_name   VARCHAR(50),
    reviewer_id     BIGINT,                               -- 复核人
    reviewer_name   VARCHAR(50),
    result          SMALLINT DEFAULT 0,                   -- 0异常 1正常 2未执行
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument_maintenance_log IS '仪器维护记录表';
CREATE INDEX idx_lis_instrument_maint_log_inst ON lis_instrument_maintenance_log (instrument_id, maintenance_date);

-- 仪器维护记录明细表
CREATE TABLE lis_instrument_maintenance_log_item (
    id              BIGSERIAL PRIMARY KEY,
    log_id          BIGINT NOT NULL,
    item_code       VARCHAR(32),
    item_name       VARCHAR(100),
    expected_value  VARCHAR(100),
    actual_value    VARCHAR(100),
    result          SMALLINT DEFAULT 0,                   -- 0异常 1正常 2未执行
    abnormal_reason TEXT
);
COMMENT ON TABLE lis_instrument_maintenance_log_item IS '仪器维护记录明细表';
CREATE INDEX idx_lis_instrument_maint_log_item_log ON lis_instrument_maintenance_log_item (log_id);

-- 仪器保养/维修/校准计划表
CREATE TABLE lis_instrument_maintenance_plan (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    plan_type       VARCHAR(20),                          -- PM/REPAIR/CALIBRATION
    cycle_type      VARCHAR(20),                          -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    first_date      DATE,                                 -- 首次日期
    next_date       DATE,                                 -- 下次执行日期
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument_maintenance_plan IS '仪器保养/维修/校准计划表';
CREATE INDEX idx_lis_instrument_maint_plan_inst ON lis_instrument_maintenance_plan (instrument_id, next_date);

-- 仪器保养/维修/校准任务表
CREATE TABLE lis_instrument_maintenance_task (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL,
    instrument_id   BIGINT NOT NULL,
    plan_type       VARCHAR(20),                          -- PM/REPAIR/CALIBRATION
    scheduled_date  DATE,                                 -- 计划日期
    finish_date     DATE,                                 -- 完成日期
    operator_id     BIGINT,
    status          SMALLINT DEFAULT 0,                   -- 0待执行 1已完成 2逾期
    cost            DECIMAL(10,2),                        -- 费用
    content         TEXT
);
COMMENT ON TABLE lis_instrument_maintenance_task IS '仪器保养/维修/校准任务表';
CREATE INDEX idx_lis_instrument_maint_task_inst ON lis_instrument_maintenance_task (instrument_id, status);

-- =============================================================================
-- 八、试剂管理模块
-- =============================================================================

-- 试剂字典表
CREATE TABLE lis_reagent (
    id              BIGSERIAL PRIMARY KEY,
    reagent_code    VARCHAR(32) NOT NULL,                 -- 试剂编码
    reagent_name    VARCHAR(100) NOT NULL,                -- 试剂名称
    specification   VARCHAR(50),                          -- 规格
    manufacturer    VARCHAR(100),                         -- 生产厂家
    cas_no          VARCHAR(50),                          -- CAS 号
    storage_cond    VARCHAR(50),                          -- 储存条件
    unit            VARCHAR(20),                          -- 单位
    instrument_id   BIGINT,                               -- 适用仪器
    test_item_id    BIGINT,                               -- 关联项目
    safety_stock    DECIMAL(10,2),                        -- 安全库存
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent IS '试剂字典表';
CREATE UNIQUE INDEX uk_lis_reagent_code ON lis_reagent (reagent_code);
CREATE INDEX idx_lis_reagent_instrument ON lis_reagent (instrument_id);
CREATE INDEX idx_lis_reagent_item ON lis_reagent (test_item_id);

-- 试剂库存表 (按批次管理)
CREATE TABLE lis_reagent_stock (
    id              BIGSERIAL PRIMARY KEY,
    reagent_id      BIGINT NOT NULL,
    lot_no          VARCHAR(32) NOT NULL,                 -- 批号
    barcode         VARCHAR(64),                          -- 二维码/条码
    expiry_date     DATE NOT NULL,                       -- 效期
    quantity        DECIMAL(10,2),                        -- 当前数量
    location        VARCHAR(100),                         -- 存放位置
    status          SMALLINT DEFAULT 1,                   -- 0过期锁定 1可用 2已耗尽
    in_stock_time   TIMESTAMP,                            -- 入库时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock IS '试剂库存表';
CREATE UNIQUE INDEX uk_lis_reagent_stock_lot ON lis_reagent_stock (reagent_id, lot_no);
CREATE INDEX idx_lis_reagent_stock_expiry ON lis_reagent_stock (expiry_date);
CREATE INDEX idx_lis_reagent_stock_status ON lis_reagent_stock (status);

-- 试剂出入库记录表
CREATE TABLE lis_reagent_stock_record (
    id              BIGSERIAL PRIMARY KEY,
    stock_id        BIGINT NOT NULL,
    reagent_id      BIGINT NOT NULL,
    record_type     SMALLINT,                             -- 1入库 2出库 3报损 4退库 5盘点
    quantity        DECIMAL(10,2),                        -- 正数入库/负数出库
    before_qty      DECIMAL(10,2),
    after_qty       DECIMAL(10,2),
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    source_no       VARCHAR(32),                          -- 采购单号/领用单号
    related_id      BIGINT,                               -- 关联业务 ID (结果/质控)
    related_type    VARCHAR(20),                          -- RESULT / QC
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_record IS '试剂出入库记录表';
CREATE INDEX idx_lis_reagent_stock_record_reagent ON lis_reagent_stock_record (reagent_id);
CREATE INDEX idx_lis_reagent_stock_record_time ON lis_reagent_stock_record (create_time);

-- =============================================================================
-- 九、微生物模块扩展表 (独立模块补充)
-- =============================================================================

-- 抗生素字典表
CREATE TABLE lis_antibiotic (
    id              BIGSERIAL PRIMARY KEY,
    antibiotic_code VARCHAR(32) NOT NULL,                 -- 抗生素编码
    antibiotic_name VARCHAR(100) NOT NULL,                -- 抗生素名称
    category        VARCHAR(50),                          -- 分类: β-内酰胺类/氨基糖苷类...
    unit            VARCHAR(20),
    status          SMALLINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_antibiotic IS '抗生素字典表';
CREATE UNIQUE INDEX uk_lis_antibiotic_code ON lis_antibiotic (antibiotic_code);

-- 细菌字典表
CREATE TABLE lis_bacteria (
    id              BIGSERIAL PRIMARY KEY,
    bacteria_code   VARCHAR(32) NOT NULL,                   -- 细菌编码
    bacteria_name   VARCHAR(100) NOT NULL,                  -- 中文名称
    bacteria_name_latin VARCHAR(150),                       -- 拉丁学名
    gram_type       VARCHAR(10),                            -- 革兰氏属性: G+/G-/真菌/抗酸菌
    bacteria_category VARCHAR(50),                          -- 菌属分类
    morphology      VARCHAR(30),                            -- 形态: 球菌/杆菌/球杆菌/弯曲杆菌/酵母样/丝状真菌
    is_common       SMALLINT DEFAULT 0,                     -- 是否常见菌: 1是 0否
    status          SMALLINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_bacteria IS '细菌字典表';
CREATE UNIQUE INDEX uk_lis_bacteria_code ON lis_bacteria (bacteria_code);
CREATE INDEX idx_lis_bacteria_gram ON lis_bacteria (gram_type);
CREATE INDEX idx_lis_bacteria_category ON lis_bacteria (bacteria_category);

-- 抗生素折点表
CREATE TABLE lis_antibiotic_breakpoint (
    id              BIGSERIAL PRIMARY KEY,
    antibiotic_id   BIGINT NOT NULL,                      -- 抗生素ID，关联 lis_antibiotic.id
    bacteria_id     BIGINT NOT NULL,                      -- 细菌ID，关联 lis_bacteria.id
    method          VARCHAR(30),                          -- 方法学: K-B纸片法/微量肉汤法/E-test
    standard        VARCHAR(20),                          -- 标准: CLSI/EUCAST
    standard_version VARCHAR(30),                         -- 标准版本，如 M100-2023
    s_breakpoint    VARCHAR(30),                          -- 敏感界值(S)，如 ≤2
    r_breakpoint    VARCHAR(30),                          -- 耐药界值(R)，如 ≥8
    i_breakpoint    VARCHAR(30),                          -- 中介界值(I)
    s_dd            VARCHAR(30),                          -- K-B法敏感抑菌圈直径，如 ≥18
    r_dd            VARCHAR(30),                          -- K-B法耐药抑菌圈直径，如 ≤14
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_antibiotic_breakpoint IS '抗生素折点表';
COMMENT ON COLUMN lis_antibiotic_breakpoint.antibiotic_id IS '抗生素ID，关联 lis_antibiotic.id';
COMMENT ON COLUMN lis_antibiotic_breakpoint.bacteria_id IS '细菌ID，关联 lis_bacteria.id';
COMMENT ON COLUMN lis_antibiotic_breakpoint.method IS '方法学: K-B纸片法/微量肉汤法/E-test';
COMMENT ON COLUMN lis_antibiotic_breakpoint.standard IS '标准: CLSI/EUCAST';
COMMENT ON COLUMN lis_antibiotic_breakpoint.standard_version IS '标准版本，如 M100-2023';
COMMENT ON COLUMN lis_antibiotic_breakpoint.s_breakpoint IS '敏感界值(S)，如 ≤2';
COMMENT ON COLUMN lis_antibiotic_breakpoint.r_breakpoint IS '耐药界值(R)，如 ≥8';
COMMENT ON COLUMN lis_antibiotic_breakpoint.i_breakpoint IS '中介界值(I)';
COMMENT ON COLUMN lis_antibiotic_breakpoint.s_dd IS 'K-B法敏感抑菌圈直径，如 ≥18';
COMMENT ON COLUMN lis_antibiotic_breakpoint.r_dd IS 'K-B法耐药抑菌圈直径，如 ≤14';
COMMENT ON COLUMN lis_antibiotic_breakpoint.status IS '0停用 1启用';
CREATE INDEX idx_lis_antibiotic_breakpoint_ab ON lis_antibiotic_breakpoint (antibiotic_id);
CREATE INDEX idx_lis_antibiotic_breakpoint_ba ON lis_antibiotic_breakpoint (bacteria_id);
CREATE INDEX idx_lis_antibiotic_breakpoint_pair ON lis_antibiotic_breakpoint (antibiotic_id, bacteria_id);
CREATE INDEX idx_lis_antibiotic_breakpoint_std ON lis_antibiotic_breakpoint (standard, standard_version);
CREATE INDEX idx_lis_antibiotic_breakpoint_status ON lis_antibiotic_breakpoint (status);

-- 微生物报告打印模板配置表
CREATE TABLE lis_micro_report_print_config (
    id              BIGSERIAL PRIMARY KEY,
    config_name     VARCHAR(100),
    show_mic        SMALLINT DEFAULT 1,
    show_kb         SMALLINT DEFAULT 1,
    show_breakpoint SMALLINT DEFAULT 0,
    show_method     SMALLINT DEFAULT 0,
    hidden_antibiotics TEXT,                              -- JSON 数组: 隐藏的抗生素
    order_by_categories TEXT,                             -- JSON 数组: 排序
    breakpoint_version VARCHAR(30),
    paper_size      VARCHAR(10) DEFAULT 'A4',
    is_default      SMALLINT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_report_print_config IS '微生物报告打印模板配置表';

-- 血培养记录表
CREATE TABLE lis_blood_culture (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    bottle_no       VARCHAR(32) NOT NULL,                 -- 培养瓶号
    medium_type     VARCHAR(50),                          -- 培养基类型
    instrument_id   BIGINT,                               -- 血培养仪
    inoculation_time TIMESTAMP,                           -- 接种时间
    alarm_time      TIMESTAMP,                            -- 阳性报警时间
    final_result    VARCHAR(20),                          -- POSITIVE/NEGATIVE/CONTAMINATED
    final_time      TIMESTAMP,                            -- 最终报告时间
    ttp_hours       DECIMAL(5,1),                         -- 报警时间 - 接种时间
    operator_id     BIGINT,
    status          SMALLINT DEFAULT 0,                   -- 0培养中 1阳性 2阴性 3污染
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_blood_culture IS '血培养记录表';
CREATE INDEX idx_lis_blood_culture_sample ON lis_blood_culture (sample_id);
CREATE UNIQUE INDEX uk_lis_blood_culture_bottle ON lis_blood_culture (bottle_no);

-- 血培养转种记录表
CREATE TABLE lis_blood_culture_subculture (
    id              BIGSERIAL PRIMARY KEY,
    blood_culture_id BIGINT NOT NULL,
    subculture_time TIMESTAMP,                            -- 转种时间
    medium          VARCHAR(50),                          -- 转种培养基
    incubator_no    VARCHAR(32),                          -- 培养箱号
    operator_id     BIGINT,
    result          VARCHAR(255),                         -- 转种结果
    remark          TEXT
);
COMMENT ON TABLE lis_blood_culture_subculture IS '血培养转种记录表';
CREATE INDEX idx_lis_blood_culture_subculture_bc ON lis_blood_culture_subculture (blood_culture_id);

-- 特殊耐药标记/危急值规则表
CREATE TABLE lis_resistance_rule (
    id              BIGSERIAL PRIMARY KEY,
    organism_code   VARCHAR(32),                          -- 菌株编码
    antibiotic_codes TEXT,                                -- JSON 数组: 耐药抗生素
    rule_name       VARCHAR(100),                         -- 规则名称: MRSA/CRE/CRAB
    is_critical     SMALLINT DEFAULT 1,                   -- 是否触发危急值
    alert_message   VARCHAR(255),                         -- 通知文案
    status          SMALLINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_resistance_rule IS '特殊耐药标记/危急值规则表';

-- =============================================================================
-- 十、人事管理模块
-- =============================================================================

-- 小组/科室表
CREATE TABLE lis_lab_group (
    id              BIGSERIAL PRIMARY KEY,
    group_code      VARCHAR(32) NOT NULL,                 -- 小组编码
    group_name      VARCHAR(100) NOT NULL,                -- 小组名称
    parent_id       BIGINT DEFAULT 0,                     -- 父级小组
    leader_id       BIGINT,                               -- 组长 (staff_id)
    leader_name     VARCHAR(50),
    location        VARCHAR(100),                         -- 工作地点
    sort_order      INT DEFAULT 0,
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_lab_group IS '小组/科室表';
CREATE UNIQUE INDEX uk_lis_lab_group_code ON lis_lab_group (group_code);
CREATE INDEX idx_lis_lab_group_parent ON lis_lab_group (parent_id);

-- 小组与仪器关联表
CREATE TABLE lis_group_instrument (
    group_id        BIGINT NOT NULL,
    instrument_id   BIGINT NOT NULL,
    PRIMARY KEY (group_id, instrument_id)
);
COMMENT ON TABLE lis_group_instrument IS '小组与仪器关联表';

-- 人员档案表
CREATE TABLE lis_staff (
    id              BIGSERIAL PRIMARY KEY,
    staff_no        VARCHAR(32) NOT NULL,                 -- 工号
    name            VARCHAR(50) NOT NULL,
    phone           VARCHAR(32),
    email           VARCHAR(100),
    group_id        BIGINT,                               -- 所属小组
    user_id         BIGINT,                               -- 关联系统账号
    education       VARCHAR(20),                          -- 学历
    major           VARCHAR(50),                          -- 专业
    entry_date      DATE,                                 -- 入职日期
    leave_date      DATE,                                 -- 离职日期
    id_card         VARCHAR(64),                          -- 身份证号
    signature_image VARCHAR(255),                         -- 电子签名图片
    status          SMALLINT DEFAULT 1,                   -- 0离职 1在职 2试用期
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff IS '人员档案表';
CREATE UNIQUE INDEX uk_lis_staff_no ON lis_staff (staff_no);
CREATE INDEX idx_lis_staff_group ON lis_staff (group_id);
CREATE INDEX idx_lis_staff_user ON lis_staff (user_id);

-- 职称字典表
CREATE TABLE lis_title_dict (
    id              BIGSERIAL PRIMARY KEY,
    title_code      VARCHAR(32) NOT NULL,                 -- 职称编码
    title_name      VARCHAR(50) NOT NULL,                 -- 职称名称
    title_level     SMALLINT,                             -- 职称级别
    sort_order      INT DEFAULT 0,
    status          SMALLINT DEFAULT 1
);
COMMENT ON TABLE lis_title_dict IS '职称字典表';
CREATE UNIQUE INDEX uk_lis_title_dict_code ON lis_title_dict (title_code);

-- 人员职称记录表
CREATE TABLE lis_staff_title (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    title_code      VARCHAR(32) NOT NULL,
    title_name      VARCHAR(50),
    acquire_date    DATE,                                 -- 获得日期
    expiry_date     DATE,                                 -- 有效期
    certificate_no  VARCHAR(50),                          -- 证书编号
    attachment_url  VARCHAR(255),                         -- 证书附件
    is_current      SMALLINT DEFAULT 0,                   -- 是否当前职称
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff_title IS '人员职称记录表';
CREATE INDEX idx_lis_staff_title_staff ON lis_staff_title (staff_id);

-- 资质证书表
CREATE TABLE lis_staff_certificate (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    cert_type       VARCHAR(50) NOT NULL,                 -- 资质类型
    cert_name       VARCHAR(100) NOT NULL,                -- 证书名称
    cert_no         VARCHAR(50),                          -- 证书编号
    issue_date      DATE,                                 -- 发证日期
    expiry_date     DATE,                                 -- 到期日期
    issue_org       VARCHAR(100),                         -- 发证机构
    attachment_url  VARCHAR(255),                         -- 附件
    alert_days      INT DEFAULT 30,                       -- 提前预警天数
    status          SMALLINT DEFAULT 1,                   -- 0过期 1有效 2作废
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff_certificate IS '资质证书表';
CREATE INDEX idx_lis_staff_certificate_staff ON lis_staff_certificate (staff_id);
CREATE INDEX idx_lis_staff_certificate_expiry ON lis_staff_certificate (expiry_date);

-- 岗位授权表
CREATE TABLE lis_staff_authorization (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    auth_type       VARCHAR(50) NOT NULL,                 -- 授权类型: REPORT_AUDIT/INSTRUMENT_OP/PROJECT_OP
    auth_scope      VARCHAR(255),                         -- 授权范围
    start_date      DATE,                                 -- 授权开始
    end_date        DATE,                                 -- 授权结束
    grantor_id      BIGINT,                               -- 授权人
    grantor_name    VARCHAR(50),
    attachment_url  VARCHAR(255),
    status          SMALLINT DEFAULT 1,                   -- 0失效 1有效
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff_authorization IS '岗位授权表';
CREATE INDEX idx_lis_staff_authorization_staff ON lis_staff_authorization (staff_id);

-- 培训记录表
CREATE TABLE lis_staff_training (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    training_name   VARCHAR(100) NOT NULL,                -- 培训名称
    training_type   VARCHAR(50),                          -- 培训类型
    training_date   DATE,                                 -- 培训日期
    trainer         VARCHAR(50),                          -- 培训人
    result          VARCHAR(20),                          -- 合格/不合格
    attachment_url  VARCHAR(255),
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff_training IS '培训记录表';
CREATE INDEX idx_lis_staff_training_staff ON lis_staff_training (staff_id);

-- 文档资料表
CREATE TABLE lis_lab_document (
    id              BIGSERIAL PRIMARY KEY,
    doc_code        VARCHAR(32) NOT NULL,                 -- 文档编码
    doc_name        VARCHAR(100) NOT NULL,                -- 文档名称
    doc_type        VARCHAR(50),                          -- SOP/MANUAL/PROCEDURE/FORM
    version         VARCHAR(20) DEFAULT '1.0',            -- 版本号
    group_id        BIGINT,                               -- 适用小组
    file_url        VARCHAR(255),                         -- 文件路径
    uploader_id     BIGINT,
    upload_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status          SMALLINT DEFAULT 1                    -- 0作废 1现行 2草稿
);
COMMENT ON TABLE lis_lab_document IS '文档资料表';
CREATE UNIQUE INDEX uk_lis_lab_document_code_ver ON lis_lab_document (doc_code, version);
CREATE INDEX idx_lis_lab_document_group ON lis_lab_document (group_id);

-- 排班表
CREATE TABLE lis_staff_schedule (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    schedule_date   DATE NOT NULL,                        -- 排班日期
    shift_type      VARCHAR(20),                          -- 班次: 白班/夜班/休息
    duty_type       VARCHAR(20),                          -- 值班类型
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_staff_schedule IS '排班表';
CREATE UNIQUE INDEX uk_lis_staff_schedule_staff_date ON lis_staff_schedule (staff_id, schedule_date);


-- =============================================================================
-- 【脚本 2/2】init-data.sql —— 基础字典数据
-- (角色/管理员账号/检验项目/参考范围/权限菜单/仪器示例/试剂/微生物/人事/系统配置)
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
-- 旧版内置哈希与 123456 不匹配，此处已修正；仍为旧哈希的库会被自动纠正
-- -----------------------------------------------------------------------------
INSERT INTO sys_user (username, password, real_name, phone, email, status) VALUES
('admin', '$2a$10$ItmFQKXKPVCUs.ElcNto4.PxWs5uqIK9RpfPE4HJnMMk/kmVLnoDO', '系统管理员', '13800000000', 'admin@lis.com', 1)
ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password
  WHERE sys_user.password = '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6';

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
-- 状态: 0申请 1采样 2核收 3检测中 4已报告   (注: 旧状态值, 新状态枚举见 init-schema lis_sample.status)
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
-- 密码均为 123456 (BCrypt)，旧哈希无效，已修正
INSERT INTO sys_user (username, password, real_name, phone, email, department_id, status) VALUES
('tech',     '$2a$10$V6yN9DUIx11vniBWl7XBn.Xs9ldcBCeXwBw3AApHoFqw/mNsSE2hy', '张技师', '13800000001', 'zhang@lis.com', 4, 1),
('director', '$2a$10$uKNDORwJyz0VpgxpDeJwV.Pqwk1y38q21x2QZiDiVJ8qU0Oxxwvqe', '李主任', '13800000002', 'li@lis.com',    1, 1)
ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password
  WHERE sys_user.password = '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6';

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


-- =============================================================================
-- 【增量脚本】reagent-core-tables.sql —— 试剂核心模块(扩展 lis_reagent + 入库/消耗/开瓶)
-- =============================================================================

-- 1. 扩展试剂字典表
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS category_id BIGINT;           -- 分类ID
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS manufacturer_id BIGINT;       -- 厂商ID
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS reg_cert_no VARCHAR(64);      -- 注册证号
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS expected_use TEXT;            -- 预期用途
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS price_purchase DECIMAL(12,2); -- 采购价
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS price_selling DECIMAL(12,2);  -- 销售价
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS usage_per_test DECIMAL(10,4); -- 每测试用量
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS opened_expiry_days INT;       -- 开瓶效期(天)
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS min_stock DECIMAL(10,2);      -- 库存下限
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS max_stock DECIMAL(10,2);      -- 库存上限
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS supplier VARCHAR(100);        -- 供应商

-- 2. 试剂入库单
CREATE TABLE IF NOT EXISTS lis_reagent_inbound (
    id              BIGSERIAL PRIMARY KEY,
    inbound_no      VARCHAR(32) NOT NULL,                  -- 入库单号
    supplier        VARCHAR(100),                          -- 供应商
    total_amount    DECIMAL(12,2) DEFAULT 0,              -- 总金额
    verify_status   SMALLINT DEFAULT 0,                    -- 0待验收 1已验收 2拒收
    verifier_id     BIGINT,                                -- 验收人ID
    verifier_name   VARCHAR(50),                           -- 验收人姓名
    verify_time     TIMESTAMP,                             -- 验收时间
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_inbound IS '试剂入库单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_inbound_no ON lis_reagent_inbound (inbound_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_status ON lis_reagent_inbound (verify_status);

-- 3. 试剂入库明细
CREATE TABLE IF NOT EXISTS lis_reagent_inbound_item (
    id              BIGSERIAL PRIMARY KEY,
    inbound_id      BIGINT NOT NULL,                       -- 入库单ID
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    lot_no          VARCHAR(32) NOT NULL,                  -- 批号
    barcode         VARCHAR(64),                           -- 条码
    quantity        DECIMAL(10,2) NOT NULL,               -- 数量
    unit            VARCHAR(20),                           -- 单位
    purchase_price  DECIMAL(12,2),                         -- 采购单价
    amount          DECIMAL(12,2),                         -- 金额
    expiry_date     DATE NOT NULL,                        -- 效期
    location        VARCHAR(100),                          -- 存放位置
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_inbound_item IS '试剂入库明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_inbound ON lis_reagent_inbound_item (inbound_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_reagent ON lis_reagent_inbound_item (reagent_id);

-- 4. 试剂消耗单
CREATE TABLE IF NOT EXISTS lis_reagent_consumption (
    id              BIGSERIAL PRIMARY KEY,
    consumption_no  VARCHAR(32) NOT NULL,                  -- 消耗单号
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    stock_id        BIGINT,                                -- 批次库存ID
    lot_no          VARCHAR(32),                           -- 批号
    quantity        DECIMAL(10,2) NOT NULL,               -- 消耗数量
    unit            VARCHAR(20),                           -- 单位
    consume_type    SMALLINT DEFAULT 1,                    -- 1手工 2自动(检验结果) 3质控 4校准 5维护
    related_no      VARCHAR(32),                           -- 关联单号(检验号/质控号)
    operator_id     BIGINT,                                -- 操作人ID
    operator_name   VARCHAR(50),                           -- 操作人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_consumption IS '试剂消耗单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_consumption_no ON lis_reagent_consumption (consumption_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_consumption_reagent ON lis_reagent_consumption (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_consumption_time ON lis_reagent_consumption (create_time);

-- 5. 试剂开瓶记录
CREATE TABLE IF NOT EXISTS lis_reagent_opening (
    id              BIGSERIAL PRIMARY KEY,
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    stock_id        BIGINT,                                -- 批次库存ID
    lot_no          VARCHAR(32),                           -- 批号
    open_time       TIMESTAMP NOT NULL,                    -- 开瓶时间
    opened_expiry   DATE,                                  -- 开瓶效期
    status          SMALLINT DEFAULT 1,                    -- 1正常 0已过期
    operator_id     BIGINT,                                -- 操作人ID
    operator_name   VARCHAR(50),                           -- 操作人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_opening IS '试剂开瓶记录';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_opening_reagent ON lis_reagent_opening (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_opening_status ON lis_reagent_opening (status);

-- 6. 扩展出入库记录类型 (增加6消耗 7调拨)
-- record_type: 1入库 2出库 3报损 4退库 5盘点 6消耗 7调拨


-- =============================================================================
-- 【增量脚本】add-reagent-stock-check.sql —— 试剂库存盘点(主表 + 明细表)
-- =============================================================================

-- 1. 试剂库存盘点单
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id              BIGSERIAL PRIMARY KEY,
    check_no        VARCHAR(32) NOT NULL,                  -- 盘点单号
    warehouse       VARCHAR(100),                          -- 盘点仓库/库房
    check_date      DATE,                                   -- 盘点日期
    status          SMALLINT DEFAULT 0,                     -- 0进行中 1已完成 2已作废
    operator_id     BIGINT,                                 -- 盘点人ID
    operator_name   VARCHAR(50),                            -- 盘点人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check IS '试剂库存盘点单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_check_no ON lis_reagent_stock_check (check_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_status ON lis_reagent_stock_check (status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_warehouse ON lis_reagent_stock_check (warehouse);

-- 2. 试剂库存盘点明细
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL,                       -- 盘点单ID
    stock_id        BIGINT,                                -- 批次库存ID
    reagent_id      BIGINT,                                -- 试剂ID
    lot_no          VARCHAR(50),                           -- 批号
    book_qty        DECIMAL(12,2),                         -- 账面数量
    actual_qty      DECIMAL(12,2),                         -- 实盘数量
    diff_qty        DECIMAL(12,2) DEFAULT 0,               -- 差异数量 (实盘-账面)
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check_item IS '试剂库存盘点明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_check ON lis_reagent_stock_check_item (check_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_reagent ON lis_reagent_stock_check_item (reagent_id);


-- =============================================================================
-- 【增量脚本】add-instrument-fields.sql —— 仪器台账字段 + 试剂批次追溯 + 库存盘点表
-- =============================================================================

-- 1. lis_instrument 新增: 序列号/安装日期/保修到期/使用科室/工作状态/通道类型
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS serial_no        VARCHAR(64);     -- 序列号
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS install_date     DATE;            -- 安装日期
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS warranty_expiry  DATE;            -- 保修到期
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS department       VARCHAR(50);     -- 使用科室
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS work_status      SMALLINT DEFAULT 1; -- 工作状态: 1正常 2维护中 3故障中
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS channel_type     VARCHAR(20);     -- 通道类型: TCP / TCP_SERIAL(双通道热备)

COMMENT ON COLUMN lis_instrument.serial_no       IS '仪器序列号';
COMMENT ON COLUMN lis_instrument.install_date    IS '安装日期';
COMMENT ON COLUMN lis_instrument.warranty_expiry IS '保修到期日期';
COMMENT ON COLUMN lis_instrument.department      IS '使用科室';
COMMENT ON COLUMN lis_instrument.work_status     IS '工作状态: 1正常 2维护中 3故障中';
COMMENT ON COLUMN lis_instrument.channel_type    IS '通道类型: TCP(仅主通道) / TCP_SERIAL(主TCP+备串口双通道热备)';

-- 2. 试剂批次追溯 - lis_result 新增试剂批次字段
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;       -- 试剂批次ID
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);  -- 试剂批号

COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次ID (试剂追溯)';
COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号 (试剂追溯)';
CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot ON lis_result (reagent_lot_no);

-- 3. 试剂库存盘点 - 盘点单主表
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id              BIGSERIAL PRIMARY KEY,
    check_no        VARCHAR(32) NOT NULL,                  -- 盘点单号
    warehouse       VARCHAR(100),                          -- 仓库/位置
    check_date      DATE NOT NULL,                         -- 盘点日期
    status          SMALLINT DEFAULT 0,                    -- 0进行中 1已完成 2已作废
    operator_id     BIGINT,                                -- 盘点人ID
    operator_name   VARCHAR(50),                           -- 盘点人姓名
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check IS '试剂库存盘点单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_check_no ON lis_reagent_stock_check (check_no);

-- 4. 试剂库存盘点明细表
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL,                       -- 盘点单ID
    stock_id        BIGINT,                                -- 库存批次ID
    reagent_id      BIGINT NOT NULL,                       -- 试剂ID
    lot_no          VARCHAR(32),                           -- 批号
    book_qty        DECIMAL(10,2) NOT NULL,               -- 账面数量
    actual_qty      DECIMAL(10,2),                         -- 实盘数量
    diff_qty        DECIMAL(10,2),                         -- 差异 (实盘-账面)
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_stock_check_item IS '试剂库存盘点明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_item_check ON lis_reagent_stock_check_item (check_id);


-- =============================================================================
-- 【增量脚本】add-collector-field.sql —— 标本采集人字段
-- =============================================================================

-- 标本采集工作站: 添加采集人字段
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS collector_id BIGINT;
COMMENT ON COLUMN lis_sample.collector_id IS '采集人ID';

-- 标本申请表 (lis_lab_order) 也添加采集人和采集时间字段 (如果存在该表)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'lis_lab_order') THEN
        ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collector_id BIGINT;
        ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collect_time TIMESTAMP;
        COMMENT ON COLUMN lis_lab_order.collector_id IS '采集人ID';
        COMMENT ON COLUMN lis_lab_order.collect_time IS '采集时间';
    END IF;
END $$;


-- =============================================================================
-- 【增量脚本】add-critical-record.sql —— 危急值处理记录表
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_critical_record (
    id              BIGSERIAL PRIMARY KEY,
    result_id       BIGINT NOT NULL,                        -- 检验结果ID (lis_result.id)
    sample_id       BIGINT,                                 -- 标本ID
    test_item_id    BIGINT,                                 -- 检验指标ID
    patient_name    VARCHAR(60),                            -- 患者姓名(冗余)
    test_item_name  VARCHAR(100),                           -- 检验项目名称(冗余)
    result_value    VARCHAR(100),                           -- 结果值
    unit            VARCHAR(30),                            -- 单位
    critical_low    VARCHAR(30),                            -- 危急值下限
    critical_high   VARCHAR(30),                            -- 危急值上限
    notifier        VARCHAR(60),                            -- 危急值通知人
    notify_time     TIMESTAMP,                              -- 通知时间
    sender          VARCHAR(60),                            -- 发送人
    send_time       TIMESTAMP,                              -- 发送时间
    remark          TEXT,                                   -- 备注
    status          SMALLINT DEFAULT 0,                     -- 0待通知 1已通知 2已取消
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_critical_record IS '危急值处理记录表';
COMMENT ON COLUMN lis_critical_record.notifier IS '危急值通知人';
COMMENT ON COLUMN lis_critical_record.notify_time IS '通知时间';
COMMENT ON COLUMN lis_critical_record.sender IS '发送人';
COMMENT ON COLUMN lis_critical_record.send_time IS '发送时间';
COMMENT ON COLUMN lis_critical_record.remark IS '备注';
COMMENT ON COLUMN lis_critical_record.status IS '0待通知 1已通知 2已取消';

CREATE INDEX IF NOT EXISTS idx_lis_critical_record_result ON lis_critical_record (result_id);
CREATE INDEX IF NOT EXISTS idx_lis_critical_record_status ON lis_critical_record (status);


-- =============================================================================
-- 【增量脚本】add-deliver-signoff.sql —— 标本送检/签收字段
-- =============================================================================

ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_time TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_person VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sign_off_time TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sign_off_person VARCHAR(64);
-- 就诊号字段 (从 lis_visit 冗余, 便于按就诊号查询)
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS visit_no VARCHAR(64);

COMMENT ON COLUMN lis_sample.deliver_time IS '送检时间';
COMMENT ON COLUMN lis_sample.deliver_person IS '送检人';
COMMENT ON COLUMN lis_sample.sign_off_time IS '签收时间';
COMMENT ON COLUMN lis_sample.sign_off_person IS '签收人';
COMMENT ON COLUMN lis_sample.visit_no IS '就诊号';

-- 回填历史数据的 visit_no (从 lis_visit JOIN, 首次部署无历史数据, 无操作)
UPDATE lis_sample s
SET visit_no = v.visit_no
FROM lis_visit v
WHERE s.visit_id = v.id AND s.visit_no IS NULL;


-- =============================================================================
-- 【增量脚本】add-eqa-name-fields.sql —— 室间质控名称字段
-- =============================================================================

ALTER TABLE lis_eqa_record ADD COLUMN IF NOT EXISTS test_item_name VARCHAR(100);
ALTER TABLE lis_eqa_record ADD COLUMN IF NOT EXISTS instrument_name VARCHAR(100);

COMMENT ON COLUMN lis_eqa_record.test_item_name IS '检验项目名称(室间质控允许直接记录名称)';
COMMENT ON COLUMN lis_eqa_record.instrument_name IS '仪器名称';

-- 回填已有数据：根据 test_item_id / instrument_id 关联填充名称 (首次部署无历史数据, 无操作)
UPDATE lis_eqa_record r
SET test_item_name = t.item_name
FROM lis_test_item t
WHERE r.test_item_id = t.id AND r.test_item_name IS NULL;

UPDATE lis_eqa_record r
SET instrument_name = i.instrument_name
FROM lis_instrument i
WHERE r.instrument_id = i.id AND r.instrument_name IS NULL;


-- =============================================================================
-- 【增量脚本】add-his-sync-retry.sql —— HIS 同步重试表
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_his_sync_retry (
    id              BIGSERIAL PRIMARY KEY,
    notify_type     VARCHAR(32) NOT NULL,                 -- 通知类型: critical=危急值 / sample-status=标本状态
    payload         TEXT NOT NULL,                        -- JSON 序列化的消息体
    retry_count     INTEGER DEFAULT 0,                    -- 已重试次数
    max_retry       INTEGER DEFAULT 6,                    -- 最大重试次数 (超过则放弃)
    status          SMALLINT DEFAULT 0,                   -- 0=待重试 1=成功 2=已放弃
    error_msg       TEXT,                                 -- 最后一次错误信息
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_his_sync_retry IS 'HIS 同步重试表 (危急值/标本状态回传失败重试)';
COMMENT ON COLUMN lis_his_sync_retry.notify_type IS '通知类型: critical=危急值 / sample-status=标本状态';
COMMENT ON COLUMN lis_his_sync_retry.payload IS 'JSON 序列化的消息体';
COMMENT ON COLUMN lis_his_sync_retry.retry_count IS '已重试次数';
COMMENT ON COLUMN lis_his_sync_retry.max_retry IS '最大重试次数, 超过则放弃';
COMMENT ON COLUMN lis_his_sync_retry.status IS '0=待重试 1=成功 2=已放弃';

CREATE INDEX IF NOT EXISTS idx_lis_his_sync_retry_status ON lis_his_sync_retry (status);
CREATE INDEX IF NOT EXISTS idx_lis_his_sync_retry_create_time ON lis_his_sync_retry (create_time);


-- =============================================================================
-- 【增量脚本】add-instrument-calibration-repair.sql —— 仪器校准/维修记录表(结构)
-- =============================================================================

-- 仪器校准记录表
CREATE TABLE IF NOT EXISTS lis_instrument_calibration (
    id BIGINT PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    calibration_no VARCHAR(64),
    calibration_type VARCHAR(32),
    calibration_time TIMESTAMP,
    next_calibration_time TIMESTAMP,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    result VARCHAR(16),
    remark VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument_calibration IS '仪器校准记录';

-- 仪器维修记录表
CREATE TABLE IF NOT EXISTS lis_instrument_repair (
    id BIGINT PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    repair_no VARCHAR(64),
    fault_time TIMESTAMP,
    fault_desc VARCHAR(500),
    repair_time TIMESTAMP,
    finish_time TIMESTAMP,
    repairman VARCHAR(64),
    downtime_hours NUMERIC(10,2),
    result VARCHAR(16),
    parts_replaced VARCHAR(255),
    cost NUMERIC(12,2),
    remark VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument_repair IS '仪器维修记录';
-- 注: 原脚本中的校准/维修示例数据(演示数据)已移除, 正式部署不预置。


-- =============================================================================
-- 【增量脚本】add-instrument-staff-map-table.sql —— 仪器-操作人员关联表
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_instrument_staff_map (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,      -- 仪器ID (关联 lis_instrument.id)
    staff_id        BIGINT,               -- 操作人员ID (关联 lis_staff.id)
    staff_name      VARCHAR(100)          -- 操作人员姓名(冗余)
);
COMMENT ON TABLE lis_instrument_staff_map IS '仪器-操作人员关联表';
COMMENT ON COLUMN lis_instrument_staff_map.instrument_id IS '仪器ID (关联 lis_instrument.id)';
COMMENT ON COLUMN lis_instrument_staff_map.staff_id IS '操作人员ID (关联 lis_staff.id)';
COMMENT ON COLUMN lis_instrument_staff_map.staff_name IS '操作人员姓名(冗余)';
CREATE INDEX IF NOT EXISTS idx_lis_instrument_staff_map_instrument ON lis_instrument_staff_map (instrument_id);


-- =============================================================================
-- 【增量脚本】add-micro-gram-negative.sql —— 微生物报告革兰氏染色/阴性报告字段
-- =============================================================================

ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_result VARCHAR(200);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_remark VARCHAR(500);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_time TIMESTAMP;
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS gram_stain_operator VARCHAR(50);

ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS is_negative SMALLINT DEFAULT 0;
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_remark VARCHAR(500);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_operator VARCHAR(50);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS negative_time TIMESTAMP;

COMMENT ON COLUMN lis_micro_report.gram_stain_result IS '革兰氏染色结果';
COMMENT ON COLUMN lis_micro_report.gram_stain_remark IS '革兰氏染色备注';
COMMENT ON COLUMN lis_micro_report.gram_stain_time IS '革兰氏染色时间';
COMMENT ON COLUMN lis_micro_report.gram_stain_operator IS '革兰氏染色操作人';

COMMENT ON COLUMN lis_micro_report.is_negative IS '是否阴性报告 0否 1是';
COMMENT ON COLUMN lis_micro_report.negative_remark IS '阴性报告备注';
COMMENT ON COLUMN lis_micro_report.negative_operator IS '阴性报告操作人';
COMMENT ON COLUMN lis_micro_report.negative_time IS '阴性报告时间';


-- =============================================================================
-- 【增量脚本】add-order-patient-condition.sql —— 申请单患者生理条件字段
-- =============================================================================

BEGIN;

-- lis_lab_order 增加 patient_condition 列
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS patient_condition VARCHAR(100);
COMMENT ON COLUMN lis_lab_order.patient_condition IS '患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等, 用于匹配参考范围';

COMMIT;


-- =============================================================================
-- 【增量脚本】add-qc-rule.sql —— 质控规则配置 & 质控预警表
-- =============================================================================

-- Westgard 规则配置表
CREATE TABLE IF NOT EXISTS lis_qc_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(20) NOT NULL UNIQUE,          -- 规则编码: 1-2s/1-3s/2-2s/R-4s/4-1s/10x
    rule_name       VARCHAR(50) NOT NULL,                 -- 规则名称
    rule_type       VARCHAR(20) DEFAULT 'REJECT',         -- WARNING/REJECT
    enabled         SMALLINT DEFAULT 1,                   -- 0禁用 1启用
    description     VARCHAR(200),                         -- 说明
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_rule IS 'Westgard 规则配置表';

INSERT INTO lis_qc_rule (rule_code, rule_name, rule_type, enabled, description) VALUES
('1-2s','1-2s警告规则','WARNING',1,'1个质控点超出均值±2SD (警告, 不拒绝)'),
('1-3s','1-3s失控规则','REJECT',1,'1个质控点超出均值±3SD (失控)'),
('2-2s','2-2s失控规则','REJECT',1,'连续2个质控点同侧超出均值±2SD (失控)'),
('R-4s','R-4s失控规则','REJECT',1,'连续2个质控点极差超过4SD (失控)'),
('4-1s','4-1s警告规则','WARNING',1,'连续4个质控点同侧超出均值±1SD (警告)'),
('10x','10x警告规则','WARNING',1,'连续10个质控点在均值同侧 (警告)')
ON CONFLICT (rule_code) DO NOTHING;

-- 质控偏差趋势预警表
CREATE TABLE IF NOT EXISTS lis_qc_alert (
    id              BIGSERIAL PRIMARY KEY,
    alert_type      VARCHAR(30),                         -- SAME_SIDE_BIAS/TREND/TREND_AND_SAME_SIDE
    lot_id          BIGINT,                               -- 质控品批号ID
    test_item_id    BIGINT,                               -- 检验项目ID
    instrument_id   BIGINT,                              -- 仪器ID
    last_measure_value DECIMAL(10,4),                     -- 最近测量值
    last_z_score    DECIMAL(5,2),                         -- 最近Z分数
    message         VARCHAR(255),                         -- 预警描述
    status          VARCHAR(20) DEFAULT 'PENDING',        -- PENDING待处理 / READ已读
    alert_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 预警时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_alert IS '质控偏差趋势预警表';
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_time ON lis_qc_alert (alert_time);
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_status ON lis_qc_alert (status);

-- =============================================================================
-- 【增量脚本】add-reagent-enhance.sql
-- =============================================================================
BEGIN;


-- =============================================================================
-- 一、基础主数据（3 张）
-- =============================================================================

-- 1. 试剂分类字典（树形，填实 lis_reagent.category_id 悬空外键）
CREATE TABLE IF NOT EXISTS lis_reagent_category (
    id               BIGSERIAL PRIMARY KEY,
    category_code    VARCHAR(32)  NOT NULL,              -- 分类编码
    category_name    VARCHAR(100) NOT NULL,              -- 分类名称
    parent_id        BIGINT       NOT NULL DEFAULT 0,    -- 父分类ID，0=根
    path             VARCHAR(255),                       -- 物化路径 /CAT_CHEM/，用于子树查询
    level_no         SMALLINT     DEFAULT 1,             -- 层级 1/2/3
    hazard_category  SMALLINT     DEFAULT 0,             -- 0普通 1危险品类（该类下试剂默认危险品）
    sort_order       INT          DEFAULT 0,
    status           SMALLINT     DEFAULT 1,             -- 0停用 1启用
    remark           TEXT,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_category IS '试剂分类字典（树形）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_category_code    ON lis_reagent_category (category_code);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_category_parent ON lis_reagent_category (parent_id);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_category_path   ON lis_reagent_category (path);


-- 2. 供应商主数据（通用表，不带 reagent 前缀，仪器模块未来可复用 —— 主理人 Q1 裁决）
CREATE TABLE IF NOT EXISTS lis_supplier (
    id                   BIGSERIAL PRIMARY KEY,
    supplier_code        VARCHAR(32)  NOT NULL,          -- 供应商编码
    supplier_name        VARCHAR(200) NOT NULL,          -- 供应商名称
    short_name           VARCHAR(100),                   -- 简称
    credit_code          VARCHAR(64),                    -- 统一社会信用代码
    contact_person       VARCHAR(50),                    -- 联系人
    phone                VARCHAR(30),
    email                VARCHAR(100),
    address              VARCHAR(255),
    license_no           VARCHAR(64),                    -- 营业执照号
    license_expiry       DATE,                           -- 营业执照到期日
    medical_cert_no      VARCHAR(64),                    -- 医疗器械经营许可证号
    medical_cert_expiry  DATE,                           -- 医疗器械经营许可证到期日
    qual_status          SMALLINT DEFAULT 0,             -- 资质状态 0待完善 1有效 2已过期（由 Job 每日刷新）
    score                DECIMAL(5,2),                   -- 综合评分（第二批 R-11 使用）
    source_type          SMALLINT DEFAULT 1,             -- 1手工录入 2存量字符串迁移生成（草稿）
    status               SMALLINT DEFAULT 1,             -- 0停用 1启用
    remark               TEXT,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_supplier IS '供应商主数据（通用，非试剂专属）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_supplier_code    ON lis_supplier (supplier_code);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_name   ON lis_supplier (supplier_name);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_qual   ON lis_supplier (qual_status, status);
CREATE INDEX        IF NOT EXISTS idx_lis_supplier_expiry ON lis_supplier (license_expiry, medical_cert_expiry);


-- 3. 仓库/冰箱/层架主数据（三级树，带温度区间）
CREATE TABLE IF NOT EXISTS lis_reagent_warehouse (
    id            BIGSERIAL PRIMARY KEY,
    wh_code       VARCHAR(32)  NOT NULL,                 -- 仓库编码
    wh_name       VARCHAR(100) NOT NULL,                 -- 仓库名称
    parent_id     BIGINT       NOT NULL DEFAULT 0,       -- 父节点，0=根
    path          VARCHAR(255),                          -- 物化路径 /1/5/23/，子树查询用 likeRight
    wh_type       SMALLINT     DEFAULT 1,                -- 1库房 2冰箱 3层架
    dept_id       BIGINT,                                -- 归属科室（快照，指向 sys_department.id，不建外键）
    dept_name     VARCHAR(100),                          -- 科室名称冗余快照
    temp_min      DECIMAL(6,2),                          -- 温度区间下限(℃)
    temp_max      DECIMAL(6,2),                          -- 温度区间上限(℃)
    location_desc VARCHAR(255),                          -- 位置描述
    sort_order    INT      DEFAULT 0,
    status        SMALLINT DEFAULT 1,                    -- 0停用 1启用
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_warehouse IS '试剂仓库/冰箱/层架主数据（三级树）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_warehouse_code    ON lis_reagent_warehouse (wh_code);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_warehouse_parent ON lis_reagent_warehouse (parent_id);
CREATE INDEX        IF NOT EXISTS idx_lis_reagent_warehouse_path   ON lis_reagent_warehouse (path);

-- 3.1 【关键】插入 id=0 的默认库房哨兵。
--     存量 lis_reagent_stock 无仓库归属，统一落到 warehouse_id=0；
--     用哨兵值而非 NULL，是因为 PostgreSQL 唯一索引中 NULL<>NULL 会让第 17 节的
--     三列唯一约束对存量数据完全失效（详见设计文档 C-2）。
INSERT INTO lis_reagent_warehouse (id, wh_code, wh_name, parent_id, path, wh_type, sort_order, status, remark)
SELECT 0, 'WH_DEFAULT', '默认库房（待归集）', 0, '/0/', 1, 9999, 1,
       '系统内置。存量库存与未指定仓库的入库默认归入此节点，请逐步归集到实际仓库。'
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_warehouse WHERE id = 0);


-- =============================================================================
-- 二、预算域（2 张）
-- =============================================================================

-- 4. 预算主表
CREATE TABLE IF NOT EXISTS lis_reagent_budget (
    id            BIGSERIAL PRIMARY KEY,
    budget_no     VARCHAR(32),                           -- 预算编号（可选，展示用）
    budget_year   INT           NOT NULL,                -- 预算年度
    dept_id       BIGINT        NOT NULL,                -- 科室ID（指向 sys_department.id，不建外键，跨模块解耦）
    dept_name     VARCHAR(100),                          -- 科室名称冗余快照
    category_id   BIGINT,                                -- 试剂类别ID，NULL=不限类别（通用预算）
    category_name VARCHAR(100),                          -- 类别名称冗余快照
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 预算总额
    frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 已占用（申请提交时 +，驳回/作废时 -，实扣时 -）
    used_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 已实扣（入库验收通过时 +）
    warn_ratio    DECIMAL(5,4)  NOT NULL DEFAULT 0.8000, -- 预警阈值，默认 80%
    version       INT           NOT NULL DEFAULT 0,      -- 乐观锁版本号（配合 FOR UPDATE 做双保险）
    status        SMALLINT      DEFAULT 1,               -- 0停用 1启用
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 金额恒不为负的数据库级保护
    CONSTRAINT ck_lis_reagent_budget_amount CHECK (
        total_amount >= 0 AND frozen_amount >= 0 AND used_amount >= 0
    )
);
COMMENT ON TABLE lis_reagent_budget IS '试剂预算主表（年度+科室+类别 三维）';

-- 唯一约束：(budgetYear, deptId, categoryId)。
-- category_id 可为 NULL 表示「不限类别」，而 PostgreSQL 唯一索引中 NULL<>NULL，
-- 会导致可以插入多条 category_id IS NULL 的重复记录 —— 故用 COALESCE 表达式索引。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_budget_dim
    ON lis_reagent_budget (budget_year, dept_id, COALESCE(category_id, -1));
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_dept ON lis_reagent_budget (dept_id, budget_year);


-- 5. 预算变动流水（只增不改不删，支撑对账断言）
CREATE TABLE IF NOT EXISTS lis_reagent_budget_record (
    id            BIGSERIAL PRIMARY KEY,
    budget_id     BIGINT        NOT NULL,
    change_type   SMALLINT      NOT NULL,                -- 1占用 2释放 3实扣 4调增 5调减
    amount        DECIMAL(14,2) NOT NULL,                -- 变动金额（恒为正，方向由 change_type 决定）
    before_frozen DECIMAL(14,2),
    after_frozen  DECIMAL(14,2),
    before_used   DECIMAL(14,2),
    after_used    DECIMAL(14,2),
    source_type   VARCHAR(20),                           -- APPLY / ORDER / INBOUND / MANUAL
    source_id     BIGINT,                                -- 来源单据ID
    source_no     VARCHAR(32),                           -- 来源单号
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_budget_record IS '试剂预算变动流水（只增不改不删）';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_record_budget ON lis_reagent_budget_record (budget_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_budget_record_source ON lis_reagent_budget_record (source_type, source_id);

-- 【幂等保护】同一单据对同一预算的同一种变动只能发生一次。
-- 重复提交（如重复点击验收）会撞唯一约束导致事务回滚，天然幂等，无需在代码里判重。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_budget_record_src
    ON lis_reagent_budget_record (budget_id, change_type, source_type, source_id);


-- =============================================================================
-- 三、采购域（5 张）
-- =============================================================================

-- 6. 审批节点定义（配置表 —— 主理人 Q2「节点定义写配置表不硬编码」）
--    注：PRD 5.1 的 13 张表清单遗漏了本表，实际新增表为 14 张（详见设计文档 C-6）
CREATE TABLE IF NOT EXISTS lis_reagent_approval_node (
    id                  BIGSERIAL PRIMARY KEY,
    biz_type            VARCHAR(30) NOT NULL,            -- PURCHASE 采购申请 / REQUISITION 出入库业务单
    node_level          SMALLINT    NOT NULL,            -- 节点顺序 1,2,3...
    node_name           VARCHAR(50) NOT NULL,            -- 节点名称
    trigger_cond        VARCHAR(20) NOT NULL DEFAULT 'ALWAYS', -- ALWAYS 恒触发 / OVER_BUDGET 超预算触发 / HAZARD 危险品触发
    approver_role_code  VARCHAR(50),                     -- 审批人角色编码（指向 sys_role.role_code，主理人 U-4：复用现有角色体系）
    approver_dept_scope SMALLINT DEFAULT 1,              -- 1本科室 2全院
    status              SMALLINT DEFAULT 1,              -- 0停用 1启用（同时充当「是否需要审批」的开关，替代 sys_config）
    remark              TEXT,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_approval_node IS '试剂审批节点定义（配置表，本轮不做配置界面）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_approval_node
    ON lis_reagent_approval_node (biz_type, node_level, trigger_cond);

-- 6.1 初始化节点定义（幂等）
INSERT INTO lis_reagent_approval_node (biz_type, node_level, node_name, trigger_cond, approver_role_code, status, remark)
SELECT v.biz_type, v.node_level, v.node_name, v.trigger_cond, v.role_code, v.status, v.remark
FROM (VALUES
    ('PURCHASE',    1::smallint, '科室主任审批', 'ALWAYS',      'DEPT_DIRECTOR',  1::smallint, '采购申请第一级，恒触发'),
    ('PURCHASE',    2::smallint, '分管院长审批', 'OVER_BUDGET', 'VICE_PRESIDENT', 1::smallint, '仅当预算使用率达到预警阈值时追加'),
    ('REQUISITION', 1::smallint, '科室主任审批', 'ALWAYS',      'DEPT_DIRECTOR',  0::smallint, '普通试剂领用默认免审(status=0)，科室可自行启用 —— 主理人 Q6'),
    ('REQUISITION', 2::smallint, '安全员审批',   'HAZARD',      'SAFETY_OFFICER', 1::smallint, '危险品强制审批，不可跳过 —— 第二批 R-10')
) AS v(biz_type, node_level, node_name, trigger_cond, role_code, status, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM lis_reagent_approval_node n
     WHERE n.biz_type = v.biz_type AND n.node_level = v.node_level AND n.trigger_cond = v.trigger_cond
);


-- 7. 采购申请单
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_apply (
    id                 BIGSERIAL PRIMARY KEY,
    apply_no           VARCHAR(32)   NOT NULL,           -- 申请单号 PA + yyyyMMdd + 4位序列
    apply_dept_id      BIGINT        NOT NULL,           -- 申请科室（sys_department.id 快照）
    apply_dept_name    VARCHAR(100),
    apply_user_id      BIGINT,
    apply_user_name    VARCHAR(50),
    budget_id          BIGINT,                           -- 命中的预算ID（一申请一预算，见设计文档 2.2.4）
    budget_year        INT,
    category_id        BIGINT,                           -- 本申请收敛到的试剂类别
    total_amount       DECIMAL(14,2) NOT NULL DEFAULT 0, -- 明细汇总金额
    status             SMALLINT DEFAULT 0,               -- 0草稿 1待审批 2审批中 3已批准 4已驳回 5已冻结 6已作废
    current_node_level SMALLINT DEFAULT 0,               -- 当前审批到第几级，0=未进入审批
    total_node_count   SMALLINT DEFAULT 0,               -- 本次解析出的审批节点总数
    over_budget_flag   SMALLINT DEFAULT 0,               -- 0正常 1超预算（冻结原因）
    excess_amount      DECIMAL(14,2),                    -- 超额金额（供页面明示，R-03 验收 7）
    urgent_flag        SMALLINT DEFAULT 0,               -- 0普通 1紧急
    expect_date        DATE,                             -- 期望到货日期
    submit_time        TIMESTAMP,
    finish_time        TIMESTAMP,
    remark             TEXT,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_apply IS '试剂采购申请单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_purchase_apply_no ON lis_reagent_purchase_apply (apply_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_dept   ON lis_reagent_purchase_apply (apply_dept_id, status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_budget ON lis_reagent_purchase_apply (budget_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_time   ON lis_reagent_purchase_apply (create_time);


-- 8. 采购申请明细
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_apply_item (
    id              BIGSERIAL PRIMARY KEY,
    apply_id        BIGINT        NOT NULL,
    reagent_id      BIGINT        NOT NULL,
    reagent_code    VARCHAR(32),                         -- 冗余快照
    reagent_name    VARCHAR(100),                        -- 冗余快照
    specification   VARCHAR(50),
    category_id     BIGINT,                              -- 冗余，用于预算类别收敛校验
    quantity        DECIMAL(12,2) NOT NULL,
    unit            VARCHAR(20),
    estimated_price DECIMAL(12,2),                       -- 预估单价
    amount          DECIMAL(14,2),                       -- 行金额 = quantity * estimated_price
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lis_reagent_purchase_apply_item_qty CHECK (quantity > 0)
);
COMMENT ON TABLE lis_reagent_purchase_apply_item IS '试剂采购申请明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_item_apply   ON lis_reagent_purchase_apply_item (apply_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_apply_item_reagent ON lis_reagent_purchase_apply_item (reagent_id);


-- 9. 采购审批流水（只增不改不删）
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_approval (
    id            BIGSERIAL PRIMARY KEY,
    apply_id      BIGINT   NOT NULL,
    apply_no      VARCHAR(32),
    node_level    SMALLINT NOT NULL,
    node_name     VARCHAR(50),
    action        SMALLINT NOT NULL,                     -- 1通过 2驳回
    approver_id   BIGINT,
    approver_name VARCHAR(50),
    opinion       TEXT,                                  -- 审批意见
    approve_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    round_no      SMALLINT  DEFAULT 1,                   -- 第几轮（驳回后重新提交，轮次+1）
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_approval IS '试剂采购审批流水（只增不改不删，Service 层不提供 update/delete）';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_approval_apply ON lis_reagent_purchase_approval (apply_id, round_no, node_level);


-- 10. 采购订单
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_order (
    id            BIGSERIAL PRIMARY KEY,
    order_no      VARCHAR(32)   NOT NULL,                -- 订单号 PO + yyyyMMdd + 4位序列
    apply_id      BIGINT,                                -- 来源申请单
    apply_no      VARCHAR(32),
    supplier_id   BIGINT        NOT NULL,                -- 供应商主数据ID
    supplier_name VARCHAR(200),                          -- 冗余快照（供应商改名不影响历史订单）
    dept_id       BIGINT,
    dept_name     VARCHAR(100),
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    expect_date   DATE,                                  -- 期望到货日
    order_date    DATE,                                  -- 下单日
    status        SMALLINT DEFAULT 0,                    -- 0待下单 1已下单 2部分到货 3已到货 4已取消
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_purchase_order IS '试剂采购订单';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_purchase_order_no ON lis_reagent_purchase_order (order_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_apply    ON lis_reagent_purchase_order (apply_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_supplier ON lis_reagent_purchase_order (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_status   ON lis_reagent_purchase_order (status);


-- 11. 采购订单明细
CREATE TABLE IF NOT EXISTS lis_reagent_purchase_order_item (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    apply_item_id BIGINT,                                -- 来源申请明细
    reagent_id    BIGINT        NOT NULL,
    reagent_code  VARCHAR(32),
    reagent_name  VARCHAR(100),
    specification VARCHAR(50),
    quantity      DECIMAL(12,2) NOT NULL,                -- 订购数量
    arrived_qty   DECIMAL(12,2) NOT NULL DEFAULT 0,      -- 已到货累计（由 verifyInbound() 回写）
    unit          VARCHAR(20),
    unit_price    DECIMAL(12,2),
    amount        DECIMAL(14,2),
    remark        TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 数据库级防超收（R-02 验收 7 的兜底）
    CONSTRAINT ck_lis_reagent_purchase_order_item_arrived CHECK (arrived_qty >= 0 AND arrived_qty <= quantity)
);
COMMENT ON TABLE lis_reagent_purchase_order_item IS '试剂采购订单明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_item_order   ON lis_reagent_purchase_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_purchase_order_item_reagent ON lis_reagent_purchase_order_item (reagent_id);


-- =============================================================================
-- 四、统一出入库业务单（3 张）
-- =============================================================================

-- 12. 统一出入库业务单（领用/退库/报废/调拨/借用/归还 六类合一）
CREATE TABLE IF NOT EXISTS lis_reagent_requisition (
    id                 BIGSERIAL PRIMARY KEY,
    req_no             VARCHAR(32) NOT NULL,             -- 单号 RQ + yyyyMMdd + 4位序列
    req_type           SMALLINT    NOT NULL,             -- 1领用 2退库 3报废 4调拨 5借用 6归还
    dept_id            BIGINT,                           -- 归属科室（sys_department.id 快照）
    dept_name          VARCHAR(100),
    apply_user_id      BIGINT,
    apply_user_name    VARCHAR(50),
    status             SMALLINT DEFAULT 0,               -- 0草稿 1待审 2已审 3已执行 4已驳回 5已作废
    approver_id        BIGINT,
    approver_name      VARCHAR(50),
    approve_time       TIMESTAMP,
    approve_opinion    TEXT,
    executor_id        BIGINT,
    executor_name      VARCHAR(50),
    execute_time       TIMESTAMP,
    from_warehouse_id  BIGINT,                           -- 源仓库（出库类/调拨源）
    to_warehouse_id    BIGINT,                           -- 目标仓库（入库类/调拨目标）
    hazard_flag        SMALLINT DEFAULT 0,               -- 明细中是否含危险品（决定是否追加安全员节点）
    expect_return_date DATE,                             -- 预计归还日（req_type=5 借用）
    source_req_id      BIGINT,                           -- 归还单指向被归还的借用单（req_type=6）
    reason             TEXT,                             -- 报废原因（req_type=3 必填，R-05 验收 5）
    total_amount       DECIMAL(14,2),                    -- 单据金额（成本归集用）
    remark             TEXT,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_requisition IS '试剂统一出入库业务单（领用/退库/报废/调拨/借用/归还）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_requisition_no ON lis_reagent_requisition (req_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_type_status ON lis_reagent_requisition (req_type, status);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_dept        ON lis_reagent_requisition (dept_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_source      ON lis_reagent_requisition (source_req_id);


-- 13. 业务单明细
CREATE TABLE IF NOT EXISTS lis_reagent_requisition_item (
    id           BIGSERIAL PRIMARY KEY,
    req_id       BIGINT        NOT NULL,
    reagent_id   BIGINT        NOT NULL,
    reagent_code VARCHAR(32),
    reagent_name VARCHAR(100),
    stock_id     BIGINT,                                 -- 指定批次（出库类必填）
    lot_no       VARCHAR(32),
    expiry_date  DATE,
    quantity     DECIMAL(12,2) NOT NULL,                 -- 恒为正数，方向由 req_type 决定
    unit         VARCHAR(20),
    unit_price   DECIMAL(12,2),                          -- 成本单价快照
    amount       DECIMAL(14,2),
    hazard_flag  SMALLINT DEFAULT 0,
    remark       TEXT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lis_reagent_requisition_item_qty CHECK (quantity > 0)
);
COMMENT ON TABLE lis_reagent_requisition_item IS '试剂出入库业务单明细';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_req     ON lis_reagent_requisition_item (req_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_reagent ON lis_reagent_requisition_item (reagent_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_requisition_item_stock   ON lis_reagent_requisition_item (stock_id);


-- 14. 报废环保处置扩展（与报废单 1:1，不做独立流程）—— 第二批 R-10 使用，本批建表
CREATE TABLE IF NOT EXISTS lis_reagent_scrap_disposal (
    id                   BIGSERIAL PRIMARY KEY,
    req_id               BIGINT NOT NULL,                -- 关联报废单（req_type=3），1:1
    disposal_method      VARCHAR(50),                    -- 处置方式：焚烧/中和/委托回收/填埋
    disposal_org         VARCHAR(200),                   -- 处置单位
    disposal_cert        VARCHAR(100),                   -- 处置凭证号
    disposal_date        DATE,                           -- 处置日期
    hazard_flag          SMALLINT DEFAULT 0,
    hazard_class         VARCHAR(50),
    safety_approver_id   BIGINT,                         -- 安全员
    safety_approver_name VARCHAR(50),
    safety_approve_time  TIMESTAMP,
    remark               TEXT,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_reagent_scrap_disposal IS '试剂报废环保处置扩展（与报废单 1:1）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_scrap_disposal_req ON lis_reagent_scrap_disposal (req_id);


-- =============================================================================
-- 五、现有表改造（只加不删）
-- =============================================================================

-- 15. lis_reagent 扩展（7 个字段）
--     注：category_id 已由 reagent-core-tables.sql L8 添加，此处不重复
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS supplier_id      BIGINT;              -- 供应商主数据ID（旧 supplier 字符串保留冗余）
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS hazard_flag      SMALLINT DEFAULT 0;  -- 0普通 1危险品
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS hazard_class     VARCHAR(50);         -- 危险等级：易燃/易爆/剧毒/腐蚀/放射性
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS msds_summary     TEXT;                -- MSDS 要点文本（主理人 U-9：不做文件上传）
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS msds_url         VARCHAR(500);        -- MSDS 外部链接
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS storage_temp_min DECIMAL(6,2);        -- 储存温度下限(℃)，与 storage_cond 文本并存
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS storage_temp_max DECIMAL(6,2);        -- 储存温度上限(℃)

CREATE INDEX IF NOT EXISTS idx_lis_reagent_category ON lis_reagent (category_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_hazard   ON lis_reagent (hazard_flag);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_supplier ON lis_reagent (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_status   ON lis_reagent (status);           -- 缺陷 G 的 status 筛选

-- 15.1 三阈值字段统一（主理人 Q4：以 min_stock 为准）
--      幂等：只填空值，不覆盖已有 min_stock
UPDATE lis_reagent SET min_stock = safety_stock
 WHERE min_stock IS NULL AND safety_stock IS NOT NULL;
COMMENT ON COLUMN lis_reagent.safety_stock IS '【已废弃】请用 min_stock。保留仅为兼容存量数据，新代码不再写入';


-- 16. lis_reagent_stock 加仓库字段
ALTER TABLE lis_reagent_stock ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN lis_reagent_stock.warehouse_id IS '仓库ID，0=默认库房（待归集）。用哨兵值而非 NULL，见第 17 节说明';

-- 16.1 【缺陷 K 补全 —— 列约束归一，三步不可拆】
--
--  为什么第 16 节的 `ADD COLUMN IF NOT EXISTS ... NOT NULL DEFAULT 0` 不够：
--  `IF NOT EXISTS` 在列【已存在】时是【整句 no-op】——PostgreSQL 不会去补加 NOT NULL
--  或 DEFAULT。凡是列已被先前版本 / 半截执行 / legacy 库建成「可空无默认」的环境，
--  第 16 节形同虚设，warehouse_id 依旧 nullable。
--
--  后果（QA 真机实测）：第 17 节三列唯一索引建好后，插入 2 行相同 (reagent_id, lot_no)
--  且 warehouse_id = NULL 的记录，dup_rows_admitted = 2 —— 两行全部写入成功。
--  因为 SQL 语义里 NULL <> NULL，唯一索引对 NULL 值天然不去重，缺陷 K 被彻底击穿。
--
--  三步顺序不可颠倒，且【必须整体排在第 17 节建唯一索引之前】：
--    ① 先给列补上 DEFAULT，保证后续 INSERT 不显式传值时落 0 而非 NULL
--    ② 再把存量 NULL 行归一为 0（原 16.1，保留）
--    ③ 最后 SET NOT NULL，从数据库层面根除 NULL，唯一索引才真正生效
--  第 ③ 步会全表扫描校验，若仍有 NULL 会报错中断 —— 这正是我们要的强校验。
ALTER TABLE lis_reagent_stock ALTER COLUMN warehouse_id SET DEFAULT 0;
UPDATE      lis_reagent_stock SET warehouse_id = 0 WHERE warehouse_id IS NULL;
ALTER TABLE lis_reagent_stock ALTER COLUMN warehouse_id SET NOT NULL;


-- 17. 【⚠️ 破坏性变更 —— 执行前请备份 lis_reagent_stock】重建合批唯一索引（修复缺陷 K）
--
--  背景：init-schema.sql L666 已存在
--        CREATE UNIQUE INDEX uk_lis_reagent_stock_lot ON lis_reagent_stock (reagent_id, lot_no);
--  该索引在数据库层强制「同试剂同批号只能有一条库存记录」。
--  引入多仓库后，A 冰箱与 B 冰箱的同批号试剂必须是两条独立记录，
--  若不重建索引，第二次入库会抛 duplicate key 直接崩溃（详见设计文档 C-2）。
--
--  顺序不可颠倒：必须先 DROP 旧索引，再 CREATE 新索引。
DROP INDEX IF EXISTS uk_lis_reagent_stock_lot;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_stock_lot_wh
    ON lis_reagent_stock (reagent_id, lot_no, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_warehouse ON lis_reagent_stock (warehouse_id, status);


-- 18. lis_reagent_inbound 关联订单与供应商
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS order_id    BIGINT;
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS order_no    VARCHAR(32);
ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS supplier_id BIGINT;   -- 旧 supplier 字符串保留冗余
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_order    ON lis_reagent_inbound (order_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_supplier ON lis_reagent_inbound (supplier_id);


-- 19. lis_reagent_inbound_item 关联订单明细与仓库
--     【PRD 遗漏项，详见设计文档 C-4 / C-7】
--     · order_item_id：不加此字段，同一订单内同一试剂多行时 arrivedQty 无法精确回写
--     · warehouse_id ：不加此字段，采购验收入库会全部落到默认仓，R-09 对主入口失效
ALTER TABLE lis_reagent_inbound_item ADD COLUMN IF NOT EXISTS order_item_id BIGINT;
ALTER TABLE lis_reagent_inbound_item ADD COLUMN IF NOT EXISTS warehouse_id  BIGINT;
CREATE INDEX IF NOT EXISTS idx_lis_reagent_inbound_item_order_item ON lis_reagent_inbound_item (order_item_id);

-- 19.1 warehouse_id 列约束归一（与 16.1 同理，三步不可拆）
--      入库明细的 warehouse_id 决定库存落到哪个仓，若为 NULL 会在 R-09 主入口
--      产生「无归属库存」的脏数据，且与 lis_reagent_stock.warehouse_id 的
--      非空哨兵约定不一致（同一份数据两套空值语义）。统一为哨兵 0。
--      ⚠️ 对 Java 侧的硬性要求：写入前必须过 ReagentWarehouseGuard.normalize()
--         把 null 归一为 0L。MyBatis-Plus 默认 insertStrategy=NOT_NULL 会跳过
--         null 字段从而落 DEFAULT 0，但一旦显式传 null 仍会撞 NOT NULL 违例，
--         不能依赖 ORM 的默认行为兜底。
ALTER TABLE lis_reagent_inbound_item ALTER COLUMN warehouse_id SET DEFAULT 0;
UPDATE      lis_reagent_inbound_item SET warehouse_id = 0 WHERE warehouse_id IS NULL;
ALTER TABLE lis_reagent_inbound_item ALTER COLUMN warehouse_id SET NOT NULL;


-- 20. lis_reagent_stock_record 补科室归属
--     【注意】不新增 req_id。该表 init-schema.sql L682-683 已有 related_id / related_type
--     通用多态外键，复用即可（related_type 扩展枚举 REQ / CHECK / INBOUND），
--     再加 req_id 属于重复建模，违反 PRD 第 5 章第 4 条硬约束（详见设计文档 C-11）
ALTER TABLE lis_reagent_stock_record ADD COLUMN IF NOT EXISTS dept_id   BIGINT;
ALTER TABLE lis_reagent_stock_record ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_dept    ON lis_reagent_stock_record (dept_id, create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_related ON lis_reagent_stock_record (related_type, related_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_record_type    ON lis_reagent_stock_record (record_type, create_time);
COMMENT ON COLUMN lis_reagent_stock_record.record_type IS
    '1入库 2出库(领用) 3报损/报废 4退库 5盘点调账 6消耗 7调拨 8借用 9归还';
COMMENT ON COLUMN lis_reagent_stock_record.related_type IS
    'RESULT 检验结果 / QC 质控 / REQ 出入库业务单 / CHECK 盘点单 / INBOUND 入库单';


-- 21. lis_reagent_stock_check 按仓库主数据盘点
--     主理人 U-10：warehouse_id 可空 = 全盘，非空 = 按仓盘
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS warehouse_id BIGINT;
COMMENT ON COLUMN lis_reagent_stock_check.warehouse_id IS '盘点仓库ID。NULL=全库盘点，非空=按仓库（含子树）盘点';
CREATE INDEX IF NOT EXISTS idx_lis_reagent_stock_check_wh ON lis_reagent_stock_check (warehouse_id);


-- =============================================================================
-- 六、存量数据迁移（幂等）
-- =============================================================================

-- 22. 供应商字符串 → 主数据草稿（R-04 验收 7）
--
--  本节修复 QA 真机实测出的 3 个缺陷，执行顺序 22.0 → 22.5 不可调整：
--    · 缺陷 1「名称清洗不足」：TRIM() 只剥 ASCII 空格，全角空格(U+3000)/不断行空格
--      (U+00A0)/Tab 的变体逃逸，「罗氏诊断」被切成 5 条独立供应商。
--    · 缺陷 2「大小写不折叠」：roche / Roche / ROCHE 被切成 3 条。
--    · 缺陷 3「supplier_name 无唯一约束」：22.4/22.5 回填时若存在重名，
--      优化器随机选一个赢家绑定，全程不报错 —— 静默错绑，最难排查。
--  三者同源，统一用规范化函数 fn_supplier_name_norm + 表达式唯一索引根治。

-- 22.0 供应商名规范化函数（唯一事实来源，22.1~22.5 与 Java 侧比对全部走它）
--
--   规则：① 所有空白类字符（含 U+00A0 不断行空格、U+3000 全角空格、Tab、换行）
--            统一折叠为【单个半角空格】；
--         ② 首尾空白剥除（BTRIM）；
--         ③ 整体转小写（LOWER）实现大小写不敏感。
--   为什么折叠为单空格而不是全部删除：全删会把「北京 生物」与「北京生物」误判为
--   同一家，属于过度合并，宁可漏合不可错合。
--   E'...' 转义串在【解析阶段】就把 \u00A0 / \u3000 变成真实字符再交给正则引擎，
--   不依赖正则引擎对 \uXXXX 的支持，也不依赖 [[:space:]] 在当前 locale 下
--   是否覆盖全角空格 —— 这两点都是跨环境不确定项，必须绕开。
--   IMMUTABLE 是建表达式索引的硬性前提，不可省。
CREATE OR REPLACE FUNCTION fn_supplier_name_norm(p_name TEXT)
RETURNS TEXT AS $$
    SELECT LOWER(BTRIM(REGEXP_REPLACE(
               COALESCE(p_name, ''),
               E'[[:space:]\u00A0\u3000]+',
               ' ',
               'g')))
$$ LANGUAGE SQL IMMUTABLE;

COMMENT ON FUNCTION fn_supplier_name_norm(TEXT) IS
    '供应商名称规范化：空白折叠为单空格 + 去首尾 + 转小写。用于唯一索引与迁移比对';


-- 22.0.1 【前置】人工重名守卫（P2-01：守卫前移 + 条件收窄）
--
--   ▍为什么必须前移
--     本守卫原先位于 22.1.4 之后（旧 22.2）。在脚本 autocommit 执行的年代，
--     22.1.1~22.1.4 的 UPDATE 改外键 / DELETE 删草稿已经【逐句提交落库】，
--     此时守卫再 RAISE EXCEPTION 只能中断后续语句，已提交的破坏性变更无法回滚，
--     数据库停在「外键已改指、草稿已删、唯一索引未建」的半迁移态，
--     且脚本此后每次重跑都在同一处失败，人工修复成本极高。
--   ▍修复：双保险
--     ① 结构层：全脚本已用 BEGIN;/COMMIT; 包成单事务，任何异常整体回滚（根因修复）；
--     ② 时序层：把守卫提到【一切破坏性语句之前】，让不可自动处理的场景在
--        「一条破坏性语句都还没执行」时就干净退出（快速失败，语义更清晰）。
--
--   ▍为什么条件要收窄为 source_type <> 2
--     旧条件 `GROUP BY norm HAVING COUNT(*) > 1` 不区分来源，直接前移会误杀
--     「1 条人工 + 1 条自动草稿」这种【本就应当由 22.1 自动归并】的合法场景。
--     收窄后语义精确 =「人工录入之间自己重名」，这才是脚本不敢自动删、必须人工介入的情形。
--
--   ▍场景验算（QA ④ 组用例）
--     · ④a  1 条人工 +  1 条草稿（规范名相同）
--          → 人工-only 分组仅 1 条，COUNT=1，守卫【不触发】
--          → 22.1 正常归并：外键改指人工记录，草稿被删。✔ 期望行为
--     · ④d  2 条人工（规范名相同）
--          → 人工-only 分组 2 条，COUNT=2，守卫【触发】RAISE EXCEPTION
--          → 此刻 22.1.1~22.1.4 尚未执行，破坏性语句【一条都没跑】；
--            叠加单事务，连本节之前的 DDL 一并回滚，库回到执行前状态。✔ 期望行为
--     · ④其它 纯草稿重名 → 人工-only 分组 0 条，不触发，22.1 自动归并。✔
DO $$
DECLARE
    v_dup TEXT;
BEGIN
    SELECT string_agg(t.norm || '(' || t.cnt || '条)', '; ')
      INTO v_dup
      FROM (SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) AS cnt
              FROM lis_supplier
             WHERE source_type <> 2                       -- 只看人工录入，草稿交给 22.1 自动归并
             GROUP BY fn_supplier_name_norm(supplier_name)
            HAVING COUNT(*) > 1) t;
    IF v_dup IS NOT NULL THEN
        RAISE EXCEPTION
            '存在人工录入的重名供应商，需先在页面人工归并后再执行本脚本：%', v_dup
            USING HINT = '这些记录 source_type <> 2，脚本不会自动删除，避免误删人工维护的证照数据；'
                         '本次已在任何破坏性语句执行前中断，且整个脚本单事务回滚，数据库无任何变更';
    END IF;
END $$;

-- 22.1 存量 lis_supplier 规范化去重（必须早于 22.3 建唯一索引，否则索引建不出来）
--
--   背景：本脚本先前版本已在真实库执行过，已经产生了「罗氏诊断」5 条、roche 3 条
--   这类规范化后重名的记录。若直接建唯一索引会报
--   `could not create unique index ... Key ... is duplicated` 而中断部署，
--   所以必须先归并。
--
--   赢家规则（PARTITION BY 规范名，ORDER BY 决定）：
--     ① 人工维护的记录（source_type <> 2）优先于自动迁移草稿 —— 前者有真实证照数据；
--     ② 同源时取最小 id（最早创建者胜出，结果稳定可复现）。
--   输家处理：先把所有外键引用改指向赢家，再【只删除自动迁移草稿(source_type=2)】。
--   人工录入的重名记录一律不自动删除，交由 22.3 的守卫显式报错、人工归并。

-- 22.1.1 外键改指向赢家 —— lis_reagent
UPDATE lis_reagent t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.2 外键改指向赢家 —— lis_reagent_inbound
UPDATE lis_reagent_inbound t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.3 外键改指向赢家 —— lis_reagent_purchase_order（新表，通常为空；幂等保险）
UPDATE lis_reagent_purchase_order t SET supplier_id = m.keep_id
  FROM (SELECT id, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE t.supplier_id = m.id AND m.id <> m.keep_id;

-- 22.1.4 删除已无引用的自动迁移草稿输家
DELETE FROM lis_supplier a
 USING (SELECT id, source_type, FIRST_VALUE(id) OVER w AS keep_id
          FROM lis_supplier
        WINDOW w AS (PARTITION BY fn_supplier_name_norm(supplier_name)
                     ORDER BY CASE WHEN source_type = 2 THEN 1 ELSE 0 END, id)) m
 WHERE a.id = m.id AND m.id <> m.keep_id AND m.source_type = 2;

-- 22.2 【后置】全量断言：归并完成后，对【全部记录（不限 source_type）】再查一次规范名重复。
--
--   与 22.0.1 的分工：
--     · 22.0.1 前置守卫 —— 拦【输入不合法】（人工之间重名），条件 source_type <> 2；
--     · 22.2   后置断言 —— 防【归并逻辑自身出错】，条件不限 source_type，是兜底防御。
--   正常情况下本断言【永不触发】：22.1 的窗口函数每个规范名分组只保留一个 keep_id，
--   输家若是草稿则被 22.1.4 删除，若是人工则早已被 22.0.1 拦在门外。
--   一旦触发，说明 22.1 的赢家选取或删除条件存在 bug（例如窗口分区键与索引表达式不一致），
--   此时宁可整体回滚也绝不能让下一句 CREATE UNIQUE INDEX 抛出一句
--   看不懂的 `Key (fn_supplier_name_norm(...)) is duplicated` 而中断部署。
DO $$
DECLARE
    v_dup TEXT;
BEGIN
    SELECT string_agg(t.norm || '(' || t.cnt || '条)', '; ')
      INTO v_dup
      FROM (SELECT fn_supplier_name_norm(supplier_name) AS norm, COUNT(*) AS cnt
              FROM lis_supplier
             GROUP BY fn_supplier_name_norm(supplier_name)
            HAVING COUNT(*) > 1) t;
    IF v_dup IS NOT NULL THEN
        RAISE EXCEPTION
            '【内部断言失败】22.1 归并后仍存在规范名重复的供应商：%', v_dup
            USING HINT = '这不是数据问题而是脚本缺陷：22.0.1 前置守卫已放行，说明重复项含自动草稿，'
                         '但 22.1 未能正确归并。请检查 22.1 窗口分区键是否与 fn_supplier_name_norm 一致。'
                         '整个脚本为单事务，本次执行已全部回滚，数据库无任何变更';
    END IF;
END $$;

-- 22.3 供应商名唯一索引（规范化后唯一）—— 根治 22.4/22.5 的「静默错绑」
--      注意索引建在函数表达式上，而 supplier_name 列本身仍存【原始串】，
--      页面展示保持原样，不会变成小写。
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_supplier_name_norm
    ON lis_supplier (fn_supplier_name_norm(supplier_name));

-- 22.4 供应商字符串 → 主数据草稿
--
--   从 lis_reagent.supplier 与 lis_reagent_inbound.supplier 提取，
--   按【规范名】分组去重，生成 source_type=2 / qual_status=0（待完善）的草稿。
--   组内显示名取 MIN(BTRIM(原始串))：存原值不存小写版，且 MIN 保证结果确定可复现。
--   P4-01 —— 为什么套 BTRIM 而不是裸 MIN(u.raw)：
--     文本排序中前导空格排在可见字符之前（' 罗氏诊断' < '罗氏诊断'），裸 MIN 会稳定
--     选中【带前导空格的脏串】作为该供应商的展示名，页面上表现为名称莫名缩进、
--     搜索框精确匹配失败。BTRIM 先剥首尾空白再参与 MIN 比较与落库，
--     既不影响分组（分组键始终是 fn_supplier_name_norm，与本改动无关），
--     又保证落库的 supplier_name 首尾干净。注意仅 BTRIM 不 LOWER：
--     中间空白与大小写属于「原始书写形态」，按既定策略保留原值。
--
--   编号幂等（原缺陷：ROW_NUMBER 每次从 1 重排，不感知库内已有 SUP_MIG_xxxx）：
--   QA 实测 pass1 插入 A、业务新增 B、pass2 时 B 又拿到 SUP_MIG_0001，
--   触发 `重复键违反唯一约束 uk_lis_supplier_code`，带 ON_ERROR_STOP=1 的部署整体中断。
--   改为「库内已有最大编号 + 行号」续号：
--     · 子查询不相关，PostgreSQL 作为 InitPlan 只求值一次，所有行共享同一基数；
--     · INSERT ... SELECT 使用语句开始前的快照，读不到本次自己插入的行，故需 ROW_NUMBER 递增；
--     · 窗口函数在 WHERE 之后计算，行号只覆盖真正要插入的行，不会产生编号空洞。
--   正则守卫 ^SUP_MIG_[0-9]+$ 不可省：库中若存在人工录入的 SUP_MIG_ABC 之类非规范编码，
--   CAST(... AS INT) 会直接抛异常中断整个脚本。
INSERT INTO lis_supplier (supplier_code, supplier_name, qual_status, source_type, status, remark)
SELECT 'SUP_MIG_' || LPAD(CAST(
           (SELECT COALESCE(MAX(CAST(SUBSTRING(supplier_code FROM 9) AS INT)), 0)
              FROM lis_supplier
             WHERE supplier_code ~ '^SUP_MIG_[0-9]+$')
           + ROW_NUMBER() OVER (ORDER BY s.norm)
       AS VARCHAR), 4, '0'),
       s.name, 0, 2, 1, '由存量 supplier 字符串自动迁移生成，请补录证照信息'
FROM (
    SELECT fn_supplier_name_norm(u.raw) AS norm, MIN(BTRIM(u.raw)) AS name
    FROM (
        SELECT supplier AS raw FROM lis_reagent
         WHERE supplier IS NOT NULL AND fn_supplier_name_norm(supplier) <> ''
        UNION ALL
        SELECT supplier AS raw FROM lis_reagent_inbound
         WHERE supplier IS NOT NULL AND fn_supplier_name_norm(supplier) <> ''
    ) u
    GROUP BY fn_supplier_name_norm(u.raw)
) s
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier x
                   WHERE fn_supplier_name_norm(x.supplier_name) = s.norm);

-- 22.5 回填 lis_reagent.supplier_id（按规范名匹配，幂等）
--      22.3 的唯一索引保证至多命中一行，不再出现「优化器随机选赢家」。
UPDATE lis_reagent r SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE r.supplier_id IS NULL
   AND r.supplier IS NOT NULL
   AND fn_supplier_name_norm(r.supplier) = fn_supplier_name_norm(sp.supplier_name);

-- 22.6 回填 lis_reagent_inbound.supplier_id
UPDATE lis_reagent_inbound i SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE i.supplier_id IS NULL
   AND i.supplier IS NOT NULL
   AND fn_supplier_name_norm(i.supplier) = fn_supplier_name_norm(sp.supplier_name);


-- 23. 试剂分类字典种子数据（幂等）
INSERT INTO lis_reagent_category (category_code, category_name, parent_id, path, level_no, hazard_category, sort_order, status)
SELECT v.code, v.name, v.pid, v.path, v.lv, v.hz, v.sort, 1
FROM (VALUES
    ('CAT_CHEM',   '化学试剂',      0::bigint, '/CAT_CHEM/',   1::smallint, 0::smallint, 10),
    ('CAT_BIO',    '生物试剂',      0::bigint, '/CAT_BIO/',    1::smallint, 0::smallint, 20),
    ('CAT_HAZARD', '危险品',        0::bigint, '/CAT_HAZARD/', 1::smallint, 1::smallint, 30),
    ('CAT_CONSUM', '耗材',          0::bigint, '/CAT_CONSUM/', 1::smallint, 0::smallint, 40),
    ('CAT_QC',     '质控品/校准品', 0::bigint, '/CAT_QC/',     1::smallint, 0::smallint, 50)
) AS v(code, name, pid, path, lv, hz, sort)
WHERE NOT EXISTS (SELECT 1 FROM lis_reagent_category c WHERE c.category_code = v.code);


-- 24. reagent_code 存量重复检测（主理人 U-1：索引已存在，不新建索引，仅保留检测）
--     init-schema.sql L648 已有 uk_lis_reagent_code 唯一索引，理论上不可能存在重复。
--     以下为只读检测语句，正常应返回 0 行；若返回非 0 行说明索引曾被人工删除，
--     需由试剂管理员在页面人工归并后再补建索引，不在本脚本中自动处理。
-- SELECT reagent_code, COUNT(*) AS cnt
--   FROM lis_reagent
--  GROUP BY reagent_code
-- HAVING COUNT(*) > 1;


-- =============================================================================
-- 七、执行后自检（QA 可直接跑，全部应返回 0 行 / 期望值）
-- =============================================================================

-- 自检 1：库存唯一索引已切换为三列（应返回 uk_lis_reagent_stock_lot_wh 一行，且无 uk_lis_reagent_stock_lot）
-- SELECT indexname FROM pg_indexes WHERE tablename = 'lis_reagent_stock' AND indexname LIKE 'uk_%';

-- 自检 2：无 warehouse_id 为 NULL 的库存记录（应返回 0）
-- SELECT COUNT(*) FROM lis_reagent_stock WHERE warehouse_id IS NULL;

-- 自检 3：默认库房哨兵存在（应返回 1）
-- SELECT COUNT(*) FROM lis_reagent_warehouse WHERE id = 0;

-- 自检 4：审批节点已初始化（应返回 4）
-- SELECT COUNT(*) FROM lis_reagent_approval_node;

-- 自检 5：min_stock 已从 safety_stock 迁移（应返回 0）
-- SELECT COUNT(*) FROM lis_reagent WHERE min_stock IS NULL AND safety_stock IS NOT NULL;

-- 自检 6：新增表数量（应返回 14）
-- SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN (
--   'lis_reagent_category','lis_supplier','lis_reagent_warehouse',
--   'lis_reagent_budget','lis_reagent_budget_record','lis_reagent_approval_node',
--   'lis_reagent_purchase_apply','lis_reagent_purchase_apply_item','lis_reagent_purchase_approval',
--   'lis_reagent_purchase_order','lis_reagent_purchase_order_item',
--   'lis_reagent_requisition','lis_reagent_requisition_item','lis_reagent_scrap_disposal');

-- 自检 7：全部试剂相关表均无 deleted 列（应返回 0）—— 见附录 A
-- SELECT COUNT(*) FROM information_schema.columns
--  WHERE column_name = 'deleted' AND (table_name = 'lis_supplier' OR table_name LIKE 'lis_reagent%');

-- 自检 8：CHECK 约束存在（应返回 4）
-- SELECT COUNT(*) FROM pg_constraint WHERE conname IN (
--   'ck_lis_reagent_budget_amount','ck_lis_reagent_purchase_apply_item_qty',
--   'ck_lis_reagent_purchase_order_item_arrived','ck_lis_reagent_requisition_item_qty');


-- =============================================================================
-- 附录 A：C-9「logic-delete 地雷」复核结论 —— 本脚本不加 deleted 列的依据
-- =============================================================================
--
-- 设计文档 §6 T-01/T-02、§7 第 4 条、§8 U-2 主张给全部新表与 9 张现有试剂表补
-- `deleted BIGINT NOT NULL DEFAULT 0` + 实体 `@TableLogic`。经实测复核，该主张不成立，
-- 且与设计文档自身 §1 C-9 正文（第 277/281 行）相互矛盾。本脚本采纳 §1 C-9 正文结论。
--
-- 证据：
--   1) MyBatis-Plus 3.5.5 的 logic-delete-field 是「按实体字段名匹配」生效的：
--      TableInfoHelper 在解析实体时，只有发现同名字段才会标记 logicDelete 并注入
--      `AND deleted = 0` / 改写 deleteById 为 UPDATE。实体无该字段时配置完全惰性。
--   2) grep lis-service-reagent/src/main/java 中 `deleted` / `@TableLogic` —— 零命中，
--      9 个存量实体均未声明 deleted 字段。
--   3) 全项目仅 lis-service-sample 的 Sample.java 声明了 `private Integer deleted`，
--      且 lis_sample 表确有该列（init-schema.sql L113）；日志中也仅 lis_sample 的 SQL
--      带 `deleted = 0`，其余表（含全部 reagent 表）的 SQL 均不带。
--   4) 存量代码已在调用 removeById / deleteById（ReagentController L55、
--      ReagentStockController L63、ReagentStockRecordController L58），服务长期正常运行，
--      日志中不存在任何 `column "deleted" does not exist` 报错。
--
-- 结论与决策（经主理人裁决）：
--   · 不给 9 张现有试剂表加 deleted 列（避免无谓的破坏性变更）。
--   · 14 张新表同样不加 deleted 列，与存量保持一致，避免出现「一半表有逻辑删除、
--     一半没有」的半吊子状态。
--   · 新实体禁止声明名为 deleted 的字段（否则才会真正触发 C-9 描述的运行期报错）。
--     需要逻辑删除语义时一律使用既有约定的 status 字段（0停用 1启用）。
--
-- =============================================================================
-- 脚本结束。新增表 14 张，改造现有表 7 处，重建索引 1 处，数据迁移 4 项。
-- =============================================================================


-- ⚠️ P2-01：单事务提交。与文件头部的 BEGIN; 严格配对。
-- 若前文任一语句报错且 psql 带 ON_ERROR_STOP=1，将不会执行到此处，事务由 psql 退出时回滚；
-- 若未带 ON_ERROR_STOP，事务已处于 aborted 状态，此 COMMIT 等价 ROLLBACK，同样不会留下半迁移态。
COMMIT;

-- =============================================================================
-- 【增量脚本】add-reagent-lot-trace.sql
-- =============================================================================
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;       -- 试剂批次库存ID (关联 lis_reagent_stock.id)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);  -- 试剂批号 (冗余, 便于追溯查询)

COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次库存ID (关联 lis_reagent_stock.id)';
COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号 (冗余, 便于追溯查询)';

CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_no ON lis_result (reagent_lot_no);
CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_id ON lis_result (reagent_lot_id);

-- =============================================================================
-- 【增量脚本】add-test-tube.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_test_tube (
    id              BIGSERIAL PRIMARY KEY,
    tube_code       VARCHAR(32) NOT NULL,                  -- 管编码
    tube_name       VARCHAR(100) NOT NULL,                 -- 管名称
    tube_color      VARCHAR(20),                           -- 管颜色: 红/紫/蓝/绿/黑/黄/灰
    tube_type       VARCHAR(20),                           -- 管类型: 采血管/尿杯/粪盒/培养管
    volume          VARCHAR(20),                           -- 容量
    additive        VARCHAR(100),                          -- 抗凝剂/添加剂
    sort_order      INT DEFAULT 0,                         -- 排序
    status          SMALLINT DEFAULT 1,                    -- 0停用 1启用
    remark          VARCHAR(200),                          -- 备注
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_test_tube IS '检验试管字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_test_tube_code ON lis_test_tube (tube_code);
CREATE INDEX IF NOT EXISTS idx_lis_test_tube_type ON lis_test_tube (tube_type);
CREATE INDEX IF NOT EXISTS idx_lis_test_tube_status ON lis_test_tube (status);

-- 2. 检验项目(指标/组合) 绑定试管: 增加 tube_id 字段 (NULL表示未绑定)
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS tube_id BIGINT;
COMMENT ON COLUMN lis_test_item.tube_id IS '绑定的检验试管ID (NULL表示未绑定)';
CREATE INDEX IF NOT EXISTS idx_lis_test_item_tube ON lis_test_item (tube_id);

-- 2.1 检验项目 绑定采集方式与报告模板
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS collection_method_id BIGINT;
COMMENT ON COLUMN lis_test_item.collection_method_id IS '采集方式ID (关联 lis_collection_method, NULL表示未绑定)';
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS report_template_id BIGINT;
COMMENT ON COLUMN lis_test_item.report_template_id IS '报告模板ID (关联 lis_report_template, NULL表示未绑定)';
CREATE INDEX IF NOT EXISTS idx_lis_test_item_collection_method ON lis_test_item (collection_method_id);
CREATE INDEX IF NOT EXISTS idx_lis_test_item_report_template ON lis_test_item (report_template_id);

-- 2.2 采集方式字典表
CREATE TABLE IF NOT EXISTS lis_collection_method (
    id              BIGSERIAL PRIMARY KEY,
    method_code     VARCHAR(50) NOT NULL,                 -- 方式编码
    method_name     VARCHAR(100) NOT NULL,                -- 方式名称
    description     TEXT,                                 -- 描述
    sort_order      INT DEFAULT 0,                        -- 排序
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_collection_method IS '采集方式字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_collection_method_code ON lis_collection_method (method_code);
CREATE INDEX IF NOT EXISTS idx_lis_collection_method_status ON lis_collection_method (status);

-- 2.3 初始化采集方式数据
INSERT INTO lis_collection_method (method_code, method_name, description, sort_order, status)
SELECT * FROM (VALUES
    ('VENOUS',   '静脉采血', '使用静脉穿刺采集血液标本', 10, 1),
    ('FINGER',   '末梢采血', '指尖末梢采血', 20, 1),
    ('EAR',      '耳垂采血', '耳垂采血', 30, 1),
    ('URINE',    '尿液采集', '留取中段尿', 40, 1),
    ('STOOL',    '粪便采集', '新鲜粪便样本', 50, 1),
    ('THROAT',   '咽拭子',   '咽后壁拭子采集', 60, 1),
    ('NASAL',    '鼻拭子',   '鼻腔拭子采集', 70, 1),
    ('SPUTUM',   '痰液采集', '晨起深咳痰液', 80, 1),
    ('BLOOD_GAS','动脉采血', '动脉血气采血', 90, 1),
    ('CULTURE',  '无菌采集', '无菌操作采集培养标本', 100, 1)
) AS v(method_code, method_name, description, sort_order, status)
WHERE NOT EXISTS (SELECT 1 FROM lis_collection_method);

-- 3. 仪器项目映射表扩展: 增加标本类型和操作人员字段
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS specimen_type VARCHAR(20);
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS operator_id BIGINT;
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS operator_name VARCHAR(50);
COMMENT ON COLUMN lis_instrument_item_map.specimen_type IS '适用标本类型 (NULL表示全部)';
COMMENT ON COLUMN lis_instrument_item_map.operator_id IS '操作人员ID';
COMMENT ON COLUMN lis_instrument_item_map.operator_name IS '操作人员姓名';
CREATE INDEX IF NOT EXISTS idx_lis_instrument_item_map_specimen ON lis_instrument_item_map (specimen_type);

-- 4. 初始化检验试管数据
INSERT INTO lis_test_tube (tube_code, tube_name, tube_color, tube_type, volume, additive, sort_order, status, remark) VALUES
('RED_TUBE',   '红头管',   '红', '采血管', '5ml',  '无(促凝)',         1, 1, '生化/免疫/血清学'),
('PST_TUBE',   '黄头管',   '黄', '采血管', '5ml',  '分离胶+促凝',      2, 1, '生化/免疫'),
('EDTA_TUBE',  '紫头管',   '紫', '采血管', '2ml',  'EDTA-K2',          3, 1, '血常规/血型/糖化血红蛋白'),
('CITRATE_TUBE','蓝头管',  '蓝', '采血管', '2.7ml','枸橼酸钠(1:9)',     4, 1, '凝血功能'),
('HEPARIN_TUBE','绿头管',  '绿', '采血管', '5ml',  '肝素钠',           5, 1, '生化/血流变'),
('OXALATE_TUBE','黑头管',  '黑', '采血管', '2ml',  '草酸钾/氟化钠',     6, 1, '血沉'),
('FLUORIDE_TUBE','灰头管', '灰', '采血管', '2ml',  '草酸钾/氟化钠',     7, 1, '血糖'),
('URINE_CUP',   '尿杯',    '',  '尿杯',   '50ml', '无',               10, 1, '尿常规'),
('STOOL_BOX',   '粪盒',    '',  '粪盒',   '15ml', '无',               11, 1, '粪便常规'),
('CULTURE_TUBE','培养管',  '',  '培养管', '10ml', '无菌',             12, 1, '微生物培养')
ON CONFLICT DO NOTHING;

-- 5. 授权给应用数据库用户
GRANT SELECT, INSERT, UPDATE, DELETE ON lis_test_tube TO lis_user;
GRANT USAGE, SELECT ON SEQUENCE lis_test_tube_id_seq TO lis_user;

-- =============================================================================
-- 【增量脚本】add-specimen-type.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_specimen_type (
    id              BIGSERIAL PRIMARY KEY,
    type_code       VARCHAR(32) NOT NULL,                 -- 类型编码
    type_name       VARCHAR(100) NOT NULL,                -- 类型名称
    description     VARCHAR(200),                         -- 描述
    status          SMALLINT DEFAULT 1,                   -- 0停用 1启用
    sort_order      INT DEFAULT 0,                        -- 排序
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_specimen_type IS '标本类型字典表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_specimen_type_code ON lis_specimen_type (type_code);
CREATE INDEX IF NOT EXISTS idx_lis_specimen_type_status ON lis_specimen_type (status);

-- 2. 参考范围表扩展: 增加标本类型和仪器字段 (NULL 表示全部适用)
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS specimen_type VARCHAR(20);
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS instrument_id BIGINT;
COMMENT ON COLUMN lis_reference_range.specimen_type IS '标本类型 (NULL表示全部适用)';
COMMENT ON COLUMN lis_reference_range.instrument_id IS '仪器ID (NULL表示全部适用)';
CREATE INDEX IF NOT EXISTS idx_lis_reference_range_specimen ON lis_reference_range (specimen_type);
CREATE INDEX IF NOT EXISTS idx_lis_reference_range_instrument ON lis_reference_range (instrument_id);

-- 3. 初始化标本类型数据
INSERT INTO lis_specimen_type (type_code, type_name, description, status, sort_order) VALUES
('WHOLE_BLOOD', '全血', '全血标本', 1, 1),
('SERUM',       '血清', '血清标本', 1, 2),
('PLASMA',      '血浆', '血浆标本', 1, 3),
('URINE',       '尿液', '尿液标本', 1, 4),
('STOOL',       '粪便', '粪便标本', 1, 5),
('SPUTUM',      '痰液', '痰液标本', 1, 6),
('CSF',         '脑脊液', '脑脊液标本', 1, 7),
('PLEURAL',     '胸腹水', '胸腹水标本', 1, 8),
('SECRETION',   '分泌物', '分泌物标本', 1, 9),
('OTHER',       '其他', '其他标本类型', 1, 99)
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 【增量脚本】add-result-extra-fields.sql
-- =============================================================================
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_result_value VARCHAR(100);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_result_time TIMESTAMP;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS last_abnormal_flag VARCHAR(10);
COMMENT ON COLUMN lis_result.last_result_value IS '上次结果值 (历史记录, 患者同一指标的上一次检验结果)';
COMMENT ON COLUMN lis_result.last_result_time IS '上次结果时间';
COMMENT ON COLUMN lis_result.last_abnormal_flag IS '上次结果异常标志';

-- 2) 仪器计算相关: OD值 / CUTOFF值 / S/CO值 (常用于酶标仪/化学发光)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS od_value DECIMAL(12, 4);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS cutoff_value DECIMAL(12, 4);
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS sc_ratio DECIMAL(12, 4);
COMMENT ON COLUMN lis_result.od_value IS 'OD值 (光密度, 仪器原始输出)';
COMMENT ON COLUMN lis_result.cutoff_value IS 'CUTOFF值 (仪器计算阈值)';
COMMENT ON COLUMN lis_result.sc_ratio IS 'S/CO值 (样本信号/ cutoff 比值)';

-- 3) 自动审核相关 (扩展 audit_status 语义)
-- auto_audit: 0=未做自动审核 1=通过自动审核 2=自动审核异常 (即触发规则)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS auto_audit SMALLINT DEFAULT 0;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS auto_audit_message VARCHAR(500);
COMMENT ON COLUMN lis_result.auto_audit IS '自动审核标志: 0未做 1通过 2异常';
COMMENT ON COLUMN lis_result.auto_audit_message IS '自动审核异常信息';

-- 4) 项目/指标知识备注 (技师工作站 "项目知识" 提示)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS item_knowledge VARCHAR(1000);
COMMENT ON COLUMN lis_result.item_knowledge IS '项目知识 / 临床提示 (存储为结果级别的备注, 可由自动审核/规则引擎回填)';

-- 5) 索引 (按仪器/时间筛选)
CREATE INDEX IF NOT EXISTS idx_lis_result_audit ON lis_result (auto_audit, audit_status);

-- =============================================================================
-- 【增量脚本】add-result-image.sql
-- =============================================================================
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS image_url VARCHAR(2000);
COMMENT ON COLUMN lis_result.image_url IS '检验结果图像(JSON数组字符串, 存储血常规/尿常规等形态图像的URL, 通常3-4张)';

-- =============================================================================
-- 【增量脚本】add-sample-image-table.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_sample_image (
    id             BIGSERIAL PRIMARY KEY,
    sample_id      BIGINT NOT NULL,                        -- 标本ID
    image_url      VARCHAR(1000) NOT NULL,                 -- 图像URL
    image_name     VARCHAR(200),                           -- 图像名称/说明 (如 图1-细胞形态)
    image_type     VARCHAR(50),                            -- 图像类型 (如 CELL_MORPHOLOGY/URINE_SEDIMENT)
    sort_no        SMALLINT DEFAULT 0,                     -- 排序号
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_sample_image IS '标本图像表(一个标本对应多个图像, 血常规/尿常规等形态图像)';
COMMENT ON COLUMN lis_sample_image.sample_id IS '标本ID (关联 lis_sample.id)';
COMMENT ON COLUMN lis_sample_image.image_url IS '图像URL';
COMMENT ON COLUMN lis_sample_image.image_name IS '图像名称/说明';
COMMENT ON COLUMN lis_sample_image.image_type IS '图像类型';
COMMENT ON COLUMN lis_sample_image.sort_no IS '排序号';
CREATE INDEX IF NOT EXISTS idx_lis_sample_image_sample ON lis_sample_image (sample_id);

-- =============================================================================
-- 【增量脚本】add-sample-no-instrument.sql
-- =============================================================================
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sample_no VARCHAR(32);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS instrument_id BIGINT;

COMMENT ON COLUMN lis_sample.sample_no IS '标本号 (按仪器序号生成)';
COMMENT ON COLUMN lis_sample.instrument_id IS '核收仪器ID';

-- 按仪器查询标本号索引
CREATE INDEX IF NOT EXISTS idx_lis_sample_instrument_no ON lis_sample (instrument_id, sample_no);

-- =============================================================================
-- 【增量脚本】add-sample-receiver.sql
-- =============================================================================
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver_id BIGINT;

COMMENT ON COLUMN lis_sample.receiver IS '核收人(姓名冗余)';
COMMENT ON COLUMN lis_sample.receiver_id IS '核收人ID(关联人事员工 lis_staff.id)';

-- =============================================================================
-- 【增量脚本】add-technician-batch-fields.sql
-- =============================================================================
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS waive_flag       SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS waive_reason     VARCHAR(500);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS split_flag       SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS parent_id        BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_reason   VARCHAR(500);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_operator VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS destroy_time     TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS inspector        VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS inspector_user_id BIGINT;

COMMENT ON COLUMN lis_sample.waive_flag        IS '让步核收标志: 0否 1是';
COMMENT ON COLUMN lis_sample.waive_reason      IS '让步核收原因';
COMMENT ON COLUMN lis_sample.split_flag        IS '拆分标志: 0未拆分 1已拆分';
COMMENT ON COLUMN lis_sample.parent_id         IS '拆分子标本的父标本ID';
COMMENT ON COLUMN lis_sample.destroy_reason    IS '销毁原因';
COMMENT ON COLUMN lis_sample.destroy_operator  IS '销毁操作人';
COMMENT ON COLUMN lis_sample.destroy_time      IS '销毁时间';
COMMENT ON COLUMN lis_sample.inspector         IS '检验人姓名';
COMMENT ON COLUMN lis_sample.inspector_user_id IS '检验人用户ID';

CREATE INDEX IF NOT EXISTS idx_lis_sample_parent ON lis_sample (parent_id);

-- 2. 技师工作站收藏夹表
CREATE TABLE IF NOT EXISTS lis_technician_favorite (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20) NOT NULL DEFAULT 'sample',
    payload      JSON,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_technician_favorite IS '技师工作站收藏夹';
COMMENT ON COLUMN lis_technician_favorite.name  IS '收藏名称';
COMMENT ON COLUMN lis_technician_favorite.type  IS '收藏类型: sample=标本 keyword=搜索条件 panel=检验项目';
COMMENT ON COLUMN lis_technician_favorite.payload IS '收藏内容JSON';

-- 3. lis_result 表增加唯一约束 (sample_id + test_item_id) 用于 upsert
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_result_sample_item ON lis_result (sample_id, test_item_id);

-- =============================================================================
-- 【增量脚本】add-technician-signin-print.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_barcode_print_log (
    id             BIGSERIAL PRIMARY KEY,
    sample_id      BIGINT      NOT NULL,
    sample_barcode VARCHAR(64),
    print_type     VARCHAR(20) DEFAULT 'REPRINT',
    copies         SMALLINT    DEFAULT 1,
    printer_name   VARCHAR(64),
    reason         VARCHAR(200),
    operator_id    BIGINT,
    operator_name  VARCHAR(64),
    print_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  lis_barcode_print_log                IS '条码打印/补打记录';
COMMENT ON COLUMN lis_barcode_print_log.sample_id      IS '标本ID, 关联 lis_sample.id';
COMMENT ON COLUMN lis_barcode_print_log.sample_barcode IS '标本条码(冗余, 记录打印当时的条码)';
COMMENT ON COLUMN lis_barcode_print_log.print_type     IS '打印类型: FIRST 首次 / REPRINT 补打';
COMMENT ON COLUMN lis_barcode_print_log.copies         IS '打印份数';
COMMENT ON COLUMN lis_barcode_print_log.printer_name   IS '打印机名称';
COMMENT ON COLUMN lis_barcode_print_log.reason         IS '补打原因';
COMMENT ON COLUMN lis_barcode_print_log.operator_id    IS '操作人ID';
COMMENT ON COLUMN lis_barcode_print_log.operator_name  IS '操作人姓名';
COMMENT ON COLUMN lis_barcode_print_log.print_time     IS '打印时间';

CREATE INDEX IF NOT EXISTS idx_bpl_sample ON lis_barcode_print_log (sample_id);
CREATE INDEX IF NOT EXISTS idx_bpl_print_time ON lis_barcode_print_log (print_time);

-- 2. 工作站签到表 (仪器签到 / 自动审核签到)
CREATE TABLE IF NOT EXISTS lis_station_signin (
    id            BIGSERIAL PRIMARY KEY,
    signin_type   VARCHAR(20) NOT NULL,
    instrument_id BIGINT,
    operator_id   BIGINT,
    operator_name VARCHAR(64),
    action        VARCHAR(10) DEFAULT 'signin',
    signin_time   TIMESTAMP,
    signout_time  TIMESTAMP,
    status        SMALLINT    DEFAULT 1,
    remark        VARCHAR(200)
);

COMMENT ON TABLE  lis_station_signin               IS '工作站签到记录: 仪器签到 / 自动审核签到';
COMMENT ON COLUMN lis_station_signin.signin_type   IS '签到类型: INSTRUMENT 仪器 / AUTO_AUDIT 自动审核';
COMMENT ON COLUMN lis_station_signin.instrument_id IS '仪器ID (AUTO_AUDIT 时为 NULL)';
COMMENT ON COLUMN lis_station_signin.operator_id   IS '操作人ID';
COMMENT ON COLUMN lis_station_signin.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_station_signin.action        IS '动作: signin 签到 / signout 签退';
COMMENT ON COLUMN lis_station_signin.signin_time   IS '签到时间';
COMMENT ON COLUMN lis_station_signin.signout_time  IS '签退时间';
COMMENT ON COLUMN lis_station_signin.status        IS '状态: 1 在岗 / 0 已签退';
COMMENT ON COLUMN lis_station_signin.remark        IS '备注';

CREATE INDEX IF NOT EXISTS idx_signin_type_status ON lis_station_signin (signin_type, status);
CREATE INDEX IF NOT EXISTS idx_signin_instrument ON lis_station_signin (instrument_id);
CREATE INDEX IF NOT EXISTS idx_signin_operator ON lis_station_signin (operator_id);

-- =============================================================================
-- 【增量脚本】create-report-template.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS lis_report_template (
    id BIGSERIAL PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    instrument_id BIGINT,
    instrument_name VARCHAR(100),
    template_config TEXT,
    dual_column SMALLINT DEFAULT 0,
    paper_size VARCHAR(10) DEFAULT 'A4',
    orientation VARCHAR(10) DEFAULT 'portrait',
    margin INT DEFAULT 10,
    is_default SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_report_template IS '报告模板表';
COMMENT ON COLUMN lis_report_template.template_name IS '模板名称';
COMMENT ON COLUMN lis_report_template.instrument_id IS '关联仪器ID(为空表示通用模板)';
COMMENT ON COLUMN lis_report_template.template_config IS '模板配置JSON';
COMMENT ON COLUMN lis_report_template.dual_column IS '结果是否双列显示 0-单列 1-双列';
COMMENT ON COLUMN lis_report_template.is_default IS '是否默认模板 0-否 1-是';

INSERT INTO lis_report_template (template_name, instrument_id, instrument_name, template_config, dual_column, paper_size, orientation, margin, is_default, status, create_time, update_time)
VALUES ('默认模板', NULL, NULL, '{}', 1, 'A4', 'portrait', 10, 1, 1, NOW(), NOW());

-- =============================================================================
-- 【增量脚本】insert-test-panels.sql
-- =============================================================================
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CBP',  '血常规',       'Complete Blood Count',     '血常规',  '血常规', '全血', NULL, NULL, 1, NULL, 55.00, 1),
('LFT',  '肝功能',       'Liver Function Test',       '肝功能',  '生化',   '血清', NULL, NULL, 1, NULL, 36.00, 1),
('GLU_T','血糖',         'Blood Glucose',             '血糖',    '生化',   '血清', NULL, NULL, 1, NULL, 12.00, 1),
('TP_T', '总蛋白测定',   'Total Protein Test',        '总蛋白',  '生化',   '血清', NULL, NULL, 1, NULL, 10.00, 1);

-- 关联检验指标到检验项目 (设置parent_id)
-- 血常规: WBC(1), RBC(2), HGB(3), PLT(4)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'CBP') WHERE id IN (1, 2, 3, 4);
-- 肝功能: ALT(6), AST(7)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'LFT') WHERE id IN (6, 7);
-- 血糖: GLU(5)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'GLU_T') WHERE id = 5;
-- 总蛋白: TP(8)
UPDATE lis_test_item SET parent_id = (SELECT id FROM lis_test_item WHERE item_code = 'TP_T') WHERE id = 8;

-- =============================================================================
-- 【增量脚本】add-test-item-instrument-data.sql
-- =============================================================================
BEGIN;

-- ============================================================
-- 一、清理旧数据 (保留已有ID, 追加新数据)
-- ============================================================
DELETE FROM lis_reference_range;
DELETE FROM lis_instrument_item_map;
-- 不删除 lis_test_item 和 lis_instrument, 使用追加方式

-- ============================================================
-- 二、检验项目 (is_panel=1) — 20个检验项目面板
-- ============================================================
-- 已有: 9=血常规 10=肝功能 11=血糖 12=总蛋白测定
-- 新增从 ID=13 开始

INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, is_panel, parent_id, price, status) VALUES
-- 肝功能组 (扩展)
('LFT_FULL',  '肝功能全套',     'Liver Function Test Full',   '肝全',   '生化', '血清', 1, NULL, 80.00, 1),  -- 13
('TP_T2',      '总胆红素测定',   'Total Bilirubin Test',       'TBIL',   '生化', '血清', 1, NULL, 15.00, 1),  -- 14
-- 肾功能组
('RFT',        '肾功能',         'Renal Function Test',        '肾功',   '生化', '血清', 1, NULL, 35.00, 1),  -- 15
-- 血脂组
('LIPID',      '血脂全套',       'Lipid Profile',              '血脂',   '生化', '血清', 1, NULL, 60.00, 1),  -- 16
-- 心肌酶谱
('CK_PANEL',   '心肌酶谱',       'Cardiac Enzyme Panel',       '心肌酶', '生化', '血清', 1, NULL, 50.00, 1),  -- 17
-- 电解质
('ELECTRO',    '电解质',         'Electrolytes',               '电解质', '生化', '血清', 1, NULL, 30.00, 1),  -- 18
-- 胰腺功能
('PANC',       '胰腺功能',       'Pancreatic Function',        '胰腺',   '生化', '血清', 1, NULL, 40.00, 1),  -- 19
-- 凝血功能
('COAG',       '凝血功能',       'Coagulation Panel',          '凝血',   '凝血', '血浆', 1, NULL, 45.00, 1),  -- 20
-- 尿液分析
('UA_PANEL',   '尿液分析',       'Urinalysis Panel',           '尿常规', '尿液', '尿液', 1, NULL, 20.00, 1),  -- 21
-- 甲状腺功能
('THYROID',    '甲状腺功能',     'Thyroid Function',           '甲功',   '免疫', '血清', 1, NULL, 120.00, 1), -- 22
-- 肿瘤标志物
('TUMOR',      '肿瘤标志物',     'Tumor Markers',              '肿瘤',   '免疫', '血清', 1, NULL, 200.00, 1), -- 23
-- 糖代谢
('GLU_META',   '糖代谢',         'Glucose Metabolism',         '糖代谢', '生化', '血清', 1, NULL, 55.00, 1),  -- 24
-- 炎症标志物
('INFLAM',     '炎症标志物',     'Inflammation Markers',       '炎症',   '免疫', '血清', 1, NULL, 50.00, 1),  -- 25
-- 免疫球蛋白
('IG_PANEL',   '免疫球蛋白',     'Immunoglobulin Panel',       '免疫球蛋白', '免疫', '血清', 1, NULL, 80.00, 1), -- 26
-- 血气分析
('BLOOD_GAS',  '血气分析',       'Blood Gas Analysis',         '血气',   '生化', '全血', 1, NULL, 60.00, 1),  -- 27
-- 骨代谢
('BONE_META',  '骨代谢标志物',   'Bone Metabolism Markers',    '骨代谢', '生化', '血清', 1, NULL, 90.00, 1),  -- 28
-- 铁代谢
('IRON_META',  '铁代谢',         'Iron Metabolism',            '铁代谢', '生化', '血清', 1, NULL, 40.00, 1),  -- 29
-- 自身免疫
('AUTO_IMM',   '自身免疫抗体',   'Autoimmune Antibodies',      '自免',   '免疫', '血清', 1, NULL, 150.00, 1), -- 30
-- 肝纤维化
('LIVER_FIB',  '肝纤维化指标',   'Liver Fibrosis Markers',     '肝纤',   '免疫', '血清', 1, NULL, 100.00, 1), -- 31
-- 血红蛋白电泳
('HGB_ELEC',   '血红蛋白电泳',   'Hemoglobin Electrophoresis', '电泳',   '生化', '全血', 1, NULL, 70.00, 1)   -- 32
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 三、检验指标 (is_panel=0) — 120+个指标
-- ============================================================

-- ===== 血常规指标 (parent_id=9, 已有1-4) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HCT',     '红细胞压积',           'Hematocrit',                'HCT',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('MCV',     '平均红细胞体积',       'Mean Corpuscular Volume',   'MCV',   '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('MCH',     '平均红细胞血红蛋白量', 'Mean Corpuscular Hemoglobin','MCH',  '血常规', '全血', 'pg',    '仪器法', 0, 9, 5.00, 1),
('MCHC',    '平均红细胞血红蛋白浓度','Mean Corpuscular Hb Conc.', 'MCHC',  '血常规', '全血', 'g/L',   '仪器法', 0, 9, 5.00, 1),
('RDW',     '红细胞分布宽度',       'RBC Distribution Width',    'RDW',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('PDW',     '血小板分布宽度',       'Platelet Distribution Width','PDW',  '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('MPV',     '平均血小板体积',       'Mean Platelet Volume',      'MPV',   '血常规', '全血', 'fL',    '仪器法', 0, 9, 5.00, 1),
('PCT',     '血小板压积',           'Plateletcrit',              'PCT',   '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('NEUT',    '中性粒细胞计数',       'Neutrophil Count',          'NEUT',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('LYMPH',   '淋巴细胞计数',         'Lymphocyte Count',          'LYMPH', '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('MONO',    '单核细胞计数',         'Monocyte Count',            'MONO',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('EOS',     '嗜酸性粒细胞计数',     'Eosinophil Count',          'EOS',   '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('BASO',    '嗜碱性粒细胞计数',     'Basophil Count',            'BASO',  '血常规', '全血', '10^9/L','仪器法', 0, 9, 8.00, 1),
('NEUT_P',  '中性粒细胞百分比',     'Neutrophil Percentage',     'NEUT%', '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('LYMPH_P', '淋巴细胞百分比',       'Lymphocyte Percentage',     'LYMPH%','血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1),
('MONO_P',  '单核细胞百分比',       'Monocyte Percentage',       'MONO%', '血常规', '全血', '%',     '仪器法', 0, 9, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肝功能指标 (parent_id=10 已有ALT/AST, parent_id=13 肝功能全套) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TBIL',    '总胆红素',             'Total Bilirubin',           'TBIL',  '生化', '血清', 'umol/L', '钒酸盐氧化法', 0, 10, 10.00, 1),
('DBIL',    '直接胆红素',           'Direct Bilirubin',          'DBIL',  '生化', '血清', 'umol/L', '钒酸盐氧化法', 0, 10, 10.00, 1),
('IBIL',    '间接胆红素',           'Indirect Bilirubin',        'IBIL',  '生化', '血清', 'umol/L', '计算法',       0, 10, 10.00, 1),
('ALP',     '碱性磷酸酶',           'Alkaline Phosphatase',      'ALP',   '生化', '血清', 'U/L',    'AMP缓冲液法',  0, 10, 10.00, 1),
('GGT',     'γ-谷氨酰转移酶',       'Gamma-Glutamyl Transferase','GGT',   '生化', '血清', 'U/L',    '酶法',         0, 10, 10.00, 1),
('ALB',     '白蛋白',               'Albumin',                   'ALB',   '生化', '血清', 'g/L',    '溴甲酚绿法',   0, 10, 10.00, 1),
('GLO',     '球蛋白',               'Globulin',                  'GLO',   '生化', '血清', 'g/L',    '计算法',       0, 10, 10.00, 1),
('A_G',     '白球比',               'Albumin/Globulin Ratio',    'A/G',   '生化', '血清', '',       '计算法',       0, 10, 5.00, 1),
('CHE',     '胆碱酯酶',             'Cholinesterase',            'CHE',   '生化', '血清', 'U/L',    '酶法',         0, 10, 15.00, 1),
('TBA',     '总胆汁酸',             'Total Bile Acid',           'TBA',   '生化', '血清', 'umol/L', '酶法',         0, 10, 15.00, 1),
('LDH',     '乳酸脱氢酶',           'Lactate Dehydrogenase',     'LDH',   '生化', '血清', 'U/L',    '酶法',         0, 10, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肾功能指标 (parent_id=15) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('BUN',     '尿素氮',               'Blood Urea Nitrogen',       'BUN',   '生化', '血清', 'mmol/L', '酶偶联法', 0, 15, 10.00, 1),
('CRE',     '肌酐',                 'Creatinine',                'CRE',   '生化', '血清', 'umol/L', '酶法',     0, 15, 10.00, 1),
('UA',      '尿酸',                 'Uric Acid',                 'UA',    '生化', '血清', 'umol/L', '酶法',     0, 15, 10.00, 1),
('CYS_C',   '胱抑素C',              'Cystatin C',                'Cys-C', '生化', '血清', 'mg/L',   '免疫比浊法',0, 15, 30.00, 1),
('B2_MG',   'β2-微球蛋白',          'Beta-2 Microglobulin',      'β2MG',  '生化', '血清', 'mg/L',   '免疫比浊法',0, 15, 25.00, 1),
('EGFR',    '估算肾小球滤过率',     'Estimated GFR',             'eGFR',  '生化', '血清', 'mL/min', '计算法',   0, 15, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 血脂指标 (parent_id=16) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TC',      '总胆固醇',             'Total Cholesterol',         'TC',    '生化', '血清', 'mmol/L', '酶法',     0, 16, 10.00, 1),
('TG',      '甘油三酯',             'Triglycerides',             'TG',    '生化', '血清', 'mmol/L', '酶法',     0, 16, 10.00, 1),
('HDL_C',   '高密度脂蛋白胆固醇',   'HDL Cholesterol',           'HDL-C', '生化', '血清', 'mmol/L', '直接法',   0, 16, 15.00, 1),
('LDL_C',   '低密度脂蛋白胆固醇',   'LDL Cholesterol',           'LDL-C', '生化', '血清', 'mmol/L', '直接法',   0, 16, 15.00, 1),
('APO_A1',  '载脂蛋白A1',           'Apolipoprotein A1',         'ApoA1', '生化', '血清', 'g/L',    '免疫比浊法',0, 16, 20.00, 1),
('APO_B',   '载脂蛋白B',            'Apolipoprotein B',          'ApoB',  '生化', '血清', 'g/L',    '免疫比浊法',0, 16, 20.00, 1),
('LPA',     '脂蛋白a',              'Lipoprotein(a)',            'Lp(a)', '生化', '血清', 'mg/L',   '免疫比浊法',0, 16, 25.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 心肌酶谱指标 (parent_id=17) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CK',      '肌酸激酶',             'Creatine Kinase',           'CK',    '生化', '血清', 'U/L',    '酶法',     0, 17, 15.00, 1),
('CK_MB',   '肌酸激酶同工酶',       'CK-MB',                     'CK-MB', '生化', '血清', 'U/L',    '免疫抑制法',0, 17, 25.00, 1),
('MB',      '肌红蛋白',             'Myoglobin',                 'Mb',    '生化', '血清', 'ng/mL',  '化学发光法',0, 17, 40.00, 1),
('CTNI',    '肌钙蛋白I',            'Cardiac Troponin I',        'cTnI',  '生化', '血清', 'ng/mL',  '化学发光法',0, 17, 50.00, 1),
('BNP',     'B型钠尿肽',            'BNP',                       'BNP',   '生化', '血清', 'pg/mL',  '化学发光法',0, 17, 60.00, 1),
('NT_PROBNP','N末端B型钠尿肽原',    'NT-proBNP',                 'NT-proBNP','生化','血清','pg/mL', '化学发光法',0, 17, 60.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 电解质指标 (parent_id=18) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('K',       '钾',                   'Potassium',                 'K',     '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('NA',      '钠',                   'Sodium',                    'Na',    '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('CL',      '氯',                   'Chloride',                  'Cl',    '生化', '血清', 'mmol/L', '离子选择电极法', 0, 18, 8.00, 1),
('CA',      '钙',                   'Calcium',                   'Ca',    '生化', '血清', 'mmol/L', '偶氮砷III法',    0, 18, 8.00, 1),
('P',       '磷',                   'Phosphorus',                'P',     '生化', '血清', 'mmol/L', '紫外法',         0, 18, 8.00, 1),
('MG',      '镁',                   'Magnesium',                 'Mg',    '生化', '血清', 'mmol/L', '二甲苯蓝法',     0, 18, 8.00, 1),
('FE',      '铁',                   'Iron',                      'Fe',    '生化', '血清', 'umol/L', '比色法',         0, 18, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 胰腺功能指标 (parent_id=19) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('AMY',     '淀粉酶',               'Amylase',                   'AMY',   '生化', '血清', 'U/L',    '酶法', 0, 19, 15.00, 1),
('LPS',     '脂肪酶',               'Lipase',                    'LPS',   '生化', '血清', 'U/L',    '酶法', 0, 19, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 凝血功能指标 (parent_id=20) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('PT',      '凝血酶原时间',         'Prothrombin Time',          'PT',    '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('INR',     '国际标准化比值',       'INR',                       'INR',   '凝血', '血浆', '',      '计算法', 0, 20, 5.00, 1),
('APTT',    '活化部分凝血活酶时间', 'APTT',                      'APTT',  '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('FIB',     '纤维蛋白原',           'Fibrinogen',                'FIB',   '凝血', '血浆', 'g/L',   '凝固法', 0, 20, 15.00, 1),
('TT',      '凝血酶时间',           'Thrombin Time',             'TT',    '凝血', '血浆', 's',     '凝固法', 0, 20, 15.00, 1),
('D_DIMER', 'D-二聚体',             'D-Dimer',                   'D-D',   '凝血', '血浆', 'mg/L',  '免疫比浊法', 0, 20, 30.00, 1),
('FDP',     '纤维蛋白降解产物',     'Fibrin Degradation Products','FDP',  '凝血', '血浆', 'mg/L',  '免疫比浊法', 0, 20, 30.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 尿液分析指标 (parent_id=21) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('U_PRO',   '尿蛋白',               'Urine Protein',             'U-Pro', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_GLU',   '尿糖',                 'Urine Glucose',             'U-Glu', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_OB',    '尿潜血',               'Urine Occult Blood',        'U-OB',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_LEU',   '尿白细胞',             'Urine Leukocyte',           'U-LEU', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_PH',    '尿pH值',              'Urine pH',                  'U-pH',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_SG',    '尿比重',               'Urine Specific Gravity',    'U-SG',  '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_KET',   '尿酮体',               'Urine Ketone',              'U-KET', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_NIT',   '尿亚硝酸盐',           'Urine Nitrite',             'U-NIT', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_BIL',   '尿胆红素',             'Urine Bilirubin',           'U-BIL', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1),
('U_UBG',   '尿胆原',               'Urine Urobilinogen',        'U-UBG', '尿液', '尿液', '',      '干化学法', 0, 21, 5.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 甲状腺功能指标 (parent_id=22) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TSH',     '促甲状腺激素',         'Thyroid Stimulating Hormone','TSH',   '免疫', '血清', 'uIU/mL','化学发光法', 0, 22, 30.00, 1),
('FT3',     '游离三碘甲状腺原氨酸', 'Free T3',                   'FT3',   '免疫', '血清', 'pmol/L','化学发光法', 0, 22, 35.00, 1),
('FT4',     '游离甲状腺素',         'Free T4',                   'FT4',   '免疫', '血清', 'pmol/L','化学发光法', 0, 22, 35.00, 1),
('TT3',     '总三碘甲状腺原氨酸',   'Total T3',                  'TT3',   '免疫', '血清', 'nmol/L','化学发光法', 0, 22, 30.00, 1),
('TT4',     '总甲状腺素',           'Total T4',                  'TT4',   '免疫', '血清', 'nmol/L','化学发光法', 0, 22, 30.00, 1),
('TPO_AB',  '抗甲状腺过氧化物酶抗体','Anti-TPO Antibody',        'TPOAb', '免疫', '血清', 'IU/mL', '化学发光法', 0, 22, 40.00, 1),
('TG_AB',   '抗甲状腺球蛋白抗体',   'Anti-TG Antibody',          'TGAb',  '免疫', '血清', 'IU/mL', '化学发光法', 0, 22, 40.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肿瘤标志物指标 (parent_id=23) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('AFP',     '甲胎蛋白',             'Alpha-Fetoprotein',         'AFP',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('CEA',     '癌胚抗原',             'Carcinoembryonic Antigen',  'CEA',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('CA125',   '糖类抗原125',          'CA125',                     'CA125', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA153',   '糖类抗原15-3',         'CA15-3',                    'CA153', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA199',   '糖类抗原19-9',         'CA19-9',                    'CA199', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('CA724',   '糖类抗原72-4',         'CA72-4',                    'CA724', '免疫', '血清', 'U/mL',   '化学发光法', 0, 23, 45.00, 1),
('PSA',     '前列腺特异性抗原',     'PSA',                       'PSA',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('FPSA',    '游离前列腺特异性抗原', 'Free PSA',                  'fPSA',  '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 40.00, 1),
('NSE',     '神经元特异性烯醇化酶', 'NSE',                       'NSE',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 50.00, 1),
('CYFRA211','细胞角蛋白19片段',     'CYFRA21-1',                 'CYFRA', '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 50.00, 1),
('SCC',     '鳞状细胞癌抗原',       'SCC Antigen',               'SCC',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, 23, 45.00, 1),
('B2_HCG',  '人绒毛膜促性腺激素',   'Beta-hCG',                  'β-HCG', '免疫', '血清', 'mIU/mL', '化学发光法', 0, 23, 40.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 糖代谢指标 (parent_id=24, 已有GLU parent_id=11) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HBA1C',   '糖化血红蛋白',         'HbA1c',                     'HbA1c', '生化', '全血', '%',     'HPLC法', 0, 24, 40.00, 1),
('GA',      '糖化白蛋白',           'Glycated Albumin',          'GA',    '生化', '血清', '%',     '酶法',   0, 24, 35.00, 1),
('INS',     '胰岛素',               'Insulin',                   'INS',   '免疫', '血清', 'uIU/mL','化学发光法', 0, 24, 35.00, 1),
('CPEP',    'C肽',                  'C-Peptide',                 'C-P',   '免疫', '血清', 'ng/mL', '化学发光法', 0, 24, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 炎症标志物指标 (parent_id=25) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CRP',     'C反应蛋白',            'C-Reactive Protein',        'CRP',   '免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 15.00, 1),
('HSCRP',   '超敏C反应蛋白',        'High-Sensitivity CRP',      'hs-CRP','免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 20.00, 1),
('PCT_INF', '降钙素原',             'Procalcitonin',             'PCT',   '免疫', '血清', 'ng/mL', '化学发光法', 0, 25, 40.00, 1),
('SAA',     '血清淀粉样蛋白A',      'Serum Amyloid A',           'SAA',   '免疫', '血清', 'mg/L',  '免疫比浊法', 0, 25, 25.00, 1),
('IL6',     '白细胞介素6',          'Interleukin-6',             'IL-6',  '免疫', '血清', 'pg/mL', '化学发光法', 0, 25, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 免疫球蛋白指标 (parent_id=26) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('IGG',     '免疫球蛋白G',          'IgG',                       'IgG',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGA',     '免疫球蛋白A',          'IgA',                       'IgA',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGM',     '免疫球蛋白M',          'IgM',                       'IgM',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('IGE',     '免疫球蛋白E',          'IgE',                       'IgE',   '免疫', '血清', 'IU/mL', '化学发光法', 0, 26, 30.00, 1),
('C3',      '补体C3',               'Complement C3',             'C3',    '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1),
('C4',      '补体C4',               'Complement C4',             'C4',    '免疫', '血清', 'g/L',   '免疫比浊法', 0, 26, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 血气分析指标 (parent_id=27) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('PH',      'pH值',                 'pH',                        'pH',    '生化', '全血', '',      '电极法', 0, 27, 10.00, 1),
('PCO2',    '二氧化碳分压',         'PCO2',                      'pCO2',  '生化', '全血', 'mmHg',  '电极法', 0, 27, 10.00, 1),
('PO2',     '氧分压',               'PO2',                       'pO2',   '生化', '全血', 'mmHg',  '电极法', 0, 27, 10.00, 1),
('HCO3',    '碳酸氢根',             'HCO3',                      'HCO3-', '生化', '全血', 'mmol/L','计算法', 0, 27, 10.00, 1),
('BE',      '碱剩余',               'Base Excess',               'BE',    '生化', '全血', 'mmol/L','计算法', 0, 27, 10.00, 1),
('SO2',     '血氧饱和度',           'Oxygen Saturation',         'sO2',   '生化', '全血', '%',     '计算法', 0, 27, 10.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 铁代谢指标 (parent_id=29) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('TIBC',    '总铁结合力',           'Total Iron Binding Capacity','TIBC',  '生化', '血清', 'umol/L','比色法', 0, 29, 15.00, 1),
('UIBC',    '不饱和铁结合力',       'Unsaturated Iron Binding Capacity','UIBC','生化','血清','umol/L','比色法',0, 29, 15.00, 1),
('FERR',    '铁蛋白',               'Ferritin',                  'SF',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 29, 30.00, 1),
('TS',      '转铁蛋白饱和度',       'Transferrin Saturation',    'TS',    '生化', '血清', '%',     '计算法', 0, 29, 5.00, 1),
('TRF',     '转铁蛋白',             'Transferrin',               'TRF',   '免疫', '血清', 'g/L',   '免疫比浊法', 0, 29, 20.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 肝纤维化指标 (parent_id=31) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('HA',      '透明质酸',             'Hyaluronic Acid',           'HA',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1),
('LN',      '层粘连蛋白',           'Laminin',                   'LN',    '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1),
('PIIINP',  'III型前胶原N端肽',     'PIIINP',                    'PIIINP','免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 40.00, 1),
('CIV',     'IV型胶原',             'Collagen IV',               'C-IV',  '免疫', '血清', 'ng/mL', '化学发光法', 0, 31, 35.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 四、仪器字典 — 30台仪器 (已有2台, 新增28台)
-- ============================================================
-- ID 1-2 已存在
-- 新增从 ID=3 开始

INSERT INTO lis_instrument (instrument_code, instrument_name, brand, model, protocol_type, connect_type, ip_address, port, status) VALUES
-- 血细胞分析仪 (ID 3-7)
('BC6800',     '迈瑞 BC-6800 血细胞分析仪',    'Mindray',  'BC-6800',    'ASTM', 'TCP', '192.168.1.11', 9100, 1),
('BC5390',     '迈瑞 BC-5390 血细胞分析仪',    'Mindray',  'BC-5390',    'ASTM', 'TCP', '192.168.1.12', 9100, 1),
('XN1000',     '希森美康 XN-1000 血细胞分析仪', 'Sysmex',   'XN-1000',    'HL7',  'TCP', '192.168.1.13', 9100, 1),
('XN2000',     '希森美康 XN-2000 血细胞分析仪', 'Sysmex',   'XN-2000',    'HL7',  'TCP', '192.168.1.14', 9100, 1),
('LH750',      '贝克曼 LH 750 血细胞分析仪',    'Beckman',  'LH 750',     'ASTM', 'TCP', '192.168.1.15', 9100, 1),
-- 生化分析仪 (ID 8-14)
('AU5800',     '贝克曼 AU5800 生化分析仪',      'Beckman',  'AU5800',     'ASTM', 'TCP', '192.168.1.21', 9100, 1),
('AU680',      '贝克曼 AU680 生化分析仪',       'Beckman',  'AU680',      'ASTM', 'TCP', '192.168.1.22', 9100, 1),
('BS2000M',    '迈瑞 BS-2000M 生化分析仪',     'Mindray',  'BS-2000M',   'ASTM', 'TCP', '192.168.1.23', 9100, 1),
('BS380',      '迈瑞 BS-380 生化分析仪',        'Mindray',  'BS-380',     'ASTM', 'TCP', '192.168.1.24', 9100, 1),
('7180',       '日立 7180 生化分析仪',          'Hitachi',  '7180',       'ASTM', 'TCP', '192.168.1.25', 9100, 1),
('7600',       '日立 7600 生化分析仪',          'Hitachi',  '7600',       'ASTM', 'TCP', '192.168.1.26', 9100, 1),
('ABBOTT_C',   '雅培 Architect c16000 生化仪',  'Abbott',   'c16000',     'HL7',  'TCP', '192.168.1.27', 9100, 1),
-- 免疫分析仪 (ID 15-20)
('ARCHITECT',  '雅培 Architect i2000 免疫分析仪','Abbott',  'i2000',      'HL7',  'TCP', '192.168.1.31', 9100, 1),
('COBAS_E',    '罗氏 cobas e601 免疫分析仪',    'Roche',    'cobas e601', 'HL7',  'TCP', '192.168.1.32', 9100, 1),
('COBAS_E411', '罗氏 cobas e411 免疫分析仪',    'Roche',    'cobas e411', 'HL7',  'TCP', '192.168.1.33', 9100, 1),
('MAGLUMI',    '新产业 Maglumi 4000 免疫分析仪','Snibe',    'Maglumi 4000','HL7', 'TCP', '192.168.1.34', 9100, 1),
('CL2000',     '安图 CL2000i 免疫分析仪',       'Autobio',  'CL2000i',    'HL7',  'TCP', '192.168.1.35', 9100, 1),
('ACCESS',     '贝克曼 Access 2 免疫分析仪',    'Beckman',  'Access 2',   'ASTM', 'TCP', '192.168.1.36', 9100, 1),
-- 凝血分析仪 (ID 21-22)
('CS5100',     '希森美康 CS-5100 凝血分析仪',   'Sysmex',   'CS-5100',    'HL7',  'TCP', '192.168.1.41', 9100, 1),
('STAGO',      '法国思塔高 STA-R 凝血分析仪',   'Stago',    'STA-R',      'ASTM', 'TCP', '192.168.1.42', 9100, 1),
-- 尿液分析仪 (ID 23-24)
('UX2000',     '希森美康 UX-2000 尿液分析仪',   'Sysmex',   'UX-2000',    'HL7',  'TCP', '192.168.1.51', 9100, 1),
('H800',       '迪瑞 H-800 尿液分析仪',         'Dirui',    'H-800',      'ASTM', 'TCP', '192.168.1.52', 9100, 1),
-- 血气分析仪 (ID 25-26)
('RAPIDPOINT', '西门子 RAPIDPoint 500 血气仪',  'Siemens',  'RAPIDPoint 500','HL7','TCP','192.168.1.61',9100, 1),
('ABL90',      '雷度 ABL90 血气分析仪',         'Radiometer','ABL90',     'ASTM', 'TCP', '192.168.1.62', 9100, 1),
-- 糖化血红蛋白分析仪 (ID 27-28)
('D100',       '伯乐 D-100 糖化血红蛋白仪',     'Bio-Rad',  'D-100',      'HL7',  'TCP', '192.168.1.71', 9100, 1),
('HLC723',     '东曹 HLC-723 G11 糖化仪',      'Tosoh',    'HLC-723 G11','HL7',  'TCP', '192.168.1.72', 9100, 1),
-- 电解质分析仪 (ID 29-30)
('CARESURE',   '康宇 Caresure 电解质分析仪',    'Konsung',  'Caresure',   'ASTM', 'TCP', '192.168.1.81', 9100, 1),
('AVL9180',    '罗氏 AVL 9180 电解质分析仪',    'Roche',    'AVL 9180',   'ASTM', 'TCP', '192.168.1.82', 9100, 1)
ON CONFLICT (instrument_code) DO NOTHING;


-- ============================================================
-- 五、仪器-检验指标关联 (lis_instrument_item_map)
-- ============================================================

-- 清理旧关联后重建全部
-- 仪器ID范围: 1-30, 检验指标ID范围: 1(已有) + 33(新增)
-- 使用子查询获取检验指标ID

-- 血细胞分析仪 (1-7) → 血常规指标 (WBC/RBC/HGB/PLT/HCT/MCV/MCH/MCHC/RDW/PDW/MPV/PCT/NEUT/LYMPH/MONO/EOS/BASO/NEUT_P/LYMPH_P/MONO_P)
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('BC7500','BC6800','BC5390','XN1000','XN2000','LH750')
  AND t.is_panel = 0 AND t.category = '血常规';

-- 生化分析仪 (2, 8-14) → 生化类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('COBAS8000','AU5800','AU680','BS2000M','BS380','7180','7600','ABBOTT_C')
  AND t.is_panel = 0 AND t.category = '生化';

-- 免疫分析仪 (15-20) → 免疫类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('ARCHITECT','COBAS_E','COBAS_E411','MAGLUMI','CL2000','ACCESS')
  AND t.is_panel = 0 AND t.category = '免疫';

-- 凝血分析仪 (21-22) → 凝血类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '血浆'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('CS5100','STAGO')
  AND t.is_panel = 0 AND t.category = '凝血';

-- 尿液分析仪 (23-24) → 尿液类指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '尿液'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('UX2000','H800')
  AND t.is_panel = 0 AND t.category = '尿液';

-- 血气分析仪 (25-26) → 血气指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('RAPIDPOINT','ABL90')
  AND t.is_panel = 0 AND t.category = '生化' AND t.specimen_type = '全血';

-- 糖化血红蛋白仪 (27-28) → 糖化血红蛋白指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '全血'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('D100','HLC723')
  AND t.item_code = 'HBA1C';

-- 电解质分析仪 (29-30) → 电解质指标
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, '血清'
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('CARESURE','AVL9180')
  AND t.item_code IN ('K','NA','CL','CA');


-- ============================================================
-- 六、参考范围 (lis_reference_range)
-- ============================================================
-- gender: NULL=通用 1=男 2=女
-- 为所有检验指标(is_panel=0)生成参考范围

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, critical_low, critical_high, unit, specimen_type)
SELECT t.id, NULL, 0, 999, NULL, NULL, NULL, NULL, NULL, t.unit, t.specimen_type
FROM lis_test_item t WHERE t.is_panel = 0 AND t.id > 12
ON CONFLICT DO NOTHING;

-- 逐项补充具体参考值
-- 血常规 (已有1-10, 补充新增指标)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HCT'), 1, 0, 999, '40', '50', '20', '60', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='HCT'), 2, 0, 999, '35', '45', '15', '55', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCV'), NULL, 0, 999, '80', '100', '60', '120', 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCH'), NULL, 0, 999, '27', '34', '20', '40', 'pg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MCHC'), NULL, 0, 999, '320', '360', '250', '400', 'g/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='RDW'), NULL, 0, 999, '11.5', '14.5', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PDW'), NULL, 0, 999, '9', '17', NULL, NULL, 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MPV'), NULL, 0, 999, '7', '13', NULL, NULL, 'fL', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PCT'), NULL, 0, 999, '0.1', '0.3', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='NEUT'), NULL, 0, 999, '1.8', '6.3', '0.5', '15', '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='LYMPH'), NULL, 0, 999, '1.1', '3.2', '0.3', '8', '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MONO'), NULL, 0, 999, '0.1', '0.6', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='EOS'), NULL, 0, 999, '0.02', '0.52', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='BASO'), NULL, 0, 999, '0', '0.06', NULL, NULL, '10^9/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='NEUT_P'), NULL, 0, 999, '40', '75', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='LYMPH_P'), NULL, 0, 999, '20', '50', NULL, NULL, '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='MONO_P'), NULL, 0, 999, '3', '10', NULL, NULL, '%', '全血')
ON CONFLICT DO NOTHING;

-- 肝功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TBIL'), NULL, 0, 999, '5.1', '28.0', NULL, '85', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='DBIL'), NULL, 0, 999, '0', '8.0', NULL, '50', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IBIL'), NULL, 0, 999, '0', '20.0', NULL, NULL, 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0, 999, '40', '150', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GGT'), 1, 0, 999, '10', '60', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GGT'), 2, 0, 999, '7', '45', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='ALB'), NULL, 0, 999, '40', '55', '20', NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='GLO'), NULL, 0, 999, '20', '40', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='A_G'), NULL, 0, 999, '1.2', '2.0', NULL, NULL, '', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CHE'), NULL, 0, 999, '4000', '12000', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TBA'), NULL, 0, 999, '0', '10', NULL, NULL, 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LDH'), NULL, 0, 999, '120', '250', NULL, NULL, 'U/L', '血清')
ON CONFLICT DO NOTHING;

-- 肾功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='BUN'), NULL, 0, 999, '2.9', '8.2', '1.0', '25', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CRE'), 1, 0, 999, '57', '97', '30', '500', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CRE'), 2, 0, 999, '44', '80', '25', '400', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UA'), 1, 0, 999, '208', '428', '120', '700', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UA'), 2, 0, 999, '155', '357', '90', '600', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CYS_C'), NULL, 0, 999, '0.5', '1.0', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='B2_MG'), NULL, 0, 999, '0.7', '1.8', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='EGFR'), NULL, 0, 999, '90', NULL, NULL, NULL, 'mL/min', '血清')
ON CONFLICT DO NOTHING;

-- 血脂
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TC'), NULL, 0, 999, '3.1', '5.7', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TG'), NULL, 0, 999, '0.4', '1.7', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='HDL_C'), NULL, 0, 999, '0.9', '1.6', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LDL_C'), NULL, 0, 999, '1.9', '3.6', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='APO_A1'), NULL, 0, 999, '1.0', '1.6', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='APO_B'), NULL, 0, 999, '0.6', '1.1', NULL, NULL, 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LPA'), NULL, 0, 999, '0', '300', NULL, NULL, 'mg/L', '血清')
ON CONFLICT DO NOTHING;

-- 心肌酶谱
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CK'), NULL, 0, 999, '25', '200', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CK_MB'), NULL, 0, 999, '0', '25', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='MB'), NULL, 0, 999, '0', '70', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CTNI'), NULL, 0, 999, '0', '0.4', NULL, '2.0', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='BNP'), NULL, 0, 999, '0', '100', NULL, '500', 'pg/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NT_PROBNP'), NULL, 0, 999, '0', '125', NULL, '450', 'pg/mL', '血清')
ON CONFLICT DO NOTHING;

-- 电解质
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='K'), NULL, 0, 999, '3.5', '5.3', '2.5', '7.0', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NA'), NULL, 0, 999, '136', '146', '120', '160', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CL'), NULL, 0, 999, '98', '107', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA'), NULL, 0, 999, '2.1', '2.6', '1.5', '3.5', 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='P'), NULL, 0, 999, '0.8', '1.5', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='MG'), NULL, 0, 999, '0.7', '1.1', NULL, NULL, 'mmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FE'), NULL, 0, 999, '7', '30', NULL, NULL, 'umol/L', '血清')
ON CONFLICT DO NOTHING;

-- 胰腺功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='AMY'), NULL, 0, 999, '30', '110', NULL, NULL, 'U/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LPS'), NULL, 0, 999, '0', '60', NULL, NULL, 'U/L', '血清')
ON CONFLICT DO NOTHING;

-- 凝血功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PT'), NULL, 0, 999, '9.4', '12.5', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='INR'), NULL, 0, 999, '0.8', '1.2', NULL, NULL, '', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='APTT'), NULL, 0, 999, '25', '37', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='FIB'), NULL, 0, 999, '2', '4', '1', '8', 'g/L', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='TT'), NULL, 0, 999, '14', '21', NULL, NULL, 's', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='D_DIMER'), NULL, 0, 999, '0', '0.5', NULL, NULL, 'mg/L', '血浆'),
((SELECT id FROM lis_test_item WHERE item_code='FDP'), NULL, 0, 999, '0', '5', NULL, NULL, 'mg/L', '血浆')
ON CONFLICT DO NOTHING;

-- 尿液分析 (文本型参考值)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, ref_text, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='U_PRO'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_GLU'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_OB'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_LEU'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_PH'), NULL, 0, 999, '5.0', '8.0', NULL, '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_SG'), NULL, 0, 999, '1.003', '1.030', NULL, '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_KET'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_NIT'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_BIL'), NULL, 0, 999, NULL, NULL, '阴性', '', '尿液'),
((SELECT id FROM lis_test_item WHERE item_code='U_UBG'), NULL, 0, 999, NULL, NULL, '阴性或弱阳性', '', '尿液')
ON CONFLICT DO NOTHING;

-- 甲状腺功能
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TSH'), NULL, 0, 999, '0.4', '4.0', NULL, NULL, 'uIU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FT3'), NULL, 0, 999, '3.5', '6.5', NULL, NULL, 'pmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FT4'), NULL, 0, 999, '11.5', '22.7', NULL, NULL, 'pmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TT3'), NULL, 0, 999, '1.0', '3.0', NULL, NULL, 'nmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TT4'), NULL, 0, 999, '60', '160', NULL, NULL, 'nmol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TPO_AB'), NULL, 0, 999, '0', '34', NULL, NULL, 'IU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TG_AB'), NULL, 0, 999, '0', '115', NULL, NULL, 'IU/mL', '血清')
ON CONFLICT DO NOTHING;

-- 肿瘤标志物
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='AFP'), NULL, 0, 999, '0', '7', NULL, '400', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CEA'), NULL, 0, 999, '0', '5', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA125'), NULL, 0, 999, '0', '35', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA153'), NULL, 0, 999, '0', '25', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA199'), NULL, 0, 999, '0', '37', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CA724'), NULL, 0, 999, '0', '6.9', NULL, NULL, 'U/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PSA'), NULL, 0, 999, '0', '4', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FPSA'), NULL, 0, 999, '0', '1', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='NSE'), NULL, 0, 999, '0', '16.3', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CYFRA211'), NULL, 0, 999, '0', '3.3', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='SCC'), NULL, 0, 999, '0', '1.5', NULL, NULL, 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), NULL, 0, 999, '0', '5', NULL, NULL, 'mIU/mL', '血清')
ON CONFLICT DO NOTHING;

-- 糖代谢
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HBA1C'), NULL, 0, 999, '4.0', '6.0', '3.0', '10.0', '%', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='GA'), NULL, 0, 999, '11', '16', NULL, NULL, '%', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='INS'), NULL, 0, 999, '2.6', '24.9', NULL, NULL, 'uIU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CPEP'), NULL, 0, 999, '1.1', '4.4', NULL, NULL, 'ng/mL', '血清')
ON CONFLICT DO NOTHING;

-- 炎症标志物
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CRP'), NULL, 0, 999, '0', '10', NULL, '100', 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='HSCRP'), NULL, 0, 999, '0', '3', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PCT_INF'), NULL, 0, 999, '0', '0.05', NULL, '10', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='SAA'), NULL, 0, 999, '0', '10', NULL, NULL, 'mg/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IL6'), NULL, 0, 999, '0', '7', NULL, NULL, 'pg/mL', '血清')
ON CONFLICT DO NOTHING;

-- 免疫球蛋白
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='IGG'), NULL, 0, 999, '7', '16', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGA'), NULL, 0, 999, '0.7', '4.0', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGM'), NULL, 0, 999, '0.4', '2.3', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='IGE'), NULL, 0, 999, '0', '100', 'IU/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='C3'), NULL, 0, 999, '0.9', '1.8', 'g/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='C4'), NULL, 0, 999, '0.1', '0.4', 'g/L', '血清')
ON CONFLICT DO NOTHING;

-- 血气分析
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PH'), NULL, 0, 999, '7.35', '7.45', '7.0', '7.6', '', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PCO2'), NULL, 0, 999, '35', '45', '20', '70', 'mmHg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='PO2'), NULL, 0, 999, '80', '100', '40', NULL, 'mmHg', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='HCO3'), NULL, 0, 999, '21', '28', '10', '40', 'mmol/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='BE'), NULL, 0, 999, '-3', '3', '-10', '10', 'mmol/L', '全血'),
((SELECT id FROM lis_test_item WHERE item_code='SO2'), NULL, 0, 999, '95', '100', '85', NULL, '%', '全血')
ON CONFLICT DO NOTHING;

-- 铁代谢
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TIBC'), NULL, 0, 999, '50', '77', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='UIBC'), NULL, 0, 999, '25', '50', 'umol/L', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FERR'), 1, 0, 999, '30', '400', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='FERR'), 2, 0, 999, '13', '150', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TS'), NULL, 0, 999, '20', '50', '%', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='TRF'), NULL, 0, 999, '2.0', '3.6', 'g/L', '血清')
ON CONFLICT DO NOTHING;

-- 肝纤维化
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, unit, specimen_type) VALUES
((SELECT id FROM lis_test_item WHERE item_code='HA'), NULL, 0, 999, '0', '100', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='LN'), NULL, 0, 999, '0', '130', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='PIIINP'), NULL, 0, 999, '0', '15', 'ng/mL', '血清'),
((SELECT id FROM lis_test_item WHERE item_code='CIV'), NULL, 0, 999, '0', '95', 'ng/mL', '血清')
ON CONFLICT DO NOTHING;

-- 已有指标补充危急值 (WBC/RBC/HGB/PLT/GLU/ALT/AST/TP)
UPDATE lis_reference_range SET critical_low='1.0', critical_high='30' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='WBC') AND ref_low='3.5';
UPDATE lis_reference_range SET critical_low='2.0', critical_high='8.0' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='RBC');
UPDATE lis_reference_range SET critical_low='60', critical_high='200' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='HGB');
UPDATE lis_reference_range SET critical_low='20', critical_high='1000' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='PLT');
UPDATE lis_reference_range SET critical_low='2.2', critical_high='25' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='GLU');
UPDATE lis_reference_range SET critical_low='2.1', critical_high='3.5' WHERE test_item_id=(SELECT id FROM lis_test_item WHERE item_code='CA');

COMMIT;

-- ============================================================
-- 验证
-- ============================================================
-- SELECT COUNT(*) FROM lis_test_item;              -- 应 132+ (12原有 + 120新增)
-- SELECT COUNT(*) FROM lis_test_item WHERE is_panel=0; -- 应 120+ 检验指标
-- SELECT COUNT(*) FROM lis_test_item WHERE is_panel=1; -- 应 20+ 检验项目
-- SELECT COUNT(*) FROM lis_instrument;             -- 应 30
-- SELECT COUNT(*) FROM lis_instrument_item_map;    -- 应 200+ 关联
-- SELECT COUNT(*) FROM lis_reference_range;        -- 应 150+ 参考范围

-- =============================================================================
-- 【增量脚本】add-reference-range-special.sql
-- =============================================================================
BEGIN;

-- ============================================================
-- 一、参考范围表新增 condition_remark 列
-- ============================================================
-- 存储特殊参考范围条件: 如"女性-卵泡期""立位""晨8点""空腹""孕妇-孕早期"等
ALTER TABLE lis_reference_range ADD COLUMN IF NOT EXISTS condition_remark VARCHAR(100);
COMMENT ON COLUMN lis_reference_range.condition_remark IS '参考范围条件备注: 生理周期/体位/采血时间/饮食状态/孕周等';


-- ============================================================
-- 二、新增检验项目 (is_panel=1)
-- ============================================================
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, is_panel, parent_id, price, status) VALUES
('REPRO',  '生殖激素六项',   'Reproductive Hormone Panel',  '性激素', '免疫', '血清', 1, NULL, 180.00, 1),
('ENDO',   '内分泌激素',     'Endocrine Hormone Panel',     '内分泌', '免疫', '血清', 1, NULL, 200.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 三、新增检验指标 (is_panel=0)
-- ============================================================

-- ===== 性激素六项 (parent_id = REPRO) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('FSH',    '促卵泡生成素',     'Follicle Stimulating Hormone', 'FSH',   '免疫', '血清', 'mIU/mL', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('LH',     '促黄体生成素',     'Luteinizing Hormone',          'LH',    '免疫', '血清', 'mIU/mL', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('E2',     '雌二醇',           'Estradiol',                    'E2',    '免疫', '血清', 'pg/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('PROG',   '孕酮',             'Progesterone',                 'P',     '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('TESTO',  '睾酮',             'Testosterone',                 'T',     '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1),
('PRL',    '泌乳素',           'Prolactin',                    'PRL',   '免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='REPRO'), 30.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- ===== 内分泌激素 (parent_id = ENDO) =====
INSERT INTO lis_test_item (item_code, item_name, item_name_en, short_name, category, specimen_type, unit, method, is_panel, parent_id, price, status) VALUES
('CORT',   '皮质醇',           'Cortisol',                     'CORT',  '免疫', '血清', 'nmol/L', '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 35.00, 1),
('ACTH',   '促肾上腺皮质激素', 'Adrenocorticotropic Hormone',  'ACTH',  '免疫', '血浆', 'pg/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 50.00, 1),
('ALDO',   '醛固酮',           'Aldosterone',                  'ALDO',  '免疫', '血清', 'ng/dL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 45.00, 1),
('RENIN',  '肾素活性',         'Plasma Renin Activity',        'PRA',   '免疫', '血浆', 'ng/mL/h','放射免疫法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 50.00, 1),
('VITD',   '25-羟基维生素D',   '25-Hydroxy Vitamin D',         '25(OH)D','免疫', '血清', 'ng/mL',  '化学发光法', 0, (SELECT id FROM lis_test_item WHERE item_code='ENDO'), 60.00, 1)
ON CONFLICT (item_code) DO NOTHING;


-- ============================================================
-- 四、仪器-检验指标关联 (内分泌指标关联到免疫分析仪)
-- ============================================================
INSERT INTO lis_instrument_item_map (instrument_id, test_item_id, instrument_item_code, instrument_item_name, specimen_type)
SELECT i.id, t.id, t.item_code, t.item_name, t.specimen_type
FROM lis_instrument i, lis_test_item t
WHERE i.instrument_code IN ('ARCHITECT','COBAS_E','COBAS_E411','MAGLUMI','CL2000','ACCESS')
  AND t.is_panel = 0
  AND t.item_code IN ('FSH','LH','E2','PROG','TESTO','PRL','CORT','ACTH','ALDO','RENIN','VITD')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 五、性激素六项参考范围 — 女性生理周期 + 男性 + 年龄分段
-- ============================================================

-- FSH 促卵泡生成素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
-- 男性
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 1, 0, 999, '1.5', '12.4', NULL, NULL, 'mIU/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 1, 0, 14, '0.3', '4.5', NULL, NULL, 'mIU/mL', '血清', '男性-儿童'),
-- 女性-卵泡期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '3.5', '12.5', NULL, NULL, 'mIU/mL', '血清', '女性-卵泡期'),
-- 女性-排卵期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '4.7', '21.5', NULL, NULL, 'mIU/mL', '血清', '女性-排卵期'),
-- 女性-黄体期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '1.7', '7.7', NULL, NULL, 'mIU/mL', '血清', '女性-黄体期'),
-- 女性-绝经期
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 999, '25.8', '134.8', NULL, NULL, 'mIU/mL', '血清', '女性-绝经期'),
-- 女性儿童
((SELECT id FROM lis_test_item WHERE item_code='FSH'), 2, 0, 9, '0.6', '5.0', NULL, NULL, 'mIU/mL', '血清', '女性-儿童')
ON CONFLICT DO NOTHING;

-- LH 促黄体生成素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='LH'), 1, 0, 999, '1.7', '8.6', NULL, NULL, 'mIU/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '2.4', '12.6', NULL, NULL, 'mIU/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '14.0', '95.6', NULL, NULL, 'mIU/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '1.0', '11.4', NULL, NULL, 'mIU/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='LH'), 2, 0, 999, '7.7', '58.5', NULL, NULL, 'mIU/mL', '血清', '女性-绝经期')
ON CONFLICT DO NOTHING;

-- E2 雌二醇
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='E2'), 1, 0, 999, '11', '44', NULL, NULL, 'pg/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '21', '251', NULL, NULL, 'pg/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '38', '649', NULL, NULL, 'pg/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '21', '312', NULL, NULL, 'pg/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='E2'), 2, 0, 999, '0', '28', NULL, NULL, 'pg/mL', '血清', '女性-绝经期')
ON CONFLICT DO NOTHING;

-- PROG 孕酮
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 1, 0, 999, '0.2', '1.4', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.2', '1.5', NULL, NULL, 'ng/mL', '血清', '女性-卵泡期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.8', '3.0', NULL, NULL, 'ng/mL', '血清', '女性-排卵期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '1.7', '27', NULL, NULL, 'ng/mL', '血清', '女性-黄体期'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '0.1', '0.8', NULL, NULL, 'ng/mL', '血清', '女性-绝经期'),
-- 孕妇参考范围 (按孕周)
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '2.8', '147', NULL, NULL, 'ng/mL', '血清', '孕妇-孕早期(1-12周)'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '22', '117', NULL, NULL, 'ng/mL', '血清', '孕妇-孕中期(13-27周)'),
((SELECT id FROM lis_test_item WHERE item_code='PROG'), 2, 0, 999, '65', '290', NULL, NULL, 'ng/mL', '血清', '孕妇-孕晚期(28-40周)')
ON CONFLICT DO NOTHING;

-- TESTO 睾酮
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 1, 0, 999, '2.8', '8.0', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 1, 0, 14, '0.1', '0.5', NULL, NULL, 'ng/mL', '血清', '男性-儿童'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 2, 0, 999, '0.06', '0.82', NULL, NULL, 'ng/mL', '血清', '女性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='TESTO'), 2, 0, 14, '0.05', '0.3', NULL, NULL, 'ng/mL', '血清', '女性-儿童')
ON CONFLICT DO NOTHING;

-- PRL 泌乳素
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 1, 0, 999, '3.5', '15.2', NULL, NULL, 'ng/mL', '血清', '男性-成人'),
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 2, 0, 999, '4.8', '23.3', NULL, NULL, 'ng/mL', '血清', '女性-非孕期'),
((SELECT id FROM lis_test_item WHERE item_code='PRL'), 2, 0, 999, '95', '475', NULL, NULL, 'ng/mL', '血清', '孕妇-孕晚期(28-40周)')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 六、内分泌激素参考范围 — 昼夜节律 + 体位差异
-- ============================================================

-- CORT 皮质醇 (昼夜节律: 晨8点最高, 午夜最低)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '171', '536', '27', '1380', 'nmol/L', '血清', '晨8点'),
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '64', '327', NULL, NULL, 'nmol/L', '血清', '下午4点'),
((SELECT id FROM lis_test_item WHERE item_code='CORT'), NULL, 0, 999, '0', '138', NULL, NULL, 'nmol/L', '血清', '午夜')
ON CONFLICT DO NOTHING;

-- ACTH 促肾上腺皮质激素 (昼夜节律)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ACTH'), NULL, 0, 999, '7.2', '63.3', NULL, NULL, 'pg/mL', '血浆', '晨6-10点'),
((SELECT id FROM lis_test_item WHERE item_code='ACTH'), NULL, 0, 999, '0', '30', NULL, NULL, 'pg/mL', '血浆', '下午16-20点')
ON CONFLICT DO NOTHING;

-- ALDO 醛固酮 (体位差异: 立位显著高于卧位)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ALDO'), NULL, 0, 999, '3.0', '23.6', NULL, NULL, 'ng/dL', '血清', '卧位(晨起卧床)'),
((SELECT id FROM lis_test_item WHERE item_code='ALDO'), NULL, 0, 999, '7.2', '39.9', NULL, NULL, 'ng/dL', '血清', '立位(站立2小时)')
ON CONFLICT DO NOTHING;

-- RENIN 肾素活性 (体位差异)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='RENIN'), NULL, 0, 999, '0.05', '0.79', NULL, NULL, 'ng/mL/h', '血浆', '卧位(晨起卧床)'),
((SELECT id FROM lis_test_item WHERE item_code='RENIN'), NULL, 0, 999, '0.93', '6.56', NULL, NULL, 'ng/mL/h', '血浆', '立位(站立2小时)')
ON CONFLICT DO NOTHING;

-- VITD 25-羟基维生素D (季节差异 + 营养状态分级)
INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '30', '100', NULL, NULL, 'ng/mL', '血清', '充足(夏季偏高)'),
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '20', '30', NULL, NULL, 'ng/mL', '血清', '不足(冬季偏低)'),
((SELECT id FROM lis_test_item WHERE item_code='VITD'), NULL, 0, 999, '0', '20', NULL, NULL, 'ng/mL', '血清', '缺乏')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 七、孕妇特殊参考范围 — B2_HCG 按孕周
-- ============================================================
-- B2_HCG 已存在(非孕参考范围 0-5 mIU/mL), 补充孕周参考范围
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='B2_HCG')
  AND condition_remark LIKE '孕妇%';

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '0', '5', NULL, NULL, 'mIU/mL', '血清', '非孕期'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '5', '426', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕0.2-1周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '18', '7340', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕1-2周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '1080', '56500', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕2-3周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '7650', '229000', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕3-4周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '25700', '288000', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕4-5周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '23300', '137116', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕5-6周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '15900', '181400', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕6-8周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '14050', '62800', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕8-12周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '6140', '65100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕12-16周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '4730', '80100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕16-29周'),
((SELECT id FROM lis_test_item WHERE item_code='B2_HCG'), 2, 0, 999, '2700', '78100', NULL, NULL, 'mIU/mL', '血清', '孕妇-孕29-41周')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 八、血糖饮食状态参考范围
-- ============================================================
-- GLU 已存在空腹参考范围, 补充餐后2小时及孕妇OGTT
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GLU')
  AND condition_remark LIKE '餐后%';

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '6.1', '2.2', '25', 'mmol/L', '血清', '空腹'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '7.8', NULL, NULL, 'mmol/L', '血清', '餐后2小时'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '3.9', '5.1', NULL, NULL, 'mmol/L', '血清', '孕妇-空腹OGTT'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '0', '10.0', NULL, NULL, 'mmol/L', '血清', '孕妇-服糖后1小时OGTT'),
((SELECT id FROM lis_test_item WHERE item_code='GLU'), NULL, 0, 999, '0', '8.5', NULL, NULL, 'mmol/L', '血清', '孕妇-服糖后2小时OGTT')
ON CONFLICT DO NOTHING;

-- 更新原有GLU参考范围为空腹标记
UPDATE lis_reference_range SET condition_remark = '空腹'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GLU')
  AND condition_remark IS NULL;


-- ============================================================
-- 九、年龄分段参考范围补充 — ALP/GGT/HGB/RBC (年龄影响显著)
-- ============================================================

-- ALP 碱性磷酸酶: 儿童/青少年因骨骼发育, ALP显著高于成人
DELETE FROM lis_reference_range
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='ALP')
  AND (condition_remark LIKE '儿童%' OR condition_remark LIKE '青少年%');

INSERT INTO lis_reference_range (test_item_id, gender, age_min, age_max, ref_low, ref_high, critical_low, critical_high, unit, specimen_type, condition_remark) VALUES
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0, 0.1, '90', '500', NULL, NULL, 'U/L', '血清', '新生儿'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 0.1, 1, '120', '650', NULL, NULL, 'U/L', '血清', '婴幼儿(1个月-1岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 1, 12, '100', '350', NULL, NULL, 'U/L', '血清', '儿童(1-12岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), 1, 12, 18, '100', '560', NULL, NULL, 'U/L', '血清', '青少年男性(12-18岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), 2, 12, 18, '80', '380', NULL, NULL, 'U/L', '血清', '青少年女性(12-18岁)'),
((SELECT id FROM lis_test_item WHERE item_code='ALP'), NULL, 18, 999, '40', '150', NULL, NULL, 'U/L', '血清', '成人')
ON CONFLICT DO NOTHING;

-- 更新原有ALP成人为标记
UPDATE lis_reference_range SET condition_remark = '成人'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='ALP')
  AND condition_remark IS NULL;

-- 更新ALP成人为标记
UPDATE lis_reference_range SET condition_remark = '成人'
WHERE test_item_id = (SELECT id FROM lis_test_item WHERE item_code='GGT')
  AND condition_remark IS NULL;

COMMIT;

-- ============================================================
-- 验证查询
-- ============================================================
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('REPRO','ENDO');                                -- 应 2
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('FSH','LH','E2','PROG','TESTO','PRL');           -- 应 6
-- SELECT COUNT(*) FROM lis_test_item WHERE item_code IN ('CORT','ACTH','ALDO','RENIN','VITD');            -- 应 5
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '女性-%';                          -- 性激素女性生理周期参考范围
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '孕妇%';                           -- 孕妇参考范围
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '晨%' OR condition_remark LIKE '下午%'; -- 昼夜节律
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '%位%';                            -- 体位差异
-- SELECT COUNT(*) FROM lis_reference_range WHERE condition_remark LIKE '餐后%' OR condition_remark LIKE '空腹'; -- 饮食状态
-- SELECT COUNT(*) FROM lis_instrument_item_map WHERE test_item_id IN (SELECT id FROM lis_test_item WHERE item_code IN ('FSH','LH','E2','PROG','TESTO','PRL','CORT','ACTH','ALDO','RENIN','VITD')); -- 仪器关联

-- =============================================================================
-- 【增量脚本】seed-antibiotic-breakpoints.sql
-- =============================================================================
DELETE FROM lis_antibiotic_breakpoint;

-- ============ 大肠埃希菌 (id=1) / 肠杆菌目 ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(2, 1, 'MIC', 'CLSI', 'M100-S33', '≤8',  '≥32', '16',  NULL, NULL, 1),  -- 氨苄西林
(2, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥17', '≤13', 1),  -- 氨苄西林
(4, 1, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',   NULL, NULL, 1),   -- 庆大霉素
(4, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥15', '≤12', 1),  -- 庆大霉素
(5, 1, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',   NULL, NULL, 1),   -- 环丙沙星
(5, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥21', '≤15', 1),  -- 环丙沙星
(7, 1, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',   NULL, NULL, 1),   -- 亚胺培南
(7, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥23', '≤19', 1),  -- 亚胺培南
(8, 1, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32',  NULL, NULL, 1),   -- 阿米卡星
(8, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥17', '≤14', 1),  -- 阿米卡星
(9, 1, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',   NULL, NULL, 1),   -- 头孢他啶
(9, 1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥21', '≤17', 1),  -- 头孢他啶
(10,1, 'MIC', 'CLSI', 'M100-S33', '≤2',  '≥4',  NULL,  NULL, NULL, 1),    -- 复方新诺明
(10,1, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL,  '≥16', '≤10', 1); -- 复方新诺明

-- ============ 肺炎克雷伯菌 (id=2) / 肠杆菌目 ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(2, 2, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(2, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤13', 1),
(4, 2, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 2, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 2, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(7, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤19', 1),
(8, 2, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 2, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(9, 2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤17', 1),
(10,2, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),
(10,2, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥16', '≤10', 1);

-- ============ 金黄色葡萄球菌 (id=77) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(3, 77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),  -- 苯唑西林
(3, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥13', '≤10', 1), -- 苯唑西林
(4, 77, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),   -- 庆大霉素
(4, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 77, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),   -- 环丙沙星
(5, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(6, 77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥16', '4',  NULL, NULL, 1),   -- 万古霉素
(8, 77, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),   -- 阿米卡星
(8, 77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(10,77, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥4',  NULL, NULL, NULL, 1),   -- 复方新诺明
(10,77, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥16', '≤10', 1);

-- ============ 铜绿假单胞菌 (id=28) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(4, 28, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 28, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 28, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥8',  '4',  NULL, NULL, 1),
(7, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤14', 1),
(8, 28, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 28, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(9, 28, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥18', '≤14', 1);

-- ============ 鲍曼不动杆菌 (id=33) ============
INSERT INTO lis_antibiotic_breakpoint (antibiotic_id, bacteria_id, method, standard, standard_version, s_breakpoint, r_breakpoint, i_breakpoint, s_dd, r_dd, status) VALUES
(4, 33, 'MIC', 'CLSI', 'M100-S33',  '≤4',  '≥16', '8',  NULL, NULL, 1),
(4, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥15', '≤12', 1),
(5, 33, 'MIC', 'CLSI', 'M100-S33',  '≤1',  '≥4',  '2',  NULL, NULL, 1),
(5, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥21', '≤15', 1),
(7, 33, 'MIC', 'CLSI', 'M100-S33',  '≤2',  '≥8',  '4',  NULL, NULL, 1),
(7, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥23', '≤14', 1),
(8, 33, 'MIC', 'CLSI', 'M100-S33',  '≤16', '≥64', '32', NULL, NULL, 1),
(8, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥17', '≤14', 1),
(9, 33, 'MIC', 'CLSI', 'M100-S33',  '≤8',  '≥32', '16', NULL, NULL, 1),
(9, 33, 'K-B', 'CLSI', 'M100-S33',  NULL,  NULL,  NULL, '≥18', '≤14', 1);

SELECT count(*) AS seeded_breakpoints FROM lis_antibiotic_breakpoint;

-- =============================================================================
-- TAT 超时通知 & 消息提醒
-- =============================================================================

-- TAT 超时规则表 (按 标本类型 + 检验项目 设置时限)
CREATE TABLE IF NOT EXISTS lis_tat_rule (
    id              BIGSERIAL PRIMARY KEY,
    specimen_type   VARCHAR(50) NOT NULL,
    test_item_id    BIGINT NOT NULL,
    test_item_name  VARCHAR(100),
    urgent_timeout  INT NOT NULL DEFAULT 60,     -- 急查超时时间(分钟)
    normal_timeout  INT NOT NULL DEFAULT 120,    -- 普通超时时间(分钟)
    enabled         SMALLINT DEFAULT 1,          -- 0停用 1启用
    remark          VARCHAR(200),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_tat_rule IS 'TAT超时规则表';

-- TAT 超时监控记录表 (定时任务扫描标本生成, 去重)
CREATE TABLE IF NOT EXISTS lis_tat_monitor (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    sample_barcode  VARCHAR(50),
    patient_name    VARCHAR(50),
    test_item_id    BIGINT,
    test_item_name  VARCHAR(100),
    specimen_type   VARCHAR(50),
    is_urgent       SMALLINT DEFAULT 0,
    sign_off_time   TIMESTAMP,
    timeout_minutes INT,                          -- 该规则时限(分钟)
    elapsed_minutes INT,                          -- 已耗时(分钟)
    is_overdue      SMALLINT DEFAULT 0,           -- 0未超时 1已超时
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    department      VARCHAR(50),
    notified        SMALLINT DEFAULT 0,           -- 0未通知 1已通知
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_tat_monitor IS 'TAT超时监控记录表';
CREATE INDEX IF NOT EXISTS idx_lis_tat_monitor_sample ON lis_tat_monitor (sample_id);
CREATE INDEX IF NOT EXISTS idx_lis_tat_monitor_overdue ON lis_tat_monitor (is_overdue);

-- 消息表 (危急值/传染病/TAT超时)
CREATE TABLE IF NOT EXISTS lis_message (
    id              BIGSERIAL PRIMARY KEY,
    message_type    VARCHAR(20) NOT NULL,         -- CRITICAL / INFECTIOUS / TAT
    title           VARCHAR(120),
    content         TEXT,
    user_id         BIGINT,                        -- 指定接收用户, NULL表示按科室/全员
    department_id   BIGINT,                        -- 指定接收科室, NULL表示全员
    business_id     BIGINT,                        -- 业务ID(如 resultId/sampleId)
    sample_id       BIGINT,
    test_item_id    BIGINT,
    is_read         SMALLINT DEFAULT 0,
    read_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_message IS '消息提醒表';
CREATE INDEX IF NOT EXISTS idx_lis_message_user ON lis_message (user_id);
CREATE INDEX IF NOT EXISTS idx_lis_message_dept ON lis_message (department_id);
CREATE INDEX IF NOT EXISTS idx_lis_message_read ON lis_message (is_read);

-- 传染病检验项目配置表 (阳性时发送传染病消息)
CREATE TABLE IF NOT EXISTS lis_infectious_item (
    id              BIGSERIAL PRIMARY KEY,
    test_item_id    BIGINT NOT NULL,
    test_item_name  VARCHAR(100),
    positive_text   VARCHAR(50) DEFAULT '阳性',    -- 判定阳性的文本
    enabled         SMALLINT DEFAULT 1,
    remark          VARCHAR(200),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_infectious_item IS '传染病检验项目配置表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_infectious_item ON lis_infectious_item (test_item_id);


-- ============================================================
-- 三方卫材对接（可配置适配层）
-- ============================================================
-- ============================================================
-- 三方卫材对接（可配置适配层）
-- 适用：PostgreSQL 12+，脚本可重复执行
-- ============================================================

-- 1. 对接配置：每个对接方一条，接口地址/鉴权/路径全部配置化
CREATE TABLE IF NOT EXISTS lis_reagent_thirdparty_config (
    id              BIGSERIAL PRIMARY KEY,
    partner_code    VARCHAR(64)  NOT NULL,
    partner_name    VARCHAR(128) NOT NULL,
    enabled         SMALLINT     NOT NULL DEFAULT 0,
    base_url        VARCHAR(300),
    -- 鉴权方式：0无 1Token 2AK/SK签名 3Basic
    auth_type       SMALLINT     NOT NULL DEFAULT 0,
    auth_header     VARCHAR(64)  DEFAULT 'Authorization',
    auth_token      VARCHAR(300),
    app_key         VARCHAR(128),
    app_secret      VARCHAR(256),
    basic_username  VARCHAR(128),
    basic_password  VARCHAR(256),
    timeout_ms      INTEGER      DEFAULT 10000,
    retry_times     INTEGER      DEFAULT 0,
    -- 出站接口路径（拼在 base_url 之后）
    order_path      VARCHAR(200) DEFAULT '/orders',
    product_path    VARCHAR(200) DEFAULT '/products',
    supplier_path   VARCHAR(200) DEFAULT '/suppliers',
    stock_path      VARCHAR(200) DEFAULT '/stock',
    -- 入站开关：对方调我们时，按 inbound_token 校验
    inbound_enabled SMALLINT     NOT NULL DEFAULT 0,
    inbound_token   VARCHAR(200),
    remark          VARCHAR(500),
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_tp_config_code
    ON lis_reagent_thirdparty_config (partner_code);

-- 2. 字段映射：对方字段名与本系统字段名的对应关系，调整映射即可适配不同标准
CREATE TABLE IF NOT EXISTS lis_reagent_thirdparty_field_map (
    id           BIGSERIAL PRIMARY KEY,
    partner_code VARCHAR(64)  NOT NULL,
    -- 业务类型：ORDER 采购订单 / PRODUCT 商品 / SUPPLIER 供应商 / STOCK 库存 / DELIVERY 到货
    biz_type     VARCHAR(32)  NOT NULL,
    -- 方向：1 出站（我们调对方）2 入站（对方调我们）
    direction    SMALLINT     NOT NULL DEFAULT 1,
    local_field  VARCHAR(100) NOT NULL,
    remote_field VARCHAR(200) NOT NULL,
    default_value VARCHAR(200),
    remark       VARCHAR(300),
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_tp_field
    ON lis_reagent_thirdparty_field_map (partner_code, biz_type, direction, local_field);

-- 3. 商品映射：本系统试剂与对方商品编码的对应关系
CREATE TABLE IF NOT EXISTS lis_reagent_thirdparty_product_map (
    id             BIGSERIAL PRIMARY KEY,
    partner_code   VARCHAR(64)  NOT NULL,
    reagent_id     BIGINT,
    reagent_code   VARCHAR(64),
    reagent_name   VARCHAR(200),
    remote_code    VARCHAR(100) NOT NULL,
    remote_name    VARCHAR(200),
    remote_spec    VARCHAR(200),
    remote_unit    VARCHAR(50),
    remote_price   NUMERIC(12,2),
    last_sync_time TIMESTAMP,
    remark         VARCHAR(300),
    create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_tp_product_remote
    ON lis_reagent_thirdparty_product_map (partner_code, remote_code);

CREATE INDEX IF NOT EXISTS idx_lis_reagent_tp_product_reagent
    ON lis_reagent_thirdparty_product_map (partner_code, reagent_id);

-- 4. 调用日志：出站请求与入站请求统一留痕，便于对接排查
CREATE TABLE IF NOT EXISTS lis_reagent_thirdparty_log (
    id            BIGSERIAL PRIMARY KEY,
    partner_code  VARCHAR(64),
    biz_type      VARCHAR(32),
    direction     SMALLINT,
    method        VARCHAR(10),
    url           VARCHAR(500),
    request_body  TEXT,
    response_body TEXT,
    http_status   INTEGER,
    success       SMALLINT,
    biz_no        VARCHAR(100),
    error_msg     VARCHAR(500),
    cost_ms       BIGINT,
    operator_name VARCHAR(100),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lis_reagent_tp_log_time
    ON lis_reagent_thirdparty_log (create_time DESC);

CREATE INDEX IF NOT EXISTS idx_lis_reagent_tp_log_partner
    ON lis_reagent_thirdparty_log (partner_code, biz_type);

-- 5. 采购订单补充对方单号字段，用于回写下单结果
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS third_party_code VARCHAR(64);
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS remote_order_no VARCHAR(100);



-- ===== 试剂打印模板（lis_print_template） =====
-- =============================================================================
-- 试剂管理：打印模板（条码标签 + 各类单据）
-- 幂等脚本：可重复执行；默认模板按 template_code 去重，不覆盖已修改的模板
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_print_template (
    id             BIGSERIAL PRIMARY KEY,
    template_code  VARCHAR(64)  NOT NULL,
    template_name  VARCHAR(128) NOT NULL,
    biz_type       VARCHAR(32)  NOT NULL,
    paper_width    DECIMAL(6,1) NOT NULL DEFAULT 210,
    paper_height   DECIMAL(6,1) NOT NULL DEFAULT 297,
    margin         DECIMAL(5,1) NOT NULL DEFAULT 5,
    template_json  TEXT         NOT NULL,
    is_default     SMALLINT     NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    remark         VARCHAR(500),
    create_by      VARCHAR(50),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(50),
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_print_template IS '试剂管理打印模板（条码标签与单据）';
COMMENT ON COLUMN lis_print_template.biz_type IS 'REAGENT_LABEL/PURCHASE_APPLY/PURCHASE_ORDER/INBOUND/REQUISITION/STOCK_CHECK/SCRAP_DISPOSAL';
COMMENT ON COLUMN lis_print_template.template_json IS '画布与元素定义的 JSON，坐标单位 mm';
COMMENT ON COLUMN lis_print_template.is_default IS '0否 1是该业务类型的默认模板';

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_print_template_code ON lis_print_template (template_code);
-- 同一业务类型只允许一个启用的默认模板
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_print_template_default
    ON lis_print_template (biz_type) WHERE is_default = 1 AND status = 1;
CREATE INDEX IF NOT EXISTS idx_lis_print_template_biz ON lis_print_template (biz_type, status);

-- =============================================================================
-- 默认模板种子（开箱即用，可在页面上继续编辑）
-- =============================================================================

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('LBL_REAGENT_DEFAULT', '试剂条码标签（默认）', 'REAGENT_LABEL', 60, 40, 2, $json$
{
  "version": 1,
  "canvas": { "width": 60, "height": 40, "background": "#ffffff" },
  "elements": [
    { "id": "l1", "type": "field", "x": 0, "y": 1, "w": 56, "h": 4.5, "field": "reagentName", "label": "试剂", "labelWidth": 10, "fontSize": 9 },
    { "id": "l2", "type": "field", "x": 0, "y": 6, "w": 56, "h": 4.5, "field": "lotNo", "label": "批号", "labelWidth": 10, "fontSize": 9 },
    { "id": "l3", "type": "field", "x": 0, "y": 11, "w": 56, "h": 4.5, "field": "expiryDate", "label": "效期", "labelWidth": 10, "fontSize": 9 },
    { "id": "l4", "type": "field", "x": 0, "y": 16, "w": 56, "h": 4.5, "field": "location", "label": "位置", "labelWidth": 10, "fontSize": 9 },
    { "id": "l5", "type": "barcode", "x": 0, "y": 21, "w": 56, "h": 12, "symbology": "CODE128", "value": "{{barcode}}", "showText": true }
  ]
}
$json$, 1, 1, 'system', '默认试剂条码标签：试剂名称/批号/效期/位置 + CODE128')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('PA_DEFAULT', '采购申请单（默认）', 'PURCHASE_APPLY', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂采购申请单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "applyNo", "label": "申请单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "submitTime", "label": "提交时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "applyDeptName", "label": "申请科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "申请人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "expectDate", "label": "期望到货", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "urgentFlagText", "label": "紧急程度", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 50, "align": "left" },
        { "field": "specification", "title": "规格", "width": 38, "align": "left" },
        { "field": "quantity", "title": "数量", "width": 18, "align": "right" },
        { "field": "unit", "title": "单位", "width": 14, "align": "center" },
        { "field": "estimatedPrice", "title": "预估单价", "width": 18, "align": "right" },
        { "field": "amount", "title": "金额", "width": 18, "align": "right" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 168, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 170, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 250, "w": 60, "h": 8, "text": "申请人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 250, "w": 60, "h": 8, "text": "科室负责人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 250, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 262, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认采购申请单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('PO_DEFAULT', '采购订单（默认）', 'PURCHASE_ORDER', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂采购订单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "orderNo", "label": "订单编号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "orderDate", "label": "下单日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "supplierName", "label": "供应商", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "expectDate", "label": "期望到货", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "deptName", "label": "请购科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "operatorName", "label": "经办人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f7", "type": "field", "x": 0, "y": 32, "w": 97, "h": 6, "field": "applyNo", "label": "来源申请单", "labelWidth": 22, "fontSize": 10 },
    { "id": "f8", "type": "field", "x": 97, "y": 32, "w": 97, "h": 6, "field": "statusText", "label": "订单状态", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 39, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 41, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 50, "align": "left" },
        { "field": "specification", "title": "规格", "width": 36, "align": "left" },
        { "field": "quantity", "title": "数量", "width": 16, "align": "right" },
        { "field": "unit", "title": "单位", "width": 12, "align": "center" },
        { "field": "unitPrice", "title": "单价", "width": 20, "align": "right" },
        { "field": "amount", "title": "金额", "width": 22, "align": "right" }
      ] },
    { "id": "f9", "type": "field", "x": 120, "y": 174, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f10", "type": "field", "x": 0, "y": 176, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "经办人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "供应商确认：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认采购订单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('IN_DEFAULT', '试剂入库单（默认）', 'INBOUND', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂入库单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "inboundNo", "label": "入库单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "入库时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "supplierName", "label": "供应商", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "orderNo", "label": "采购订单", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "verifyStatusText", "label": "验收状态", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "verifierName", "label": "验收人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 150, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 8, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 22, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 32, "align": "left" },
        { "field": "specification", "title": "规格", "width": 24, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 18, "align": "center" },
        { "field": "barcode", "title": "条码", "width": 24, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 10, "align": "right" },
        { "field": "unit", "title": "单位", "width": 8, "align": "center" },
        { "field": "purchasePrice", "title": "单价", "width": 12, "align": "right" },
        { "field": "amount", "title": "金额", "width": 12, "align": "right" },
        { "field": "expiryDate", "title": "有效期至", "width": 20, "align": "center" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 194, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 196, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "验收人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "库管员签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认入库单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('RQ_DEFAULT', '试剂领用/出库单（默认）', 'REQUISITION', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂领用（出库）单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "reqNo", "label": "单据编号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "申请时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "deptName", "label": "领用科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "领用人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "reqTypeText", "label": "单据类型", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "approverName", "label": "审核人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 140, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 26, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 44, "align": "left" },
        { "field": "specification", "title": "规格", "width": 32, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 18, "align": "center" },
        { "field": "expiryDate", "title": "有效期至", "width": 22, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 14, "align": "right" },
        { "field": "unit", "title": "单位", "width": 10, "align": "center" },
        { "field": "amount", "title": "金额", "width": 18, "align": "right" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 184, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 186, "w": 194, "h": 8, "field": "reason", "label": "领用原因", "labelWidth": 16, "fontSize": 10 },
    { "id": "f9", "type": "field", "x": 0, "y": 194, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "领用人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "发料人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认领用/出库单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('SC_DEFAULT', '试剂盘点单（默认）', 'STOCK_CHECK', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂库存盘点单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "checkNo", "label": "盘点单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "checkDate", "label": "盘点日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "warehouse", "label": "盘点仓库", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "operatorName", "label": "盘点人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "statusText", "label": "盘点状态", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "auditorName", "label": "审核人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 140, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 26, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 44, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 22, "align": "center" },
        { "field": "bookQty", "title": "账面数量", "width": 24, "align": "right" },
        { "field": "actualQty", "title": "实盘数量", "width": 24, "align": "right" },
        { "field": "diffQty", "title": "差异", "width": 20, "align": "right" },
        { "field": "diffReason", "title": "差异原因", "width": 24, "align": "left" }
      ] },
    { "id": "f7", "type": "field", "x": 0, "y": 184, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "盘点人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "监盘人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认盘点单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('SD_DEFAULT', '试剂报损单（默认）', 'SCRAP_DISPOSAL', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂报损（报废）单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "reqNo", "label": "报废单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "登记时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "deptName", "label": "报损科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "经办人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "disposalMethod", "label": "处置方式", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "disposalDate", "label": "处置日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f7", "type": "field", "x": 0, "y": 32, "w": 97, "h": 6, "field": "disposalOrg", "label": "处置单位", "labelWidth": 22, "fontSize": 10 },
    { "id": "f8", "type": "field", "x": 97, "y": 32, "w": 97, "h": 6, "field": "disposalCert", "label": "处置凭证", "labelWidth": 20, "fontSize": 10 },
    { "id": "f9", "type": "field", "x": 0, "y": 38, "w": 97, "h": 6, "field": "hazardText", "label": "危险品", "labelWidth": 22, "fontSize": 10 },
    { "id": "f10", "type": "field", "x": 97, "y": 38, "w": 97, "h": 6, "field": "safetyApproverName", "label": "安全员", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 45, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 47, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 48, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 22, "align": "center" },
        { "field": "expiryDate", "title": "有效期至", "width": 22, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 20, "align": "right" },
        { "field": "unit", "title": "单位", "width": 12, "align": "center" },
        { "field": "amount", "title": "金额", "width": 32, "align": "right" }
      ] },
    { "id": "f11", "type": "field", "x": 120, "y": 180, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f12", "type": "field", "x": 0, "y": 182, "w": 194, "h": 10, "field": "reason", "label": "报损原因", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "经办人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "安全员签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认报损单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;


-- =============================================================================
-- 【补充脚本】v1.0.1 增量表结构与字典
-- 补齐 16 张业务表、缺失字段与内置字典（培养基/培养方案/条码规则/试剂参数）
-- 全部语句幂等，可重复执行
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、标本-申请项目 中间表（跨申请合管）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_sample_order_item (
    id BIGINT PRIMARY KEY,
    sample_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    test_item_id BIGINT,
    create_time TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_soi_sample_id ON lis_sample_order_item(sample_id);
CREATE INDEX IF NOT EXISTS idx_soi_order_id ON lis_sample_order_item(order_id);
CREATE INDEX IF NOT EXISTS idx_soi_order_item_id ON lis_sample_order_item(order_item_id);

-- -----------------------------------------------------------------------------
-- 二、条码规则表 + 默认规则
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_barcode_rule (
    id BIGINT PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    prefix VARCHAR(10) DEFAULT '',
    date_pattern VARCHAR(20) DEFAULT 'none',
    seq_length INT DEFAULT 6,
    seq_strategy VARCHAR(20) DEFAULT 'daily',
    enabled INT DEFAULT 1,
    remark VARCHAR(200),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_barcode_rule_code ON lis_barcode_rule(rule_code) WHERE enabled = 1;
INSERT INTO lis_barcode_rule (id, rule_code, rule_name, prefix, date_pattern, seq_length, seq_strategy, enabled, remark, create_time, update_time) VALUES
(1, 'SAMPLE', '标本条码', '', 'yyMMdd', 6, 'daily', 1, '格式: 2位年+月日+6位序号(共12位), 如 260731000001', NOW(), NOW()),
(2, 'ORDER', '申请号', 'O', 'yyMMdd', 6, 'daily', 1, '格式: O+2位年月+6位序号, 如 O260731000001', NOW(), NOW()),
(3, 'VISIT', '就诊号', 'V', 'yyMMdd', 6, 'daily', 1, '格式: V+2位年月+6位序号, 如 V260731000001', NOW(), NOW()),
(4, 'PATIENT', '患者号', 'P', 'none', 10, 'global', 1, '格式: P+10位序号(全局递增), 如 P0000000001', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 三、仪器通道扩展字段（文件 / 数据库 / 串口采集）
-- -----------------------------------------------------------------------------
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_path VARCHAR(255);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_format VARCHAR(20);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_pattern VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS processed_dir VARCHAR(255);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS poll_interval INT DEFAULT 30;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_type VARCHAR(20);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_host VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_port INT;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_name VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_user VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_password VARCHAR(200);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_driver VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_table VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_columns VARCHAR(500);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_where VARCHAR(500);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS db_watermark_col VARCHAR(64);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS last_polled_time TIMESTAMP;

-- -----------------------------------------------------------------------------
-- 四、仪器-标本类型映射
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_instrument_specimen_map (
    id BIGINT PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    specimen_type_id BIGINT,
    specimen_type_name VARCHAR(64),
    specimen_type_code VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_lis_instrument_specimen_map_instr ON lis_instrument_specimen_map (instrument_id);

-- -----------------------------------------------------------------------------
-- 五、微生物室内质控记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_micro_qc_record (
    id BIGSERIAL PRIMARY KEY,
    item_type SMALLINT NOT NULL,
    item_code VARCHAR(64),
    item_name VARCHAR(128) NOT NULL,
    qc_lot_no VARCHAR(64),
    control_strain VARCHAR(64),
    expected_result VARCHAR(64),
    actual_result VARCHAR(64),
    result VARCHAR(16) DEFAULT 'PASS',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    measure_time TIMESTAMP,
    remark VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_qc_record IS '微生物室内质控记录表';
COMMENT ON COLUMN lis_micro_qc_record.item_type IS '类别: 1生化反应试剂 2抗菌药物 3纸片法药敏 4诊断血清 5血培养瓶 6染色液 7标准菌株';
COMMENT ON COLUMN lis_micro_qc_record.result IS '判定: PASS合格 FAIL不合格';
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_type ON lis_micro_qc_record (item_type);
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_time ON lis_micro_qc_record (measure_time);

-- -----------------------------------------------------------------------------
-- 六、仪器「无主标本」结果表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_instrument_unmatched_result (
    id                BIGSERIAL PRIMARY KEY,
    instrument_id     BIGINT,
    instrument_name   VARCHAR(100),
    sample_no         VARCHAR(100),
    test_code         VARCHAR(50),
    test_name         VARCHAR(100),
    result_value      VARCHAR(100),
    unit              VARCHAR(50),
    ref_range         VARCHAR(100),
    abnormal_flag     VARCHAR(50),
    is_critical       SMALLINT DEFAULT 0,
    status            SMALLINT DEFAULT 0,
    matched_sample_id BIGINT,
    remark            VARCHAR(500),
    test_time         TIMESTAMP,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_unmatched_instrument ON lis_instrument_unmatched_result (instrument_id);
CREATE INDEX IF NOT EXISTS idx_unmatched_status ON lis_instrument_unmatched_result (status);

-- -----------------------------------------------------------------------------
-- 七、资质证书附件
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_certificate_attachment (
    id              BIGSERIAL PRIMARY KEY,
    certificate_id  BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(50),
    file_size       BIGINT,
    content_type    VARCHAR(100),
    file_data       BYTEA,
    uploader_id     BIGINT,
    uploader_name   VARCHAR(50),
    upload_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(500)
);
COMMENT ON TABLE lis_certificate_attachment IS '资质证书附件表';
CREATE INDEX IF NOT EXISTS idx_cert_attachment_certificate ON lis_certificate_attachment (certificate_id);
CREATE INDEX IF NOT EXISTS idx_cert_attachment_upload_time ON lis_certificate_attachment (upload_time);
ALTER TABLE lis_staff_certificate ADD COLUMN IF NOT EXISTS attachment_count INT DEFAULT 0;

-- -----------------------------------------------------------------------------
-- 八、微生物培养：培养基字典 / 培养方案 / 方案明细 / 培养皿
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_medium_type (
    id BIGSERIAL PRIMARY KEY,
    medium_code VARCHAR(32),
    medium_name VARCHAR(100) NOT NULL,
    medium_abbr VARCHAR(20),
    remark VARCHAR(500),
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_culture_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,
    test_item_id BIGINT,
    test_item_name VARCHAR(100),
    sample_type VARCHAR(50),
    remark VARCHAR(500),
    is_default SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE lis_culture_plan ADD COLUMN IF NOT EXISTS sample_type VARCHAR(50);
DROP INDEX IF EXISTS uk_lis_culture_plan_item;
CREATE TABLE IF NOT EXISTS lis_culture_plan_item (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT,
    medium_type_id BIGINT,
    medium_type_name VARCHAR(100),
    quantity INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_sample_plate (
    id BIGSERIAL PRIMARY KEY,
    sample_id BIGINT,
    sample_barcode VARCHAR(50),
    plate_barcode VARCHAR(100),
    plan_id BIGINT,
    medium_type_id BIGINT,
    medium_type_name VARCHAR(100),
    patient_name VARCHAR(100),
    seq_no INT,
    status SMALLINT DEFAULT 0,
    operator_id BIGINT,
    operator_name VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancel_time TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_plate_barcode ON lis_sample_plate (plate_barcode);

-- 培养基字典（仅在表为空时灌入）
INSERT INTO lis_medium_type (medium_code, medium_name, medium_abbr, remark, status)
SELECT * FROM (VALUES
 ('BAP','血平板','血','通用分离培养, 观察溶血',1),
 ('MAC','麦康凯平板','麦','肠道革兰阴性杆菌分离',1),
 ('CHOC','巧克力平板','巧','嗜血杆菌/奈瑟菌等苛养菌',1),
 ('SS','SS平板','SS','沙门菌/志贺菌分离',1),
 ('EMB','伊红美蓝平板','EM','肠道菌分离鉴别',1),
 ('XLD','木糖赖氨酸脱氧胆盐平板','XL','沙门菌/志贺菌分离',1),
 ('SDA','沙保弱葡萄糖琼脂','沙','真菌分离培养',1),
 ('CA','哥伦比亚血琼脂','哥','苛养菌和厌氧',1),
 ('CNA','哥伦比亚CNA琼脂','CN','革兰阳性菌选择',1),
 ('AN','厌氧血平板','厌','厌氧菌分离',1),
 ('KV','卡那霉素万古霉素平板','KV','厌氧菌选择(拟杆菌)',1),
 ('PEA','苯乙基乙醇平板','PE','厌氧/革兰阳性选择',1),
 ('T-M','Thayer-Martin平板','TM','淋病奈瑟菌选择',1),
 ('BCYE','缓冲炭酵母浸膏','BC','军团菌分离',1),
 ('LJ','罗氏培养基','罗','结核分枝杆菌',1),
 ('MB','Middlebrook 7H10','MB','分枝杆菌分离',1),
 ('TCBS','硫代硫酸盐枸橼酸盐胆盐平板','TC','弧菌分离',1),
 ('Cary-Blair','Cary-Blair运送','CB','标本运送',1),
 ('Stuart','Stuart运送','ST','运送',1),
 ('MHA','MH琼脂平板','MH','药敏KB纸片法',1)
) AS v(medium_code, medium_name, medium_abbr, remark, status)
WHERE NOT EXISTS (SELECT 1 FROM lis_medium_type);

-- 培养方案（按检验项目名称匹配，可为空）
INSERT INTO lis_culture_plan (plan_name, test_item_id, test_item_name, remark, is_default, status)
SELECT v.plan_name,
       (SELECT id FROM lis_test_item t WHERE t.item_name = v.test_item_name LIMIT 1),
       v.test_item_name, '系统内置方案', 0, 1
FROM (VALUES
 ('痰培养方案','痰一般细菌培养'),('尿培养方案','尿一般细菌培养'),
 ('血培养方案','血培养'),('分泌物培养方案','分泌物培养'),
 ('脑脊液培养方案','脑脊液培养'),('胸腹培养方案','胸腹水培养'),
 ('粪便培养方案','粪便培养'),('真菌培养方案','真菌培养'),
 ('厌氧培养方案','厌氧菌培养'),('淋球菌培养方案','淋球菌培养')
) AS v(plan_name, test_item_name)
WHERE NOT EXISTS (SELECT 1 FROM lis_culture_plan);

-- 培养方案明细（方案 -> 培养基，按编码匹配）
INSERT INTO lis_culture_plan_item (plan_id, medium_type_id, medium_type_name, quantity, sort_order)
SELECT p.id, m.id, m.medium_name, 1, row_number() OVER (PARTITION BY p.id)
FROM lis_culture_plan p
JOIN (
    SELECT '痰培养方案' plan_name, 'BAP' code UNION ALL SELECT '痰培养方案','MAC' UNION ALL SELECT '痰培养方案','CHOC'
    UNION ALL SELECT '尿培养方案','BAP' UNION ALL SELECT '尿培养方案','MAC'
    UNION ALL SELECT '血培养方案','BAP' UNION ALL SELECT '血培养方案','AN' UNION ALL SELECT '血培养方案','CHOC'
    UNION ALL SELECT '分泌物培养方案','BAP' UNION ALL SELECT '分泌物培养方案','MAC' UNION ALL SELECT '分泌物培养方案','CHOC'
    UNION ALL SELECT '脑脊液培养方案','BAP' UNION ALL SELECT '脑脊液培养方案','CHOC' UNION ALL SELECT '脑脊液培养方案','SDA'
    UNION ALL SELECT '胸腹培养方案','BAP' UNION ALL SELECT '胸腹培养方案','MAC' UNION ALL SELECT '胸腹培养方案','CHOC'
    UNION ALL SELECT '粪便培养方案','SS' UNION ALL SELECT '粪便培养方案','MAC' UNION ALL SELECT '粪便培养方案','XLD'
    UNION ALL SELECT '真菌培养方案','SDA' UNION ALL SELECT '真菌培养方案','SDA'
    UNION ALL SELECT '厌氧培养方案','AN' UNION ALL SELECT '厌氧培养方案','KV' UNION ALL SELECT '厌氧培养方案','PEA'
    UNION ALL SELECT '淋球菌培养方案','T-M' UNION ALL SELECT '淋球菌培养方案','CHOC'
) v ON v.plan_name = p.plan_name
JOIN lis_medium_type m ON m.medium_code = v.code
WHERE NOT EXISTS (SELECT 1 FROM lis_culture_plan_item pi WHERE pi.plan_id = p.id);

-- -----------------------------------------------------------------------------
-- 九、微生物特殊耐药 / 危急值预警
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_micro_alert (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT,
    organism_id     BIGINT,
    alert_type      VARCHAR(32),
    alert_level     VARCHAR(16),
    organism_name   VARCHAR(100),
    message         TEXT,
    status          VARCHAR(16),
    handler         VARCHAR(50),
    handle_remark   VARCHAR(500),
    handle_time     TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_alert IS '微生物特殊耐药/危急值预警表';
CREATE INDEX IF NOT EXISTS idx_micro_alert_report ON lis_micro_alert (micro_report_id);
CREATE INDEX IF NOT EXISTS idx_micro_alert_status ON lis_micro_alert (status);

-- -----------------------------------------------------------------------------
-- 十、失控报告（OCC）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_qc_occ_event (
  id              BIGSERIAL PRIMARY KEY,
  occ_no          VARCHAR(40)  NOT NULL,
  lot_id          BIGINT,
  project_code    VARCHAR(40),
  project_name    VARCHAR(120),
  group_name      VARCHAR(60),
  trigger_rules   VARCHAR(200),
  found_at        TIMESTAMP,
  found_by        VARCHAR(60),
  status          VARCHAR(20)  DEFAULT 'PENDING',
  error_type      VARCHAR(20),
  qc_values       VARCHAR(200),
  target_value    NUMERIC(18,4),
  sd              NUMERIC(18,6),
  root_cause_category VARCHAR(40),
  root_cause_desc VARCHAR(255),
  root_cause_by   VARCHAR(60),
  root_cause_at   TIMESTAMP,
  result_impact   VARCHAR(500),
  prevention      VARCHAR(500),
  material_snapshot VARCHAR(120),
  current_step    INT DEFAULT 1,
  create_time     TIMESTAMP DEFAULT now(),
  update_time     TIMESTAMP,
  deleted         INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_occ_event_occ_no ON lis_qc_occ_event(occ_no);

CREATE TABLE IF NOT EXISTS lis_qc_occ_step (
  id         BIGSERIAL PRIMARY KEY,
  occ_id     BIGINT NOT NULL,
  step_no    INT NOT NULL,
  name       VARCHAR(60),
  status     VARCHAR(20) DEFAULT 'PENDING',
  checklist  VARCHAR(3000),
  operator   VARCHAR(60),
  operated_at TIMESTAMP,
  conclusion VARCHAR(500),
  CONSTRAINT uq_occ_step UNIQUE(occ_id, step_no)
);

CREATE TABLE IF NOT EXISTS lis_qc_occ_approval (
  id          BIGSERIAL PRIMARY KEY,
  occ_id      BIGINT NOT NULL,
  level_no    INT,
  level_name  VARCHAR(40),
  status      VARCHAR(20) DEFAULT 'PENDING',
  approver    VARCHAR(60),
  comment     VARCHAR(255),
  approved_at TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 十一、试剂：系统参数 / 供应商发票
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_sys_config (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(255),
    config_name  VARCHAR(100),
    remark       VARCHAR(255),
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_sys_config_key ON lis_sys_config (config_key);
INSERT INTO lis_sys_config (config_key, config_value, config_name, remark) VALUES
    ('reagent.purchase_order_need_approval', '0', '采购订单是否需要审批', '1=需组长/主任审批通过后才能审核；0=可直接审核'),
    ('reagent.scrap_need_approval',          '1', '报损/报废是否需要审批', '1=需审批；0=可直接审核'),
    ('reagent.high_stock_warning_enabled',   '1', '是否启用高库存预警', '1=启用；0=关闭')
ON CONFLICT (config_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS lis_reagent_invoice (
    id            BIGSERIAL PRIMARY KEY,
    invoice_no    VARCHAR(64) NOT NULL,
    supplier_id   BIGINT,
    supplier_name VARCHAR(100),
    invoice_date  DATE,
    amount        DECIMAL(12,2),
    tax_amount    DECIMAL(12,2),
    order_id      BIGINT,
    order_no      VARCHAR(32),
    status        SMALLINT DEFAULT 1,
    remark        VARCHAR(255),
    operator_name VARCHAR(50),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_invoice_no ON lis_reagent_invoice (invoice_no);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_invoice_supplier ON lis_reagent_invoice (supplier_id);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_invoice_date ON lis_reagent_invoice (invoice_date);

-- -----------------------------------------------------------------------------
-- 十二、试剂扩展字段（字典 / 采购 / 盘点 / 消耗 / 开封）
-- -----------------------------------------------------------------------------
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS optimal_stock      DECIMAL(10,2);
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS purchase_cycle_days INT;
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS barcode_alias      VARCHAR(64);
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS qc_flag            SMALLINT DEFAULT 0;
ALTER TABLE lis_reagent ADD COLUMN IF NOT EXISTS qc_pack_count      INT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_reagent_barcode_alias
    ON lis_reagent (barcode_alias) WHERE barcode_alias IS NOT NULL;

ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS apply_ids        TEXT;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_status  SMALLINT DEFAULT 0;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_time    TIMESTAMP;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS approval_opinion VARCHAR(255);
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS audit_status     SMALLINT DEFAULT 0;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS auditor_id       BIGINT;
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS auditor_name     VARCHAR(50);
ALTER TABLE lis_reagent_purchase_order ADD COLUMN IF NOT EXISTS audit_time       TIMESTAMP;

ALTER TABLE lis_reagent_requisition ADD COLUMN IF NOT EXISTS supplier_id   BIGINT;
ALTER TABLE lis_reagent_requisition ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(100);

ALTER TABLE lis_reagent_inbound ADD COLUMN IF NOT EXISTS inbound_mode VARCHAR(20);

ALTER TABLE lis_reagent_stock_check_item ADD COLUMN IF NOT EXISTS diff_reason VARCHAR(100);
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS audit_status  SMALLINT DEFAULT 0;
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS auditor_id    BIGINT;
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS auditor_name  VARCHAR(50);
ALTER TABLE lis_reagent_stock_check ADD COLUMN IF NOT EXISTS audit_time    TIMESTAMP;

ALTER TABLE lis_reagent_consumption ADD COLUMN IF NOT EXISTS instrument_id BIGINT;
ALTER TABLE lis_reagent_consumption ADD COLUMN IF NOT EXISTS test_count    INT;

ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS opening_type    SMALLINT DEFAULT 1;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS parent_id       BIGINT;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS pack_no         INT;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS pack_total      INT;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS demand_qty      DECIMAL(10,2);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approval_status SMALLINT DEFAULT 1;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approver_id     BIGINT;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approver_name   VARCHAR(50);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approve_time    TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS approve_opinion VARCHAR(255);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_time       TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_operator   VARCHAR(50);
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS exhaust_time    TIMESTAMP;
ALTER TABLE lis_reagent_opening ADD COLUMN IF NOT EXISTS seal_status     SMALLINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_lis_reagent_record_time ON lis_reagent_stock_record (create_time);
CREATE INDEX IF NOT EXISTS idx_lis_reagent_record_type ON lis_reagent_stock_record (record_type);

-- -----------------------------------------------------------------------------
-- 十三、微生物报告 / 鉴定 / 药敏扩展字段
-- -----------------------------------------------------------------------------
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS plate_barcode VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_lis_micro_report_plate_barcode ON lis_micro_report (plate_barcode);
ALTER TABLE lis_micro_organism ADD COLUMN IF NOT EXISTS remark VARCHAR(255);
ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS method VARCHAR(32);
ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS zone_size INTEGER;
ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS remark VARCHAR(255);

-- -----------------------------------------------------------------------------
-- 十四、耐药规则扩展（可配置规则引擎）
-- -----------------------------------------------------------------------------
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS bacteria_codes TEXT;
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS rule_type VARCHAR(30);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS result VARCHAR(5);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS match_mode VARCHAR(20) DEFAULT 'ANY_RESISTANT';
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS is_builtin SMALLINT DEFAULT 0;


-- -----------------------------------------------------------------------------
-- 十五、运行时补充字段（与生产库结构对齐）
-- -----------------------------------------------------------------------------
ALTER TABLE lis_instrument_item_map ADD COLUMN IF NOT EXISTS channel_code VARCHAR(32);
ALTER TABLE lis_instrument_unmatched_result ADD COLUMN IF NOT EXISTS matched_qc_lot_id BIGINT;
ALTER TABLE lis_instrument_unmatched_result ADD COLUMN IF NOT EXISTS matched_qc_record_id BIGINT;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_id BIGINT;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_name VARCHAR(64);
ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS sample_barcode VARCHAR(50);
ALTER TABLE lis_patient ADD COLUMN IF NOT EXISTS is_test SMALLINT DEFAULT 0;
ALTER TABLE lis_qc_alert ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE lis_qc_lot ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE lis_qc_record ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE lis_qc_record ADD COLUMN IF NOT EXISTS sample_id BIGINT;
ALTER TABLE lis_qc_rule ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS patient_age INT;
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS patient_gender SMALLINT;
ALTER TABLE lis_report_template ADD COLUMN IF NOT EXISTS report_type SMALLINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_person_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sign_off_person_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_count INT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_type VARCHAR(50);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_person VARCHAR(50);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_person_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS reject_time TIMESTAMP;


-- -----------------------------------------------------------------------------
-- 十六、HIS 检验申请接口（HIS WebAPI 推送）
-- -----------------------------------------------------------------------------
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS his_apply_id VARCHAR(80);
COMMENT ON COLUMN lis_lab_order.his_apply_id IS 'HIS 申请唯一键(申请号#项目序号), 用于重复推送幂等去重';
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_lab_order_his_apply
    ON lis_lab_order (his_apply_id) WHERE his_apply_id IS NOT NULL;

INSERT INTO sys_config (config_key, config_value, config_name, remark) VALUES
('his.apply.enabled',         'true', 'HIS检验申请接口开关',   'true启用 / false停用'),
('his.apply.token',           '',     'HIS检验申请接口令牌',   '为空不校验；配置后HIS需在请求头X-HIS-Token或URL参数token携带'),
('his.apply.auto-create-item','true', 'HIS项目未匹配自动建档', 'true自动新增lis_test_item项目 / false返回错误'),
('his.apply.default-dept',    '门诊', 'HIS申请默认科室',       'HIS未传申请科室时使用'),
('his.result.enabled',        'true', 'LIS结果回传HIS开关',    'true启用 / false停用'),
('his.result.url',            '',     'HIS检验结果接收地址(4010)', 'HIS提供的WebAPI完整地址，为空时不回传'),
('his.result.token',          '',     'HIS结果回传令牌',       '为空不校验；回传时置于请求头X-HIS-Token'),
('his.result.source-system',  'ZLHIS', 'HIS结果回传来源系统',  '本院传ZLHIS / 外院传ZLRCE'),
('his.result.default-lspcm-no', '1',  'HIS结果回传标本类型码', 'HIS无标本类型对照时使用的lspcm_no')
ON CONFLICT (config_key) DO NOTHING;


-- -----------------------------------------------------------------------------
-- 十七、HIS 新标准服务（检验）S1001~S4020 回写进度表与参数
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_his_sample_status_push (
    sample_id           BIGINT PRIMARY KEY,
    last_status         SMALLINT,
    last_pushed_status  SMALLINT,
    retry_count         INT DEFAULT 0,
    error_msg           VARCHAR(500),
    last_push_time      TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_his_sample_status_push IS 'S4009 标本状态回写进度';

CREATE TABLE IF NOT EXISTS lis_his_critical_push (
    result_id       BIGINT PRIMARY KEY,
    critical_id     VARCHAR(64),
    push_status     SMALLINT DEFAULT 0,
    retry_count     INT DEFAULT 0,
    error_msg       VARCHAR(500),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_his_critical_push IS 'S3103 危急值回传进度';

CREATE TABLE IF NOT EXISTS lis_his_zl_log (
    id              BIGSERIAL PRIMARY KEY,
    service_no      VARCHAR(16),
    action_no       VARCHAR(64),
    direction       SMALLINT DEFAULT 1,
    url             VARCHAR(500),
    request_body    TEXT,
    response_body   TEXT,
    http_status     INT,
    success         SMALLINT DEFAULT 1,
    error_msg       VARCHAR(1000),
    cost_ms         BIGINT,
    ref_no          VARCHAR(200),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_time ON lis_his_zl_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_service ON lis_his_zl_log (service_no);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_ref ON lis_his_zl_log (ref_no);
COMMENT ON TABLE lis_his_zl_log IS 'HIS 接口调用日志（前台 HIS接口日志 页面可查）';

INSERT INTO sys_config (config_key, config_value, config_name, remark) VALUES
('his.zl.enabled',     'true',  'HIS标准服务总开关',   'true启用/false停用'),
('his.zl.url',         '',      'HIS数据处理平台地址', '如 http://HIS-IP:端口/zlstddService，为空不回写'),
('his.zl.sysno',       'LIS',   'LIS系统编码',         '报文head.sysno'),
('his.zl.tarno',       'ZLHIS', '目标系统编码',        '固定ZLHIS'),
('his.zl.token',       '',      'HIS标准服务令牌',     '为空不校验'),
('his.zl.org-id',      '0',     '检验科科室ID',        'S4009 organization_id'),
('his.zl.org-code',    'LIS',   '检验科科室编码',      'S4009 organization_code'),
('his.zl.org-name',    '检验科', '检验科科室名称',     'S4009 organization_name'),
('his.zl.rpt-dept-id', '0',     '报告科室ID',          'S3103 rptexedept_id'),
('his.zl.operator',    '系统',  '回写操作员姓名',      'S4011 oprtr_name')
ON CONFLICT (config_key) DO NOTHING;
