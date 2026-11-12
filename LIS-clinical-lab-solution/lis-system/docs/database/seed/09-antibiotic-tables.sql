-- =====================================================================
-- 09-antibiotic-tables.sql
-- 抗生素模板（抗体药敏模板）建表脚本
-- 幂等 PostgreSQL，可安全重复执行
-- 表命名规范、BIGSERIAL 主键、SMALLINT 状态与现有微生物模块保持一致
-- =====================================================================

-- ---------- 抗生素模板主表 ----------
CREATE TABLE IF NOT EXISTS lis_antibiotic_template (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(64)  NOT NULL,                 -- 模板编码，如 ABX_GP_ROUTINE
    name              VARCHAR(128) NOT NULL,                 -- 模板名称，如「G+ 常规药敏板」
    template_category VARCHAR(32),                           -- 分类(预设): G+/G-/非发酵/苛养/厌氧/真菌
    default_method    VARCHAR(32),                           -- 默认检测方法(MIC/KB)，为空表示不预设
    description       VARCHAR(500),                          -- 描述：适用仪器、板子货号、适用场景
    status            SMALLINT     NOT NULL DEFAULT 1,       -- 1 启用 / 0 禁用（禁用模板在报告页下拉不展示）
    is_system         SMALLINT     NOT NULL DEFAULT 0,       -- 1 系统内置(不可删除，仅可改名/描述/明细) / 0 客户自建
    sort_no           INTEGER      NOT NULL DEFAULT 0,       -- 同级排序
    create_user_id    BIGINT,                                -- 创建人 ID
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  lis_antibiotic_template     IS '抗生素药敏模板主表';
COMMENT ON COLUMN lis_antibiotic_template.code IS '模板编码，唯一';
COMMENT ON COLUMN lis_antibiotic_template.default_method IS '默认检测方法(MIC/KB);为空时现场手工确定';
COMMENT ON COLUMN lis_antibiotic_template.is_system IS '1=系统内置(不可删除);0=客户自建';

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_antibiotic_template_code ON lis_antibiotic_template (code);
CREATE INDEX IF NOT EXISTS idx_lis_antibiotic_template_status ON lis_antibiotic_template (status);

-- ---------- 抗生素模板明细表 ----------
CREATE TABLE IF NOT EXISTS lis_antibiotic_template_item (
    id             BIGSERIAL PRIMARY KEY,
    template_id    BIGINT    NOT NULL,                       -- FK -> lis_antibiotic_template.id
    antibiotic_id  BIGINT    NOT NULL,                       -- FK -> lis_antibiotic.id (字典)
    default_method VARCHAR(32),                              -- 本条抗生素检测方法(MIC/KB)，空则继承模板头
    sort_no        INTEGER   NOT NULL DEFAULT 0,             -- 显示顺序（决定报告页药敏表插入顺序）
    remark         VARCHAR(255),                             -- 备注
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  lis_antibiotic_template_item IS '抗生素模板明细(模板包含的抗生素)';
COMMENT ON COLUMN lis_antibiotic_template_item.antibiotic_id IS '抗生素字典ID，关联 lis_antibiotic.id';

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_abx_tpl_item_tpl_abx ON lis_antibiotic_template_item (template_id, antibiotic_id);
CREATE INDEX IF NOT EXISTS idx_lis_abx_tpl_item_tpl_sort ON lis_antibiotic_template_item (template_id, sort_no);

-- ---------- 模板-细菌绑定(多对多) ----------
CREATE TABLE IF NOT EXISTS lis_antibiotic_template_bacteria (
    id           BIGSERIAL PRIMARY KEY,
    template_id  BIGINT    NOT NULL,                         -- FK -> lis_antibiotic_template.id
    bacteria_id  BIGINT    NOT NULL,                         -- FK -> lis_bacteria.id (字典)
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  lis_antibiotic_template_bacteria IS '抗生素模板与细菌字典绑定关系(按细菌匹配模板)';
COMMENT ON COLUMN lis_antibiotic_template_bacteria.bacteria_id IS '细菌字典ID，关联 lis_bacteria.id';

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_abx_tpl_bac_tpl_bac ON lis_antibiotic_template_bacteria (template_id, bacteria_id);
CREATE INDEX IF NOT EXISTS idx_lis_abx_tpl_bac_bacteria ON lis_antibiotic_template_bacteria (bacteria_id);