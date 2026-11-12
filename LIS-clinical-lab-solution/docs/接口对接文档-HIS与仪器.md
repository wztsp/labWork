# LIS 接口对接文档（HIS 接口 + 仪器对接）

> 适用范围：LIS 检验信息系统
> 说明：本文档整理两部分内容
> 1. **HIS 对接接口** —— HIS 向 LIS 下发检验申请、LIS 向 HIS 回传数据的接口及调用界面
> 2. **仪器对接接口** —— 仪器数据接收、条目匹配、无主标本手工调整与技师工作站认领

---

## 一、角色与调用关系总览

```
                    ┌─────────────────── HIS 系统 ───────────────────┐
                    │                                                │
  ① 申请下发(拉取/推送)│                                                │ ③ 结果/状态/危急值/费用 回传
                    ▼                                                ▲
        ┌────────────────────────────┐  ┌──────────────────────────┐ │
        │  lis-service-integration   │──┤  定时任务 + 手动接口        │ │
        │  (HisAdapter 适配层)        │  └──────────────────────────┘ │
        └────────────────────────────┘                              │
                    │ ② 创建标本申请                                 │
                    ▼                                                │
        ┌────────────────────────────┐                    ┌─────────┘
        │  标本服务 / 报告服务           │───────────────────┘
        └────────────────────────────┘
                        ▲ 结果自动匹配(按条码)
        ┌────────────────────────────┐
        │  lis-service-instrument     │
        │  ④ 仪器数据接收 + ASTM/HL7 解析│
        │   └─ 未匹配 → 无主标本表        │
        └────────────────────────────┘
                        ▲ 仪器原始数据(条码/无条码)
                    ┌───┴────────┐
                  (仪器TCP/串口/文件/数据库)
```

---

# 第一部分：HIS 对接接口

## 1. 概览

| 方向 | 业务 | 对接方式 | 触发 | 关键实现 |
|------|------|----------|------|----------|
| HIS→LIS | 检验申请下发 | REST 拉取 / HL7 ORM^O01 / Oracle 直连 | 定时任务 + 手动 | `OrderSyncJob`、`IntegrationController` |
| LIS→HIS | 检验报告结果回传 | REST POST / HL7 ORU^R01 | 定时任务 + 手动 | `ResultSyncJob`、`IntegrationController` |
| LIS→HIS | 患者信息查询 | REST GET | 手动 | `IntegrationController` |
| LIS→HIS | 标本状态回传 | REST POST + MQ | 事件驱动 + 重试 | `HisNotifyService` |
| LIS→HIS | 危急值回传 | REST POST + MQ | 事件驱动 + 重试 | `HisNotifyService` |
| LIS→HIS | 费用回传 | REST POST | 手动 | `IntegrationController` |

**适配器机制**（`HisAdapter` / `HisAdapterFactory`）：支持 `UNIVERSAL_REST`、`HL7V2`、`FHIR`、`ORACLE_DB` 多厂商，通过 `integration.his.default-vendor` 指定默认厂商，接口可按需以 `adapterId` 参数切换。

**调用界面（触发方式）说明**：
- **定时任务**（后台自动，默认每 5 分钟）：
  - 申请拉取：`integration.sync.order-cron`（默认 `0 */5 * * * ?`）
  - 报告回传：`integration.sync.result-cron`（默认 `0 */5 * * * ?`），受 `integration.sync.result-enabled` 控制
  - 失败重试：`HisNotifyService.retryFailed()`（每 5 分钟）
- **手动触发**：通过以下 REST 接口人工发起对接（可用于联调、补数）
- **HL7 消息接收**：HIS 直接 POST HL7 报文到 LIS

---

## 2. HIS → LIS：检验申请下发

### 2.1 手动拉取申请

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/sync/orders` |
| 作用 | 调用默认(或指定)适配器，从 **HIS** 拉取时间区间内的检验申请 |
| 调用界面 | 定时任务 `OrderSyncJob` 与人工联调时调用 |
| 返回 | `R<List<OrderDTO>>`（申请单列表） |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `startTime` | Long | 否 | 起始时间戳(ms)，缺省=当前-1小时 |
| `endTime` | Long | 否 | 结束时间戳(ms)，缺省=当前 |

**示例**
```bash
curl -X POST \
  'http://LIS:8080/api/integration/sync/orders?startTime=1735603200000&endTime=1735606800000'
