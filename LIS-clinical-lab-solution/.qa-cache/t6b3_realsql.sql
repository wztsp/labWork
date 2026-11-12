\echo '=== 6b-3 run the ACTUAL SupplierMapper.licenseExpiryWithinDays SQL against lis_supplier ==='
BEGIN;
SAVEPOINT sp;
INSERT INTO lis_supplier(supplier_code, supplier_name, status, source_type, license_expiry, medical_cert_expiry, create_time)
VALUES ('QA_EXP1','QA临期公司甲',1,1, CURRENT_DATE + 3,  NULL, NOW()),
       ('QA_EXP2','QA临期公司乙',1,1, NULL, CURRENT_DATE + 20, NOW()),
       ('QA_EXP3','QA远期公司丙',1,1, CURRENT_DATE + 400, CURRENT_DATE + 500, NOW()),
       ('QA_EXP4','QA已过期公司丁',1,1, CURRENT_DATE - 10, NULL, NOW());

\echo '--- NEW form, parameterized PREPARE(int) = MyBatis Integer bind, days=30 ---'
PREPARE mapper_new(int) AS
SELECT supplier_code, license_expiry, medical_cert_expiry FROM lis_supplier WHERE status = 1 AND (
  (license_expiry IS NOT NULL AND license_expiry <= CURRENT_DATE + ($1 * INTERVAL '1 day'))
  OR (medical_cert_expiry IS NOT NULL AND medical_cert_expiry <= CURRENT_DATE + ($1 * INTERVAL '1 day'))
) ORDER BY LEAST(COALESCE(license_expiry, DATE '9999-12-31'),
                 COALESCE(medical_cert_expiry, DATE '9999-12-31')) ASC, id ASC;
EXECUTE mapper_new(30);

\echo '--- OLD form same query, days=30 ---'
PREPARE mapper_old(int) AS
SELECT supplier_code FROM lis_supplier WHERE status = 1 AND (
  (license_expiry IS NOT NULL AND license_expiry <= CURRENT_DATE + ($1 || ' day')::interval)
  OR (medical_cert_expiry IS NOT NULL AND medical_cert_expiry <= CURRENT_DATE + ($1 || ' day')::interval)
) ORDER BY id ASC;
EXECUTE mapper_old(30);

\echo '--- old vs new numeric equivalence over 0/1/7/30/365/-5 (expect all t) ---'
SELECT d,
       (CURRENT_DATE + (d * INTERVAL '1 day')) = (CURRENT_DATE + (d || ' day')::interval) AS same
FROM (VALUES (0),(1),(7),(30),(365),(-5)) AS v(d);

ROLLBACK;
\echo '--- residue check after rollback (expect 0) ---'
SELECT COUNT(*) AS qa_left FROM lis_supplier WHERE supplier_code LIKE 'QA_%';
