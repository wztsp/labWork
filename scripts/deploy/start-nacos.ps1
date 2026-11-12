$nacosHome = 'd:\traeitem\labWork\infra\nacos\nacos'
$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$logs = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Start Nacos in standalone mode
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "$nacosHome\bin\startup.cmd -m standalone" -RedirectStandardOutput "$logs\nacos.out.log" -RedirectStandardError "$logs\nacos.err.log" -WindowStyle Hidden
Write-Host "Started Nacos. Waiting 30s for it to be ready..."
Start-Sleep -Seconds 30

# Verify Nacos ports
$tcp8848 = (Test-NetConnection -ComputerName localhost -Port 8848 -WarningAction SilentlyContinue).TcpTestSucceeded
$tcp9848 = (Test-NetConnection -ComputerName localhost -Port 9848 -WarningAction SilentlyContinue).TcpTestSucceeded
Write-Host "Nacos 8848: $tcp8848"
Write-Host "Nacos 9848: $tcp9848"

# Also start Redis if not running
$redis = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
if (-not $redis.TcpTestSucceeded) {
    Write-Host "Redis not running, starting..."
    $redisHome = 'd:\traeitem\labWork\infra\redis'
    Start-Process -FilePath "$redisHome\redis-server.exe" -ArgumentList '--port', '6379' -WindowStyle Hidden
    Start-Sleep -Seconds 2
    $redis = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
    Write-Host "Redis: $($redis.TcpTestSucceeded)"
} else {
    Write-Host "Redis already running"
}
