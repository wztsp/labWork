$ErrorActionPreference = "Continue"

Write-Host "=== 1. Test Login API directly ===" -ForegroundColor Cyan
try {
    $body = '{"username":"admin","password":"123456"}'
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 15
    Write-Host "[OK] Status: $($r.StatusCode)" -ForegroundColor Green
    $resp = $r.Content | ConvertFrom-Json
    Write-Host "Response code: $($resp.code)"
    Write-Host "Response message: $($resp.message)"
    Write-Host "Has token: $($null -ne $resp.data.token)"
    Write-Host "Has userId: $($null -ne $resp.data.userId)"
    Write-Host "Has username: $($null -ne $resp.data.username)"
    $token = $resp.data.token
    Write-Host "Token prefix: $($token.Substring(0, [Math]::Min(50, $token.Length)))..."
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) { Write-Host "Detail: $($_.ErrorDetails.Message)" -ForegroundColor Red }
}

Write-Host ""
Write-Host "=== 2. Test Login via Frontend Proxy ===" -ForegroundColor Cyan
try {
    $body = '{"username":"admin","password":"123456"}'
    $r = Invoke-WebRequest -Uri "http://localhost:3000/api/auth/login" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 15
    Write-Host "[OK] Status: $($r.StatusCode)" -ForegroundColor Green
    $resp = $r.Content | ConvertFrom-Json
    Write-Host "Response code: $($resp.code)"
    Write-Host "Has token: $($null -ne $resp.data.token)"
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) { Write-Host "Detail: $($_.ErrorDetails.Message)" -ForegroundColor Red }
}

Write-Host ""
Write-Host "=== 3. Test /me endpoint with token ===" -ForegroundColor Cyan
try {
    $headers = @{ Authorization = "Bearer $token" }
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/system/users/me" -Headers $headers -UseBasicParsing -TimeoutSec 10
    Write-Host "[OK] Status: $($r.StatusCode)" -ForegroundColor Green
    $preview = $r.Content
    if ($preview.Length -gt 400) { $preview = $preview.Substring(0, 400) + "..." }
    Write-Host "Response: $preview"
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 4. Test wrong password ===" -ForegroundColor Cyan
try {
    $body = '{"username":"admin","password":"wrong"}'
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "Response: $($r.Content)"
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Host "[Expected Error] HTTP $status - $($_.Exception.Message)" -ForegroundColor Yellow
}
