-- ============================================================
-- 01-schema-migration.sql
-- 原 DatabaseSchemaInitializer / DatabaseMigrationRunner / SampleTableInitializer
-- 只做 DDL（建表/加列）+ 少量基础字典种子。全部幂等，可安全重跑。
-- 执行: psql -f 01-schema-migration.sql
-- ============================================================

-- ---------- 迁移追踪表（可选，保留用于记录） ----------
CREATE TABLE IF NOT EXISTS lis_schema_migrations (
    id SERIAL PRIMARY KEY,
    migration_name VARCHAR(100) NOT NULL UNIQUE,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    success SMALLINT DEFAULT 1
);

-- ---------- lis_instrument 扩展字段 ----------
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS serial_no VARCHAR(64);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS install_date DATE;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS warranty_expiry DATE;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS department VARCHAR(50);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS work_status SMALLINT DEFAULT 1;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS channel_type VARCHAR(20);
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

-- ---------- lis_result ----------
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);

-- ---------- 试剂盘点 ----------
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id BIGSERIAL PRIMARY KEY, check_no VARCHAR(32) NOT NULL, warehouse VARCHAR(100),
    check_date DATE NOT NULL, status SMALLINT DEFAULT 0, operator_id BIGINT,
    operator_name VARCHAR(50), remark TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id BIGSERIAL PRIMARY KEY, check_id BIGINT NOT NULL, stock_id BIGINT,
    reagent_id BIGINT NOT NULL, lot_no VARCHAR(32), book_qty DECIMAL(10,2) NOT NULL,
    actual_qty DECIMAL(10,2), diff_qty DECIMAL(10,2), remark TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 仪器校准 / 维修 ----------
