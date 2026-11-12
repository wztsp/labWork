# LIS 数据库设计文档

> 数据库：PostgreSQL 15+ (兼容 MySQL 8.0)  
> 字符集：UTF-8  
> 命名规范：表名 `lis_` 前缀，字段名 `snake_case`，主键统一 `id BIGINT`

---

## 一、ER 关系总览

```
┌──────────────┐      ┌──────────────┐      ┌──────────────────┐
│  lis_patient  │      │  lis_visit   │      │  lis_lab_order    │
│  (患者)       │──1:N─│  (就诊)       │──1:N─│  (检验申请)       │
└──────────────┘      └──────────────┘      └────────┬─────────┘
                                                     │ 1:N
                            ┌────────────────────────┴────┐
                            │                              │
                     ┌──────┴───────┐              ┌──────┴────────┐
                     │  lis_sample   │──1:N────────│ lis_order_item │
                     │  (标本)       │              │ (申请项目)     │
                     └──────┬───────┘              └───────────────┘
                            │ 1:N
                     ┌──────┴──────────┐
                     │  lis_result      │
                     │  (检验结果)      │
                     └──────┬──────────┘
                            │ N:1
                     ┌──────┴──────────┐
                     │  lis_test_item   │
                     │  (检验项目字典)  │
                     └─────────────────┘

┌──────────────┐      ┌──────────────────┐      ┌──────────────────┐
│ lis_instrument│      │  lis_instrument  │      │  lis_qc_lot       │
│ (仪器字典)    │──1:N─│ _maintenance_log │      │  (质控品批号)      │
└──────────────┘      │ (维护记录)        │      └────────┬─────────┘
                      └──────────────────┘               │ 1:N
                                              ┌───────────┴───────────┐
                                              │    lis_qc_record      │
                                              │    (质控记录)          │
                                              └───────────────────────┘

┌──────────────┐      ┌──────────────┐      ┌──────────────────┐
│  lis_reagent  │      │ lis_reagent  │      │ lis_micro_report  │
│  (试剂字典)   │──1:N─│ _stock       │      │ (微生物报告)      │
└──────────────┘      │ (试剂库存)    │      ├────────┬─────────┘
                      └──────┬───────┘      │ 1:N    │ 1:N
                             │ 1:N          ┌┴───────┐┴──────────┐
                             │        ┌─────┴──────┐ ┌┴───────────┐
                             │        │lis_micro_  │ │lis_micro_   │
                             │        │ organism   │ │ sensitivity │
                             │        │ (检出菌)    │ │ (药敏结果)   │
                             │        └────────────┘ └─────────────┘
                             │
                      ┌──────┴──────────┐
                      │ lis_reagent_use   │
                      │ (试剂使用记录)     │
                      └───────────────────┘

┌──────────────┐      ┌──────────────────┐
│  lis_report   │      │ lis_blood_culture│
│  (报告)       │      │ (血培养记录)      │
└──────────────┘      └──────────────────┘

┌──────────────┐      ┌──────────────┐      ┌──────────────────┐
│  lis_lab_group│      │  lis_staff   │      │  lis_staff_title  │
│  (小组/科室)  │──1:N─│  (人员档案)  │──1:N─│  (职称记录)       │
└──────────────┘      └──────┬───────┘      └───────────────────┘
                             │ 1:N
              ┌──────────────┼──────────────┐
              │              │              │
       ┌──────┴──────┐ ┌────┴──────────┐ ┌┴──────────────┐
       │lis_staff_   │ │lis_staff_     │ │ lis_lab_doc   │
       │certificate │ │authorization  │ │ (文档资料)    │
       │(资质证书)   │ │(岗位授权)     │ └───────────────┘
       └─────────────┘ └───────────────┘

┌──────────────┐      ┌──────────────┐
│ lis_audit_log│      │lis_staff_    │
│ (操作日志)    │      │training      │
└──────────────┘      │(培训记录)    │
                      └──────────────┘
```

---

## 二、核心表结构

### 2.1 患者与就诊

```sql
-- 患者信息表
CREATE TABLE lis_patient (
    id              BIGSERIAL PRIMARY KEY,
    patient_no      VARCHAR(32) NOT NULL UNIQUE,    -- 患者编号 (HIS 同步)
    id_card         VARCHAR(64),                     -- 身份证号 (AES 加密)
    name            VARCHAR(50) NOT NULL,
    gender          TINYINT,                         -- 0未知 1男 2女
    birth_date      DATE,
    age             INT,
    phone           VARCHAR(32),                     -- 手机号 (AES 加密)
    address         VARCHAR(255),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_name_phone (name, phone)
);
COMMENT ON TABLE lis_patient IS '患者信息表';

-- 就诊记录表
CREATE TABLE lis_visit (
    id              BIGSERIAL PRIMARY KEY,
    visit_no        VARCHAR(32) NOT NULL UNIQUE,    -- 就诊号
    patient_id      BIGINT NOT NULL,
    visit_type      TINYINT,                         -- 1门诊 2住院 3体检
    department      VARCHAR(50),                     -- 就诊科室
    bed_no          VARCHAR(20),                     -- 床位号 (住院)
    doctor_id       BIGINT,                          -- 开单医生
    doctor_name     VARCHAR(50),
    visit_time      TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (patient_id) REFERENCES lis_patient(id),
    INDEX idx_patient (patient_id),
    INDEX idx_visit_no (visit_no)
);
COMMENT ON TABLE lis_visit IS '就诊记录表';
```

### 2.2 检验申请与标本

