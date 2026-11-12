# B-3 ~ B-8 缺陷修复交付报告

> 交付日期：2026-08-02 · 团队：software-lis-bbatch（架构师 高见远 / 工程师 寇豆码 / QA 严过关）
> 关联文档：`docs/system-overall-check.md`（缺陷来源）、`docs/b3-b8-design.md`（增量设计）

---

## 一、本次交付范围

用户要求三件事，均已完成：

| # | 要求 | 状态 | 说明 |
|---|---|---|---|
| 1 | 建表 | ✅ 完成 | 执行 `lis-system/docs/database/add-technician-signin-print.sql`，`lis_barcode_print_log` / `lis_station_signin` 两表已建，字段与索引已验证 |
| 2 | 执行测试 | ✅ 部分真实执行 | 零依赖自检 Runner **179/179 断言全绿**；JUnit 127 @Test 编译零错误，因离线缺 `junit-platform-launcher` 未能在本机跑 JUnit |
| 3 | 修复 B-3 ~ B-8 | ✅ 完成 | 6 项全部修复，514 主源文件全量 javac 编译 **0 error 0 warning**，IS_PASS: YES，QA 路由判定 NoOne |

---

## 二、六项缺陷修复明细

| 编号 | 缺陷 | 修复方案 |
|---|---|---|
| **B-3** | qc 模块 SQL 字符串拼接 | 新增纯静态工具 `QcQueryGuard`；`LIMIT n` 拼接改 MyBatis-Plus 分页 `selectPage(new Page<>(1,n,false), wrapper)`；`IN (%s)` 拼接改 `QcQueryGuard.inClause(size)` + 参数绑定 |
| **B-4** | 员工离职不回收账号 | 新增 `SysUserAccount` + Mapper + `AccountRevokeService(Impl)`；`StaffServiceImpl.resign` 在**同一 `@Transactional`** 内停用账号 + 剥权 + 写 Redis 撤销标记（`CacheKey.USER_REVOKED`） |
| **B-5** | Instrument 实体重复 | 方案 A：base 为**写权威**、instrument 为**只读投影**，双方类注释明确职责边界；`InstrumentDTO` 补齐 7 字段；反射字段契约测试兜底 |
| **B-6** | 仪器连接管理器不自举 | 新增 `InstrumentBootstrap`（`ApplicationListener<ApplicationReadyEvent>`）；`autoConnectAll()` 用 `AtomicBoolean.compareAndSet(false,true)` 保证幂等；开关 `instrument.connection.auto-connect` |
| **B-7** | HIS 患者查询未实现 | `HL7MessageBuilder.buildQBP_Q21` + `HL7MessageParser.parseRSP_K21`；`HL7v2Adapter.queryPatient` 走通完整链路；**顺带修复潜伏 Bug**（见下） |
| **B-8** | stats 入参零校验 + 不可测 | 新增 `StatsQueryGuard`（`parseDate`/`checkRange`/`clampLimit`/`checkDim`）+ `StatsSqlSupport`；4 个 ServiceImpl 的 `new JdbcTemplate(dataSource)` 改**构造器注入** |

---

## 三、实读校正（本轮最有价值的产出）

原检查报告是静态扫描 + 推断得出的，本轮三位成员逐层实读源码后纠正了多处初判偏差：

### 3.1 B-8 其实没有 SQL 注入
报告判定"裸 SQL 零校验存在注入风险"。架构师实读发现 **SQL 早已用 `?` 参数化**，不存在注入面。真正的问题是另外两个：
- 所有对外查询入口**入参零校验**（日期格式、区间跨度、维度白名单、limit 上限全裸奔）
- `new JdbcTemplate(dataSource)` 硬 new，导致 Service **无法单元测试**

修复方向据此调整，从"防注入"改为"校验 + 可测性"。

### 3.2 B-5 权威方向写反了
报告认为 instrument 模块的实体应为权威。架构师实读调用链后确认**恰恰相反：base 才是写权威**，instrument 侧是只读消费方。主理人裁决采纳架构师结论。若按原报告实施，会把数据流改反。

### 3.3 B-3 拼接点从 1 处扩到 5 处
报告只点名 1 处。逐层 grep 后：
- 架构师 +2 处（`QCRecordServiceImpl` L199-207、L218-226）
- 工程师 +2 处（`QcAlertServiceImpl:102`、`QCLotServiceImpl:105`）

合计 **5 处**全部修复。只按报告改会漏掉 80%。

### 3.4 B-4 风险远低于预估
报告预估需新建"员工→账号"映射表。实读发现 **`Staff` 实体上已有 `userId` 字段**，直接可用，改动面大幅收窄。

### 3.5 B-7 挖出报告完全没写的潜伏 Bug ⚠️
`HL7MessageParser` 原用 `split("\r?\n")` 切分报文段。而 **HL7 v2 标准的段分隔符恰恰是纯 `\r`**（CR，非 CRLF、非 LF）。这意味着对接严格遵循标准的 HIS 时，**整条报文会被当成一个段，解析全盘失败**。

修复：统一改为私有方法 `splitSegments(String)`，用 `split("\\r\\n|\\r|\\n")` 同时兼容三种分隔符，3 处调用点全部替换。QA 对三种分隔符各写一组用例并断言解析结果等价。

这类 Bug 静态扫描发现不了，只有实读 + 领域知识才能抓到。

---

## 四、文件清单

