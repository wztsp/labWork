-- =============================================================================
-- 资质证书附件表
-- 用于存储证书相关的文档和图片附件
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_certificate_attachment (
    id              BIGSERIAL PRIMARY KEY,
    certificate_id  BIGINT NOT NULL,                           -- 关联证书ID
    file_name       VARCHAR(255) NOT NULL,                     -- 原始文件名
    file_type       VARCHAR(50),                               -- 文件类型 (pdf/jpg/png/doc/doc等)
    file_size       BIGINT,                                    -- 文件大小(字节)
    content_type    VARCHAR(100),                              -- MIME类型
    file_data       BYTEA,                                     -- 文件二进制数据
    uploader_id     BIGINT,                                    -- 上传人ID
    uploader_name   VARCHAR(50),                               -- 上传人姓名
    upload_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,       -- 上传时间
    remark          VARCHAR(500)                               -- 备注
);

COMMENT ON TABLE lis_certificate_attachment IS '资质证书附件表';

CREATE INDEX IF NOT EXISTS idx_cert_attachment_certificate ON lis_certificate_attachment (certificate_id);
CREATE INDEX IF NOT EXISTS idx_cert_attachment_upload_time ON lis_certificate_attachment (upload_time);

-- 添加附件数量统计字段到证书表
ALTER TABLE lis_staff_certificate ADD COLUMN IF NOT EXISTS attachment_count INT DEFAULT 0;
COMMENT ON COLUMN lis_staff_certificate.attachment_count IS '附件数量';