```sql
-- 检验申请表
CREATE TABLE lis_lab_order (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL UNIQUE,    -- 申请单号
    visit_id        BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    order_type      TINYINT,                         -- 1常规 2急诊 3体检
    status          TINYINT DEFAULT 0,               -- 0待处理 1已接收 2已作废
    apply_doctor    VARCHAR(50),                     -- 申请医生
    apply_dept      VARCHAR(50),                     -- 申请科室
    clinical_diag   TEXT,                            -- 临床诊断
    apply_time      TIMESTAMP,                       -- 申请时间
    receive_time    TIMESTAMP,                       -- 接收时间
    cancel_time     TIMESTAMP,                       -- 作废时间
    his_sync_status TINYINT DEFAULT 0,               -- HIS 同步状态
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (visit_id) REFERENCES lis_visit(id),
    INDEX idx_visit (visit_id),
    INDEX idx_status_time (status, apply_time)
);
COMMENT ON TABLE lis_lab_order IS '检验申请表';

-- 申请项目明细表
CREATE TABLE lis_order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,                 -- 检验项目 ID
    test_item_code  VARCHAR(32),                     -- 项目代码
    test_item_name  VARCHAR(100),                    -- 项目名称
    status          TINYINT DEFAULT 0,               -- 0待检 1已检 2已报告
    fee             DECIMAL(10,2),                   -- 费用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES lis_lab_order(id),
    INDEX idx_order (order_id),
    INDEX idx_item (test_item_id)
);
COMMENT ON TABLE lis_order_item IS '申请项目明细表';

-- 标本表
CREATE TABLE lis_sample (
    id              BIGSERIAL PRIMARY KEY,
    sample_barcode  VARCHAR(32) NOT NULL UNIQUE,    -- 标本条码
    order_id        BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    visit_id        BIGINT,
    sample_type     VARCHAR(20) NOT NULL,            -- 标本类型
    container_type  VARCHAR(20),                     -- 容器类型
    status          TINYINT DEFAULT 0,               -- 0申请 1采样 2核收 3检测中 4已报告
    is_urgent       TINYINT DEFAULT 0,               -- 急诊标志
    collect_time    TIMESTAMP,                       -- 采集时间
    receive_time    TIMESTAMP,                       -- 核收时间
    test_start_time TIMESTAMP,                       -- 检测开始
    report_time     TIMESTAMP,                       -- 报告时间
    operator_id     BIGINT,                          -- 核收操作员
    department      VARCHAR(50),                     -- 送检科室
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES lis_lab_order(id),
    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_urgent_status (is_urgent, status),
    INDEX idx_barcode (sample_barcode),
    INDEX idx_receive_time (receive_time)
);
COMMENT ON TABLE lis_sample IS '标本表';
```

### 2.3 检验项目与结果

```sql
-- 检验项目字典表
CREATE TABLE lis_test_item (
    id              BIGSERIAL PRIMARY KEY,
    item_code       VARCHAR(32) NOT NULL UNIQUE,    -- 项目代码
    item_name       VARCHAR(100) NOT NULL,           -- 项目中文名
    item_name_en    VARCHAR(100),                    -- 项目英文名
    short_name      VARCHAR(50),                     -- 简称/缩写
    category        VARCHAR(50),                     -- 分类: 血常规/生化/免疫...
    specimen_type   VARCHAR(20),                     -- 适用标本类型
    unit            VARCHAR(30),                     -- 单位
    method          VARCHAR(50),                     -- 检测方法
    is_panel        TINYINT DEFAULT 0,               -- 是否组合项目
    parent_id       BIGINT,                          -- 父项目 (组合项目)
    price           DECIMAL(10,2),
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_parent (parent_id)
);
COMMENT ON TABLE lis_test_item IS '检验项目字典表';

-- 参考范围表
CREATE TABLE lis_reference_range (
    id              BIGSERIAL PRIMARY KEY,
    test_item_id    BIGINT NOT NULL,
    gender          TINYINT,                         -- 性别限制: null全部 1男 2女
    age_min         DECIMAL(5,1),                    -- 最小年龄
    age_max         DECIMAL(5,1),                    -- 最大年龄
    ref_low         VARCHAR(30),                     -- 参考下限
    ref_high        VARCHAR(30),                     -- 参考上限
    ref_text        VARCHAR(100),                    -- 文字参考范围 (如阴性)
    critical_low    VARCHAR(30),                     -- 危急值下限
    critical_high   VARCHAR(30),                     -- 危急值上限
    unit            VARCHAR(30),
    FOREIGN KEY (test_item_id) REFERENCES lis_test_item(id),
    INDEX idx_item (test_item_id)
);
COMMENT ON TABLE lis_reference_range IS '参考范围表';

-- 检验结果表
CREATE TABLE lis_result (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,
    result_value    VARCHAR(100),                    -- 结果值
    result_text     TEXT,                            -- 文字结果
    unit            VARCHAR(30),                     -- 单位
    ref_range       VARCHAR(100),                    -- 参考范围
    abnormal_flag   VARCHAR(10),                     -- 异常标志: N正常 H高 L低
    is_critical     TINYINT DEFAULT 0,               -- 是否危急值
    instrument_id   BIGINT,                          -- 检测仪器
    operator_id     BIGINT,                          -- 操作员
    test_time       TIMESTAMP,                       -- 检测时间
    audit_status    TINYINT DEFAULT 0,               -- 0未审核 1自动审核 2人工审核
    auditor_id      BIGINT,                          -- 审核人
    audit_time      TIMESTAMP,                       -- 审核时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sample_id) REFERENCES lis_sample(id),
    FOREIGN KEY (test_item_id) REFERENCES lis_test_item(id),
    INDEX idx_sample (sample_id),
    INDEX idx_item (test_item_id),
    INDEX idx_critical (is_critical, audit_status)
);
COMMENT ON TABLE lis_result IS '检验结果表';
```

