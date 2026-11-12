$base = 'http://localhost:8087'

Write-Host "=== Test 1: GET /api/system/users ==="
try {
    $r = Invoke-WebRequest -Uri "$base/api/system/users" -UseBasicParsing -TimeoutSec 5
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "Body: $($r.Content.Substring(0, [math]::Min(800, $r.Content.Length)))"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Body: $body"
    }
}

Write-Host ""
Write-Host "=== Test 2: GET /api/system/roles ==="
try {
    $r = Invoke-WebRequest -Uri "$base/api/system/roles" -UseBasicParsing -TimeoutSec 5
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "Body: $($r.Content.Substring(0, [math]::Min(800, $r.Content.Length)))"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "=== Test 3: GET /api/system/permissions ==="
try {
    $r = Invoke-WebRequest -Uri "$base/api/system/permissions" -UseBasicParsing -TimeoutSec 5
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "Body: $($r.Content.Substring(0, [math]::Min(800, $r.Content.Length)))"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "=== Test 4: GET /api/system/configs ==="
try {
    $r = Invoke-WebRequest -Uri "$base/api/system/configs" -UseBasicParsing -TimeoutSec 5
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "Body: $($r.Content.Substring(0, [math]::Min(800, $r.Content.Length)))"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
}
