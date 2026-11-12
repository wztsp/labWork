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

-- 插入示例数据
INSERT INTO lis_instrument_calibration (id, instrument_id, calibration_no, calibration_type, calibration_time, next_calibration_time, operator_id, operator_name, result, remark)
VALUES
(1001, 1, 'CAL-2026-001', ' daily', '2026-07-01 09:00:00', '2026-08-01 09:00:00', 1, '张三', 'PASS', '日常校准'),
(1002, 2, 'CAL-2026-002', 'monthly', '2026-07-15 10:00:00', '2026-08-15 10:00:00', 1, '李四', 'PASS', '月度校准');

INSERT INTO lis_instrument_repair (id, instrument_id, repair_no, fault_time, fault_desc, repair_time, finish_time, repairman, downtime_hours, result, parts_replaced, cost, remark)
VALUES
(2001, 1, 'REP-2026-001', '2026-06-10 14:00:00', '采样针堵塞', '2026-06-10 15:00:00', '2026-06-10 16:30:00', '王工', 2.5, 'FIXED', '采样针', 800.00, '更换采样针'),
(2002, 2, 'REP-2026-002', '2026-05-20 09:30:00', '光源衰减', '2026-05-20 10:00:00', '2026-05-20 12:00:00', '李工', 2.5, 'FIXED', '卤素灯', 1200.00, '更换光源');