```

**响应示例**（`OrderDTO`）
```json
{
  "code": 200,
  "msg": "ok",
  "data": [
    {
      "orderNo": "ORD202401010001",
      "visitId": 1001,
      "patientId": 2001,
      "patientName": "张三",
      "orderType": "1",
      "applyDoctor": "李医生",
      "applyDept": "内科",
      "clinicalDiag": "发热待查",
      "patientCondition": "空腹",
      "collectionMethodId": 5,
      "collectionMethodName": "静脉血",
      "items": [
        { "itemCode": "GLU", "itemName": "葡萄糖", "specimenType": "01", "tubeType": "AT" }
      ]
    }
  ]
}
```

### 2.2 定时拉取申请

- 任务类：`OrderSyncJob.syncOrders()`
- 频率：默认每 5 分钟拉取最近 5 分钟的新增申请
- 说明：简化场景下仅打印日志（不落库）；生产环境应通过内部调用创建标本申请

### 2.3 接收 HIS 推送的 HL7 申请（ORM^O01）

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/hl7/receive` |
| 参数 | `messageType`（默认 `ORM_O01`）；body 为 HL7 报文原文 |
| 作用 | 解析 HL7 ORM^O01（检验医嘱）→ `OrderDTO` |

**示例**
```bash
curl -X POST 'http://LIS:8080/api/integration/hl7/receive?messageType=ORM_O01' \
  -H 'Content-Type: text/plain' --data-binary @order.hl7
```
响应：`R.ok("ORM^O01 解析成功: orderNo=...")`

> 本方案还提供 `ADT_A01`（患者入出院/基本信息）消息解析，返回 `PatientInfo`。

### 2.4 患者信息查询

| 项 | 内容 |
|----|------|
| 方法 | `GET` |
| 路径 | `/api/integration/patients/{patientId}` |
| 参数 | `visitId`（可选）、`adapterId`（可选） |
| 返回 | `R<PatientInfo>` |

---

## 3. LIS → HIS：数据回传

### 3.1 检验报告结果回传（手动）

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/sync/results` |
| 参数 | `adapterId`（可选）；body 为 `LabReport` |
| 作用 | 将一份检验报告（含结果项）主动回传给 HIS |
| 调用界面 | 结果审核发布后可触发；也可作为补推接口 |

**请求体**（`LabReport`）
```json
{
  "reportNo": "RPT202401010001",
  "sampleId": 5001,
  "patientId": "2001",
  "patientName": "张三",
  "reportType": "ROUTINE",
  "reportDoctor": "王技师",
  "checker": "赵主任",
  "publishTime": "2024-01-01T10:30:00",
  "results": [
    {
      "itemCode": "GLU",
      "itemName": "葡萄糖",
      "resultValue": "5.5",
      "unit": "mmol/L",
      "refRange": "3.9-6.1",
      "abnormalFlag": "N"
    }
  ]
}
```
响应：`R<Boolean>`（是否回传成功）

### 3.2 报告自动回传（定时批量）

- 任务类：`ResultSyncJob.syncResults()`
- 频率：默认每 5 分钟；开关 `integration.sync.result-enabled`，每次上限 `result-batch-size`(默认 50)
- 回传条件：扫描 `lis_report` 中 `status = 2`（已发布/终审）且 `his_sync_status = 0` 的报告
- 成功：更新 `his_sync_status = 1, his_sync_time = now`
- 失败：记录日志等待下次重试

### 3.3 标本状态回传（事件驱动 + 失败重试）

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/samples/status` |
| 参数 | `adapterId`（可选）；body 为 `SampleStatusUpdate` |
| 触发 | `HisNotifyService` 监听 MQ `sample.status.changed` → REST POST |

**`SampleStatusUpdate`**
```json
{
  "orderNo": "ORD202401010001",
  "sampleBarcode": "YB202401010001",
  "status": "RECEIVED",
  "updateTime": "2024-01-01T09:00:00",
  "operatorName": "李护士",
  "remark": ""
}
```
状态枚举：`COLLECTED` 已采集 / `RECEIVED` 已核收 / `TESTING` 检测中 / `COMPLETED` 已完成 / `REJECTED` 已拒收

