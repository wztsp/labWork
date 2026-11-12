$ErrorActionPreference = "Continue"

Write-Host "=== Stopping all LIS service Java processes (keeping Nacos) ===" -ForegroundColor Yellow

# Get all java processes except Nacos (PID 24912)
$javaProcs = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne 24912 }

foreach ($proc in $javaProcs) {
    $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.Id)").CommandLine
    # Skip Nacos
    if ($cmdLine -match "nacos-server") { continue }
    Write-Host "  Stopping PID $($proc.Id)..." -ForegroundColor Gray
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
}

Write-Host "Waiting for processes to stop..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host ""
Write-Host "=== Restarting all services with HikariCP pool size=3 ===" -ForegroundColor Cyan

$baseDir = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system"
$services = @(
    @{ Name = "auth";              Jar = "$baseDir\lis-auth\target\lis-auth-1.0.0.jar" },
    @{ Name = "system";            Jar = "$baseDir\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar" },
    @{ Name = "base";              Jar = "$baseDir\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar" },
    @{ Name = "sample";            Jar = "$baseDir\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar" },
    @{ Name = "report";            Jar = "$baseDir\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar" },
    @{ Name = "qc";                Jar = "$baseDir\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar" },
    @{ Name = "instrument-mgmt";   Jar = "$baseDir\lis-service\lis-service-instrument-mgmt\target\lis-service-instrument-mgmt-1.0.0.jar" },
    @{ Name = "instrument";        Jar = "$baseDir\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar" },
    @{ Name = "reagent";           Jar = "$baseDir\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar" },
    @{ Name = "micro";             Jar = "$baseDir\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar" },
    @{ Name = "hr";                Jar = "$baseDir\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar" },
    @{ Name = "stats";             Jar = "$baseDir\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar" },
    @{ Name = "integration";       Jar = "$baseDir\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar" },
    @{ Name = "gateway";           Jar = "$baseDir\lis-gateway\target\lis-gateway-1.0.0.jar" }
)

foreach ($svc in $services) {
    $jarPath = $svc.Jar
    $svcName = $svc.Name
    if (Test-Path $jarPath) {
        Write-Host "  Starting $svcName..." -ForegroundColor Green
        $jvmArgs = "-Xms128m -Xmx384m -Dfile.encoding=UTF-8 -DDB_PASSWORD=lis_dev_2026 -Dspring.datasource.hikari.maximum-pool-size=3"
        Start-Process -FilePath "java" -ArgumentList "$jvmArgs -jar `"$jarPath`"" -WindowStyle Hidden
        Start-Sleep -Seconds 2
    } else {
        Write-Host "  [SKIP] $svcName jar not found: $jarPath" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "All services started. Waiting 30s for registration..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "=== Checking listening ports ===" -ForegroundColor Cyan
netstat -ano | findstr "LISTENING" | findstr ":8080 :8081 :8085 :8087 :8088 :8090 :8091 :8092 :8093 :8094"
