# 启动所有后端 Spring Boot 微服务
$ErrorActionPreference = "Continue"
$baseDir = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system"
$logDir = "$baseDir\logs"

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

# 服务列表：按依赖顺序排列
$services = @(
    @{ Name = "lis-gateway"; Jar = "$baseDir\lis-gateway\target\lis-gateway-1.0.0.jar" },
    @{ Name = "lis-auth"; Jar = "$baseDir\lis-auth\target\lis-auth-1.0.0.jar" },
    @{ Name = "lis-service-base"; Jar = "$baseDir\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar" },
    @{ Name = "lis-service-system"; Jar = "$baseDir\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar" },
    @{ Name = "lis-service-hr"; Jar = "$baseDir\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar" },
    @{ Name = "lis-service-sample"; Jar = "$baseDir\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar" },
    @{ Name = "lis-service-report"; Jar = "$baseDir\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar" },
    @{ Name = "lis-service-qc"; Jar = "$baseDir\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar" },
    @{ Name = "lis-service-instrument"; Jar = "$baseDir\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar" },
    @{ Name = "lis-service-instrument-mgmt"; Jar = "$baseDir\lis-service\lis-service-instrument-mgmt\target\lis-service-instrument-mgmt-1.0.0.jar" },
    @{ Name = "lis-service-reagent"; Jar = "$baseDir\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar" },
    @{ Name = "lis-service-micro"; Jar = "$baseDir\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar" },
    @{ Name = "lis-service-integration"; Jar = "$baseDir\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar" },
    @{ Name = "lis-service-stats"; Jar = "$baseDir\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar" }
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  开始启动 LIS 微服务集群..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

foreach ($svc in $services) {
    if (Test-Path $svc.Jar) {
        $outLog = "$logDir\$($svc.Name).out.log"
        $errLog = "$logDir\$($svc.Name).err.log"
        
        Write-Host "Starting $($svc.Name) ..." -ForegroundColor Green -NoNewline
        
        $proc = Start-Process -FilePath "java" -ArgumentList "-jar", $svc.Jar -RedirectStandardOutput $outLog -RedirectStandardError $errLog -NoNewWindow -PassThru
        
        if ($proc) {
            Write-Host " (PID: $($proc.Id))" -ForegroundColor Yellow
        } else {
            Write-Host " Failed" -ForegroundColor Red
        }
    } else {
        Write-Host "[SKIP] $($svc.Jar) not found" -ForegroundColor Gray
    }
    
    # 稍微间隔一下，避免瞬间资源打满
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  所有服务启动指令已下发！" -ForegroundColor Cyan
Write-Host "  请查看 $logDir 获取启动日志" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