### 3.4 危急值回传

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/critical-alerts` |
| 触发 | `HisNotifyService` 监听 MQ `critical.alert` → REST POST |
| 示例 | `CriticalValueAlert{ patientId, patientName, sampleBarcode, itemCode, itemName, resultValue, unit, refRange, alertTime, alertSource, remark }` |

### 3.5 费用回传

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/integration/fees` |
| 参数 | `adapterId`（可选）；body 为 `FeeBilling` |
| 说明 | 将检验计费/扣费信息回传 HIS |

### 3.6 失败重试机制

- 监听回传（危急值/标本状态）失败时写入 `lis_his_sync_retry` 重试表（`status=0`）
- `HisNotifyService.retryFailed()` 每 5 分钟重试，超过最大次数(默认 6 次)标记 `status=2` 放弃

---

## 4. HIS 对接相关配置与表

**配置（application.yml）**
```yaml
integration:
  his:
    default-vendor: UNIVERSAL_REST
    vendors:
      UNIVERSAL_REST:
        base-url: http://his.example.com/api   # 改为实际 HIS 地址
        auth-token: ${HIS_TOKEN:dev-token}
      HL7V2:    { host: his.example.com, port: 2575 }
      FHIR:     { base-url: http://his.example.com/fhir, auth-token: ${HIS_FHIR_TOKEN:dev-token} }
      ORACLE_DB: { ... }    # HIS 为 Oracle 时：直连 + 自定义 SQL
  sync:
    order-cron: "0 */5 * * * ?"     # 申请拉取
    result-cron: "0 */5 * * * ?"    # 报告回传
    result-enabled: true
    result-batch-size: 50
```

**关键表**
| 表 | 作用 |
|----|------|
| `lis_report` | `his_sync_status`(0未回传/1已回传)、`his_sync_time` 标志报告是否已同步 HIS |
| `lis_his_sync_retry` | 危急值/标本状态回传失败的重试队列 |

**健康检查**
- `GET /api/integration/health/{adapterId}` → `R<Boolean>`
- `GET /api/integration/adapters` → 可用适配器列表

---

# 第二部分：仪器对接接口

## 1. 概览

```
仪器(迈瑞/希森美康/罗氏/雅培...)  ──TCP/串口/文件/数据库──▶  数据接收
                                                         │
                                          ASTMML7/LIS01 解析器
                                                         │
                                    ResultMatchingService.matchAndSave()
                                                     │
                              ┌──────────────────────┼────────────────────────┐
                         有条码/样本号匹配到业务标本        │            未匹配到业务标本
                          (sample_barcode OR sample_no) │                 (无主标本)
                              ↓                         ↓                    ↓
                          LIS 正式结果                落库 lis_instrument_unmatched_result
                     (MQ 通知/直连落库 lis_result)     技师工作站「无主标本」弹窗
                                                      ├─ 手工调整结果值(可改)
                                                      ├─ 按条码「匹配」→回写正式结果
                                                      └─ 「作废」
```

## 2. 仪器数据接收接口

### 2.1 手动/测试接收原始报文（`DataReceiveController`）

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/instrument/data/receive` |
| 参数 | `instrumentId`(Long)、`brand`(MINDRAY/SYSMEX/ROCHE/ABBOTT) |
| body | 原始报文文本（ASTM/HL7 等） |
| 返回 | `R<Boolean>` |
| 调用界面 | 联调 / 手工灌数据时使用；生产由 TCP/串口/文件/DB 采集通道自动调用对应 `InstrumentHandler` |

**示例（ASTM 报文，含无条码样本 `NOMB0001`）**
```bash
curl -X POST 'http://LIS:8080/api/instrument/data/receive?instrumentId=1&brand=MINDRAY' \
  -H 'Content-Type: text/plain' --data-binary $'H|\\^&|||psiv3^^^^|||LIS||||||P|1|20240101\nP|1||||||Smith^John||19800101|M|||||^||\nO|1|NOMB0001||^^^GLU|N|20240101120000||||||N||||||||||||||O\nR|1|^^^GLU|6.8|mmol/L|3.9-6.1|H||F|||||||||'