### 2.4 报告管理

```sql
-- 检验报告表
CREATE TABLE lis_report (
    id              BIGSERIAL PRIMARY KEY,
    report_no       VARCHAR(32) NOT NULL UNIQUE,    -- 报告编号
    sample_id       BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    report_type     TINYINT,                         -- 1常规 2急诊 3微生物
    status          TINYINT DEFAULT 0,               -- 0草稿 1已审核 2已发布 3已修改
    auditor_id      BIGINT,                          -- 审核人
    audit_time      TIMESTAMP,                       -- 审核时间
    publisher_id    BIGINT,                          -- 发布人
    publish_time    TIMESTAMP,                       -- 发布时间
    report_doctor   VARCHAR(50),                     -- 报告医生
    checker         VARCHAR(50),                     -- 检验者
    pdf_path        VARCHAR(255),                    -- PDF 文件路径
    his_sync_status TINYINT DEFAULT 0,               -- HIS 同步状态
    his_sync_time   TIMESTAMP,                       -- HIS 同步时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sample_id) REFERENCES lis_sample(id),
    INDEX idx_sample (sample_id),
    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_publish_time (publish_time)
);
COMMENT ON TABLE lis_report IS '检验报告表';

-- 报告修改记录表 (留痕)
CREATE TABLE lis_report_modify_log (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL,
    item_id         BIGINT,                          -- 修改的项目
    old_value       TEXT,                            -- 修改前值
    new_value       TEXT,                            -- 修改后值
    modify_reason   VARCHAR(255),                    -- 修改原因
    operator_id     BIGINT,                          -- 操作人
    operator_name   VARCHAR(50),
    modify_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (report_id) REFERENCES lis_report(id),
    INDEX idx_report (report_id)
);
COMMENT ON TABLE lis_report_modify_log IS '报告修改记录表';
```

### 2.5 仪器与质控

```sql
-- 仪器字典表
CREATE TABLE lis_instrument (
    id              BIGSERIAL PRIMARY KEY,
    instrument_code VARCHAR(32) NOT NULL UNIQUE,    -- 仪器编码
    instrument_name VARCHAR(100) NOT NULL,           -- 仪器名称
    brand           VARCHAR(50),                     -- 品牌
    model           VARCHAR(50),                     -- 型号
    protocol_type   VARCHAR(20),                     -- 协议类型: ASTM/HL7/LIS01
    connect_type    VARCHAR(20),                     -- 连接方式: SERIAL/TCP
    serial_port     VARCHAR(20),                     -- 串口号 (SERIAL)
    baud_rate       INT,                             -- 波特率
    ip_address      VARCHAR(50),                     -- IP (TCP)
    port            INT,                             -- 端口 (TCP)
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    last_online_time TIMESTAMP,                      -- 最后在线时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_instrument IS '仪器字典表';

-- 仪器项目映射表
CREATE TABLE lis_instrument_item_map (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,                 -- LIS 项目 ID
    instrument_item_code VARCHAR(32),                -- 仪器项目代码
    instrument_item_name VARCHAR(100),               -- 仪器项目名称
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id),
    FOREIGN KEY (test_item_id) REFERENCES lis_test_item(id),
    UNIQUE KEY uk_instrument_item (instrument_id, test_item_id)
);
COMMENT ON TABLE lis_instrument_item_map IS '仪器项目映射表';

-- 质控品批号表
CREATE TABLE lis_qc_lot (
    id              BIGSERIAL PRIMARY KEY,
    lot_no          VARCHAR(32) NOT NULL,            -- 批号
    test_item_id    BIGINT NOT NULL,                 -- 检验项目
    instrument_id   BIGINT,                          -- 仪器
    target_value    DECIMAL(10,4),                   -- 靶值
    sd              DECIMAL(10,4),                   -- 标准差
    cv              DECIMAL(5,2),                    -- 变异系数
    level           TINYINT,                         -- 质控级别: 1低 2中 3高
    start_date      DATE,                            -- 启用日期
    end_date        DATE,                            -- 失效日期
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_item_lot (test_item_id, lot_no)
);
COMMENT ON TABLE lis_qc_lot IS '质控品批号表';

-- 质控记录表
CREATE TABLE lis_qc_record (
    id              BIGSERIAL PRIMARY KEY,
    qc_lot_id       BIGINT NOT NULL,
    test_item_id    BIGINT NOT NULL,
    instrument_id   BIGINT,
    measure_value   DECIMAL(10,4),                   -- 测量值
    measure_time    TIMESTAMP,                       -- 测量时间
    operator_id     BIGINT,
    z_score         DECIMAL(5,2),                    -- Z 分数
    rule_violation  VARCHAR(50),                     -- 违反规则 (如 1-3s)
    qc_result       TINYINT,                         -- 0失控 1在控
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (qc_lot_id) REFERENCES lis_qc_lot(id),
    INDEX idx_lot_time (qc_lot_id, measure_time)
);
COMMENT ON TABLE lis_qc_record IS '质控记录表';

-- 仪器维护模板表
CREATE TABLE lis_maintenance_template (
    id              BIGSERIAL PRIMARY KEY,
    template_code   VARCHAR(32) NOT NULL UNIQUE,    -- 模板编码
    template_name   VARCHAR(100) NOT NULL,           -- 模板名称
    instrument_type VARCHAR(50),                     -- 适用仪器类型
    cycle_type      VARCHAR(20),                     -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    status          TINYINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_maintenance_template IS '仪器维护模板表';

-- 仪器维护模板明细表
CREATE TABLE lis_maintenance_template_item (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    item_code       VARCHAR(32) NOT NULL,            -- 维护项编码
    item_name       VARCHAR(100) NOT NULL,           -- 维护项名称
    item_type       TINYINT,                         -- 1开关机 2清洗 3耗材 4质控 5其他
    expected_value  VARCHAR(100),                    -- 预期结果
    sort_order      INT DEFAULT 0,
    FOREIGN KEY (template_id) REFERENCES lis_maintenance_template(id),
    INDEX idx_template (template_id)
);
COMMENT ON TABLE lis_maintenance_template_item IS '仪器维护模板明细表';

-- 仪器维护记录表
CREATE TABLE lis_instrument_maintenance_log (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    template_id     BIGINT,                          -- 关联模板
    maintenance_date DATE NOT NULL,                  -- 维护日期
    maintenance_type VARCHAR(20),                    -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    operator_id     BIGINT,                          -- 执行人
    operator_name   VARCHAR(50),
    reviewer_id     BIGINT,                          -- 复核人
    reviewer_name   VARCHAR(50),
    result          TINYINT DEFAULT 0,               -- 0异常 1正常 2未执行
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id),
    INDEX idx_instrument_date (instrument_id, maintenance_date)
);
COMMENT ON TABLE lis_instrument_maintenance_log IS '仪器维护记录表';

-- 仪器维护记录明细表
CREATE TABLE lis_instrument_maintenance_log_item (
    id              BIGSERIAL PRIMARY KEY,
    log_id          BIGINT NOT NULL,
    item_code       VARCHAR(32),                     -- 维护项编码
    item_name       VARCHAR(100),                    -- 维护项名称
    expected_value  VARCHAR(100),                    -- 预期结果
    actual_value    VARCHAR(100),                    -- 实际结果
    result          TINYINT DEFAULT 0,               -- 0异常 1正常 2未执行
    abnormal_reason TEXT,
    FOREIGN KEY (log_id) REFERENCES lis_instrument_maintenance_log(id),
    INDEX idx_log (log_id)
);
COMMENT ON TABLE lis_instrument_maintenance_log_item IS '仪器维护记录明细表';

-- 仪器保养/维修/校准计划表
CREATE TABLE lis_instrument_maintenance_plan (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL,
    plan_type       VARCHAR(20),                     -- PM/REPAIR/CALIBRATION
    cycle_type      VARCHAR(20),                     -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    first_date      DATE,                            -- 首次日期
    next_date       DATE,                            -- 下次执行日期
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id),
    INDEX idx_instrument_next (instrument_id, next_date)
);
COMMENT ON TABLE lis_instrument_maintenance_plan IS '仪器保养/维修/校准计划表';

-- 仪器保养/维修/校准任务表
CREATE TABLE lis_instrument_maintenance_task (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL,
    instrument_id   BIGINT NOT NULL,
    plan_type       VARCHAR(20),                     -- PM/REPAIR/CALIBRATION
    scheduled_date  DATE,                            -- 计划日期
    finish_date     DATE,                            -- 完成日期
    operator_id     BIGINT,
    status          TINYINT DEFAULT 0,               -- 0待执行 1已完成 2逾期
    cost            DECIMAL(10,2),                   -- 费用
    content         TEXT,                            -- 内容
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id),
    INDEX idx_instrument_status (instrument_id, status)
);
COMMENT ON TABLE lis_instrument_maintenance_task IS '仪器保养/维修/校准任务表';
```

