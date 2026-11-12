-- =============================================================================
-- HIS 新标准服务（检验）执行脚本  S1001 ~ S4020
-- 适用数据库: PostgreSQL 16（LIS 主库 lis_db）
-- 说明: 幂等脚本，可重复执行；建议以 lis_user 身份执行（安装脚本会 SET ROLE）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、表结构：S4009 申请状态回写进度
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
COMMENT ON TABLE lis_his_sample_status_push IS 'S4009 标本状态回写进度（按标本记录最后状态与推送结果）';
COMMENT ON COLUMN lis_his_sample_status_push.last_status IS 'LIS 当前标本状态';
COMMENT ON COLUMN lis_his_sample_status_push.last_pushed_status IS '已成功回写 HIS 的标本状态';

-- -----------------------------------------------------------------------------
-- 二、表结构：S3103 危急值回传进度
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_his_critical_push (
    result_id       BIGINT PRIMARY KEY,
    critical_id     VARCHAR(64),
    push_status     SMALLINT DEFAULT 0,
    retry_count     INT DEFAULT 0,
    error_msg       VARCHAR(500),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_his_critical_push IS 'S3103 危急值回传进度与 HIS 危急值记录ID';
COMMENT ON COLUMN lis_his_critical_push.critical_id IS 'HIS 返回的危急值记录ID（critical_type=2/3/4 时使用）';

-- -----------------------------------------------------------------------------
-- 三、表结构：HIS 接口调用日志（前台「系统管理 -> HIS接口日志」可查）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lis_his_zl_log (
    id              BIGSERIAL PRIMARY KEY,
    service_no      VARCHAR(16),              -- 服务编号 S1001~S4020
    action_no       VARCHAR(64),              -- 报文流水号 head.action_no
    direction       SMALLINT DEFAULT 1,       -- 1出站(LIS调HIS) 2入站(HIS调LIS)
    url             VARCHAR(500),
    request_body    TEXT,
    response_body   TEXT,
    http_status     INT,
    success         SMALLINT DEFAULT 1,       -- 0失败 1成功
    error_msg       VARCHAR(1000),
    cost_ms         BIGINT,
    ref_no          VARCHAR(200),             -- 关联单号: 申请号/条码/医嘱号
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_time ON lis_his_zl_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_service ON lis_his_zl_log (service_no);
CREATE INDEX IF NOT EXISTS idx_lis_his_zl_log_ref ON lis_his_zl_log (ref_no);
COMMENT ON TABLE lis_his_zl_log IS 'HIS 接口调用日志（出站标准服务调用留痕，便于前台排查）';

-- -----------------------------------------------------------------------------
-- 四、权限：若本脚本以 postgres 等超级用户执行，需把表权限授予应用账号
--     （安装脚本以 SET ROLE lis_user 执行时，表本身即归应用账号所有，此处为兜底）
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lis_user') THEN
        BEGIN
            ALTER TABLE lis_his_sample_status_push OWNER TO lis_user;
            ALTER TABLE lis_his_critical_push OWNER TO lis_user;
            ALTER TABLE lis_his_zl_log OWNER TO lis_user;
            GRANT SELECT, INSERT, UPDATE, DELETE
                ON lis_his_sample_status_push, lis_his_critical_push, lis_his_zl_log TO lis_user;
            RAISE NOTICE '已授权 his 进度表/接口日志表给 lis_user';
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE '表授权跳过（当前账号非表属主）: %', SQLERRM;
        END;
        BEGIN
            GRANT USAGE, SELECT ON SEQUENCE lis_his_zl_log_id_seq TO lis_user;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE '序列授权跳过: %', SQLERRM;
        END;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 五、系统参数（系统设置 -> 参数配置可直接修改，改完即生效）
-- -----------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, config_name, remark) VALUES
('his.zl.enabled',        'true',   'HIS标准服务总开关',        'true启用 / false停用（S4009/S4010/S4011/S3103自动回写）'),
('his.zl.url',            '',       'HIS数据处理平台地址',       '如 http://HIS-IP:端口/zlstddService，为空时不回写'),
('his.zl.sysno',          'LIS',    'LIS系统编码',              '对接方分配的三方系统编码，报文 head.sysno'),
('his.zl.tarno',          'ZLHIS',  '目标系统编码',             '固定 ZLHIS'),
('his.zl.token',          '',       'HIS标准服务令牌',          '为空不校验；配置后置于请求头 X-HIS-Token'),
('his.zl.org-id',         '0',      '检验科科室ID',             'S4009 organization_id'),
('his.zl.org-code',       'LIS',    '检验科科室编码',           'S4009 organization_code'),
('his.zl.org-name',       '检验科', '检验科科室名称',           'S4009 organization_name'),
('his.zl.rpt-dept-id',    '0',      '报告科室ID',               'S3103 rptexedept_id'),
('his.zl.operator',       '系统',   '回写操作员姓名',           'S4011 oprtr_name 等'),
('his.result.enabled',    'true',   'LIS结果回传HIS开关',       'true启用 / false停用'),
('his.result.url',        '',       'HIS检验结果接收地址(4010)', 'HIS提供的WebAPI完整地址，为空时不回传'),
('his.result.token',      '',       'HIS结果回传令牌',          '为空不校验；回传时置于请求头X-HIS-Token'),
('his.result.source-system','ZLHIS','HIS结果回传来源系统',      '本院传ZLHIS / 外院传ZLRCE'),
('his.result.default-lspcm-no','1', 'HIS结果回传标本类型码',    'HIS无标本类型对照时使用的lspcm_no（须>0）')
ON CONFLICT (config_key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 六、执行结果自检
-- -----------------------------------------------------------------------------
SELECT config_key, config_value
  FROM sys_config
 WHERE config_key LIKE 'his.zl.%'
 ORDER BY config_key;

SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = 'public'
   AND table_name IN ('lis_his_sample_status_push', 'lis_his_critical_push', 'lis_his_zl_log')
 ORDER BY table_name;
