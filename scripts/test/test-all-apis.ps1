# LIS System API Test Script
# Usage: powershell -ExecutionPolicy Bypass -File test-all-apis.ps1
$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
$pass = 0; $fail = 0; $skip = 0

function Test-Api {
    param([string]$name, [string]$method, [string]$url, [string]$body = $null, [hashtable]$headers = @{}, [int]$expect = 200)
    Write-Host ""
    Write-Host "[$method] $name -> $url" -ForegroundColor Cyan
    try {
        $params = @{ Uri = $url; Method = $method; UseBasicParsing = $true; TimeoutSec = 15 }
        if ($headers.Count -gt 0) { $params.Headers = $headers }
        if ($body) { $params.Body = $body; $params.ContentType = "application/json" }
        $r = Invoke-WebRequest @params
        $ok = $r.StatusCode -eq $expect
        if ($ok) {
            Write-Host "  [PASS] HTTP $($r.StatusCode)" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "  [FAIL] HTTP $($r.StatusCode), expected $expect" -ForegroundColor Red
            $script:fail++
        }
        $preview = $r.Content
        if ($preview.Length -gt 300) { $preview = $preview.Substring(0, 300) + "..." }
        Write-Host "  Response: $preview"
        return $r.Content
    } catch {
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq 401 -or $status -eq 403) {
            Write-Host "  [SKIP] HTTP $status - auth required" -ForegroundColor Yellow
            $script:skip++
        } else {
            Write-Host "  [FAIL] $($_.Exception.Message)" -ForegroundColor Red
            $script:fail++
            if ($_.ErrorDetails) { Write-Host "  Detail: $($_.ErrorDetails.Message)" }
        }
        return $null
    }
}

Write-Host "===== LIS System API Test =====" -ForegroundColor Cyan
Write-Host "Gateway: $base"

# 1. Login to get token
$loginBody = '{"username":"admin","password":"123456"}'
$resp = Test-Api -name "User Login" -method Post -url "$base/api/auth/login" -body $loginBody
if ($resp) {
    $token = ($resp | ConvertFrom-Json).data.token
    Write-Host "  >> Token: $($token.Substring(0, [Math]::Min(50, $token.Length)))..." -ForegroundColor DarkGray
} else {
    Write-Host "Login failed, remaining tests will be affected" -ForegroundColor Red
    $token = ""
}
$auth = @{ Authorization = "Bearer $token" }

# 2. System Service (root-path pagination pattern)
Write-Host "`n===== System Service =====" -ForegroundColor Cyan
Test-Api -name "Current User Info" -method Get -url "$base/api/system/users/me" -headers $auth
Test-Api -name "User List" -method Get -url ($base + "/api/system/users?current=1&size=10") -headers $auth
Test-Api -name "Role List" -method Get -url ($base + "/api/system/roles?current=1&size=10") -headers $auth
Test-Api -name "Permission List" -method Get -url "$base/api/system/permissions" -headers $auth
Test-Api -name "Permission Tree" -method Get -url "$base/api/system/permissions/tree" -headers $auth

# 3. Base Data Service (root-path pagination, custom paths)
Write-Host "`n===== Base Data Service =====" -ForegroundColor Cyan
Test-Api -name "Test Items List" -method Get -url ($base + "/api/base/items?current=1&size=10") -headers $auth
Test-Api -name "Reference Ranges List" -method Get -url ($base + "/api/base/ref-ranges?current=1&size=10") -headers $auth
Test-Api -name "Instrument List (base)" -method Get -url ($base + "/api/base/instruments?current=1&size=10") -headers $auth
Test-Api -name "Instrument Status List" -method Get -url "$base/api/base/instrument-status" -headers $auth

# 4. Sample Service
Write-Host "`n===== Sample Service =====" -ForegroundColor Cyan
Test-Api -name "Sample List" -method Get -url ($base + "/api/sample/page?pageNo=1&pageSize=10") -headers $auth

# 5. Report Service
Write-Host "`n===== Report Service =====" -ForegroundColor Cyan
Test-Api -name "Report List" -method Get -url ($base + "/api/report/page?pageNo=1&pageSize=10") -headers $auth

# 6. QC Service (mixed: records/page exists, lots uses /list)
Write-Host "`n===== QC Service =====" -ForegroundColor Cyan
Test-Api -name "QC Lots List" -method Get -url "$base/api/qc/lots/list" -headers $auth
Test-Api -name "QC Lots Enabled" -method Get -url "$base/api/qc/lots/enabled" -headers $auth
Test-Api -name "QC Records List" -method Get -url ($base + "/api/qc/records/page?current=1&size=10") -headers $auth
Test-Api -name "QC EQA List" -method Get -url ($base + "/api/qc/eqa/page?current=1&size=10") -headers $auth
Test-Api -name "QC Chart LJ" -method Get -url ($base + "/api/qc/charts/lj?lotId=1&testItemId=1") -headers $auth

