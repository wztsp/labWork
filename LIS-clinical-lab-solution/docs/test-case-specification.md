# LIS 系统测试用例规格说明（Test Case Specification）

> 版本 v1.0 ｜ 日期 2026-08-02 ｜ 范围：LIS 核心业务模块单元测试
> 配套代码：各 `lis-service-*/src/test/java/**` 下 JUnit 5 测试类
> 运行方式：在 `lis-system` 下 `mvn -pl lis-service-qc,lis-service-report,lis-service-instrument,lis-service-integration -am test`

## 1. 测试策略概述

| 维度 | 说明 |
|---|---|
| 测试类型 | 单元测试（白盒），聚焦于**纯业务逻辑**与**协议解析** |
| 框架 | JUnit 5 (Jupiter) + Mockito |
| 被测模块 | ① Westgard 质控引擎 ② 自动审核引擎 ③ 仪器协议解析（ASTM/HL7）④ HIS 消息解析（HL7 ORM/ADT/RSP） |
| 不依赖项 | 不启动 Spring 容器；直接 `new` 被测对象，仅对 Mapper 用 Mockito 模拟 |
| 覆盖目标 | 规则判定正确性、边界值、异常输入（null/空/非数字）、优先级与阻断语义 |

## 2. 模块一：Westgard 多规则质控引擎（lis-service-qc）

**靶值=100, SD=5 ⇒ z=(value−100)/5**

### 2.1 引擎 `WestgardRuleEngineTest`
| 用例ID | 场景 | 输入 | 期望结果 |
|---|---|---|---|
| QC-E-01 | 空记录 | records=[] | REJECT, ruleName="PreCheck" |
| QC-E-02 | lot 为 null | records=[100], lot=null | REJECT, ruleName="PreCheck" |
| QC-E-03 | 1-3s 失控 | 末点=117.5 (z=3.5) | REJECT, ruleName="1-3s" |
| QC-E-04 | 1-2s 警告 | 末点=112.5 (z=2.5) | WARNING(接受), ruleName="1-2s" |
| QC-E-05 | 在控 | 末点=100 (z=0) | ACCEPT |

### 2.2 具体规则 `WestgardRulesTest`
| 用例ID | 规则 | 场景 | 期望 |
|---|---|---|---|
| QC-R-01 | calcZScore | (112.5−100)/5 | = 2.5 |
| QC-R-02 | calcZScore | SD=0 | null（防除零） |
| QC-R-03 | calcZScore | measureValue=null | null |
| QC-R-04 | 1-3s | z=3.5 | 命中(true) |
| QC-R-05 | 1-3s | z=2.8 / z=0 | 不命中 |
| QC-R-06 | 2-2s | 连续2点同侧 z=2,2.4 | 命中 |
| QC-R-07 | 2-2s | 异侧(z=2.4,−2.4) | 不命中 |
| QC-R-08 | 2-2s | 仅1点 | 不命中(数据不足) |
| QC-R-09 | R-4s | 两点极差=4.2SD | 命中 |
| QC-R-10 | R-4s | 两点极差=0.4SD | 不命中 |
| QC-R-11 | 1-2s | 2<|z|<3 | 命中 |
| QC-R-12 | 1-2s | |z|≥3 或 |z|≤2 | 不命中 |
| QC-R-13 | 4-1s | 连续4点同侧>1SD | 命中 |
| QC-R-14 | 4-1s | 含一点≤1SD | 不命中 |
| QC-R-15 | 4-1s | 仅3点 | 不命中 |
| QC-R-16 | 10x | 连续10点同侧 | 命中 |
| QC-R-17 | 10x | 含一点异侧(跨0) | 不命中 |
| QC-R-18 | 10x | 仅9点 | 不命中 |

## 3. 模块二：自动审核引擎（lis-service-report）

### 3.1 引擎 `AutoAuditEngineTest`
| 用例ID | 场景 | 期望 |
|---|---|---|
| AU-E-01 | report=null | 失败, PreCheck |
| AU-E-02 | 全部通过 | 通过 |
| AU-E-03 | 含危急值(blocking) | 失败, "CriticalValueCheck" |
| AU-E-04 | 仅参考范围超限(non-blocking) | 仍通过（不阻断） |
| AU-E-05 | 危急值+信息缺失同时触发 | 返回优先级更高者 "CriticalValueCheck" |
| AU-E-06 | Delta 超限(非阻断) | 通过 |

