-- 失控报告模块 (OCC) 表结构 —— 依据 D:\his文档\QC\occ-module-design.md
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
  status          VARCHAR(20)  DEFAULT 'PENDING',   -- PENDING/INVESTIGATING/CORRECTED/CLOSED
  error_type      VARCHAR(20),                       -- SYSTEM/RANDOM
  qc_values       VARCHAR(200),                      -- 逗号分隔质控值快照
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
  status     VARCHAR(20) DEFAULT 'PENDING',        -- PENDING/CURRENT/DONE
  checklist  VARCHAR(3000),                        -- JSON: [{"text":..,"done":true}]
  operator   VARCHAR(60),
  operated_at TIMESTAMP,
  conclusion VARCHAR(500),
  CONSTRAINT uq_occ_step UNIQUE(occ_id, step_no)
);

CREATE TABLE IF NOT EXISTS lis_qc_occ_approval (
  id          BIGSERIAL PRIMARY KEY,
  occ_id      BIGINT NOT NULL,
  level_no    INT,                                  -- 1=专业组组长 2=质控负责人 3=科主任
  level_name  VARCHAR(40),
  status      VARCHAR(20) DEFAULT 'PENDING',        -- PENDING/APPROVED/REJECTED
  approver    VARCHAR(60),
  comment     VARCHAR(255),
  approved_at TIMESTAMP
);