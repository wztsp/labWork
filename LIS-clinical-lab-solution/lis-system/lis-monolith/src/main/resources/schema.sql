-- 数据库 Schema 迁移脚本 (Spring Boot 启动时自动执行)

-- lis_instrument 表新增字段
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS serial_no VARCHAR(64);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS install_date DATE;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS warranty_expiry DATE;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS department VARCHAR(50);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS work_status SMALLINT DEFAULT 1;
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS channel_type VARCHAR(20);

-- lis_instrument 本地文件采集配置 (connect_type=FILE)
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_path VARCHAR(255);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_format VARCHAR(20);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS file_pattern VARCHAR(100);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS processed_dir VARCHAR(255);
ALTER TABLE lis_instrument ADD COLUMN IF NOT EXISTS poll_interval INT DEFAULT 30;

-- lis_instrument 数据库采集配置 (connect_type=DB)
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

-- lis_result 表新增字段
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);

-- 创建试剂库存盘点表
CREATE TABLE IF NOT EXISTS lis_reagent_stock_check (
    id              BIGSERIAL PRIMARY KEY,
    check_no        VARCHAR(32) NOT NULL,
    warehouse       VARCHAR(100),
    check_date      DATE NOT NULL,
    status          SMALLINT DEFAULT 0,
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lis_reagent_stock_check_item (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL,
    stock_id        BIGINT,
    reagent_id      BIGINT NOT NULL,
    lot_no          VARCHAR(32),
    book_qty        DECIMAL(10,2) NOT NULL,
    actual_qty      DECIMAL(10,2),
    diff_qty        DECIMAL(10,2),
    remark          TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 修正未来日期的报告/标本/申请/就诊数据日期 (原数据 2026-08-10 -> 调整为 2026-08-01, 使其落入近30天查询范围)
-- 每次启动重新计算: 基于当前日期将8-10日偏移调整为相对当前日期-2天/-7天,确保始终可查询
DO $$
DECLARE
    v_base_date DATE := CURRENT_DATE;
    v_old_date DATE := DATE '2026-08-10';
    -- 目标日期: 对 id=200->-2天, 201->-3天, 202->-6天, 203->-7天
    v_shift_days INT;
    v_rec RECORD;
BEGIN
    -- 仅当存在 2026-08-10 日期数据时执行更新,避免重复偏移
    IF EXISTS (SELECT 1 FROM lis_report WHERE DATE(create_time) = v_old_date) THEN
        -- id=200 -> 新日期=基准日前2天
        v_shift_days := (v_old_date - (v_base_date - INTERVAL '2 days'))::int;
        UPDATE lis_report SET
            create_time = create_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            audit_time = audit_time - (v_shift_days||' days')::interval,
            publish_time = publish_time - (v_shift_days||' days')::interval
        WHERE id = 200;
        UPDATE lis_sample SET
            create_time = create_time - (v_shift_days||' days')::interval,
            collect_time = collect_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            signoff_time = signoff_time - (v_shift_days||' days')::interval
        WHERE id = 200;

        -- id=201 -> 基准日前3天
        v_shift_days := (v_old_date - (v_base_date - INTERVAL '3 days'))::int;
        UPDATE lis_report SET
            create_time = create_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            audit_time = audit_time - (v_shift_days||' days')::interval,
            publish_time = publish_time - (v_shift_days||' days')::interval
        WHERE id = 201;
        UPDATE lis_sample SET
            create_time = create_time - (v_shift_days||' days')::interval,
            collect_time = collect_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            signoff_time = signoff_time - (v_shift_days||' days')::interval
        WHERE id = 201;

        -- id=202 -> 基准日前6天
        v_shift_days := (v_old_date - (v_base_date - INTERVAL '6 days'))::int;
        UPDATE lis_report SET
            create_time = create_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            audit_time = audit_time - (v_shift_days||' days')::interval,
            publish_time = publish_time - (v_shift_days||' days')::interval
        WHERE id = 202;
        UPDATE lis_sample SET
            create_time = create_time - (v_shift_days||' days')::interval,
            collect_time = collect_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            signoff_time = signoff_time - (v_shift_days||' days')::interval
        WHERE id = 202;

        -- id=203 -> 基准日前7天
        v_shift_days := (v_old_date - (v_base_date - INTERVAL '7 days'))::int;
        UPDATE lis_report SET
            create_time = create_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            audit_time = audit_time - (v_shift_days||' days')::interval,
            publish_time = publish_time - (v_shift_days||' days')::interval
        WHERE id = 203;
        UPDATE lis_sample SET
            create_time = create_time - (v_shift_days||' days')::interval,
            collect_time = collect_time - (v_shift_days||' days')::interval,
            receive_time = receive_time - (v_shift_days||' days')::interval,
            signoff_time = signoff_time - (v_shift_days||' days')::interval
        WHERE id = 203;

        -- 同步更新 lis_lab_order, lis_visit 的日期
        FOR v_rec IN SELECT id, patient_id FROM lis_sample WHERE id IN (200,201,202,203) LOOP
            -- 根据 sample.id 找关联的 order 和 visit
            UPDATE lis_lab_order SET
                create_time = (SELECT s.create_time FROM lis_sample s WHERE s.id = v_rec.id)
            WHERE id IN (SELECT order_id FROM lis_sample WHERE id = v_rec.id);

            UPDATE lis_visit SET
                visit_time = (SELECT s.create_time FROM lis_sample s WHERE s.id = v_rec.id)
            WHERE patient_id = v_rec.patient_id
              AND DATE(visit_time) = v_old_date;
        END LOOP;
    END IF;
END $$;

-- =============================================================================
-- 质控规则配置 & 质控预警表
-- =============================================================================

-- Westgard 规则配置表
CREATE TABLE IF NOT EXISTS lis_qc_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(20) NOT NULL UNIQUE,
    rule_name       VARCHAR(50) NOT NULL,
    rule_type       VARCHAR(20) DEFAULT 'REJECT',
    enabled         SMALLINT DEFAULT 1,
    description     VARCHAR(200),
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
    alert_type      VARCHAR(30),
    lot_id          BIGINT,
    test_item_id    BIGINT,
    instrument_id   BIGINT,
    last_measure_value DECIMAL(10,4),
    last_z_score    DECIMAL(5,2),
    message         VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'PENDING',
    alert_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_qc_alert IS '质控偏差趋势预警表';
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_time ON lis_qc_alert (alert_time);
CREATE INDEX IF NOT EXISTS idx_lis_qc_alert_status ON lis_qc_alert (status);

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
