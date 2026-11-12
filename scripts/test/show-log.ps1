$log = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\logs\system.out.log'
# Find error patterns in the log
$content = Get-Content $log -ErrorAction SilentlyContinue
# Find lines with ERROR or exception
$errors = $content | Select-String -Pattern 'ERROR|Exception|Caused by' -Context 0,3
$errors | Select-Object -First 30 | ForEach-Object { Write-Host $_.Line }