CREATE TABLE IF NOT EXISTS lis_instrument_calibration (
    id BIGSERIAL PRIMARY KEY, instrument_id BIGINT NOT NULL, calibrate_date DATE,
    result VARCHAR(20), operator_id BIGINT, operator_name VARCHAR(50),
    remark TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_instrument_repair (
    id BIGSERIAL PRIMARY KEY, instrument_id BIGINT NOT NULL, repair_date DATE,
    repair_content TEXT, cost DECIMAL(10,2), operator_id BIGINT,
    operator_name VARCHAR(50), remark TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 患者 / 就诊 / 医嘱 ----------
CREATE TABLE IF NOT EXISTS lis_patient (
    id BIGSERIAL PRIMARY KEY, patient_no VARCHAR(32), name VARCHAR(50) NOT NULL,
    gender SMALLINT, age INTEGER, id_card VARCHAR(18), id_card_hash VARCHAR(64),
    phone VARCHAR(20), address VARCHAR(200), remark TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_visit (
    id BIGSERIAL PRIMARY KEY, visit_no VARCHAR(32) NOT NULL, patient_id BIGINT,
    visit_type SMALLINT DEFAULT 1, department VARCHAR(50), doctor VARCHAR(50),
    clinical_diag VARCHAR(200), visit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_id BIGINT;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS collection_method_name VARCHAR(100);
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS order_type SMALLINT;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 0;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS apply_time TIMESTAMP;
ALTER TABLE lis_lab_order ADD COLUMN IF NOT EXISTS receive_time TIMESTAMP;

-- ---------- lis_sample 扩展字段 ----------
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS order_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS patient_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS department VARCHAR(50);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sample_barcode VARCHAR(50);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receive_time TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS sample_no VARCHAR(32);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver VARCHAR(64);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS receiver_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS collector_id BIGINT;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS is_urgent SMALLINT DEFAULT 0;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS container_type VARCHAR(30);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS deliver_person VARCHAR(50);
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE lis_sample ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE lis_patient ADD COLUMN IF NOT EXISTS is_test SMALLINT DEFAULT 0;

-- ---------- 标本-申请项目 中间表 (跨申请合管) ----------
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

-- ---------- 条码规则表 + 默认规则 ----------
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

-- ---------- lis_report 扩展字段 ----------
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS patient_name VARCHAR(50);
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS patient_gender SMALLINT;
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS patient_age INTEGER;
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS his_sync_status SMALLINT DEFAULT 0;
ALTER TABLE lis_report ADD COLUMN IF NOT EXISTS his_sync_time TIMESTAMP;

-- ---------- QC 规则表 ----------
CREATE TABLE IF NOT EXISTS lis_qc_rule (
    id BIGSERIAL PRIMARY KEY, rule_code VARCHAR(20) NOT NULL UNIQUE,
    rule_name VARCHAR(50) NOT NULL, rule_type VARCHAR(20) DEFAULT 'REJECT',
    enabled SMALLINT DEFAULT 1, description VARCHAR(200),
    deleted SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO lis_qc_rule (rule_code, rule_name, rule_type, enabled, description) VALUES
('1-2s','1-2s警告规则','WARNING',1,'1个质控点超出均值±2SD'),
('1-3s','1-3s失控规则','REJECT',1,'1个质控点超出均值±3SD'),
('2-2s','2-2s失控规则','REJECT',1,'连续2个质控点同侧超出均值±2SD'),
('R-4s','R-4s失控规则','REJECT',1,'连续2个质控点极差超过4SD'),
('4-1s','4-1s警告规则','WARNING',1,'连续4个质控点同侧超出均值±1SD'),
('10x','10x警告规则','WARNING',1,'连续10个质控点在均值同侧')
ON CONFLICT (rule_code) DO NOTHING;

-- ---------- QC 表软删除列 ----------
ALTER TABLE lis_qc_lot ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
UPDATE lis_qc_lot SET deleted = 0 WHERE deleted IS NULL;
ALTER TABLE lis_qc_record ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
UPDATE lis_qc_record SET deleted = 0 WHERE deleted IS NULL;
ALTER TABLE lis_qc_alert ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
UPDATE lis_qc_alert SET deleted = 0 WHERE deleted IS NULL;

-- Z-score 精度
ALTER TABLE lis_qc_record ALTER COLUMN z_score TYPE DECIMAL(12,6) USING z_score::DECIMAL(12,6);
ALTER TABLE lis_qc_record ALTER COLUMN measure_value TYPE DECIMAL(14,4) USING measure_value::DECIMAL(14,4);

-- QC 预警表
CREATE TABLE IF NOT EXISTS lis_qc_alert (
    id BIGSERIAL PRIMARY KEY, alert_type VARCHAR(30), lot_id BIGINT,
    test_item_id BIGINT, instrument_id BIGINT, last_measure_value DECIMAL(10,4),
    last_z_score DECIMAL(5,2), message VARCHAR(255), status VARCHAR(20) DEFAULT 'PENDING',
    alert_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_time ON lis_qc_alert (alert_time);
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_status ON lis_qc_alert (status);

-- ---------- 采集方式字典 ----------
CREATE TABLE IF NOT EXISTS lis_collection_method (
    id BIGINT PRIMARY KEY, method_code VARCHAR(32) NOT NULL, method_name VARCHAR(64) NOT NULL,
    description VARCHAR(255), status SMALLINT DEFAULT 1, sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_collection_method_code ON lis_collection_method (method_code);
INSERT INTO lis_collection_method (id, method_code, method_name, description, status, sort_order) VALUES
(1001, 'OUTPATIENT', '门诊采集', '门诊患者标本采集', 1, 1),
(1002, 'INPATIENT', '病房采集', '住院患者病房内标本采集', 1, 2),
(1003, 'EMERGENCY', '急诊采集', '急诊患者标本采集', 1, 3),
(1004, 'DEPARTMENT', '科室采集', '检验科内采集', 1, 4),
(1005, 'PATIENT_SELF', '病人自采', '患者自行采集标本', 1, 5),
(1006, 'PHYSICAL_EXAM', '体检采集', '体检中心标本采集', 1, 6),
(1007, 'HOME', '上门采集', '上门服务标本采集', 1, 7)
ON CONFLICT (id) DO NOTHING;
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS collection_method_id BIGINT;
ALTER TABLE lis_test_item ADD COLUMN IF NOT EXISTS report_template_id BIGINT;

-- ---------- 仪器-标本类型映射 ----------
CREATE TABLE IF NOT EXISTS lis_instrument_specimen_map (
    id BIGINT PRIMARY KEY, instrument_id BIGINT NOT NULL, specimen_type_id BIGINT,
    specimen_type_name VARCHAR(64), specimen_type_code VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_lis_instrument_specimen_map_instr ON lis_instrument_specimen_map (instrument_id);

-- ---------- 微生物质控 ----------
CREATE TABLE IF NOT EXISTS lis_micro_qc_record (
    id BIGSERIAL PRIMARY KEY, item_type SMALLINT NOT NULL, item_code VARCHAR(64),
    item_name VARCHAR(128) NOT NULL, qc_lot_no VARCHAR(64), control_strain VARCHAR(64),
    expected_result VARCHAR(64), actual_result VARCHAR(64), result VARCHAR(16) DEFAULT 'PASS',
    operator_id BIGINT, operator_name VARCHAR(64), measure_time TIMESTAMP,
    remark VARCHAR(255), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_type ON lis_micro_qc_record (item_type);
CREATE INDEX IF NOT EXISTS idx_lis_micro_qc_record_time ON lis_micro_qc_record (measure_time);

-- ---------- TAT / 消息 / 传染病 / 培养相关 ----------
CREATE TABLE IF NOT EXISTS lis_tat_rule (
    id BIGSERIAL PRIMARY KEY, specimen_type VARCHAR(50) NOT NULL, test_item_id BIGINT NOT NULL,
    test_item_name VARCHAR(100), urgent_timeout INT NOT NULL DEFAULT 60, normal_timeout INT NOT NULL DEFAULT 120,
    enabled SMALLINT DEFAULT 0, remark VARCHAR(200),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_tat_monitor (
    id BIGSERIAL PRIMARY KEY, sample_id BIGINT NOT NULL, sample_barcode VARCHAR(50),
    patient_name VARCHAR(50), test_item_id BIGINT, test_item_name VARCHAR(100),
    specimen_type VARCHAR(50), is_urgent SMALLINT DEFAULT 0, sign_off_time TIMESTAMP,
    timeout_minutes INT, elapsed_minutes INT, is_overdue SMALLINT DEFAULT 0,
    operator_id BIGINT, operator_name VARCHAR(50), department VARCHAR(50),
    notified SMALLINT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_message (
    id BIGSERIAL PRIMARY KEY, message_type VARCHAR(20) NOT NULL, title VARCHAR(120),
    content TEXT, user_id BIGINT, department_id BIGINT, business_id BIGINT,
    sample_id BIGINT, test_item_id BIGINT, is_read SMALLINT DEFAULT 0,
    read_time TIMESTAMP, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_infectious_item (
    id BIGSERIAL PRIMARY KEY, test_item_id BIGINT NOT NULL, test_item_name VARCHAR(100),
    positive_text VARCHAR(50) DEFAULT '阳性', enabled SMALLINT DEFAULT 1,
    remark VARCHAR(200), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 仪器「无主标本」结果表 ----------
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

-- ---------- 资质证书附件 ----------
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

-- ---------- 耐药规则表扩展 (统一可配置规则引擎, 含原专家规则) ----------
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS bacteria_codes TEXT;
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS rule_type VARCHAR(30);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS result VARCHAR(5);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS match_mode VARCHAR(20) DEFAULT 'ANY_RESISTANT';
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE lis_resistance_rule ADD COLUMN IF NOT EXISTS is_builtin SMALLINT DEFAULT 0;

-- ---------- HIS 检验申请接收接口 ----------
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

-- ---------- HIS 新标准服务 S4009/S3103 回写进度表 ----------
CREATE TABLE IF NOT EXISTS lis_his_sample_status_push (
    sample_id           BIGINT PRIMARY KEY,
    last_status         SMALLINT,
    last_pushed_status  SMALLINT,
    retry_count         INT DEFAULT 0,
    error_msg           VARCHAR(500),
    last_push_time      TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_his_critical_push (
    result_id       BIGINT PRIMARY KEY,
    critical_id     VARCHAR(64),
    push_status     SMALLINT DEFAULT 0,
    retry_count     INT DEFAULT 0,
    error_msg       VARCHAR(500),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- HIS 接口调用日志（前台 HIS接口日志 页面可查） ----------
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