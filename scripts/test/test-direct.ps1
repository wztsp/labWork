$ErrorActionPreference = "Continue"

Write-Host "=== Testing Base Service Directly (port 8085) ===" -ForegroundColor Cyan

$endpoints = @(
    "/api/base/items?current=1&size=10",
    "/api/base/ref-ranges?current=1&size=10",
    "/api/base/instruments?current=1&size=10",
    "/api/base/instrument-status"
)

foreach ($ep in $endpoints) {
    Write-Host ""
    Write-Host "Testing: $ep" -ForegroundColor Yellow
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8085$ep" -UseBasicParsing -TimeoutSec 10
        Write-Host "  [OK] HTTP $($r.StatusCode)" -ForegroundColor Green
        $preview = $r.Content
        if ($preview.Length -gt 500) { $preview = $preview.Substring(0, 500) + "..." }
        Write-Host "  Response: $preview"
    } catch {
        Write-Host "  [ERROR] $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails) {
            $detail = $_.ErrorDetails.Message
            if ($detail.Length -gt 800) { $detail = $detail.Substring(0, 800) + "..." }
            Write-Host "  Detail: $detail" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "=== Testing Sample Service Directly (port 8081) ===" -ForegroundColor Cyan

try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/sample/page?pageNo=1&pageSize=10" -UseBasicParsing -TimeoutSec 10
    Write-Host "[OK] HTTP $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 500) { $preview = $preview.Substring(0, 500) + "..." }
    Write-Host "Response: $preview"
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) {
        $detail = $_.ErrorDetails.Message
        if ($detail.Length -gt 800) { $detail = $detail.Substring(0, 800) + "..." }
        Write-Host "Detail: $detail" -ForegroundColor Red
    }
}
