# ========== LIS 一体化部署包 - 数据库初始化 (Windows) ==========
# 前置条件: PostgreSQL 14+, psql 在 PATH 中, 且已修改过 application.yml 里的 DB 密码
# 首次部署执行一次即可, 后续启动用 start.bat

$ErrorActionPreference = "Stop"
$BASE_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "================================================"
Write-Host "  LIS 数据库初始化"
Write-Host "================================================"
Write-Host ""

# ---- 交互式参数 ----
$host = Read-Host "PostgreSQL 主机 (默认 localhost)"
if (-not $host) { $host = "localhost" }
$port = Read-Host "PostgreSQL 端口 (默认 5432)"
if (-not $port) { $port = "5432" }
$user = Read-Host "PostgreSQL 管理员用户名 (默认 postgres)"
if (-not $user) { $user = "postgres" }
$pgPassword = Read-Host -AsSecureString "PostgreSQL 管理员密码"
$dbName = Read-Host "数据库名 (默认 lis_db)"
if (-not $dbName) { $dbName = "lis_db" }
$dbUser = Read-Host "应用用户名 (默认 lis_user)"
if (-not $dbUser) { $dbUser = "lis_user" }
$dbPassword = Read-Host "应用用户密码 (默认 lis_dev_2026)"
if (-not $dbPassword) { $dbPassword = "lis_dev_2026" }
$dbPasswordPlain = [System.Net.NetworkCredential]::new("", $dbPassword).Password

$env:PGPASSWORD = ([System.Net.NetworkCredential]::new("", $pgPassword)).Password

Write-Host ""
Write-Host "[步骤 1/4] 创建数据库用户 $dbUser ..."
try {
    psql -h $host -p $port -U $user -d postgres -c "SELECT 1 FROM pg_roles WHERE rolname='$dbUser';" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  用户已存在, 跳过"
    } else {
        psql -h $host -p $port -U $user -d postgres -c "CREATE USER $dbUser WITH PASSWORD '$dbPasswordPlain';" 2>&1
        Write-Host "  创建成功"
    }
} catch {
    Write-Host "  创建用户: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[步骤 2/4] 创建数据库 $dbName ..."
try {
    psql -h $host -p $port -U $user -d postgres -c "SELECT 1 FROM pg_database WHERE datname='$dbName';" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  数据库已存在, 跳过"
    } else {
        psql -h $host -p $port -U $user -d postgres -c "CREATE DATABASE $dbName OWNER $dbUser ENCODING 'UTF8';" 2>&1
        Write-Host "  创建成功"
    }
} catch {
    Write-Host "  创建数据库: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[步骤 3/4] 导入 Schema ..."
$initSql = "$BASE_DIR\db\init.sql"
if (Test-Path $initSql) {
    psql -h $host -p $port -U $user -d $dbName -f $initSql 2>&1 | Select-Object -Last 5
    Write-Host "  Schema 导入完成"
} else {
    Write-Host "  跳过 (init.sql 不存在)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[步骤 4/4] 导入演示数据 ..."
$seedSql = "$BASE_DIR\db\seed_30_technician.sql"
if (Test-Path $seedSql) {
    psql -h $host -p $port -U $user -d $dbName -f $seedSql 2>&1 | Select-Object -Last 5
    Write-Host "  演示数据导入完成"
} else {
    Write-Host "  跳过 (seed SQL 不存在)" -ForegroundColor Yellow
}

# ---- 更新 application.yml 里的密码 ----
$ymlFile = "$BASE_DIR\backend\application.yml"
if (Test-Path $ymlFile -and $dbPasswordPlain -ne "lis_dev_2026") {
    Write-Host ""
    Write-Host "[可选] 更新 $ymlFile 中的数据库密码..."
    $content = Get-Content $ymlFile -Raw
    $content = $content -replace '\$\{DB_PASSWORD:lis_dev_2026\}', "`${DB_PASSWORD:$dbPasswordPlain}"
    Set-Content $ymlFile $content -NoNewline
    Write-Host "  已更新 DB_PASSWORD 默认值"
}

$env:PGPASSWORD = ""

Write-Host ""
Write-Host "================================================"
Write-Host "  完成! 现在可以运行 start.bat 启动服务"
Write-Host "================================================"
