\echo '=== ⑥b licenseExpiryWithinDays old vs new, parameterized (PREPARE int = MyBatis Integer bind) ==='
\echo '--- OLD writing: ($1 || '' day'')::interval  -> expect ERROR ---'
PREPARE old_lic(int) AS SELECT ($1 || ' day')::interval AS result;
EXECUTE old_lic(5);
\echo '--- NEW writing: ($1 * INTERVAL ''1 day'')  -> expect SUCCESS (e.g. 5 days) ---'
PREPARE new_lic(int) AS SELECT ($1 * INTERVAL '1 day') AS result;
EXECUTE new_lic(5);
\echo '--- literal forms for clarity ---'
SELECT (5 || ' day')::interval AS old_literal;
SELECT (5 * INTERVAL '1 day') AS new_literal;
