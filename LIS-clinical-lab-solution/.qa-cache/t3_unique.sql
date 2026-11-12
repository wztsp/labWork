\echo '=== ③ expression unique index uk_lis_supplier_name_norm must REJECT normalized duplicates ==='
BEGIN;
SAVEPOINT s3;
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_U1', '安捷伦科技', 1, 1);
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_U2', '安捷伦科技' || CHR(12288), 1, 1);
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_U3', '安捷伦科技' || CHR(9), 1, 1);
SELECT 'REACHED_END_SHOULD_NOT_HAPPEN_IF_INDEX_WORKS' AS marker;
ROLLBACK;