```

### 2.2 可用品牌

- `GET /api/instrument/data/brands` → `R<List<String>>`

### 2.3 通讯日志

- `GET /api/instrument/{id}/logs` 或 `GET /api/instrument/logs` → `PageResult<CommLog>`
- 记录每次接收的原始报文、协议类型、解析状态、解析结果（`lis_instrument_comm_log`）

### 2.4 微生物鉴定 / 药敏结果回传（`send-micro`，通信程序模拟链路）

> 用途：**微生物分析仪（BD Phoenix / 梅里埃 VITEK 等）**、或 LIS 通信程序回传细菌鉴定 + 药敏结果。
> 与普通 `/send` 不同：不解析 ASTM 报文，直接按 **培养基条码 / 采集条码** 匹配目标微生物报告，
> 将细菌写入 `lis_micro_organism`、药敏写入 `lis_antibiotic_sensitivity`，并**自动触发耐药规则联动**
> （`MicroExpertRuleService.evaluate` → 命中内置规则则写入 `lis_micro_alert`）。

| 项 | 内容 |
|----|------|
| 方法 | `POST` |
| 路径 | `/api/instrument/{id}/send-micro` |
| 参数 | body：`{ plateBarcode?, sampleBarcode?, organisms: [...] }` |
| 返回 | `R<{ parseStatus, parseMsg, organismCount, sensitivityCount, report }>` |

**匹配优先级**：`plateBarcode`（培养基条码）优先，其次 `sampleBarcode`（采集条码），至少填一项。
目标微生物报告须处于未终审/可编辑状态，否则回传会被拒绝。

**请求示例（MRSA 案例）**
```bash
curl -X POST 'http://LIS:8080/api/instrument/1/send-micro' \
  -H 'Content-Type: application/json' \
  -d '{
    "plateBarcode": "",
    "sampleBarcode": "SM-ST-001",
    "organisms": [
      {
        "organismCode": "SAU",
        "organismName": "金黄色葡萄球菌",
        "quantity": "大量",
        "sensitivities": [
          { "antibioticCode": "OXA", "antibioticName": "苯唑西林", "micValue": ">4", "result": "R", "breakpoint": "≤2", "method": "MIC" },
          { "antibioticCode": "CFZ", "antibioticName": "头孢唑林", "micValue": ">8", "result": "R", "breakpoint": "≤2", "method": "MIC" }
        ]
      }
    ]
  }'
