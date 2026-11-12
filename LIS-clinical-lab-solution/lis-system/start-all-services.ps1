# LIS 后端服务批量启动脚本
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
Write-Host "  LIS 后端服务批量启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 先检查是否有已运行的服务
$runningJava = Get-Process -Name java -ErrorAction SilentlyContinue
if ($runningJava) {
    Write-Host "警告: 检测到 $($runningJava.Count) 个Java进程正在运行" -ForegroundColor Yellow
    Write-Host "如需停止，请运行: Get-Process -Name java | Stop-Process -Force" -ForegroundColor Yellow
    $continue = Read-Host "是否继续启动? (Y/N)"
    if ($continue -ne "Y") {
        Write-Host "已取消启动" -ForegroundColor Red
        exit 0
    }
}

# 启动所有服务
$processes = @()
foreach ($name in $services.Keys) {
    $jarPath = $services[$name]
    if (-not (Test-Path $jarPath)) {
        Write-Host "[跳过] $name - jar文件不存在: $jarPath" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "[启动] $name ..." -ForegroundColor Green
    $proc = Start-Process -FilePath "java" `
        -ArgumentList "-jar", $jarPath, $nacosDisable `
        -NoNewWindow `
        -PassThru `
        -RedirectStandardOutput "$jarBase\$name.log" `
        -RedirectStandardError "$jarBase\$name-error.log"
    
    $processes += @{ Name = $name; PID = $proc.Id }
    Write-Host "  PID: $($proc.Id)" -ForegroundColor Gray
    
    # 稍微间隔一下，避免同时启动太多
    Start-Sleep -Seconds 1
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

Write-Host ""
Write-Host "日志文件位于: $jarBase\*.log" -ForegroundColor Gray
Write-Host ""
Write-Host "提示: 使用 'Get-Process -Name java' 查看所有Java进程" -ForegroundColor Gray
Write-Host "      使用 'Get-Process -Name java | Stop-Process -Force' 停止所有服务" -ForegroundColor Gray
