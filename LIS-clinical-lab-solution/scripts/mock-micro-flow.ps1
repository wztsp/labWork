# ============================
# LIS 模拟 5 条微生物全流程数据脚本
# 申请 -> 标本 -> 采集 -> 培养基条码生成
# ============================
$base = "http://localhost:8080/api"

# 1) 登录
Write-Host "=== 1. 登录获取 Token ===" -ForegroundColor Cyan
$loginBody = @{ username = "admin"; password = "123456" } | ConvertTo-Json
try {
    $loginRes = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -Body $loginBody -ContentType "application/json; charset=utf-8"
} catch {
    Write-Host "登录失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
# 兼容多种返回结构
$token = if ($loginRes.token) { $loginRes.token } `
         elseif ($loginRes.data.token) { $loginRes.data.token } `
         else { ($loginRes | ConvertTo-Json -Depth 5) }
Write-Host "Token 已获取 (前 20 位): $($token.ToString().Substring(0, [Math]::Min(20, $token.Length)))" -ForegroundColor Green
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json; charset=utf-8" }

# 2) 获取检验项目字典 (找到微生物类项目)
Write-Host "`n=== 2. 获取微生物检验项目字典 ===" -ForegroundColor Cyan
function DoGet($url) {
    try {
        return Invoke-RestMethod -Uri $url -Method GET -Headers $headers
    } catch {
        Write-Host "GET 失败 $url : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}
function DoPost($url, $bodyObj) {
    try {
        $json = if ($bodyObj) { $bodyObj | ConvertTo-Json -Depth 10 } else { $null }
        return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -Body $json
    } catch {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $errText = $reader.ReadToEnd()
        Write-Host "POST 失败 $url : $errText" -ForegroundColor Red
        return $null
    }
}

$allItems = DoGet("$base/base/items/dict")
if (-not $allItems) {
    Write-Host "获取检验项目失败，尝试分页接口" -ForegroundColor Yellow
    $page = DoGet("$base/base/items?current=1&size=500")
    $allItems = if ($page.records) { $page.records } elseif ($page.list) { $page.list } else { $page.data.records }
}

# 兼容 data 包裹
$items = if ($allItems -is [array]) { $allItems } `
         elseif ($allItems.data -is [array]) { $allItems.data } `
         else { @() }
Write-Host "共获取检验项目 $($items.Count) 条"

# 按关键字匹配微生物培养项目
function FindItem($kw) {
    $hit = $items | Where-Object {
        $name = if ($_.itemName) { $_.itemName } else { $_.testItemName }
        $code = if ($_.itemCode) { $_.itemCode } else { $_.testItemCode }
        return ($name -match $kw) -or ($code -match $kw)
    }
    if (-not $hit) { $hit = $items | Select-Object -First 1 }
    if ($hit -is [array]) { $hit = $hit[0] }
    return $hit
}

$sputumItem    = FindItem "痰.*培养|一般细菌培养"         # 痰培养
$urineItem     = FindItem "尿.*培养"                     # 尿培养
$bloodItem     = FindItem "血培养$|血液培养"              # 血培养
$secretionItem = FindItem "分泌.*培养|伤口"               # 分泌物培养
$stoolItem     = FindItem "粪便.*培养|大便培养"           # 粪便培养

function ItemSummary($it, $kw) {
    if (-not $it) { return "$kw -> NULL" }
    $id   = if ($it.id) { $it.id } else { $it.testItemId }
    $name = if ($it.itemName) { $it.itemName } else { $it.testItemName }
    $code = if ($it.itemCode) { $it.itemCode } else { $it.testItemCode }
    return "$kw -> id=$id code=$code name=$name"
}
Write-Host (ItemSummary $sputumItem "痰培养")
Write-Host (ItemSummary $urineItem "尿培养")
Write-Host (ItemSummary $bloodItem "血培养")
Write-Host (ItemSummary $secretionItem "分泌物培养")
Write-Host (ItemSummary $stoolItem "粪便培养")

function ToOrderItem($it) {
    return @{
        testItemId   = [int64](if ($it.id) { $it.id } else { $it.testItemId })
        testItemCode = if ($it.itemCode) { $it.itemCode } else { $it.testItemCode }
        testItemName = if ($it.itemName) { $it.itemName } else { $it.testItemName }
    }
}

# 3) 5 个患者申请数据
Write-Host "`n=== 3. 创建 5 条检验申请 (自动生成标本与采集条码) ===" -ForegroundColor Cyan
$patients = @(
    @{ Name="张三"; Gender="男"; Age="45岁"; Type="门诊"; Dept="呼吸内科"; Doctor="李医生"; Diag="咳嗽咳痰3天，疑似肺部感染"; Item=$sputumItem;    Cond="空腹"; SampleType="呼吸道分泌物" },
    @{ Name="李四"; Gender="女"; Age="32岁"; Type="门诊"; Dept="肾内科";   Doctor="王医生"; Diag="尿频尿急，疑似尿路感染";     Item=$urineItem;     Cond="";     SampleType="尿液" },
    @{ Name="王五"; Gender="男"; Age="67岁"; Type="急诊"; Dept="急诊科";   Doctor="赵医生"; Diag="发热39.2度，疑似败血症";     Item=$bloodItem;     Cond="空腹"; SampleType="血液" },
    @{ Name="赵六"; Gender="女"; Age="29岁"; Type="门诊"; Dept="普外科";   Doctor="钱医生"; Diag="术后伤口红肿渗出";           Item=$secretionItem; Cond="";     SampleType="分泌物" },
    @{ Name="孙七"; Gender="男"; Age="55岁"; Type="住院"; Dept="消化内科"; Doctor="孙医生"; Diag="腹泻伴发热2天";             Item=$stoolItem;     Cond="";     SampleType="粪便" }
)

$visitTypes = @{ "门诊" = 1; "住院" = 2; "急诊" = 3 }

$generated = @()
for ($i = 0; $i -lt $patients.Count; $i++) {
    $p = $patients[$i]
    $order = @{
        patientName         = $p.Name
        patientGender       = $p.Gender
        patientAge          = $p.Age
        orderType           = $p.Type
        visitType           = $visitTypes[$p.Type]
        applyDoctor         = $p.Doctor
        applyDept           = $p.Dept
        clinicalDiag        = $p.Diag
        patientCondition    = $p.Cond
        collectionMethodId  = $null
        collectionMethodName= "常规采集"
        items               = @( ToOrderItem $p.Item )
        specimenType        = $p.SampleType
    }
    Write-Host "`n[$($i+1)/5] 创建申请: $($p.Name) - $($p.SampleType) / $($p.Diag.Substring(0, [Math]::Min(14, $p.Diag.Length)))"
    $res = DoPost "$base/sample/orders/receive" $order
    if (-not $res) { continue }

    # receiveOrder 返回 SampleVO[] 或 data -> SampleVO[]
    $samples = if ($res -is [array]) { $res } `
               elseif ($res.data -is [array]) { $res.data } `
               else { @($res) }
    foreach ($s in $samples) {
        $sample = if ($s.sampleBarcode) { $s } else { $s.data }
        if ($sample.sampleBarcode) {
            $generated += [PSCustomObject]@{
                No            = $i + 1
                Patient       = $p.Name
                Gender        = $p.Gender
                Age           = $p.Age
                OrderType     = $p.Type
                Dept          = $p.Dept
                Doctor        = $p.Doctor
                Diag          = $p.Diag
                TestItem      = (ToOrderItem $p.Item).testItemName
                TestItemId    = (ToOrderItem $p.Item).testItemId
                SampleId      = $sample.id
                SampleBarcode = $sample.sampleBarcode
                SampleType    = $sample.sampleType
                VisitNo       = $sample.visitNo
            }
            Write-Host "  + 生成标本 OK: 条码=$($sample.sampleBarcode) 类型=$($sample.sampleType) id=$($sample.id)" -ForegroundColor Green
        } else {
            Write-Host "  + 返回结构异常: $($s | ConvertTo-Json -Depth 3)" -ForegroundColor Yellow
        }
    }
}

# 4) 对每个标本完成采集
Write-Host "`n=== 4. 完成 5 条标本的采集流程 (模拟采集工作站扫码采集) ===" -ForegroundColor Cyan
$collectResult = @()
foreach ($g in $generated) {
    $collectBody = @{
        sampleBarcode = $g.SampleBarcode
        sampleId      = $g.SampleId
        collectorName = "张护士"
        collectTime   = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
        remark        = "模拟采集"
    }
    # 尝试 POST /sample/collect 或 /sample/{id}/collect
    try {
        $r = DoPost "$base/sample/collect" $collectBody
        if (-not $r) {
            # 备用接口：/sample/{id}/status 或直接 /sample/page 再次确认状态即可
            Write-Host "  [$($g.No)] 调用 /sample/collect 失败 (无此接口也可), 跳过直接下一步" -ForegroundColor Yellow
        } else {
            Write-Host "  [$($g.No)] $($g.Patient) 采集完成 - 条码 $($g.SampleBarcode)" -ForegroundColor Green
        }
    } catch {
        Write-Host "  [$($g.No)] 采集异常: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    $collectResult += $g
}

# 5) 获取培养方案列表 (为生成培养基条码挑选合适方案)
Write-Host "`n=== 5. 获取所有启用培养方案 ===" -ForegroundColor Cyan
$plansRaw = DoGet "$base/micro/culture-plan/all"
$plans = if ($plansRaw -is [array]) { $plansRaw } `
         elseif ($plansRaw.data -is [array]) { $plansRaw.data } `
         else { @() }
Write-Host "可用培养方案: $($plans.Count) 条"
foreach ($p in $plans) {
    Write-Host "  - id=$($p.id) name=$($p.planName)  testItem=$($p.testItemName)"
}

function MatchPlan($g) {
    # 1. 按 testItem 名称精准匹配
    $hit = $plans | Where-Object { $_.testItemName -and $g.TestItem -and $_.testItemName -eq $g.TestItem }
    if ($hit -is [array]) { $hit = $hit[0] }
    if ($hit) { return $hit }
    # 2. 关键字匹配
    $kwMap = @(
        @{ K="痰";     P="痰培养方案" },
        @{ K="尿";     P="尿培养方案" },
        @{ K="血培养"; P="血培养方案" },
        @{ K="分泌";   P="分泌物培养方案" },
        @{ K="粪便";   P="粪便培养方案" },
        @{ K="脑脊液"; P="脑脊液培养方案" },
        @{ K="胸腹";   P="胸腹培养方案" },
        @{ K="真菌";   P="真菌培养方案" },
        @{ K="厌氧";   P="厌氧培养方案" },
        @{ K="淋球菌"; P="淋球菌培养方案" }
    )
    foreach ($km in $kwMap) {
        if ($g.TestItem -match $km.K -or $g.Diag -match $km.K) {
            $h = $plans | Where-Object { $_.planName -eq $km.P }
            if ($h -is [array]) { $h = $h[0] }
            if ($h) { return $h }
        }
    }
    return if ($plans.Count -gt 0) { $plans[0] } else { $null }
}

# 6) 调用生成培养基条码接口
Write-Host "`n=== 6. 为每条标本生成培养基条码 (结合匹配的培养方案) ===" -ForegroundColor Cyan
$allPlates = @()
foreach ($g in $collectResult) {
    $plan = MatchPlan $g
    if (-not $plan) {
        Write-Host "  [$($g.No)] 未匹配到培养方案，跳过生成" -ForegroundColor Yellow
        continue
    }
    $genReq = @{
        sampleBarcode = $g.SampleBarcode
        sampleId      = [int64]$g.SampleId
        testItemIds   = @([int64]$g.TestItemId)
        patientName   = $g.Patient
        operatorId    = $null
        operatorName  = "系统管理员"
    }
    Write-Host "  [$($g.No)] $($g.Patient) - 标本 $($g.SampleBarcode) | 方案='$($plan.planName)'  开始生成..."
    $res = DoPost "$base/micro/sample-plate/generate" $genReq
    $plates = if ($res -is [array]) { $res } `
              elseif ($res.data -is [array]) { $res.data } `
              else { @() }
    Write-Host "      -> 生成 $($plates.Count) 个培养基条码:" -ForegroundColor Green
    foreach ($pl in $plates) {
        Write-Host "         [$($pl.seqNo)] 培养基=$($pl.mediumTypeName)  条码=$($pl.plateBarcode)"
        $allPlates += [PSCustomObject]@{
            Patient        = $g.Patient
            Dept           = $g.Dept
            Diag           = $g.Diag
            SampleBarcode  = $g.SampleBarcode
            PlanName       = $plan.planName
            MediumName     = $pl.mediumTypeName
            SeqNo          = $pl.seqNo
            PlateBarcode   = $pl.plateBarcode
            PlateStatus    = $pl.status
        }
    }
}

# 7) 汇总输出
Write-Host "`n`n=====================================" -ForegroundColor Cyan
Write-Host "  ★★ 模拟全流程数据结果汇总 ★★" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

Write-Host "`n【表1 - 5条申请/标本/采集条码】"
$collectResult | ForEach-Object {
    [PSCustomObject]@{
        序号        = $_.No
        患者        = "$($_.Patient)($($_.Gender)/$($_.Age))"
        科室类型     = "$($_.Dept)/$($_.OrderType)"
        临床诊断     = $_.Diag
        检验项目     = $_.TestItem
        采集条码     = $_.SampleBarcode
    }
} | Format-Table -AutoSize | Out-String | Write-Host

Write-Host "`n【表2 - 生成的培养基条码 (共 $($allPlates.Count) 管)】 - 这些条码可在「微生物报告」页面用于核收"
$allPlates | ForEach-Object {
    [PSCustomObject]@{
        患者        = $_.Patient
        采集条码     = $_.SampleBarcode
        培养方案     = $_.PlanName
        序号         = $_.SeqNo
        培养基名称   = $_.MediumName
        培养基条码   = $_.PlateBarcode
    }
} | Format-Table -AutoSize | Out-String | Write-Host

Write-Host "`n【下一步操作指引】"
Write-Host "  ① 打开前端: http://localhost:3000/  (或 5173)"
Write-Host "  ② 进入菜单 [微生物 → 培养基条码生成] (/micro/patient-data-by-barcode)"
Write-Host "     - 输入上表中的【采集条码】查询患者信息 → 可再次生成或打印培养基条码"
Write-Host "  ③ 进入菜单 [报告 → 微生物报告] (/report/micro)"
Write-Host "     - 在顶部【培养基条码核收】输入框中扫描上表中的【培养基条码】后回车"
Write-Host "     - 系统会自动核收标本并创建微生物报告 / 或定位到已存在报告"
Write-Host "  ④ 使用【培养基条码生成】页面的打印功能打印条码标签"
Write-Host ""
