# LIS 后端服务批量启动脚本 (非交互式)
# 禁用Nacos服务发现，使用本地配置

$ErrorActionPreference = "Continue"
$jarBase = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system"
$nacosDisable = "--spring.cloud.nacos.discovery.enabled=false"

# 服务列表: 名称 -> jar路径
$services = @{
    "lis-gateway"           = "$jarBase\lis-gateway\target\lis-gateway-1.0.0.jar"
    "lis-auth"              = "$jarBase\lis-auth\target\lis-auth-1.0.0.jar"
    "lis-service-sample"    = "$jarBase\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar"
    "lis-service-report"    = "$jarBase\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar"
    "lis-service-qc"        = "$jarBase\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar"
    "lis-service-instrument"= "$jarBase\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar"
    "lis-service-instrument-mgmt" = "$jarBase\lis-service\lis-service-instrument-mgmt\target\lis-service-instrument-mgmt-1.0.0.jar"
    "lis-service-reagent"   = "$jarBase\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar"
    "lis-service-micro"     = "$jarBase\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar"
    "lis-service-base"      = "$jarBase\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar"
    "lis-service-hr"        = "$jarBase\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar"
    "lis-service-integration"= "$jarBase\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar"
    "lis-service-system"    = "$jarBase\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar"
    "lis-service-stats"     = "$jarBase\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  LIS 后端服务批量启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 停掉已有的Java进程
$runningJava = Get-Process -Name java -ErrorAction SilentlyContinue
if ($runningJava) {
    Write-Host "检测到 $($runningJava.Count) 个Java进程，正在停止..." -ForegroundColor Yellow
    $runningJava | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "已停止所有Java进程" -ForegroundColor Green
}

# 启动所有服务
$processes = @()
$total = $services.Count
$current = 0

foreach ($name in $services.Keys) {
    $current++
    $jarPath = $services[$name]
    $pct = [math]::Round(($current / $total) * 100)
    
    if (-not (Test-Path $jarPath)) {
        Write-Host "[$pct%] [跳过] $name - jar文件不存在" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "[$pct%] [启动] $name ..." -ForegroundColor Green
    $proc = Start-Process -FilePath "java" `
        -ArgumentList "-jar", $jarPath, $nacosDisable `
        -WindowStyle Hidden `
        -PassThru
    
    $processes += @{ Name = $name; PID = $proc.Id }
    Write-Host "        PID: $($proc.Id)" -ForegroundColor Gray
    
    # 稍微间隔一下
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  服务启动完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "已启动的服务:" -ForegroundColor White

foreach ($p in $processes) {
    $proc = Get-Process -Id $p.PID -ErrorAction SilentlyContinue
    $status = if ($proc) { "运行中" } else { "已停止" }
    $color = if ($proc) { "Green" } else { "Red" }
    Write-Host "  $($p.Name) (PID: $($p.PID)) - $status" -ForegroundColor $color
}

# 等待服务启动并检查端口
Write-Host ""
Write-Host "等待服务启动 (15秒)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

Write-Host ""
Write-Host "端口监听状态:" -ForegroundColor White
$ports = @(8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8090, 8091, 8092, 8093, 8094)
foreach ($port in $ports) {
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    $status = if ($listening) { "✓ 监听中" } else { "✗ 未监听" }
    $color = if ($listening) { "Green" } else { "Red" }
    Write-Host "  端口 $port : $status" -ForegroundColor $color
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  启动完成! 访问网关: http://localhost:8080" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