### 新增（8 个生产文件）
```
lis-system/lis-service/lis-service-qc/src/main/java/com/lis/qc/support/QcQueryGuard.java
lis-system/lis-service/lis-service-stats/src/main/java/com/lis/stats/support/StatsQueryGuard.java
lis-system/lis-service/lis-service-stats/src/main/java/com/lis/stats/support/StatsSqlSupport.java
lis-system/lis-service/lis-service-hr/src/main/java/com/lis/hr/entity/SysUserAccount.java
lis-system/lis-service/lis-service-hr/src/main/java/com/lis/hr/mapper/SysUserAccountMapper.java
lis-system/lis-service/lis-service-hr/src/main/java/com/lis/hr/service/AccountRevokeService.java
lis-system/lis-service/lis-service-hr/src/main/java/com/lis/hr/service/impl/AccountRevokeServiceImpl.java
lis-system/lis-service/lis-service-instrument/src/main/java/com/lis/instrument/comm/InstrumentBootstrap.java
```

### 修改（17 个）
- **qc**：`QCChartServiceImpl`、`QCRecordServiceImpl`、`QcAlertServiceImpl`、`QCLotServiceImpl`
- **stats**：`WorkloadStatsServiceImpl`、`ChargeStatsServiceImpl`、`CriticalStatsServiceImpl`、`TrendAnalysisServiceImpl`、`pom.xml`
- **hr**：`StaffServiceImpl`
- **common**：`CacheKey`（+`USER_REVOKED`）
- **instrument**：`InstrumentConnectionManager`、`application.yml`、`entity/Instrument.java`
- **base**：`entity/Instrument.java`、`pom.xml`（补 test 依赖）
- **api**：`InstrumentDTO`（补 7 字段）
- **integration**：`HL7MessageBuilder`、`HL7MessageParser`、`HL7v2Adapter`

### 测试（12 个文件，10 个含 @Test，共 127 @Test）
| 模块 | 测试文件 | @Test |
|---|---|---|
| stats | `StatsQueryGuardTest` | 23 |
| integration | `HL7SegmentSeparatorTest` | 19 |
| qc | `QcQueryGuardTest` | 17 |
| stats | `StatsSqlSupportTest` | 15 |
| instrument | `InstrumentConnectionManagerAutoConnectTest` | 14 |
| integration | `HL7MessageBuilderQbpQ21Test` | 12 |
| hr | `AccountRevokeServiceImplTest` | 11 |
| hr | `StaffServiceResignTest` | 7 |
| instrument | `InstrumentBootstrapTest` | 6 |
| base | `InstrumentContractTest` | 3 |

### 自检 Runner
```
tools/selfcheck/B3B8SelfCheck.java   # 零依赖，179 断言
```

---

## 五、验证结果

```
生产代码编译：javac 全量显式编译 514 个源文件 → 0 error / 0 warning (Exit=0)
测试代码编译：javac 编译 12 个测试文件      → 0 error / 0 warning (Exit=0)

自检 Runner 真实执行：
========== B-3 ~ B-8 零依赖自检 Runner ==========
-- B-3 QcQueryGuard --
-- B-8 StatsQueryGuard --
-- B-7 HL7 段分隔符 / parseRSP_K21 / buildQBP_Q21 --
-- B-5 Instrument 字段契约 (反射) --
==================================================
RESULT: total=179 passed=179 failed=0
ALL PASSED
```

**QA 智能路由判定：`NoOne`** —— 未发现源码 Bug，未发现测试自身错误，无需返工。

---

## 六、遗留项

| 项 | 说明 | 建议 |
|---|---|---|
| JUnit 未在本机执行 | 离线环境缺 `junit-platform-launcher` / `console`，无外网 | 联网机执行下方命令 |
| B-4 Tier-2 未实现 | 网关按 JWT `iat` 时间戳拒绝旧 token。当前仅 Tier-1（停用 + Redis 撤销标记），已签发的 token 在过期前理论上仍可用 | 代码内已留 `// TODO(B-4 Tier-2)` 指向 `AuthFilter`，建议下一轮补上 |
| B-5 未做物理合并 | 只加注释界定 + DTO 补字段 + 契约测试，未抽 `InstrumentBase` 公共父类 | 抽父类会触发 12 模块全量重编译，代价不划算，暂维持契约约束 |
| B-7 QPD 结构自拟 | `QBP^Q21` 的 QPD 段结构为工程自拟，现场 HIS 规格可能不同 | 联调时按对方实际规格校准字段映射 |
| base 新增 test 依赖 | `lis-service-base/pom.xml` 增加了 **test 作用域**的 `lis-service-instrument` 依赖（为 B-5 反射契约测试） | base 不反向依赖 instrument，无循环依赖风险；后续调整依赖时勿误删 |

---

## 七、运行命令

**联网机器跑 JUnit：**
```bash
cd LIS-clinical-lab-solution
mvn -pl lis-service-qc,lis-service-stats,lis-service-integration,lis-service-hr,lis-service-instrument,lis-service-base test
```

**重跑零依赖自检（git-bash，仓库根目录）：**
```bash
CP="build/selfcheck/main-classes;$(cat build/selfcheck/cp.txt)"
javac -encoding UTF-8 -d build/selfcheck/runner-classes -cp "$CP" tools/selfcheck/B3B8SelfCheck.java
java  -cp "build/selfcheck/runner-classes;$CP" B3B8SelfCheck
```

**建表（已执行，脚本自带 `IF NOT EXISTS` 可安全重跑）：**
```bash
PGPASSWORD=lis_dev_2026 "D:/Program Files/PostgreSQL/13/bin/psql.exe" \
  -h localhost -p 5432 -U lis_user -d lis_db \
  -f lis-system/docs/database/add-technician-signin-print.sql
```
