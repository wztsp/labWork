[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base='http://localhost:8080'
function S($t){Write-Host "";Write-Host "==== $t ====" -ForegroundColor Cyan}
function RAW($url){
  try {
    $r=Invoke-WebRequest -Uri $url -UseBasicParsing
    Write-Host ("STATUS "+$r.StatusCode+"  CT=["+$r.Headers['Content-Type']+"]")
    $c=$r.Content
    if($c.Length -gt 1800){$c=$c.Substring(0,1800)}
    $c
  } catch {
    if($_.Exception.Response){
      $st=[int]$_.Exception.Response.StatusCode
      $sr=New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(),[System.Text.Encoding]::UTF8)
      $body=$sr.ReadToEnd()
      Write-Host ("FAIL STATUS "+$st)
      if($body.Length -gt 1500){$body=$body.Substring(0,1500)}
      $body
    } else { Write-Host "FAIL: $_" }
  }
}
S "OCC page接口(之前路由冲突500) GET $base/api/qc/occ/page?current=1&size=20"
RAW "$base/api/qc/occ/page?current=1&size=20"

S "OCC list接口 GET $base/api/qc/occ/list"
RAW "$base/api/qc/occ/list"

S "OCC8详情(检查中文)"
RAW "$base/api/qc/occ/8"

S "找DEBUG-13S-LOT的id（质控品批号列表 前10）"
RAW "$base/api/qc/lots?current=1&size=10"

S "失控重算 POST lotId=7(RBC/DEBUG-13S-LOT之前事件id=7对应)"
try {
  $r=Invoke-WebRequest -Uri "$base/api/qc/records/recalculate?lotId=7" -Method Post -UseBasicParsing
  Write-Host ("STATUS "+$r.StatusCode)
  $r.Content
} catch {
  if($_.Exception.Response){
    $st=[int]$_.Exception.Response.StatusCode
    $sr=New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(),[System.Text.Encoding]::UTF8)
    Write-Host ("FAIL STATUS "+$st)
    $sr.ReadToEnd()
  }
}
