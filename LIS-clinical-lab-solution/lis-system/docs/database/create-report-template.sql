CREATE TABLE IF NOT EXISTS lis_report_template (
    id BIGSERIAL PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    instrument_id BIGINT,
    instrument_name VARCHAR(100),
    template_config TEXT,
    dual_column SMALLINT DEFAULT 0,
    paper_size VARCHAR(10) DEFAULT 'A4',
    orientation VARCHAR(10) DEFAULT 'portrait',
    margin INT DEFAULT 10,
    is_default SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lis_report_template IS '报告模板表';
COMMENT ON COLUMN lis_report_template.template_name IS '模板名称';
COMMENT ON COLUMN lis_report_template.instrument_id IS '关联仪器ID(为空表示通用模板)';
COMMENT ON COLUMN lis_report_template.template_config IS '模板配置JSON';
COMMENT ON COLUMN lis_report_template.dual_column IS '结果是否双列显示 0-单列 1-双列';
COMMENT ON COLUMN lis_report_template.is_default IS '是否默认模板 0-否 1-是';

INSERT INTO lis_report_template (template_name, instrument_id, instrument_name, template_config, dual_column, paper_size, orientation, margin, is_default, status, create_time, update_time)
VALUES ('默认模板', NULL, NULL, '{}', 1, 'A4', 'portrait', 10, 1, 1, NOW(), NOW());