### 2.6-- 试剂字典表
CREATE TABLE lis_reagent (
    id              BIGSERIAL PRIMARY KEY,
    reagent_code    VARCHAR(32) NOT NULL UNIQUE,    -- 试剂编码
    reagent_name    VARCHAR(100) NOT NULL,           -- 试剂名称
    specification   VARCHAR(50),                     -- 规格
    manufacturer    VARCHAR(100),                    -- 生产厂家
    cas_no          VARCHAR(50),                     -- CAS 号
    storage_cond    VARCHAR(50),                     -- 储存条件
    unit            VARCHAR(20),                     -- 单位
    instrument_id   BIGINT,                          -- 适用仪器
    test_item_id    BIGINT,                          -- 关联项目
    safety_stock    DECIMAL(10,2),                   -- 安全库存
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id),
    INDEX idx_instrument (instrument_id)
);
COMMENT ON TABLE lis_reagent IS '试剂字典表';

-- 试剂库存表 (按批次管理)
CREATE TABLE lis_reagent_stock (
    id              BIGSERIAL PRIMARY KEY,
    reagent_id      BIGINT NOT NULL,
    lot_no          VARCHAR(32) NOT NULL,            -- 批号
    barcode         VARCHAR(64),                     -- 二维码/条码
    expiry_date     DATE NOT NULL,                   -- 效期
    quantity        DECIMAL(10,2),                   -- 当前数量
    location        VARCHAR(100),                    -- 存放位置
    status          TINYINT DEFAULT 1,               -- 0过期锁定 1可用 2已耗尽
    in_stock_time   TIMESTAMP,                       -- 入库时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reagent_id) REFERENCES lis_reagent(id),
    UNIQUE KEY uk_reagent_lot (reagent_id, lot_no),
    INDEX idx_expiry (expiry_date),
    INDEX idx_status (status)
);
COMMENT ON TABLE lis_reagent_stock IS '试剂库存表';

