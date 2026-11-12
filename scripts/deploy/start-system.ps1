$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$jar = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-system\target\lis-service-system-1.0.0.jar'
$logs = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -RedirectStandardOutput "$logs\system.out.log" -RedirectStandardError "$logs\system.err.log" -WindowStyle Hidden
Write-Host "Started lis-service-system. Waiting 25s for it to register with Nacos..."
Start-Sleep -Seconds 25
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -gt 8080 -and $_.LocalPort -lt 8100 } | Select-Object LocalPort, OwningProcess
