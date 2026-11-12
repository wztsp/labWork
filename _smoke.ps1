$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
function Show-Section($t){ Write-Host ""; Write-Host "==== $t ====" -ForegroundColor Cyan }
$base = 'http://localhost:8080'

Show-Section "OCC列表 GET $base/api/qc/occ/list"
try {
  $resp = Invoke-WebRequest -Uri "$base/api/qc/occ/list" -Method Get -UseBasicParsing
  Write-Host ("STATUS: " + $resp.StatusCode)
  Write-Host ("Content-Type: " + $resp.Headers['Content-Type'])
  $obj = ($resp.Content | ConvertFrom-Json)
  Write-Host ("list count: " + $obj.data.Count)
  # 打印每条：id 状态 中文项目名 规则
  foreach ($e in $obj.data) {
    Write-Host ("  id={0,-3} status={1,-14} step={2} name=[{3}] rules=[{4}]" -f $e.id,$e.status,$e.currentStep,$e.projectName,$e.triggerRules)
  }
} catch { Write-Host "FAIL: $_" }

Show-Section "失控重算 POST $base/api/qc/records/recalculate (无参数=全量)"
try {
  $resp = Invoke-WebRequest -Uri "$base/api/qc/records/recalculate" -Method Post -UseBasicParsing
  Write-Host ("STATUS: " + $resp.StatusCode)
  Write-Host ("Content-Type: " + $resp.Headers['Content-Type'])
  $resp.Content.Substring(0, [Math]::Min(600,$resp.Content.Length))
} catch {
  if ($_.Exception.Response) {
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    Write-Host ("FAIL STATUS: " + [int]$_.Exception.Response.StatusCode)
    $reader.ReadToEnd().Substring(0,1200)
  } else { Write-Host "FAIL: $_" }
}

Show-Section "OCC8详情 GET $base/api/qc/occ/8 (验证步骤中文名)"
try {
  $resp = Invoke-WebRequest -Uri "$base/api/qc/occ/8" -Method Get -UseBasicParsing
  $obj = ($resp.Content | ConvertFrom-Json).data
  Write-Host ("id={0} name=[{1}] group=[{2}]" -f $obj.id,$obj.projectName,$obj.groupName)
  foreach ($st in $obj.steps) {
    # checklist解析后第一条
    $chk1 = ''
    try { $arr = $st.checklist | ConvertFrom-Json; if($arr.Count -gt 0){$chk1 = $arr[0].text} } catch {}
    Write-Host ("  step {0} name=[{1}] status={2}  chk1=[{3}]" -f $st.stepNo,$st.name,$st.status,$chk1)
  }
  foreach ($a in $obj.approvals) {
    Write-Host ("  apv {0} name=[{1}] status={2}" -f $a.levelNo,$a.levelName,$a.status)
  }
} catch { Write-Host "FAIL: $_" }
