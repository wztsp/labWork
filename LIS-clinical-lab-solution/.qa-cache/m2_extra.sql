\set ON_ERROR_STOP 0
\pset pager off

\echo '#############################################################'
\echo '#  X1: is SUP_MIG numbering collation-dependent?            #'
\echo '#############################################################'
\echo '--- same name set, ordered under DB collation vs C collation ---'
WITH names(n) AS (VALUES ('roche'),('Roche'),('ROCHE'),('北京生物科技有限公司'),('上海医疗器械股份'),('O''Brien 医疗'))
SELECT ROW_NUMBER() OVER (ORDER BY n)             AS seq_db_collation,
       n                                          AS name_db_order,
       ROW_NUMBER() OVER (ORDER BY n COLLATE "C") AS seq_c_collation
FROM names ORDER BY n;

\echo ''
\echo '--- the SAME set ordered under C collation (note different sequence) ---'
WITH names(n) AS (VALUES ('roche'),('Roche'),('ROCHE'),('北京生物科技有限公司'),('上海医疗器械股份'),('O''Brien 医疗'))
SELECT ROW_NUMBER() OVER (ORDER BY n COLLATE "C") AS seq, n FROM names ORDER BY n COLLATE "C";

\echo ''
\echo '--- do case variants compare EQUAL (non-deterministic collation)? ---'
SELECT ('Roche' = 'roche')                       AS eq_default,
       ('Roche' < 'roche')                       AS lt_default,
       ('Roche' COLLATE "C" < 'roche' COLLATE "C") AS lt_c_collation;

\echo ''
\echo '#############################################################'
\echo '#  X2: supplier_name is NOT unique -> 22.1 ambiguity risk   #'
\echo '#############################################################'
BEGIN;
\echo '--- stage: one MANUALLY entered supplier + one MIGRATED, identical name ---'
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, remark)
VALUES ('QA_MANUAL_01','安捷伦科技', 1, 'manually entered by admin');
INSERT INTO lis_supplier(supplier_code, supplier_name, source_type, remark)
VALUES ('QA_MIG_01','安捷伦科技', 2, 'duplicate name, different code');
SELECT id, supplier_code, supplier_name, source_type FROM lis_supplier WHERE supplier_name='安捷伦科技' ORDER BY id;

INSERT INTO lis_reagent(reagent_code, reagent_name, supplier)
VALUES ('QA_AMBIG','QA ambiguity probe','安捷伦科技');

\echo '--- run 22.1 verbatim: which of the two duplicates wins? ---'
UPDATE lis_reagent r SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE r.supplier_id IS NULL
   AND r.supplier IS NOT NULL AND TRIM(r.supplier) = sp.supplier_name;

SELECT r.reagent_code, r.supplier_id, sp.supplier_code AS won, sp.source_type
FROM lis_reagent r JOIN lis_supplier sp ON sp.id=r.supplier_id
WHERE r.reagent_code='QA_AMBIG';
\echo '--> arbitrary winner = silent mis-linking. No error raised.'
ROLLBACK;

\echo ''
\echo '#############################################################'
\echo '#  X3: LEGACY-ENV scenario behind L458 (team-lead point)    #'
\echo '#############################################################'
\echo '--- simulate an env where a PRIOR script already added warehouse_id as NULLABLE ---'
CREATE TEMP TABLE t_legacy_stock (id BIGSERIAL PRIMARY KEY, reagent_id BIGINT, lot_no VARCHAR(32));
ALTER TABLE t_legacy_stock ADD COLUMN warehouse_id BIGINT;          -- prior version: nullable, no default
INSERT INTO t_legacy_stock(reagent_id, lot_no, warehouse_id) VALUES (1,'L1',NULL),(2,'L2',NULL);

\echo '--- state BEFORE running our script line 454 ---'
SELECT column_name, is_nullable, column_default FROM information_schema.columns
WHERE table_name='t_legacy_stock' AND column_name='warehouse_id';
SELECT COUNT(*) AS rows_with_null_warehouse FROM t_legacy_stock WHERE warehouse_id IS NULL;

\echo ''
\echo '--- now run script L454 (ADD COLUMN IF NOT EXISTS ... NOT NULL DEFAULT 0) ---'
ALTER TABLE t_legacy_stock ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;

\echo '--- state AFTER L454: did NOT NULL / DEFAULT get applied? ---'
SELECT column_name, is_nullable, column_default FROM information_schema.columns
WHERE table_name='t_legacy_stock' AND column_name='warehouse_id';
SELECT COUNT(*) AS rows_still_null FROM t_legacy_stock WHERE warehouse_id IS NULL;
\echo '--> confirms team-lead: column stays NULLABLE, rows stay NULL. L458 IS needed.'

\echo ''
\echo '--- run L458 backfill ---'
UPDATE t_legacy_stock SET warehouse_id = 0 WHERE warehouse_id IS NULL;
SELECT COUNT(*) AS rows_null_after_L458 FROM t_legacy_stock WHERE warehouse_id IS NULL;

\echo ''
\echo '--- *** BUT: is the column NOT NULL now? does the 3-col UK still hold? *** ---'
SELECT column_name, is_nullable, column_default FROM information_schema.columns
WHERE table_name='t_legacy_stock' AND column_name='warehouse_id';
CREATE UNIQUE INDEX uk_t_legacy ON t_legacy_stock (reagent_id, lot_no, warehouse_id);
\echo '--- insert two rows with NULL warehouse_id, same reagent+lot ---'
INSERT INTO t_legacy_stock(reagent_id, lot_no, warehouse_id) VALUES (9,'DUP',NULL);
INSERT INTO t_legacy_stock(reagent_id, lot_no, warehouse_id) VALUES (9,'DUP',NULL);
SELECT COUNT(*) AS dup_rows_admitted FROM t_legacy_stock WHERE reagent_id=9 AND lot_no='DUP';
\echo '--> if 2 rows admitted, the 3-col UNIQUE is DEFEATED in legacy envs (NULL<>NULL).'

DROP TABLE t_legacy_stock;

\echo ''
\echo '=== CLEANLINESS ==='
SELECT (SELECT COUNT(*) FROM lis_supplier) AS supplier_rows,
       (SELECT COUNT(*) FROM lis_reagent WHERE reagent_code LIKE 'QA_%') AS qa_reagents,
       (SELECT COUNT(*) FROM lis_reagent) AS reagent_rows;
