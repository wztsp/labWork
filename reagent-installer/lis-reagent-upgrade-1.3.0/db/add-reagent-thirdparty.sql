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