-- 试剂出入库记录表
CREATE TABLE lis_reagent_stock_record (
    id              BIGSERIAL PRIMARY KEY,
    stock_id        BIGINT NOT NULL,
    reagent_id      BIGINT NOT NULL,
    record_type     TINYINT,                         -- 1入库 2出库 3报损 4退库 5盘点
    quantity        DECIMAL(10,2),                   -- 正数入库/负数出库
    before_qty      DECIMAL(10,2),
    after_qty       DECIMAL(10,2),
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    source_no       VARCHAR(32),                     -- 采购单号/领用单号
    related_id      BIGINT,                          -- 关联业务 ID (结果/质控)
    related_type    VARCHAR(20),                     -- RESULT / QC
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES lis_reagent_stock(id),
    INDEX idx_reagent (reagent_id),
    INDEX idx_create_time (create_time)
);
COMMENT ON TABLE lis_reagent_stock_record IS '试剂出入库记录表';

-- 微生物报告表
CREATE TABLE lis_micro_report (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    report_no       VARCHAR(32) NOT NULL,            -- 报告编号
    report_level    VARCHAR(20) NOT NULL,            -- LEVEL_1/LEVEL_2/LEVEL_3/FINAL
    report_status   TINYINT DEFAULT 0,               -- 0草稿 1已审核 2已发布 3已修改
    parent_report_id BIGINT,                         -- 上级报告 ID
    gram_stain_result TEXT,                          -- 涂片结果
    culture_result   TEXT,                           -- 培养结果
    auditor_id      BIGINT,
    audit_time      TIMESTAMP,
    publisher_id    BIGINT,
    publish_time    TIMESTAMP,
    his_sync_status TINYINT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sample_id) REFERENCES lis_sample(id),
    INDEX idx_sample (sample_id),
    INDEX idx_level (report_level),
    INDEX idx_status (report_status)
);
COMMENT ON TABLE lis_micro_report IS '微生物报告表';

-- 微生物报告检出菌表
CREATE TABLE lis_micro_organism (
    id              BIGSERIAL PRIMARY KEY,
    micro_report_id BIGINT NOT NULL,
    organism_code   VARCHAR(32),                     -- 菌株编码
    organism_name   VARCHAR(100) NOT NULL,           -- 菌株名称
    quantity        VARCHAR(20),                     -- 菌量 1+/2+/3+/4+
    is_pathogen     TINYINT DEFAULT 0,               -- 是否致病菌
    sort_order      INT DEFAULT 0,
    FOREIGN KEY (micro_report_id) REFERENCES lis_micro_report(id),
    INDEX idx_report (micro_report_id)
);
COMMENT ON TABLE lis_micro_organism IS '微生物报告检出菌表';

