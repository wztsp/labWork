$logs = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\logs'
$failed = @('lis-service-base', 'lis-service-sample', 'lis-service-report', 'lis-service-qc', 'lis-service-instrument', 'lis-service-reagent')

foreach ($svc in $failed) {
    $logFile = "$logs\$svc.out.log"
    Write-Host ""
    Write-Host "============================================"
    Write-Host "=== $svc - last 25 ERROR lines ==="
    Write-Host "============================================"
    if (Test-Path $logFile) {
        $content = Get-Content $logFile -ErrorAction SilentlyContinue
        $errors = $content | Select-String -Pattern 'ERROR|Exception|Caused by' -Context 0,2
        $errors | Select-Object -Last 8 | ForEach-Object { Write-Host $_.Line }
    }
}
