\echo '=== ② normalization merge: inject 罗氏诊断 x5 + roche x3 + 北京 生物 vs 北京生物 ==='
BEGIN;
SAVEPOINT s2;
-- reagent insert helper: only reagent_code / reagent_name are NOT NULL (no default).
INSERT INTO lis_reagent (reagent_code, reagent_name, supplier) VALUES
  ('QA_R1', '罗氏诊断原',       '罗氏诊断'),
  ('QA_R2', '罗氏诊断全角尾',   '罗氏诊断' || CHR(12288)),   -- U+3000 trailing
  ('QA_R3', '罗氏诊断tab尾',    '罗氏诊断' || CHR(9)),       -- TAB trailing
  ('QA_R4', '罗氏诊断前导',     ' ' || '罗氏诊断'),          -- leading half-width space
  ('QA_R5', '罗氏诊断半角尾',   '罗氏诊断' || ' '),          -- trailing half-width space
  ('QA_R6', 'roche小写',        'roche'),
  ('QA_R7', 'Roche中',          'Roche'),
  ('QA_R8', 'ROCHE大',          'ROCHE'),
  ('QA_R9', '北京空格',         '北京 生物'),                -- middle space
  ('QA_R10','北京无空格',       '北京生物');                  -- no middle space

-- run the real fixed script (migration 22.4 + backfill 22.5/22.6 use fn_supplier_name_norm)
\i D:/traeitem/labWork/LIS-clinical-lab-solution/lis-system/docs/database/add-reagent-enhance.sql

\echo '--- suppliers created from migration (source_type=2) ---'
SELECT supplier_code, supplier_name, source_type
  FROM lis_supplier WHERE source_type = 2 ORDER BY supplier_name;

\echo '--- normalization check: all variants map to same norm? ---'
SELECT fn_supplier_name_norm(supplier) AS norm, COUNT(*) AS rows
  FROM lis_reagent WHERE reagent_code LIKE 'QA_%' GROUP BY 1 ORDER BY 1;

\echo '--- backfill: each QA reagent should point to exactly one supplier ---'
SELECT r.reagent_code, r.supplier AS raw_string, s.supplier_code, s.supplier_name
  FROM lis_reagent r LEFT JOIN lis_supplier s ON r.supplier_id = s.id
 WHERE r.reagent_code LIKE 'QA_%' ORDER BY r.reagent_code;

\echo '--- reverse merge guard: 北京 生物 vs 北京生物 must stay 2 distinct suppliers ---'
SELECT supplier_name, fn_supplier_name_norm(supplier_name) AS norm
  FROM lis_supplier WHERE source_type=2 AND supplier_name IN ('北京 生物','北京生物') ORDER BY supplier_name;
ROLLBACK;