-- 抗生素字典表
CREATE TABLE lis_antibiotic (
    id              BIGSERIAL PRIMARY KEY,
    antibiotic_code VARCHAR(32) NOT NULL UNIQUE,    -- 抗生素编码
    antibiotic_name VARCHAR(100) NOT NULL,           -- 抗生素名称
    category        VARCHAR(50),                     -- 分类: β-内酰胺类/氨基糖苷类...
    unit            VARCHAR(20),                     -- 单位
    status          TINYINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_antibiotic IS '抗生素字典表';

-- 药敏结果表
CREATE TABLE lis_micro_sensitivity (
    id              BIGSERIAL PRIMARY KEY,
    organism_id     BIGINT NOT NULL,
    antibiotic_id   BIGINT NOT NULL,
    antibiotic_code VARCHAR(32),
    antibiotic_name VARCHAR(100),
    mic_value       VARCHAR(20),                     -- MIC 值
    kb_value        VARCHAR(20),                     -- 抑菌圈直径
    result          VARCHAR(10),                     -- S/I/R
    breakpoint_version VARCHAR(30),                  -- CLSI 2024 / EUCAST 2024
    method          VARCHAR(20),                     -- 检测方法
    instrument_id   BIGINT,                          -- 药敏仪
    reagent_lot_id  BIGINT,                          -- 试剂批号
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (organism_id) REFERENCES lis_micro_organism(id),
    FOREIGN KEY (antibiotic_id) REFERENCES lis_antibiotic(id),
    INDEX idx_organism (organism_id),
    INDEX idx_antibiotic (antibiotic_id)
);
COMMENT ON TABLE lis_micro_sensitivity IS '药敏结果表';

-- 微生物报告打印模板配置表
CREATE TABLE lis_micro_report_print_config (
    id              BIGSERIAL PRIMARY KEY,
    config_name     VARCHAR(100),                    -- 配置名称
    show_mic        TINYINT DEFAULT 1,
    show_kb         TINYINT DEFAULT 1,
    show_breakpoint TINYINT DEFAULT 0,
    show_method     TINYINT DEFAULT 0,
    hidden_antibiotics TEXT,                         -- JSON 数组: 隐藏的抗生素
    order_by_categories TEXT,                        -- JSON 数组: 排序
    breakpoint_version VARCHAR(30),                  -- 默认折点版本
    paper_size      VARCHAR(10) DEFAULT 'A4',        -- A4/A5/B5
    is_default      TINYINT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_micro_report_print_config IS '微生物报告打印模板配置表';

-- 血培养记录表
CREATE TABLE lis_blood_culture (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT NOT NULL,
    bottle_no       VARCHAR(32) NOT NULL,            -- 培养瓶号
    medium_type     VARCHAR(50),                     -- 培养基类型
    instrument_id   BIGINT,                          -- 血培养仪
    inoculation_time TIMESTAMP,                      -- 接种时间
    alarm_time      TIMESTAMP,                       -- 阳性报警时间
    final_result    VARCHAR(20),                     -- POSITIVE/NEGATIVE/CONTAMINATED
    final_time      TIMESTAMP,                       -- 最终报告时间
    ttp_hours       DECIMAL(5,1),                    -- 报警时间 - 接种时间
    operator_id     BIGINT,
    status          TINYINT DEFAULT 0,               -- 0培养中 1阳性 2阴性 3污染
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sample_id) REFERENCES lis_sample(id),
    INDEX idx_sample (sample_id),
    INDEX idx_bottle (bottle_no)
);
COMMENT ON TABLE lis_blood_culture IS '血培养记录表';

-- 血培养转种记录表
CREATE TABLE lis_blood_culture_subculture (
    id              BIGSERIAL PRIMARY KEY,
    blood_culture_id BIGINT NOT NULL,
    subculture_time TIMESTAMP,                       -- 转种时间
    medium          VARCHAR(50),                     -- 转种培养基
    incubator_no    VARCHAR(32),                     -- 培养箱号
    operator_id     BIGINT,
    result          VARCHAR(255),                    -- 转种结果
    remark          TEXT,
    FOREIGN KEY (blood_culture_id) REFERENCES lis_blood_culture(id),
    INDEX idx_blood_culture (blood_culture_id)
);
COMMENT ON TABLE lis_blood_culture_subculture IS '血培养转种记录表';

-- 特殊耐药标记/危急值规则表
CREATE TABLE lis_resistance_rule (
    id              BIGSERIAL PRIMARY KEY,
    organism_code   VARCHAR(32),                     -- 菌株编码
    antibiotic_codes TEXT,                           -- JSON 数组: 耐药抗生素
    rule_name       VARCHAR(100),                    -- 规则名称: MRSA/CRE/CRAB
    is_critical     TINYINT DEFAULT 1,               -- 是否触发危急值
    alert_message   VARCHAR(255),                    -- 通知文案
    status          TINYINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_resistance_rule IS '特殊耐药标记/危急值规则表';

### 2.7 系统管理

```sql
-- 用户表
CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(100) NOT NULL,           -- BCrypt 加密
    real_name       VARCHAR(50),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    department_id   BIGINT,
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    last_login_time TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_user IS '系统用户表';

-- 角色表
CREATE TABLE sys_role (
    id              BIGSERIAL PRIMARY KEY,
    role_code       VARCHAR(50) NOT NULL UNIQUE,
    role_name       VARCHAR(50) NOT NULL,
    description     VARCHAR(255),
    status          TINYINT DEFAULT 1,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE sys_permission (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       BIGINT DEFAULT 0,
    perm_code       VARCHAR(100) NOT NULL UNIQUE,
    perm_name       VARCHAR(100) NOT NULL,
    perm_type       TINYINT,                         -- 1菜单 2按钮 3接口
    path            VARCHAR(255),                    -- 路由路径
    component       VARCHAR(255),                    -- 前端组件
    icon            VARCHAR(50),
    sort_order      INT DEFAULT 0,
    status          TINYINT DEFAULT 1
);

-- 用户角色关联表
CREATE TABLE sys_user_role (
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 角色权限关联表
CREATE TABLE sys_role_permission (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 操作日志表
CREATE TABLE sys_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(50),
    module          VARCHAR(50),                     -- 模块
    operation       VARCHAR(100),                    -- 操作描述
    method          VARCHAR(200),                    -- 请求方法
    params          TEXT,                            -- 请求参数
    result          TEXT,                            -- 返回结果
    ip              VARCHAR(50),                     -- IP 地址
    cost_time       INT,                             -- 耗时(ms)
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_create_time (create_time)
);
COMMENT ON TABLE sys_audit_log IS '操作日志表';

-- 小组/科室表
CREATE TABLE lis_lab_group (
    id              BIGSERIAL PRIMARY KEY,
    group_code      VARCHAR(32) NOT NULL UNIQUE,    -- 小组编码
    group_name      VARCHAR(100) NOT NULL,           -- 小组名称
    parent_id       BIGINT DEFAULT 0,                -- 父级小组
    leader_id       BIGINT,                          -- 组长 (staff_id)
    leader_name     VARCHAR(50),
    location        VARCHAR(100),                    -- 工作地点
    sort_order      INT DEFAULT 0,
    status          TINYINT DEFAULT 1,               -- 0停用 1启用
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
);
COMMENT ON TABLE lis_lab_group IS '小组/科室表';

-- 小组与仪器关联表
CREATE TABLE lis_group_instrument (
    group_id        BIGINT NOT NULL,
    instrument_id   BIGINT NOT NULL,
    PRIMARY KEY (group_id, instrument_id),
    FOREIGN KEY (group_id) REFERENCES lis_lab_group(id),
    FOREIGN KEY (instrument_id) REFERENCES lis_instrument(id)
);
COMMENT ON TABLE lis_group_instrument IS '小组与仪器关联表';

-- 人员档案表
CREATE TABLE lis_staff (
    id              BIGSERIAL PRIMARY KEY,
    staff_no        VARCHAR(32) NOT NULL UNIQUE,    -- 工号
    name            VARCHAR(50) NOT NULL,
    phone           VARCHAR(32),                     -- 手机号
    email           VARCHAR(100),
    group_id        BIGINT,                          -- 所属小组
    user_id         BIGINT,                          -- 关联系统账号
    education       VARCHAR(20),                     -- 学历
    major           VARCHAR(50),                     -- 专业
    entry_date      DATE,                            -- 入职日期
    leave_date      DATE,                            -- 离职日期
    id_card         VARCHAR(64),                     -- 身份证号
    signature_image VARCHAR(255),                    -- 电子签名图片
    status          TINYINT DEFAULT 1,               -- 0离职 1在职 2试用期
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES lis_lab_group(id),
    INDEX idx_group (group_id),
    INDEX idx_user (user_id)
);
COMMENT ON TABLE lis_staff IS '人员档案表';

-- 职称字典表
CREATE TABLE lis_title_dict (
    id              BIGSERIAL PRIMARY KEY,
    title_code      VARCHAR(32) NOT NULL UNIQUE,    -- 职称编码
    title_name      VARCHAR(50) NOT NULL,            -- 职称名称
    title_level     TINYINT,                         -- 职称级别
    sort_order      INT DEFAULT 0,
    status          TINYINT DEFAULT 1
);
COMMENT ON TABLE lis_title_dict IS '职称字典表';

-- 人员职称记录表
CREATE TABLE lis_staff_title (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    title_code      VARCHAR(32) NOT NULL,
    title_name      VARCHAR(50),
    acquire_date    DATE,                            -- 获得日期
    expiry_date     DATE,                            -- 有效期
    certificate_no  VARCHAR(50),                     -- 证书编号
    attachment_url  VARCHAR(255),                    -- 证书附件
    is_current      TINYINT DEFAULT 0,               -- 是否当前职称
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES lis_staff(id),
    INDEX idx_staff (staff_id)
);
COMMENT ON TABLE lis_staff_title IS '人员职称记录表';

-- 资质证书表
CREATE TABLE lis_staff_certificate (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    cert_type       VARCHAR(50) NOT NULL,            -- 资质类型
    cert_name       VARCHAR(100) NOT NULL,           -- 证书名称
    cert_no         VARCHAR(50),                     -- 证书编号
    issue_date      DATE,                            -- 发证日期
    expiry_date     DATE,                            -- 到期日期
    issue_org       VARCHAR(100),                    -- 发证机构
    attachment_url  VARCHAR(255),                    -- 附件
    alert_days      INT DEFAULT 30,                  -- 提前预警天数
    status          TINYINT DEFAULT 1,               -- 0过期 1有效 2作废
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES lis_staff(id),
    INDEX idx_staff (staff_id),
    INDEX idx_expiry (expiry_date)
);
COMMENT ON TABLE lis_staff_certificate IS '资质证书表';

-- 岗位授权表
CREATE TABLE lis_staff_authorization (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    auth_type       VARCHAR(50) NOT NULL,            -- 授权类型
    auth_scope      VARCHAR(255),                    -- 授权范围
    start_date      DATE,                            -- 授权开始
    end_date        DATE,                            -- 授权结束
    grantor_id      BIGINT,                          -- 授权人
    grantor_name    VARCHAR(50),
    attachment_url  VARCHAR(255),
    status          TINYINT DEFAULT 1,               -- 0失效 1有效
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES lis_staff(id),
    INDEX idx_staff (staff_id)
);
COMMENT ON TABLE lis_staff_authorization IS '岗位授权表';

-- 培训记录表
CREATE TABLE lis_staff_training (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    training_name   VARCHAR(100) NOT NULL,           -- 培训名称
    training_type   VARCHAR(50),                     -- 培训类型
    training_date   DATE,                            -- 培训日期
    trainer         VARCHAR(50),                     -- 培训人
    result          VARCHAR(20),                     -- 合格/不合格
    attachment_url  VARCHAR(255),
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES lis_staff(id),
    INDEX idx_staff (staff_id)
);
COMMENT ON TABLE lis_staff_training IS '培训记录表';

-- 文档资料表
CREATE TABLE lis_lab_document (
    id              BIGSERIAL PRIMARY KEY,
    doc_code        VARCHAR(32) NOT NULL,            -- 文档编码
    doc_name        VARCHAR(100) NOT NULL,           -- 文档名称
    doc_type        VARCHAR(50),                     -- SOP/MANUAL/PROCEDURE/FORM
    version         VARCHAR(20) DEFAULT '1.0',       -- 版本号
    group_id        BIGINT,                          -- 适用小组
    file_url        VARCHAR(255),                    -- 文件路径
    uploader_id     BIGINT,
    upload_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status          TINYINT DEFAULT 1,               -- 0作废 1现行 2草稿
    UNIQUE KEY uk_doc_version (doc_code, version),
    INDEX idx_doc_code (doc_code),
    INDEX idx_group (group_id)
);
COMMENT ON TABLE lis_lab_document IS '文档资料表';

-- 排班表
CREATE TABLE lis_staff_schedule (
    id              BIGSERIAL PRIMARY KEY,
    staff_id        BIGINT NOT NULL,
    schedule_date   DATE NOT NULL,                   -- 排班日期
    shift_type      VARCHAR(20),                     -- 班次: 白班/夜班/休息
    duty_type       VARCHAR(20),                     -- 值班类型
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES lis_staff(id),
    UNIQUE KEY uk_staff_date (staff_id, schedule_date)
);
COMMENT ON TABLE lis_staff_schedule IS '排班表';
```

---

## 三、关键设计说明

### 3.1 分表策略

标本和结果表数据量大 (日均 5000 标本，年约 180 万)，建议按月分表：

```sql
-- 自动建表示例 (XXL-JOB 定时任务每月执行)
CREATE TABLE lis_sample_202608 ( ) INHERITS (lis_sample);
CREATE TABLE lis_result_202608 ( ) INHERITS (lis_result);

-- 查询时路由 (MyBatis 拦截器自动拼接表名后缀)
SELECT * FROM lis_sample_202608 WHERE create_time >= '2026-08-01';
```

### 3.2 索引优化建议

| 查询场景 | 索引策略 |
|---------|---------|
| 扫码核收 (条码查询) | `sample_barcode` 唯一索引 |
| 工作列表 (状态过滤) | `(status, receive_time)` 联合索引 |
| 患者历史结果 | `(patient_id, test_item_id, test_time)` 联合索引 |
| 急诊优先处理 | `(is_urgent, status)` 联合索引 |
| 报告查询 | `(patient_id, publish_time)` 联合索引 |
| 试剂效期预警 | `(expiry_date, status)` 联合索引 |
| 微生物报告查询 | `(sample_id, report_level)` 联合索引 |
| 血培养瓶号查询 | `bottle_no` 唯一索引 |
| 仪器维护记录 | `(instrument_id, maintenance_date)` 联合索引 |
| 人员查询 | `(staff_no, status)` 唯一索引 |
| 资质到期预警 | `(expiry_date, alert_days)` 联合索引 |
| 文档版本查询 | `(doc_code, version)` 联合索引 |

### 3.3 敏感数据加密

患者身份证号、手机号采用 AES-256 加密存储，查询时使用「盲索引」方案：

```sql
-- 同时存储加密值和哈希索引
id_card_enc     VARCHAR(255),    -- AES 加密后的密文
id_card_hash    CHAR(64),        -- SHA-256 哈希 (用于等值查询)

-- 查询时
SELECT * FROM lis_patient WHERE id_card_hash = SHA2('查询值', 256);
```

---

## 四、初始化数据

### 4.1 默认角色

```sql
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN',       '系统管理员', '拥有所有权限'),
('LAB_DIRECTOR','检验科主任', '审核发布、统计查看'),
('GROUP_LEADER','小组组长',   '小组管理、任务分配'),
('TECHNICIAN',  '检验技师',   '标本核收、结果录入'),
('REPORT_VIEWER','报告查看员', '仅查看报告'),
('HIS_INTEGRATE','HIS对接',    '系统对接专用账号'),
('HR_ADMIN',    '人事管理员', '人员档案、资质管理');
```

### 4.4 小组与职称字典示例

```sql
-- 小组/科室字典
INSERT INTO lis_lab_group (group_code, group_name, parent_id, sort_order) VALUES
('BIOCHEM',   '生化组',     0, 1),
('IMMUNE',    '免疫组',     0, 2),
('MICRO',     '微生物组',   0, 3),
('BLOOD',     '血液体液组', 0, 4),
('MOLECULAR', '分子诊断组', 0, 5),
('QC',        '质控组',     0, 6);

-- 职称字典
INSERT INTO lis_title_dict (title_code, title_name, title_level, sort_order) VALUES
('TECH_JUNIOR',    '检验技士',      1, 1),
('TECHNICIAN',     '检验技师',      2, 2),
('SENIOR_TECH',    '主管技师',      3, 3),
('ASSOC_CHIEF_TECH','副主任技师',   4, 4),
('CHIEF_TECH',     '主任技师',      5, 5);

-- 资质类型字典
INSERT INTO sys_dict_data (dict_type, dict_code, dict_name) VALUES
('CERT_TYPE', 'QUAL_PRACTICE', '执业资格证'),
('CERT_TYPE', 'QUAL_PCR',      'PCR 上岗证'),
('CERT_TYPE', 'QUAL_HIV',      'HIV 检测证'),
('CERT_TYPE', 'QUAL_BLOOD_TRANS', '输血证'),
('CERT_TYPE', 'QUAL_SPECIAL_EQUIP', '特种设备证');
```

### 4.2 默认检验项目示例

```sql
INSERT INTO lis_test_item (item_code, item_name, short_name, category, unit) VALUES
('WBC',  '白细胞计数',       'WBC',  '血常规', '10^9/L'),
('RBC',  '红细胞计数',       'RBC',  '血常规', '10^12/L'),
('HGB',  '血红蛋白',         'HGB',  '血常规', 'g/L'),
('PLT',  '血小板计数',       'PLT',  '血常规', '10^9/L'),
('GLU',  '葡萄糖',           'GLU',  '生化',   'mmol/L'),
('ALT',  '丙氨酸氨基转移酶', 'ALT',  '生化',   'U/L'),
('AST',  '天门冬氨酸氨基转移酶','AST','生化',   'U/L'),
('TP',   '总蛋白',           'TP',   '生化',   'g/L'),

-- 微生物常用项目
('CULTURE', '细菌培养+鉴定+药敏', 'CULTURE', '微生物', NULL),
('GRAM',    '涂片革兰染色',     'GRAM',   '微生物', NULL),
('BC',      '血培养',           'BC',     '微生物', NULL);
```

### 4.3 微生物字典示例

```sql
-- 抗生素字典
INSERT INTO lis_antibiotic (antibiotic_code, antibiotic_name, category) VALUES
('PEN',   '青霉素',        'β-内酰胺类'),
('AMP',   '氨苄西林',      'β-内酰胺类'),
('OXA',   '苯唑西林',      'β-内酰胺类'),
('GEN',   '庆大霉素',      '氨基糖苷类'),
('CIP',   '环丙沙星',      '喹诺酮类'),
('VAN',   '万古霉素',      '糖肽类'),
('IPM',   '亚胺培南',      '碳青霉烯类'),
('AK',    '阿米卡星',      '氨基糖苷类'),
('CAZ',   '头孢他啶',      '头孢菌素类'),
('SXT',   '复方新诺明',    '磺胺类');

-- 特殊耐药规则
INSERT INTO lis_resistance_rule (organism_code, antibiotic_codes, rule_name, alert_message) VALUES
('STAUR', '["OXA"]', 'MRSA', '检出耐甲氧西林金黄色葡萄球菌(MRSA)，请按院感流程处理'),
('ECOLI', '["IPM"]', 'CRE',  '检出碳青霉烯耐药肠杆菌科细菌(CRE)，请按院感流程处理'),
('ACIBA', '["IPM"]', 'CRAB', '检出碳青霉烯耐药鲍曼不动杆菌(CRAB)，请按院感流程处理');
```
