\echo '=== 4d PLANT (autocommit, no BEGIN): human dup + draft loser + FK, to test partial application ==='
DROP INDEX IF EXISTS uk_lis_supplier_name_norm;
INSERT INTO lis_supplier(supplier_code, supplier_name, status, source_type, create_time)
VALUES ('QA_PH1','roche',1,1,NOW()),
       ('QA_PH2','Roche',1,1,NOW()),
       ('QA_PD','ROCHE',1,2,NOW());
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier_id, create_time)
SELECT 'QA_PFK','QA外键试剂', id, NOW() FROM lis_supplier WHERE supplier_code='QA_PD';
\echo '--- planted state ---'
SELECT supplier_code, supplier_name, source_type, id FROM lis_supplier WHERE supplier_code LIKE 'QA_P%' ORDER BY id;
SELECT r.reagent_code, r.supplier_id, s.supplier_code FROM lis_reagent r JOIN lis_supplier s ON s.id=r.supplier_id WHERE r.reagent_code='QA_PFK';