```

**前端调用界面**：`仪器配置 → 某仪器行「通讯测试」` 或直接进入 `通讯日志` 页，在「检验项目组」选择 **微生物药敏**，
即可在页面上选预设案例 / 手工填写后点「模拟回传微生物数据」，无需再手敲 JSON。

**独立部署降级**：若 instrument 模块独立部署（未加载 report 服务），`send-micro` 返回 `FAIL`，并提示改调：
`POST /api/report/micro/instrument-results/by-barcode`。

---

### 2.5 微生物回传测试案例（耐药规则联动）

内置耐药规则见 `lis-system/docs/database/seed/08-expert-rules.sql`，预设案例数据见
`lis-system/docs/database/seed/06-micro-report-test-data.sql`。测试前需先执行这两个脚本建表 & 灌入示例标本。
以下案例在「通讯日志 → 微生物药敏」预设下拉中一键填充。

| 案例 | 细菌(编码) | 关键耐药药敏(编码=耐药) | 期望命中规则 | 建议条码 | 判读 |
|------|-----------|------------------------|-------------|----------|------|
| MRSA | 金黄色葡萄球菌(SAU) | 苯唑西林 OXA=R，头孢唑林 CFZ=R | MRSA（特殊耐药） | SM-ST-001 | `micro_alert` 出现「MRSA」 |
| CRE | 大肠埃希菌(ECO) | 亚胺培南 IPM=R，美罗培南 MEM=R | CRE（危急值） | SM-BC-001 | `micro_alert` 出现「CRE」，标记危急值 |
| VRE | 粪肠球菌(EFA) | 万古霉素 VAN=R | VRE（特殊耐药） | SM-ST-001 | `micro_alert` 出现「VRE」 |
| CRAB | 鲍曼不动杆菌(ABA) | 亚胺培南 IPM=R，美罗培南 MEM=R | CRAB（特殊耐药） | SM-BC-001 | `micro_alert` 出现「CRAB」 |

**验证步骤**：
1. 执行 `08-expert-rules.sql` 导入内置规则；执行 `06-micro-report-test-data.sql` 导入测试标本（含 `SM-BC-001`/`SM-ST-001`）。
2. 在前端 `通讯日志` 页选择一台仪器，检验项目组选「微生物药敏」，从预设下拉选 `MRSA` 或 `CRE`，点「模拟回传微生物数据」。
3. 后台自动将细菌/药敏写入报告并评估规则，返回 `parseStatus=SUCCESS` 及命中提示。
4. 到「微生物 → 细菌鉴定与药敏」打开对应报告，查看 `micro_alert`（耐药告警）面板是否出现对应规则；也可在通讯日志表中看到 `SEND-MICRO(SIM)` 方向记录。

---

## 3. 结果匹配与无主标本落库（`ResultMatchingService`）

### 3.1 匹配逻辑

- 对解析出的每条结果，取 `sampleNo`（条码/样本号）
- 查询业务标本表 `lis_sample`：`sample_barcode = ? OR sample_no = ?`
- **匹配到标本** → 组装 `ResultDTO`，通过 MQ（routingKey `instrument.result.received`）通知报告服务落库；MQ 不可用时进程内直连 `ResultPersistencePort.persistResults()` 落库 `lis_result`
- **未匹配（条码为空 / 查不到标本）** → 进入**无主标本**分支：
  1. 落库 `lis_instrument_unmatched_result`（status=0 待处理）
  2. 发送 MQ（routingKey `result.unmatched`）供扩展

### 3.2 匹配规则（关键点）

| 场景 | 判断 | 流向 |
|------|------|------|
| 有条码且能对应到业务标本 | `lis_sample.sample_barcode` 或 `sample_no` 命中 | 写正式结果 |
| 有条码但查不到标本 | 无标本记录 | 无主标本（待人工匹配/作废） |
| 无条码（样本号为空） | `sampleNo` 缺失 | 无主标本（待人工补条码/作废） |

---

## 4. 无主标本接口（技师手工调整/认领）

统一前缀：`/api/instrument/unmatched`（详见 `UnmatchedResultController`）

| 接口 | 方法 | 说明 | 关键参数 |
|------|------|------|----------|
| `/page` | `GET` | 分页查询无主标本结果 | `current`、`size`、`status`(0待处理/1已匹配/2已作废)、`instrumentId` |
| `/pending-count` | `GET` | 待处理无主标本数量（技师工作站角标用） | 无 |
| `/{id}` | `PUT` | **手工调整**无主结果字段（如结果值/单位/参考范围） | body：`InstrumentUnmatchedResult` 部分字段 |
| `/{id}/match` | `POST` | 按条码匹配到业务标本并写回正式结果 | body：`{ "sampleBarcode": "YB..." }`，返回 `sampleId` |
| `/{id}/discard` | `POST` | 作废某条无主结果 | 无 |

### 4.1 分页查询
```bash
curl -X GET 'http://LIS:8080/api/instrument/unmatched/page?current=1&size=20&status=0'
```
```json
{
  "code": 200, "msg": "ok",
  "data": {
    "records": [
      { "id": 901, "instrumentId": 1, "instrumentName": "生化分析仪",
        "sampleNo": "NOMB0001", "testCode": "GLU", "testName": "葡萄糖",
        "resultValue": "6.8", "unit": "mmol/L", "refRange": "3.9-6.1",
        "abnormalFlag": "H", "status": 0, "createTime": "2024-01-01 12:00:00" }
    ],
    "total": 1, "current": 1, "size": 20
  }
}
```

### 4.2 手工调整结果值
```bash
curl -X PUT 'http://LIS:8080/api/instrument/unmatched/901' \
  -H 'Content-Type: application/json' \
  -d '{"resultValue":"6.5","unit":"mmol/L","refRange":"3.9-6.1"}'
```

### 4.3 按条码匹配到业务标本
```bash
curl -X POST 'http://LIS:8080/api/instrument/unmatched/901/match' \
  -H 'Content-Type: application/json' \
  -d '{"sampleBarcode":"YB202401010001"}'
