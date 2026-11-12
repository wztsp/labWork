-- =============================================================================
-- LIS 临床检验系统 数据库初始化脚本 (PostgreSQL 15+)
-- 字符集: UTF-8
-- 命名规范: 表名 lis_ / sys_ 前缀, 字段名 snake_case, 主键 BIGSERIAL
-- 说明: TINYINT 一律使用 SMALLINT 替代; 索引使用 CREATE INDEX 语句
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
    sample_barcode  VARCHAR(64),                        -- 标本条码(采集工作站条码, 冗余)
    plate_barcode   VARCHAR(64),                        -- 培养条码/培养基条码: 一份报告 = 一次培养基核收, 同一采集条码下多个培养条码各自独立出报告
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
CREATE INDEX idx_lis_micro_report_plate_barcode ON lis_micro_report (plate_barcode);

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
