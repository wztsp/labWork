$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mvn = 'C:\Users\Administrator\AppData\Local\maven\apache-maven-3.9.9\bin\mvn.cmd'
$proj = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system'
$logs = "$proj\logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Step 1: Stop all java processes (including Nacos)
Write-Host "=== Step 1: Stopping all Java processes ==="
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# Step 2: Build
Write-Host ""
Write-Host "=== Step 2: Building project ==="
& $mvn -f $proj clean package -DskipTests -T 1C 2>&1 | Select-Object -Last 25
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED"; exit 1 }

# Step 3: Start Nacos
Write-Host ""
Write-Host "=== Step 3: Starting Nacos ==="
$nacosHome = 'd:\traeitem\labWork\infra\nacos\nacos'
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "$nacosHome\bin\startup.cmd -m standalone" -RedirectStandardOutput "$logs\nacos.out.log" -RedirectStandardError "$logs\nacos.err.log" -WindowStyle Hidden
Write-Host "Waiting 25s for Nacos..."
Start-Sleep -Seconds 25
$tcp8848 = (Test-NetConnection -ComputerName localhost -Port 8848 -WarningAction SilentlyContinue).TcpTestSucceeded
$tcp9848 = (Test-NetConnection -ComputerName localhost -Port 9848 -WarningAction SilentlyContinue).TcpTestSucceeded
Write-Host "Nacos 8848: $tcp8848, 9848: $tcp9848"

# Step 4: Start Redis (if not running)
$redis = (Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue).TcpTestSucceeded
if (-not $redis) {
    Write-Host "Starting Redis..."
    $redisHome = 'd:\traeitem\labWork\infra\redis'
    Start-Process -FilePath "$redisHome\redis-server.exe" -ArgumentList '--port', '6379' -WindowStyle Hidden
    Start-Sleep -Seconds 2
    $redis = (Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue).TcpTestSucceeded
}
Write-Host "Redis: $redis"

# Step 5: Start all services in order
Write-Host ""
Write-Host "=== Step 5: Starting all services ==="
$services = @(
    @{ Name = 'lis-gateway';                 Jar = "$proj\lis-gateway\target\lis-gateway-1.0.0.jar" },
    @{ Name = 'lis-auth';                    Jar = "$proj\lis-auth\target\lis-auth-1.0.0.jar" },
    @{ Name = 'lis-service-system';          Jar = "$proj\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar" },
    @{ Name = 'lis-service-base';            Jar = "$proj\lis-service\lis-service-base\target\lis-service-base-1.0.0.jar" },
    @{ Name = 'lis-service-sample';          Jar = "$proj\lis-service\lis-service-sample\target\lis-service-sample-1.0.0.jar" },
    @{ Name = 'lis-service-report';          Jar = "$proj\lis-service\lis-service-report\target\lis-service-report-1.0.0.jar" },
    @{ Name = 'lis-service-qc';              Jar = "$proj\lis-service\lis-service-qc\target\lis-service-qc-1.0.0.jar" },
    @{ Name = 'lis-service-instrument';      Jar = "$proj\lis-service\lis-service-instrument\target\lis-service-instrument-1.0.0.jar" },
    @{ Name = 'lis-service-instrument-mgmt'; Jar = "$proj\lis-service\lis-service-instrument-mgmt\target\lis-service-instrument-mgmt-1.0.0.jar" },
    @{ Name = 'lis-service-reagent';         Jar = "$proj\lis-service\lis-service-reagent\target\lis-service-reagent-1.0.0.jar" },
    @{ Name = 'lis-service-micro';           Jar = "$proj\lis-service\lis-service-micro\target\lis-service-micro-1.0.0.jar" },
    @{ Name = 'lis-service-hr';              Jar = "$proj\lis-service\lis-service-hr\target\lis-service-hr-1.0.0.jar" },
    @{ Name = 'lis-service-stats';           Jar = "$proj\lis-service\lis-service-stats\target\lis-service-stats-1.0.0.jar" },
    @{ Name = 'lis-service-integration';     Jar = "$proj\lis-service\lis-service-integration\target\lis-service-integration-1.0.0.jar" }
)

foreach ($svc in $services) {
    if (-not (Test-Path $svc.Jar)) {
        Write-Host "Skip $($svc.Name) - jar not found"
        continue
    }
    $outLog = "$logs\$($svc.Name).out.log"
    $errLog = "$logs\$($svc.Name).err.log"
    Write-Host "Starting $($svc.Name)..."
    Start-Process -FilePath 'java' -ArgumentList '-jar', $svc.Jar -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden
    Start-Sleep -Seconds 3  # Stagger startup to avoid resource contention
}

Write-Host ""
Write-Host "All start commands issued. Waiting 40s for services to fully start..."
Start-Sleep -Seconds 40

Write-Host ""
Write-Host "=== Listening ports (8080-8099) ==="
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -ge 8080 -and $_.LocalPort -le 8099 } | Sort-Object LocalPort | ForEach-Object {
    Write-Host "  Port $($_.LocalPort): PID $($_.OwningProcess)"
}
