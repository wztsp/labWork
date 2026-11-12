-- ============================================================
-- 04-micro-culture.sql   原 MicroCultureDataInitializer
-- 建培养基/培养方案/培养皿表 + 内置 20 种培养基 + 10 套培养方案。幂等。
-- ============================================================
CREATE TABLE IF NOT EXISTS lis_medium_type (
    id BIGSERIAL PRIMARY KEY,
    medium_code VARCHAR(32),
    medium_name VARCHAR(100) NOT NULL,
    medium_abbr VARCHAR(20),
    remark VARCHAR(500),
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_culture_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,
    test_item_id BIGINT,
    test_item_name VARCHAR(100),
    -- 标本类型(如 痰/尿/血等); 为空表示适用于全部标本类型
    sample_type VARCHAR(50),
    remark VARCHAR(500),
    is_default SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 兼容已存在表: 幂等补充 sample_type 列
ALTER TABLE lis_culture_plan ADD COLUMN IF NOT EXISTS sample_type VARCHAR(50);
-- 兼容已存在表: 移除“同一检验项目仅允许一个方案”的旧唯一索引，
-- 使同一检验项目可按标本类型(sample_type)配置多个差异化培养方案
DROP INDEX IF EXISTS uk_lis_culture_plan_item;
CREATE TABLE IF NOT EXISTS lis_culture_plan_item (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT,
    medium_type_id BIGINT,
    medium_type_name VARCHAR(100),
    quantity INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS lis_sample_plate (
    id BIGSERIAL PRIMARY KEY,
    sample_id BIGINT,
    sample_barcode VARCHAR(50),
    plate_barcode VARCHAR(100),
    plan_id BIGINT,
    medium_type_id BIGINT,
    medium_type_name VARCHAR(100),
    patient_name VARCHAR(100),
    seq_no INT,
    status SMALLINT DEFAULT 0,
    operator_id BIGINT,
    operator_name VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancel_time TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_plate_barcode ON lis_sample_plate (plate_barcode);

-- 培养基字典 (仅在表为空时灌入)
INSERT INTO lis_medium_type (medium_code, medium_name, medium_abbr, remark, status)
SELECT * FROM (VALUES
 ('BAP','血平板','血','通用分离培养, 观察溶血',1),
 ('MAC','麦康凯平板','麦','肠道革兰阴性杆菌分离',1),
 ('CHOC','巧克力平板','巧','嗜血杆菌/奈瑟菌等苛养菌',1),
 ('SS','SS平板','SS','沙门菌/志贺菌分离',1),
 ('EMB','伊红美蓝平板','EM','肠道菌分离鉴别',1),
 ('XLD','木糖赖氨酸脱氧胆盐平板','XL','沙门菌/志贺菌分离',1),
 ('SDA','沙保弱葡萄糖琼脂','沙','真菌分离培养',1),
 ('CA','哥伦比亚血琼脂','哥','苛养菌和厌氧',1),
 ('CNA','哥伦比亚CNA琼脂','CN','革兰阳性菌选择',1),
 ('AN','厌氧血平板','厌','厌氧菌分离',1),
 ('KV','卡那霉素万古霉素平板','KV','厌氧菌选择(拟杆菌)',1),
 ('PEA','苯乙基乙醇平板','PE','厌氧/革兰阳性选择',1),
 ('T-M','Thayer-Martin平板','TM','淋病奈瑟菌选择',1),
 ('BCYE','缓冲炭酵母浸膏','BC','军团菌分离',1),
 ('LJ','罗氏培养基','罗','结核分枝杆菌',1),
 ('MB','Middlebrook 7H10','MB','分枝杆菌分离',1),
 ('TCBS','硫代硫酸盐枸橼酸盐胆盐平板','TC','弧菌分离',1),
 ('Cary-Blair','Cary-Blair运送','CB','标本运送',1),
 ('Stuart','Stuart运送','ST','运送',1),
 ('MHA','MH琼脂平板','MH','药敏KB纸片法',1)
) AS v(medium_code, medium_name, medium_abbr, remark, status)
WHERE NOT EXISTS (SELECT 1 FROM lis_medium_type);

-- 培养方案 (resolve test_item_id by name; 可为空)
INSERT INTO lis_culture_plan (plan_name, test_item_id, test_item_name, remark, is_default, status)
SELECT v.plan_name,
       (SELECT id FROM lis_test_item t WHERE t.item_name = v.test_item_name LIMIT 1),
       v.test_item_name, '系统内置方案', 0, 1
FROM (VALUES
 ('痰培养方案','痰一般细菌培养'),('尿培养方案','尿一般细菌培养'),
 ('血培养方案','血培养'),('分泌物培养方案','分泌物培养'),
 ('脑脊液培养方案','脑脊液培养'),('胸腹培养方案','胸腹水培养'),
 ('粪便培养方案','粪便培养'),('真菌培养方案','真菌培养'),
 ('厌氧培养方案','厌氧菌培养'),('淋球菌培养方案','淋球菌培养')
) AS v(plan_name, test_item_name)
WHERE NOT EXISTS (SELECT 1 FROM lis_culture_plan);

-- 培养方案明细 (plan -> mediums by code; sort_order 递增)
INSERT INTO lis_culture_plan_item (plan_id, medium_type_id, medium_type_name, quantity, sort_order)
SELECT p.id, m.id, m.medium_name, 1, row_number() OVER (PARTITION BY p.id)
FROM lis_culture_plan p
JOIN (
    SELECT '痰培养方案' plan_name, 'BAP' code UNION ALL SELECT '痰培养方案','MAC' UNION ALL SELECT '痰培养方案','CHOC'
    UNION ALL SELECT '尿培养方案','BAP' UNION ALL SELECT '尿培养方案','MAC'
    UNION ALL SELECT '血培养方案','BAP' UNION ALL SELECT '血培养方案','AN' UNION ALL SELECT '血培养方案','CHOC'
    UNION ALL SELECT '分泌物培养方案','BAP' UNION ALL SELECT '分泌物培养方案','MAC' UNION ALL SELECT '分泌物培养方案','CHOC'
    UNION ALL SELECT '脑脊液培养方案','BAP' UNION ALL SELECT '脑脊液培养方案','CHOC' UNION ALL SELECT '脑脊液培养方案','SDA'
    UNION ALL SELECT '胸腹培养方案','BAP' UNION ALL SELECT '胸腹培养方案','MAC' UNION ALL SELECT '胸腹培养方案','CHOC'
    UNION ALL SELECT '粪便培养方案','SS' UNION ALL SELECT '粪便培养方案','MAC' UNION ALL SELECT '粪便培养方案','XLD'
    UNION ALL SELECT '真菌培养方案','SDA' UNION ALL SELECT '真菌培养方案','SDA'
    UNION ALL SELECT '厌氧培养方案','AN' UNION ALL SELECT '厌氧培养方案','KV' UNION ALL SELECT '厌氧培养方案','PEA'
    UNION ALL SELECT '淋球菌培养方案','T-M' UNION ALL SELECT '淋球菌培养方案','CHOC'
) v ON v.plan_name = p.plan_name
JOIN lis_medium_type m ON m.medium_code = v.code;