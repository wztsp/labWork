\set ON_ERROR_STOP 0
\pset pager off

BEGIN;

\echo '#############################################################'
\echo '#  SETUP: synthetic supplier strings (7 scenarios)          #'
\echo '#############################################################'

-- Scenario 1: plain CJK name shared by 3 reagents
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S1_A','QA S1 reagent A','北京生物科技有限公司'),
  ('QA_S1_B','QA S1 reagent B','北京生物科技有限公司'),
  ('QA_S1_C','QA S1 reagent C','北京生物科技有限公司');

-- Scenario 2: whitespace variants of ONE logical supplier
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S2_CLEAN','QA S2 clean',      '罗氏诊断'),
  ('QA_S2_TRAIL','QA S2 trail-ascii','罗氏诊断 '),
  ('QA_S2_LEAD', 'QA S2 lead-ascii', ' 罗氏诊断'),
  ('QA_S2_FWSP', 'QA S2 trail-fullwidth', '罗氏诊断' || U&'\3000'),
  ('QA_S2_TAB',  'QA S2 trail-tab',  '罗氏诊断' || U&'\0009');

-- Scenario 3: case variants
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S3_UPPER','QA S3 upper','ROCHE'),
  ('QA_S3_LOWER','QA S3 lower','roche'),
  ('QA_S3_MIXED','QA S3 mixed','Roche');

-- Scenario 4: SAME supplier present in BOTH source tables (highest risk)
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S4_R','QA S4 reagent','上海医疗器械股份');
INSERT INTO lis_reagent_inbound(inbound_no, supplier) VALUES
  ('QA_S4_IN01','上海医疗器械股份');

-- Scenario 5: empty / whitespace-only (NOT null)
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S5_EMPTY','QA S5 empty string',''),
  ('QA_S5_SPACES','QA S5 ascii spaces','   '),
  ('QA_S5_FWONLY','QA S5 fullwidth only', U&'\3000');

-- Scenario 6: max-length name (source is VARCHAR(100), target 200)
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S6_LONG','QA S6 long name', REPEAT('长', 100));

-- Scenario 7: embedded single quote
INSERT INTO lis_reagent(reagent_code, reagent_name, supplier) VALUES
  ('QA_S7_QUOTE','QA S7 quote', 'O''Brien 医疗');
INSERT INTO lis_reagent_inbound(inbound_no, supplier) VALUES
  ('QA_S7_IN01', 'O''Brien 医疗');

\echo ''
\echo '--- source rows staged (invisible chars exposed via length + hex) ---'
SELECT reagent_code,
       '[' || supplier || ']'      AS raw_bracketed,
       length(supplier)            AS raw_len,
       length(TRIM(supplier))      AS trimmed_len,
       (TRIM(supplier) <> '')      AS passes_filter
FROM lis_reagent WHERE reagent_code LIKE 'QA_%' ORDER BY reagent_code;

\echo ''
\echo '#############################################################'
\echo '#  RUN SECTION 22 (verbatim from add-reagent-enhance.sql)   #'
\echo '#############################################################'
INSERT INTO lis_supplier (supplier_code, supplier_name, qual_status, source_type, status, remark)
SELECT 'SUP_MIG_' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY s.name) AS VARCHAR), 4, '0'),
       s.name, 0, 2, 1, '由存量 supplier 字符串自动迁移生成，请补录证照信息'
FROM (
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent
     WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
    UNION
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent_inbound
     WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
) s
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier x WHERE x.supplier_name = s.name);

\echo ''
\echo '--- lis_supplier after section 22 ---'
SELECT id, supplier_code,
       '[' || supplier_name || ']' AS supplier_name_bracketed,
       length(supplier_name)       AS name_len
FROM lis_supplier ORDER BY supplier_code;

\echo ''
\echo '#############################################################'
\echo '#  RUN SECTION 22.1 + 22.2 (verbatim)                       #'
\echo '#############################################################'
UPDATE lis_reagent r SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE r.supplier_id IS NULL
   AND r.supplier IS NOT NULL AND TRIM(r.supplier) = sp.supplier_name;

UPDATE lis_reagent_inbound i SET supplier_id = sp.id
  FROM lis_supplier sp
 WHERE i.supplier_id IS NULL
   AND i.supplier IS NOT NULL AND TRIM(i.supplier) = sp.supplier_name;

\echo ''
\echo '=== R1: backfill result on lis_reagent ==='
SELECT r.reagent_code,
       '[' || r.supplier || ']'  AS src_string,
       r.supplier_id,
       sp.supplier_code          AS linked_code,
       '[' || sp.supplier_name || ']' AS linked_name