### 3.2 规则 `AuditRulesTest` + `DeltaCheckTest`
| 用例ID | 规则 | 场景 | 期望 |
|---|---|---|---|
| AU-R-01 | ReferenceRange | 5.0 ∈ [3.5-5.5] | 通过 |
| AU-R-02 | ReferenceRange | 6.0 ∉ [3.5-5.5] | 失败 |
| AU-R-03 | ReferenceRange | "<10": 5 通过 / 11 失败 | 分别通过/失败 |
| AU-R-04 | ReferenceRange | ">1.0": 2 通过 / 0.5 失败 | 分别通过/失败 |
| AU-R-05 | ReferenceRange | 非数字值"阴性" | 通过(容错) |
| AU-R-06 | ReferenceRange | refRange 为空 | 跳过→通过 |
| AU-R-07 | CriticalValue | isCritical=1 | 失败(blocking) |
| AU-R-08 | CriticalValue | isCritical=0 | 通过 |
| AU-R-09 | PatientInfo | 完整 | 通过 |
| AU-R-10 | PatientInfo | 缺 patientName | 失败 |
| AU-R-11 | PatientInfo | 缺 reportType | 失败 |
| AU-R-12 | AnalyticalError | 文本含 "ERROR" | 失败 |
| AU-R-13 | AnalyticalError | 值含 "ALARM" | 失败 |
| AU-R-14 | AnalyticalError | 正常结果 | 通过 |
| AU-R-15 | DeltaCheck | Δ=100% (>20%) | 失败(非阻断) |
| AU-R-16 | DeltaCheck | Δ=10% (<20%) | 通过 |
| AU-R-17 | DeltaCheck | Δ=20% (边界,不大于) | 通过 |
| AU-R-18 | DeltaCheck | 无历史 | 通过(跳过) |
| AU-R-19 | DeltaCheck | 历史值=0 | 通过(防除零) |
| AU-R-20 | DeltaCheck | patientId=null | 通过(跳过) |

## 4. 模块三：仪器协议解析（lis-service-instrument）

### 4.1 ASTM `ASTMParserTest`
| 用例ID | 场景 | 期望 |
|---|---|---|
| IN-A-01 | 标准 ASTM 报文 | 解析出 sampleNo=S12345, testCode=GLU, value=5.5, unit=mmol/L, refRange=3.9-6.1, flag=N |
| IN-A-02 | 空/ null 报文 | 返回空列表 |
| IN-A-03 | 含 ENQ(0x05) 帧控字符 | 正确过滤并解析 |
| IN-A-04 | isCriticalFlag | "***"/">5.0"/"<1.0"→true；"H"/"N"/null→false |

### 4.2 HL7 `HL7ParserTest`
| 用例ID | 场景 | 期望 |
|---|---|---|
| IN-H-01 | HL7 ORU^R01 报文 | 解析 OBX: GLU/5.5/mmol/L/3.5-6.1/N + PID: P001/Smith^John |
| IN-H-02 | 空/ null 报文 | 返回空列表 |

## 5. 模块四：HIS 消息解析（lis-service-integration）

### 5.1 `HL7MessageParserTest`
| 用例ID | 消息 | 期望 |
|---|---|---|
| HI-01 | ORM^O01 | OrderDTO: orderNo=ORD123, patientId=1001L, patientName, applyDept=DEPT01, items[GLU/Glucose] |
| HI-02 | ORM^O01 | null/空白 → null |
| HI-03 | ADT^A01 | PatientInfo: id/name/gender/birthday(1980-01-01)/phone/address/visitType/department/bedNo/doctor |
| HI-04 | ADT^A01 | null/空白 → null |
| HI-05 | RSP^K22 | 2 条 OrderDTO (ORD1, ORD2) |
| HI-06 | RSP^K22 | null/空白 → 空列表 |

## 6. 暂不纳入本次（后续迭代）

| 模块 | 原因 | 建议测试方式 |
|---|---|---|
| HL7v2Adapter / FhirAdapter | 强依赖 RestTemplate/TCP/外部 HIS，纯单元难覆盖 | Mock RestTemplate 做契约测试 / Testcontainers 集成测试 |
| 各 Controller / Service | 依赖 Spring 上下文与 DB | @SpringBootTest 集成测试 |
| 前端 Vue3 | 独立技术栈 | Vitest + Testing Library |
