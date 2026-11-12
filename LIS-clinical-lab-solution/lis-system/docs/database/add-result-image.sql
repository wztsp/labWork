-- =============================================================================
-- 技师工作站检验结果图像字段
-- 数据库: PostgreSQL
-- 说明: 血常规/尿常规等检验项目由仪器/通讯程序回传形态图像(细胞形态、尿沉渣等),
--       通常一个检验项目有 3-4 张图形。image_url 存储为 JSON 数组字符串, 例如:
--       ["/lis/result/20260804/xxx1.jpg","/lis/result/20260804/xxx2.jpg"]
-- =============================================================================

ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS image_url VARCHAR(2000);
COMMENT ON COLUMN lis_result.image_url IS '检验结果图像(JSON数组字符串, 存储血常规/尿常规等形态图像的URL, 通常3-4张)';