FROM lis_reagent r LEFT JOIN lis_supplier sp ON sp.id = r.supplier_id
WHERE r.reagent_code LIKE 'QA_%' ORDER BY r.reagent_code;

\echo ''
\echo '=== R2: backfill result on lis_reagent_inbound ==='
SELECT i.inbound_no, '[' || i.supplier || ']' AS src_string, i.supplier_id, sp.supplier_code AS linked_code
FROM lis_reagent_inbound i LEFT JOIN lis_supplier sp ON sp.id = i.supplier_id
WHERE i.inbound_no LIKE 'QA_%' ORDER BY i.inbound_no;

\echo ''
\echo '=== R3 *** ORPHAN CHECK ***: supplier non-empty but supplier_id STILL NULL ==='
SELECT 'lis_reagent' AS tbl, reagent_code AS key, '[' || supplier || ']' AS src, length(supplier) AS len
FROM lis_reagent
WHERE supplier IS NOT NULL AND TRIM(supplier) <> '' AND supplier_id IS NULL
UNION ALL
SELECT 'lis_reagent_inbound', inbound_no, '[' || supplier || ']', length(supplier)
FROM lis_reagent_inbound
WHERE supplier IS NOT NULL AND TRIM(supplier) <> '' AND supplier_id IS NULL;
\echo '--- (0 rows = no orphans) ---'

\echo ''
\echo '=== R4: SPLIT/MERGE analysis - how many suppliers per logical entity ==='
SELECT '[' || supplier_name || ']' AS supplier_name, length(supplier_name) AS len,
       (SELECT COUNT(*) FROM lis_reagent r WHERE r.supplier_id = sp.id) AS reagents_linked,
       (SELECT COUNT(*) FROM lis_reagent_inbound i WHERE i.supplier_id = sp.id) AS inbounds_linked
FROM lis_supplier sp ORDER BY sp.supplier_code;

\echo ''
\echo '=== R5: Scenario 1 - 3 reagents must share ONE supplier_id ==='
SELECT COUNT(DISTINCT supplier_id) AS distinct_supplier_ids, COUNT(*) AS reagent_rows
FROM lis_reagent WHERE reagent_code LIKE 'QA_S1_%';

\echo ''
\echo '=== R6: Scenario 2 - whitespace variants: how many suppliers created? ==='
SELECT COUNT(*) AS supplier_rows_for_luoshi
FROM lis_supplier WHERE supplier_name LIKE '罗氏诊断%';

\echo ''
\echo '=== R7: Scenario 3 - case variants: how many suppliers created? ==='
SELECT COUNT(*) AS supplier_rows_for_roche
FROM lis_supplier WHERE UPPER(supplier_name) = 'ROCHE';

\echo ''
\echo '=== R8: Scenario 4 - cross-table same supplier: MUST be exactly 1 ==='
SELECT COUNT(*) AS supplier_rows_shanghai FROM lis_supplier WHERE supplier_name = '上海医疗器械股份';

\echo ''
\echo '=== R9: Scenario 5 - did a whitespace-only garbage supplier get created? ==='
SELECT id, supplier_code, '[' || supplier_name || ']' AS name, length(supplier_name) AS len
FROM lis_supplier WHERE TRIM(supplier_name) = '' OR length(TRIM(supplier_name)) = 0
   OR supplier_name = U&'\3000';

\echo ''
\echo '=== R10: Scenario 6 - long name stored intact (no truncation)? ==='
SELECT supplier_code, length(supplier_name) AS stored_len, (supplier_name = REPEAT('长',100)) AS intact
FROM lis_supplier WHERE supplier_name LIKE '长%';

\echo ''
\echo '=== R11: Scenario 7 - single quote survived intact? ==='
SELECT supplier_code, supplier_name, (supplier_name = 'O''Brien 医疗') AS intact
FROM lis_supplier WHERE supplier_name LIKE 'O%Brien%';

ROLLBACK;
\echo ''
\echo '################ ROLLED BACK ################'
\echo '=== CLEANLINESS: QA residue must be 0 ==='
SELECT (SELECT COUNT(*) FROM lis_reagent WHERE reagent_code LIKE 'QA_%') AS qa_reagents,
       (SELECT COUNT(*) FROM lis_reagent_inbound WHERE inbound_no LIKE 'QA_%') AS qa_inbounds,
       (SELECT COUNT(*) FROM lis_supplier) AS supplier_rows;