# 7. Instrument Management Service (root-path pagination, /templates path)
Write-Host "`n===== Instrument Mgmt Service =====" -ForegroundColor Cyan
Test-Api -name "Maintenance Templates List" -method Get -url ($base + "/api/instrument-mgmt/templates?current=1&size=10") -headers $auth
Test-Api -name "Maintenance Plans Due" -method Get -url "$base/api/instrument-mgmt/plans/due" -headers $auth
Test-Api -name "Maintenance Plans Tasks" -method Get -url "$base/api/instrument-mgmt/plans/tasks" -headers $auth
Test-Api -name "Maintenance Logs List" -method Get -url ($base + "/api/instrument-mgmt/logs?current=1&size=10") -headers $auth

# 8. Reagent Service (/dict and /stock paths)
Write-Host "`n===== Reagent Service =====" -ForegroundColor Cyan
Test-Api -name "Reagent List" -method Get -url ($base + "/api/reagent/dict?current=1&size=10") -headers $auth
Test-Api -name "Reagent Stock List" -method Get -url ($base + "/api/reagent/stock?current=1&size=10") -headers $auth
Test-Api -name "Reagent Near Expiry" -method Get -url "$base/api/reagent/stock/near-expiry" -headers $auth
Test-Api -name "Reagent Below Safety" -method Get -url "$base/api/reagent/stock/below-safety" -headers $auth
Test-Api -name "Reagent Records" -method Get -url ($base + "/api/reagent/records?current=1&size=10") -headers $auth

# 9. Microbiology Service (root-path pagination, singular blood-culture)
Write-Host "`n===== Microbiology Service =====" -ForegroundColor Cyan
Test-Api -name "Antibiotic List" -method Get -url ($base + "/api/micro/antibiotics?current=1&size=10") -headers $auth
Test-Api -name "Antibiotic All" -method Get -url "$base/api/micro/antibiotics/all" -headers $auth
Test-Api -name "Resistance Rule List" -method Get -url ($base + "/api/micro/resistance-rules?current=1&size=10") -headers $auth
Test-Api -name "Resistance Rule All" -method Get -url "$base/api/micro/resistance-rules/all" -headers $auth
Test-Api -name "Blood Culture List" -method Get -url ($base + "/api/micro/blood-culture?current=1&size=10") -headers $auth

# 10. HR Service (root-path pagination, no /page for groups/titles)
Write-Host "`n===== HR Service =====" -ForegroundColor Cyan
Test-Api -name "Staff List" -method Get -url ($base + "/api/hr/staff?current=1&size=10") -headers $auth
Test-Api -name "Lab Group List" -method Get -url "$base/api/hr/groups" -headers $auth
Test-Api -name "Lab Group Tree" -method Get -url "$base/api/hr/groups/tree" -headers $auth
Test-Api -name "Lab Documents List" -method Get -url ($base + "/api/hr/documents?current=1&size=10") -headers $auth
Test-Api -name "Staff Certificates Expiring" -method Get -url "$base/api/hr/staff/certificates/expiring" -headers $auth

# 11. Statistics Service
Write-Host "`n===== Statistics Service =====" -ForegroundColor Cyan
Test-Api -name "Workload Stats" -method Get -url ($base + "/api/stats/workload?startDate=2026-07-01&endDate=2026-07-31") -headers $auth
Test-Api -name "Critical Stats" -method Get -url ($base + "/api/stats/critical?startDate=2026-07-01&endDate=2026-07-31") -headers $auth

# 12. Integration Service (/adapters path, no his-adapters)
Write-Host "`n===== Integration Service =====" -ForegroundColor Cyan
Test-Api -name "HIS Adapter List" -method Get -url "$base/api/integration/adapters" -headers $auth

# 13. Instrument Communication Service (/status path, /{id}/logs)
Write-Host "`n===== Instrument Communication =====" -ForegroundColor Cyan
Test-Api -name "Instrument Status" -method Get -url "$base/api/instrument/status" -headers $auth
Test-Api -name "Instrument Logs (id=1)" -method Get -url ($base + "/api/instrument/1/logs?page=1&size=10") -headers $auth
Test-Api -name "Instrument Brands" -method Get -url "$base/api/instrument/data/brands" -headers $auth

# Test Report
Write-Host "`n`n===== Test Report =====" -ForegroundColor Cyan
Write-Host "  PASS: $pass" -ForegroundColor Green
Write-Host "  FAIL: $fail" -ForegroundColor Red
Write-Host "  SKIP: $skip" -ForegroundColor Yellow
Write-Host "  TOTAL: $($pass + $fail + $skip)"
if ($fail -eq 0) {
    Write-Host "`nAll tests passed!" -ForegroundColor Green
} else {
    Write-Host "`n$fail test(s) failed, please check logs" -ForegroundColor Red
}