```
- 后端以 `sampleBarcode` 匹配 `lis_sample.sample_barcode` 或 `sample_no`
- 命中 → 调用 `ResultPersistencePort.persistResults()` 写回正式结果，同时更新该无主记录 `status=1`、`matched_sample_id`
- 未命中 → 抛业务异常「未找到对应的业务标本」

---

## 5. 技师工作站「无主标本」调用界面

**入口**：标本报告 → 技师工作站（`views/report/technician/index.vue`）顶部工具栏「无主标本」按钮，右上角红色角标显示待处理数量（`pending-count`）。

**弹窗**：`views/report/technician/unmatched-dialog.vue`（无主标本管理-仪器结果认领）

| 功能 | 交互 | 对应接口 |
|------|------|----------|
| 状态筛选 | 待处理 / 已匹配 / 已作废 / 全部（radio） | `/page` af status |
| 手工调整结果值 | 待处理行结果值列直接编辑，失焦自动保存 | `PUT /{id}` |
| 匹配 | 点「匹配」→ 弹窗输入业务条码/样本号 → 写回正式结果 | `POST /{id}/match` |
| 作废 | 点「作废」→ 二次确认 | `POST /{id}/discard` |
| 刷新/分页 | 工具栏刷新、底部分页 | `/page` |

**前端 API**（`api/instrument/index.ts`）：`getUnmatchedPage` / `getUnmatchedPendingCount` / `updateUnmatchedResult` / `matchUnmatchedResult` / `discardUnmatchedResult`。

---

## 6. 支持协议与解析示例（ASTM）

消息记录说明（`ASTMParser`）：

| 记录 | 含义 | 关键字段 |
|------|------|----------|
| `H` | Header 头 | —— |
| `P` | Patient 患者 | 姓名等 |
| `O` | Order 订单/样本 | 字段2=样本号/条码（`^^^` 分隔多分量） |
| `R` | Result 结果 | 字段3=指标码，字段4=结果值，字段5=单位，字段6=参考范围，字段7=异常标志 |
| `L` | Terminator 结束 | —— |

- 样本号透传：`R` 记录继承最近一条 `O` 记录的样本号
- 异常/危急值判定：异常标志含 `*`、`>`、`<` 视为危急值（`isCritical`）
- 附加品牌处理器：`MindrayHandler`（迈瑞，ASTM）等，可扩展

**对接所需手工调整的数据**
1. **仪器项目映射**：仪器通道码 ↔ LIS 检验指标（`InstrumentItemMap`），联调时按仪器实际项码调整
2. **无主标本结果值**：技师在无主标本弹窗中可手工改结果值/单位/参考范围
3. **条码对码**：无条码或错码结果，技师输入正确业务条码手工匹配

---

## 7. 仪器对接配置与表

**连接方式配置**（`Instrument` 实体，前端「仪器配置」页维护）：TCP / 串口(SERIAL) / 文件(FILE) / 数据库(DB)，含 IP/端口、串口/波特率、文件路径与轮询、DB 连接与拉取 SQL 等。

**关键表**
| 表 | 作用 |
|----|------|
| `lis_instrument_comm_log` | 仪器通讯日志（原始报文/解析结果） |
| `lis_instrument_unmatched_result` | 无主标本结果（status: 0待处理/1已匹配/2已作废，含 `matched_sample_id`） |
| `lis_sample` | 业务标本，按 `sample_barcode` / `sample_no` 匹配 |
| `lis_result` | 正式检验结果（仪器结果匹配后写入） |

---

## 附：联调清单（快速验证）

1. **HIS 申请下发**：`POST /api/integration/sync/orders` 拉取申请
2. **HIS 结果回传**：构造 `LabReport` → `POST /api/integration/sync/results`；或发布报告后等待 `ResultSyncJob` 自动回传，并确认 `lis_report.his_sync_status=1`
3. **仪器数据接收**：`POST /api/instrument/data/receive`（传入 ASTM/HL7 原始报文）
4. **无主标本**：报文样本号未匹配时，`GET /api/instrument/unmatched/page?status=0` 应能看到记录；技师工作站「无主标本」角标变化
5. **手工调整与匹配**：`PUT /{id}` 改结果值；`POST /{id}/match` 输入业务条码后，`lis_result` 出现正式结果、无主记录 `status=1`