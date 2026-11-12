\echo '=== ④b merge boundary: TWO humans(source_type=1) same norm -> must RAISE EXCEPTION ==='
\echo '=== simulate legacy pre-index state: DROP expr unique index so two same-norm humans can be planted ==='
\echo '=== then run REAL script; expect RAISE at 22.2 guard, script abort, no deletion, actionable error ==='
BEGIN;
SAVEPOINT s4b;
DROP INDEX IF EXISTS uk_lis_supplier_name_norm;
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_H1', 'roche', 1, 1);
INSERT INTO lis_supplier (supplier_code, supplier_name, source_type, status) VALUES ('QA_H2', 'Roche', 1, 1);
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql
SELECT 'SCRIPT_COMPLETED_SHOULD_NOT_HAPPEN' AS marker;
ROLLBACK;
