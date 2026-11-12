@echo off
chcp 65001 > nul
setlocal
set H=Content-Type: application/json
echo ==== 1) 调用失控计算接口(全部时间) POST /api/qc/records/recalculate 无参数 ====
powershell -NoProfile -Command "$r=try{Invoke-RestMethod -Uri 'http://localhost:8080/api/qc/records/recalculate' -Method Post -UseBasicParsing}catch{$_ | Out-String}; $r | ConvertTo-Json -Depth 6" 2>&1
echo.
echo ==== 2) 查OCC列表 GET /api/qc/occ/list ====
powershell -NoProfile -Command "$r=Invoke-RestMethod -Uri 'http://localhost:8080/api/qc/occ/list' -Method Get -UseBasicParsing; $r | ConvertTo-Json -Depth 6" 2>&1
echo.
echo ==== 3) 拿最新OCC(8号)详情 GET /api/qc/occ/8 ====
powershell -NoProfile -Command "$r=Invoke-RestMethod -Uri 'http://localhost:8080/api/qc/occ/8' -Method Get -UseBasicParsing; $r | ConvertTo-Json -Depth 6" 2>&1
