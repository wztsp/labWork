\echo '=== S1: source column lengths vs target supplier_name(200) ==='
SELECT table_name, column_name, character_maximum_length AS maxlen
FROM information_schema.columns
WHERE (table_name='lis_reagent' AND column_name='supplier')
   OR (table_name='lis_reagent_inbound' AND column_name='supplier')
   OR (table_name='lis_supplier' AND column_name IN ('supplier_name','supplier_code'))
ORDER BY table_name, column_name;

\echo ''
\echo '=== S2: indexes on lis_supplier -- is supplier_name unique? ==='
SELECT indexname, indexdef FROM pg_indexes WHERE tablename='lis_supplier' ORDER BY indexname;

\echo ''
\echo '=== S3: NOT NULL (no default) columns I must satisfy in test inserts ==='
SELECT table_name, column_name, data_type, character_maximum_length AS maxlen
FROM information_schema.columns
WHERE table_schema='public' AND table_name IN ('lis_reagent','lis_reagent_inbound')
  AND is_nullable='NO' AND column_default IS NULL
ORDER BY table_name, ordinal_position;

\echo ''
\echo '=== S4: DB collation (affects ORDER BY s.name ordering) ==='
SELECT datcollate, datctype FROM pg_database WHERE datname='lis_db';

\echo ''
\echo '=== S5: what whitespace does TRIM() actually strip? ==='
SELECT 'ascii-space'      AS kind, '[' || TRIM('  X  ')                        || ']' AS trimmed
UNION ALL SELECT 'fullwidth-U3000', '[' || TRIM(U&'\3000' || 'X' || U&'\3000') || ']'
UNION ALL SELECT 'tab-U0009',       '[' || TRIM(U&'\0009' || 'X' || U&'\0009') || ']'
UNION ALL SELECT 'newline-U000A',   '[' || TRIM(U&'\000A' || 'X' || U&'\000A') || ']'
UNION ALL SELECT 'nbsp-U00A0',      '[' || TRIM(U&'\00A0' || 'X' || U&'\00A0') || ']';

\echo ''
\echo '=== S6: does a fullwidth-space-only string survive the <> quote-quote filter? ==='
SELECT U&'\3000'          AS raw_fullwidth_only,
       TRIM(U&'\3000') <> '' AS passes_nonempty_filter,
       length(TRIM(U&'\3000')) AS trimmed_len;
