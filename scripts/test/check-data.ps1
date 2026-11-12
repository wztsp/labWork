$psql = "D:\Program Files\PostgreSQL\13\bin\psql.exe"
$env:PGPASSWORD = "lis_dev_2026"

Write-Host "=== LIS Database - All Tables and Row Counts ===" -ForegroundColor Cyan
Write-Host ""

$query = @"
SELECT schemaname||'.'||relname AS table_name, n_live_tup AS row_count 
FROM pg_stat_user_tables 
WHERE schemaname = 'public' 
ORDER BY relname;
"@

$result = & $psql -U lis_user -d lis_db -c $query 2>&1

Write-Host $result
