\echo '=== ④a + ④c merge boundary: human(source_type=1) + draft(source_type=2), SAME NORM, LITERALLY DISTINCT ==='
\echo '=== simulate legacy pre-index state: DROP the expr unique index so we can plant dup-norm rows ==='
\echo '=== then run the REAL script (22.1 merge -> 22.3 recreate index). expect: human survives, draft deleted, FK re-pointed ==='
BEGIN;
SAVEPOINT s4a;
DROP INDEX IF EXISTS uk_lis_supplier_name_norm;
-- human: literal 'roche'  (norm-distinct insert, will NOT trip index)
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_H', 'roche', 1, 1);
-- draft: literal 'Roche'  (normalizes to 'roche' = SAME norm as human, but literally distinct -> allowed while index dropped)
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_D', 'Roche', 2, 1);
INSERT INTO lis_reagent (reagent_code, reagent_name, supplier_id) VALUES
  ('QA_FK', '指草稿的试剂', (SELECT id FROM lis_supplier WHERE supplier_code='QA_D'));
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql
\echo '--- suppliers: human QA_H must survive, draft QA_D must be GONE ---'
SELECT supplier_code, supplier_name, source_type, id FROM lis_supplier WHERE supplier_code IN ('QA_H','QA_D') ORDER BY supplier_code;
\echo '--- reagent FK: must point to surviving human (no dangling) ---'
SELECT r.reagent_code, r.supplier_id, s.supplier_code, s.supplier_name, s.source_type
  FROM lis_reagent r LEFT JOIN lis_supplier s ON r.supplier_id = s.id WHERE r.reagent_code='QA_FK';
\echo '--- global dangling FK count (expect 0) ---'
SELECT COUNT(*) AS dangling_fk
  FROM lis_reagent r LEFT JOIN lis_supplier s ON r.supplier_id = s.id
 WHERE r.supplier_id IS NOT NULL AND s.id IS NULL;
\echo '--- expr unique index recreated by script 22.3? (expect 1) ---'
SELECT COUNT(*) AS idx_present FROM pg_indexes WHERE tablename='lis_supplier' AND indexname='uk_lis_supplier_name_norm';
ROLLBACK;
