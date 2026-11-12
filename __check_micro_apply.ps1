$ErrorActionPreference = 'Stop'
$body = @{ username='admin'; password='123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $body
$token = if ($login.data -and $login.data.token) { $login.data.token } elseif ($login.token) { $login.token } else { $login }
Write-Host "Login ok, token length: $($token.Length)"
$h = @{ Authorization = "Bearer $token" }

Write-Host "--- GET /api/report/micro/page?current=1&size=8 RAW ---"
$raw = Invoke-WebRequest -Uri 'http://localhost:8080/api/report/micro/page?current=1&size=8' -Headers $h
$raw.Content | Out-Host
