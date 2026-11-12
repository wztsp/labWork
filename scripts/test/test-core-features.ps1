$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Send-Json {
    param([string]$Method, [string]$Url, [string]$Json, [int]$TimeoutSec = 10)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
    try {
        $r = Invoke-WebRequest -Uri $Url -Method $Method -Body $bytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing -TimeoutSec $TimeoutSec
        Write-Host "Status: $($r.StatusCode) OK"
        Write-Host "Body: $($r.Content)"
        return $r
    } catch {
        Write-Host "Failed: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            try {
                $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
                Write-Host "Body: $($sr.ReadToEnd())"
            } catch {}
        }
        return $null
    }
}

Write-Host "=== Reset admin password to 123456 (PUT /api/system/users/1/reset-password) ==="
$json = '{"newPassword":"123456"}'
Send-Json -Method PUT -Url 'http://localhost:8087/api/system/users/1/reset-password' -Json $json

Write-Host ""
Write-Host "=== Reset tech password to 123456 ==="
Send-Json -Method PUT -Url 'http://localhost:8087/api/system/users/2/reset-password' -Json $json

Write-Host ""
Write-Host "=== Reset director password to 123456 ==="
Send-Json -Method PUT -Url 'http://localhost:8087/api/system/users/3/reset-password' -Json $json

Write-Host ""
Write-Host "=== Test login admin/123456 ==="
$json = '{"username":"admin","password":"123456"}'
$resp = Send-Json -Method POST -Url 'http://localhost:8090/api/auth/login' -Json $json
$token = $null
if ($resp) {
    try {
        $obj = $resp.Content | ConvertFrom-Json
        if ($obj.success -and $obj.data.token) { $token = $obj.data.token }
    } catch {}
}
if ($token) {
    Write-Host "Login OK! Token: $($token.Substring(0,60))..."
}

Write-Host ""
Write-Host "=== Test login tech/123456 ==="
Send-Json -Method POST -Url 'http://localhost:8090/api/auth/login' -Json '{"username":"tech","password":"123456"}'

Write-Host ""
Write-Host "=== Test /api/auth/info with token ==="
if ($token) {
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8090/api/auth/info' -Headers @{Authorization="Bearer $token"} -UseBasicParsing -TimeoutSec 10
        Write-Host "Status: $($r.StatusCode) OK"
        Write-Host "Body: $($r.Content)"
    } catch {
        Write-Host "Failed: $($_.Exception.Message)"
    }
} else { Write-Host "Skipping (no token)" }

Write-Host ""
Write-Host "=== Test QC Charts Levey-Jennings (GET /api/qc/charts/lj?lotId=1) ==="
try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/qc/charts/lj?lotId=1&limit=20' -UseBasicParsing -TimeoutSec 10
    Write-Host "Status: $($r.StatusCode) OK"
    Write-Host "Body: $($r.Content)"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        try {
            $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
            Write-Host "Body: $($sr.ReadToEnd())"
        } catch {}
    }
}

Write-Host ""
Write-Host "=== Test QC Charts Z-Score (GET /api/qc/charts/z-score?lotId=1) ==="
try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/qc/charts/z-score?lotId=1&limit=20' -UseBasicParsing -TimeoutSec 10
    Write-Host "Status: $($r.StatusCode) OK"
    Write-Host "Body: $($r.Content)"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "=== Done ==="
