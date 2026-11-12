$ErrorActionPreference = "Continue"

Write-Host "=== Testing Stats Service Directly (port 8088) ===" -ForegroundColor Cyan

# Test direct call to stats service
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8088/api/stats/workload?startDate=2026-07-01&endDate=2026-07-31" -UseBasicParsing -TimeoutSec 10
    Write-Host "[Direct workload] HTTP $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 400) { $preview = $preview.Substring(0, 400) + "..." }
    Write-Host "Response: $preview"
} catch {
    Write-Host "[Direct workload] Error: $($_.Exception.Message)" -ForegroundColor Red
}

try {
    $r = Invoke-WebRequest -Uri "http://localhost:8088/actuator/health" -UseBasicParsing -TimeoutSec 10
    Write-Host "[Health] HTTP $($r.StatusCode): $($r.Content)" -ForegroundColor Green
} catch {
    Write-Host "[Health] Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Testing via Gateway (port 8080) ===" -ForegroundColor Cyan

# Login first
$loginBody = '{"username":"admin","password":"123456"}'
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
    $token = ($r.Content | ConvertFrom-Json).data.token
    Write-Host "Login OK, token: $($token.Substring(0, 30))..." -ForegroundColor Green
} catch {
    Write-Host "Login failed: $($_.Exception.Message)" -ForegroundColor Red
    $token = ""
}

$auth = @{ Authorization = "Bearer $token" }

# Test stats via gateway
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/stats/workload?startDate=2026-07-01&endDate=2026-07-31" -Headers $auth -UseBasicParsing -TimeoutSec 15
    Write-Host "[Gateway workload] HTTP $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 400) { $preview = $preview.Substring(0, 400) + "..." }
    Write-Host "Response: $preview"
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Host "[Gateway workload] Error HTTP $status : $($_.Exception.Message)" -ForegroundColor Red
}

try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/stats/critical?startDate=2026-07-01&endDate=2026-07-31" -Headers $auth -UseBasicParsing -TimeoutSec 15
    Write-Host "[Gateway critical] HTTP $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 400) { $preview = $preview.Substring(0, 400) + "..." }
    Write-Host "Response: $preview"
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Host "[Gateway critical] Error HTTP $status : $($_.Exception.Message)" -ForegroundColor Red
}

# Test integration via gateway
Write-Host ""
Write-Host "=== Testing Integration Service via Gateway ===" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/integration/adapters" -Headers $auth -UseBasicParsing -TimeoutSec 15
    Write-Host "[Gateway adapters] HTTP $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 400) { $preview = $preview.Substring(0, 400) + "..." }
    Write-Host "Response: $preview"
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Host "[Gateway adapters] Error HTTP $status : $($_.Exception.Message)" -ForegroundColor Red
}
