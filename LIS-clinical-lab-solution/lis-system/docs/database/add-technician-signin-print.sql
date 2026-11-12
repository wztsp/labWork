-- ============================================================
-- 技师工作站: 条码补打记录表 + 工作站签到表
-- 对应缺陷 B-2 (batchReprintBarcode / instrumentSignin / autoAuditSignin 空实现)
-- 数据库: PostgreSQL
-- 执行顺序: 在 init-schema.sql 与 add-technician-batch-fields.sql 之后执行
-- ============================================================

-- 1. 条码打印/补打记录表
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
