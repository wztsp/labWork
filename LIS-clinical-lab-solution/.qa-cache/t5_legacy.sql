\echo '=== ⑤ legacy NULL warehouse_id reproduction (temp table, no touch on real data) ==='
BEGIN;
-- temp table mimicking a legacy lis_reagent_stock with NULLABLE warehouse_id
CREATE TEMP TABLE qa_legacy (id BIGINT, reagent_id BIGINT, lot_no VARCHAR(32), warehouse_id BIGINT);
-- legacy state: 2 rows same (reagent_id,lot_no) with NULL warehouse admit fine (NULL<>NULL)
INSERT INTO qa_legacy (reagent_id, lot_no, warehouse_id) VALUES (1,'L1',NULL),(2,'L2',NULL);
-- apply the FIXED 3-step (from script 16.1 / 19.1), BEFORE any 3-col unique index
ALTER TABLE qa_legacy ALTER COLUMN warehouse_id SET DEFAULT 0;
UPDATE qa_legacy SET warehouse_id = 0 WHERE warehouse_id IS NULL;
ALTER TABLE qa_legacy ALTER COLUMN warehouse_id SET NOT NULL;
-- now create the 3-col unique index (as script 17)
CREATE UNIQUE INDEX qa_uk_lot_wh ON qa_legacy (reagent_id, lot_no, warehouse_id);
\echo '--- is_nullable after 3-step (expect NO) ---'
SELECT column_name, is_nullable, column_default FROM information_schema.columns WHERE table_name='qa_legacy' AND column_name='warehouse_id';
\echo '--- insert 2 rows same (reagent_id,lot_no) with NULL warehouse: expect NOT NULL violation x2 ---'
INSERT INTO qa_legacy (reagent_id, lot_no, warehouse_id) VALUES (3,'L3',NULL);
INSERT INTO qa_legacy (reagent_id, lot_no, warehouse_id) VALUES (3,'L3',NULL);
SELECT 'REACHED_END_SHOULD_NOT_HAPPEN' AS marker;
ROLLBACK;
