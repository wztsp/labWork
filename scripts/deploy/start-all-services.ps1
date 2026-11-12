# 启动所有 LIS 微服务
# 用法: powershell -ExecutionPolicy Bypass -File start-all-services.ps1

$ErrorActionPreference = "Continue"
$javaHome = "C:\Program Files\BellSoft\LibericaJDK-17"
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;" + $env:Path

$baseDir = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system"
$logDir = "$baseDir\logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# 服务定义: 名称 => jar 路径
$services = [ordered]@{
    "lis-auth"                    = "$baseDir\lis-auth\target\lis-auth-1.0.0.jar"
    "lis-service-system"         = "$baseDir\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar"
    "lis-service-base"           = "$baseDir\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar"
    "lis-service-sample"         = "$baseDir\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar"
    "lis-service-report"         = "$baseDir\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar"
    "lis-service-qc"             = "$baseDir\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar"
    "lis-service-instrument"     = "$baseDir\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar"
    "lis-service-instrument-mgmt"= "$baseDir\lis-service\lis-service-instrument-mgmt\target\lis-service-instrument-mgmt-1.0.0.jar"
    "lis-service-integration"    = "$baseDir\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar"
    "lis-service-stats"          = "$baseDir\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar"
    "lis-service-reagent"        = "$baseDir\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar"
    "lis-service-micro"          = "$baseDir\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar"
    "lis-service-hr"             = "$baseDir\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar"
    "lis-gateway"                = "$baseDir\lis-gateway\target\lis-gateway-1.0.0.jar"
}

Write-Host "===== 启动 LIS 微服务 =====" -ForegroundColor Cyan
Write-Host "JAVA_HOME: $javaHome"
Write-Host "日志目录: $logDir"
Write-Host ""

foreach ($name in $services.Keys) {
    $jar = $services[$name]
    if (-not (Test-Path $jar)) {
        Write-Host "[SKIP] $name - jar 不存在: $jar" -ForegroundColor Yellow
        continue
    }
    $outLog = "$logDir\$name.out.log"
    $errLog = "$logDir\$name.err.log"
    Write-Host "[START] $name ..." -ForegroundColor Green
    Start-Process -FilePath "$javaHome\bin\java.exe" `
        -ArgumentList "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Dspring.cloud.nacos.discovery.enabled=false -Dspring.cloud.nacos.config.enabled=false -jar `"$jar`"" `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "===== 等待服务启动 (60秒) =====" -ForegroundColor Cyan
Start-Sleep -Seconds 60

Write-Host ""
Write-Host "===== 服务状态检查 =====" -ForegroundColor Cyan
$ports = @{
    "lis-auth"                    = 8090
    "lis-service-system"         = 8087
    "lis-service-base"           = 8085
    "lis-service-sample"         = 8081
    "lis-service-report"         = 8082
    "lis-service-qc"             = 8083
    "lis-service-instrument"     = 8084
    "lis-service-instrument-mgmt"= 8091
    "lis-service-integration"    = 8086
    "lis-service-stats"          = 8088
    "lis-service-reagent"        = 8092
    "lis-service-micro"          = 8093
    "lis-service-hr"             = 8094
    "lis-gateway"                = 8080
}

foreach ($name in $ports.Keys) {
    $port = $ports[$name]
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", $port)
        $tcp.Close()
        Write-Host "[UP]   $name (port $port)" -ForegroundColor Green
    } catch {
        Write-Host "[DOWN] $name (port $port)" -ForegroundColor Red
    }
}
