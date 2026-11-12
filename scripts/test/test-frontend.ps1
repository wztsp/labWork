$ErrorActionPreference = "Continue"
Write-Host "=== Testing Frontend Access ===" -ForegroundColor Cyan

try {
    $r = Invoke-WebRequest -Uri "http://localhost:3000/" -UseBasicParsing -TimeoutSec 10
    Write-Host "Status: $($r.StatusCode)" -ForegroundColor Green
    Write-Host "Content-Type: $($r.Headers['Content-Type'])" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 600) { $preview = $preview.Substring(0, 600) + "..." }
    Write-Host "Response: $preview"
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Checking 3000 Port Listener ===" -ForegroundColor Cyan
netstat -ano | findstr "LISTENING" | findstr ":3000"
