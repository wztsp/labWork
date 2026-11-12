\echo '=== 6b-2 faithful reproduction: UNKNOWN-typed parameter (what PG JDBC sends for #{days}) ==='
\echo '--- OLD: unknown || unknown  -> expect ERROR: operator is not unique ---'
SELECT ('5' || ' day')::interval AS old_unknown;
\echo '--- NEW: unknown * interval -> expect SUCCESS ---'
SELECT ('5' * INTERVAL '1 day') AS new_unknown;
\echo '--- NEW with text-typed param (setString path) ---'
PREPARE new_txt(text) AS SELECT ($1::int * INTERVAL '1 day');
EXECUTE new_txt('7');
\echo '--- OLD with text-typed param (setString path) ---'
PREPARE old_txt(text) AS SELECT ($1 || ' day')::interval;
EXECUTE old_txt('7');
\echo '--- real usage shape: license_expiry_date <= CURRENT_DATE + N days ---'
SELECT CURRENT_DATE + ('30' * INTERVAL '1 day') AS new_shape;
