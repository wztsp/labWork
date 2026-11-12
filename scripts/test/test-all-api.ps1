$ProgressPreference = 'SilentlyContinue'

function Test-Api {
    param([string]$Name, [string]$Url, [int]$TimeoutSec = 5)
    Write-Host "--- $Name ---"
    Write-Host "    URL: $Url"
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        $body = $r.Content
        if ($body.Length -gt 400) { $body = $body.Substring(0, 400) + '...' }
        Write-Host "    Status: $($r.StatusCode) OK"
        Write-Host "    Body: $body"
        return $true
    } catch {
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        Write-Host "    Failed: $($_.Exception.Message)"
        if ($status) { Write-Host "    HTTP Status: $status" }
        return $false
    }
}

# All endpoints use the actual @RequestMapping paths from controllers
$tests = @(
    # System service (port 8087)
    @{ Name = 'system-users'; Url = 'http://localhost:8087/api/system/users' },
    @{ Name = 'system-roles'; Url = 'http://localhost:8087/api/system/roles' },
    @{ Name = 'system-departments'; Url = 'http://localhost:8087/api/system/departments' },
    @{ Name = 'system-configs'; Url = 'http://localhost:8087/api/system/configs' },
    @{ Name = 'system-permissions'; Url = 'http://localhost:8087/api/system/permissions' },
    @{ Name = 'system-permissions-tree'; Url = 'http://localhost:8087/api/system/permissions/tree' },
    # Base service (port 8085)
    @{ Name = 'base-test-items'; Url = 'http://localhost:8085/api/base/items' },
    @{ Name = 'base-reference-ranges'; Url = 'http://localhost:8085/api/base/ref-ranges' },
    @{ Name = 'base-instruments'; Url = 'http://localhost:8085/api/base/instruments' },
    # Sample service (port 8081)
    @{ Name = 'samples-page'; Url = 'http://localhost:8081/api/sample/page' },
    # Report service (port 8082)
    @{ Name = 'reports-page'; Url = 'http://localhost:8082/api/report/page' },
    # QC service (port 8083)
    @{ Name = 'qc-lots-list'; Url = 'http://localhost:8083/api/qc/lots/list' },
    @{ Name = 'qc-lots-enabled'; Url = 'http://localhost:8083/api/qc/lots/enabled' },
    @{ Name = 'qc-records-page'; Url = 'http://localhost:8083/api/qc/records/page' },
    # Instrument comm service (port 8084)
    @{ Name = 'instrument-status'; Url = 'http://localhost:8084/api/instrument/status' },
    # Reagent service (port 8092)
    @{ Name = 'reagent-dict'; Url = 'http://localhost:8092/api/reagent/dict' },
    # Micro service (port 8093)
    @{ Name = 'micro-antibiotics'; Url = 'http://localhost:8093/api/micro/antibiotics' },
    @{ Name = 'micro-antibiotics-all'; Url = 'http://localhost:8093/api/micro/antibiotics/all' },
    @{ Name = 'micro-resistance-rules'; Url = 'http://localhost:8093/api/micro/resistance-rules' },
    @{ Name = 'micro-resistance-rules-all'; Url = 'http://localhost:8093/api/micro/resistance-rules/all' },
    # HR service (port 8094)
    @{ Name = 'hr-staff'; Url = 'http://localhost:8094/api/hr/staff' },
    # Stats service (port 8088)
    @{ Name = 'stats-workload'; Url = 'http://localhost:8088/api/stats/workload' },
    @{ Name = 'stats-workload-by-day'; Url = 'http://localhost:8088/api/stats/workload/by-day' },
    @{ Name = 'stats-workload-by-dept'; Url = 'http://localhost:8088/api/stats/workload/by-dept' }
)

$pass = 0
$fail = 0
foreach ($t in $tests) {
    $ok = Test-Api -Name $t.Name -Url $t.Url
    if ($ok) { $pass++ } else { $fail++ }
    Write-Host ""
}

Write-Host "============================================"
Write-Host "Total: $($tests.Count), Pass: $pass, Fail: $fail"
Write-Host "============================================"
