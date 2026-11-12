-- 创建缺失的仪器-操作人员关联表
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