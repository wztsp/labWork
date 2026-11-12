# LIS 系统一键启动脚本
# 用法: powershell -ExecutionPolicy Bypass -File .\start-all-services.ps1

$ErrorActionPreference = 'SilentlyContinue'
$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { "C:\Program Files\BellSoft\LibericaJDK-17\bin\java.exe" }
$serviceDir = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system"
$logDir = "$serviceDir\logs"

if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

# 服务列表: 名称, jar路径, JVM参数
$services = @(
    @{ Name='gateway';   Jar="$serviceDir\lis-gateway\target\lis-gateway-1.0.0.jar"; Mem='256m' }
    @{ Name='auth';      Jar="$serviceDir\lis-auth\target\lis-auth-1.0.0.jar"; Mem='256m' }
    @{ Name='system';    Jar="$serviceDir\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar"; Mem='384m' }
    @{ Name='base';      Jar="$serviceDir\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar"; Mem='384m' }
    @{ Name='sample';    Jar="$serviceDir\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar"; Mem='384m' }
    @{ Name='report';    Jar="$serviceDir\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar"; Mem='384m' }
    @{ Name='qc';        Jar="$serviceDir\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar"; Mem='384m' }
    @{ Name='instrument';Jar="$serviceDir\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar"; Mem='384m' }
    @{ Name='hr';        Jar="$serviceDir\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar"; Mem='384m' }
    @{ Name='micro';     Jar="$serviceDir\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar"; Mem='384m' }
    @{ Name='reagent';   Jar="$serviceDir\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar"; Mem='384m' }
    @{ Name='integration';Jar="$serviceDir\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar"; Mem='384m' }
    @{ Name='stats';     Jar="$serviceDir\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar"; Mem='384m' }
)

# 设置环境变量
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
$env:DB_PASSWORD = 'lis_dev_2026'
$env:REDIS_PASSWORD = ''
$env:NACOS_ADDR = 'localhost:8848'

Write-Host "========================================="
Write-Host "  LIS 系统启动脚本"
Write-Host "========================================="
Write-Host ""

# 检查 Java
if (-not (Test-Path $javaExe)) {
    Write-Host "[ERROR] Java not found: $javaExe" -ForegroundColor Red
    exit 1
}

# 检查 Redis
$redis = Get-Process redis-server -ErrorAction SilentlyContinue
if (-not $redis) {
    Write-Host "[INFO] Starting Redis..." -ForegroundColor Yellow
    Start-Process -FilePath "D:\traeitem\labWork\infra\redis\redis-server.exe" -ArgumentList "--port","6379" -WindowStyle Hidden
    Start-Sleep -Seconds 2
    Write-Host "[OK] Redis started" -ForegroundColor Green
} else {
    Write-Host "[OK] Redis already running" -ForegroundColor Green
}

# 启动各服务
$started = 0
$failed = 0
foreach ($svc in $services) {
    $jar = $svc.Jar
    $name = $svc.Name
    $mem = $svc.Mem

    if (-not (Test-Path $jar)) {
        Write-Host "[SKIP] $name - jar not found" -ForegroundColor Yellow
        $failed++
        continue
    }

    $outLog = "$logDir\lis-$name.out.log"
    $errLog = "$logDir\lis-$name.err.log"

    $args = @(
        "-Xms128m", "-Xmx$mem",
        '-Dfile.encoding=UTF-8',
        '-DDB_PASSWORD=lis_dev_2026',
        '-Dspring.datasource.hikari.maximum-pool-size=3',
        '-Dspring.redis.host=localhost',
        '-Dspring.redis.port=6379',
        '-Dspring.cloud.nacos.discovery.enabled=false',
        '-Dspring.cloud.nacos.config.enabled=false',
        '-jar', "`"$jar`""
    )

    Write-Host "[START] $name ..." -NoNewline
    Start-Process -FilePath $javaExe -ArgumentList $args -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden
    $started++
    Write-Host " started" -ForegroundColor Green
    # 错开启动，避免资源争抢
    Start-Sleep -Milliseconds 800
}

Write-Host ""
Write-Host "========================================="
Write-Host "  Started: $started, Skipped/Failed: $failed"
Write-Host "  等待服务初始化 (约 30 秒)..."
Write-Host "========================================="
Start-Sleep -Seconds 30
Write-Host ""
Write-Host "服务端口检查:"
$ports = @{
    'gateway' = 8080; 'auth' = 8090; 'system' = 8087; 'base' = 8085
    'sample' = 8081; 'report' = 8082; 'qc' = 8083; 'instrument' = 8084
    'hr' = 8094; 'micro' = 8093; 'reagent' = 8092; 'integration' = 8086; 'stats' = 8088
}
foreach ($name in $ports.Keys) {
    $port = $ports[$name]
    $ok = Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue
    if ($ok.TcpTestSucceeded) {
        Write-Host "  [OK]   $name (port $port)" -ForegroundColor Green
    } else {
        Write-Host "  [WAIT] $name (port $port) - still starting..." -ForegroundColor Yellow
    }
}
Write-Host ""
Write-Host "前端访问: http://localhost:3000/"
Write-Host "API 网关: http://localhost:8080/"
