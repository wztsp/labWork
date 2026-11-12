# LIS 系统整体检查报告

> 检查时间：2026-08-02
> 检查对象：`LIS-clinical-lab-solution`（后端 `lis-system` 12 个微服务 + 前端 `lis-frontend`）
> 检查范围：用户列出的业务模块（标本采集/送检/签收、技师工作站、检验报告、质控规则、试剂管理、人员管理、仪器管理、微生物等）的实现深度与前后端连通性。
> 方法：静态代码审计（包结构统计 + 关键类方法体逐行阅读），未编译运行。

---

## 一、模块覆盖总览

| 用户提到的模块 | 后端子模块 | 前端 views | 实现深度 | 说明 |
|---|---|---|---|---|
| 标本采集 / 送检 / 签收 | `lis-service-sample` (28 .java) | `views/sample/*` | ✅ 完整 | 合管算法、条码双通道、状态机均真实 |
| 检验报告 | `lis-service-report` (66 .java) | `views/report/*` | ⚠️ 完整但有缺陷 | 含 1 处逻辑 Bug + 3 处空壳方法 |
| 技师工作站 | 归属 report 模块 | `views/report/technician/*` | ⚠️ 部分 | UI 完整，后端签到/补打未持久化 |
| 质控规则 | `lis-service-qc` (48 .java) | `views/qc/*` | ✅ 完整 | Westgard 六规则 + L-J 图真实实现 |
| 试剂管理 | `lis-service-reagent` (45 .java) | `views/reagent/*` | ✅ 完整 | FIFO 出库、安全库存、成本聚合真实 |
| 人员管理 | `lis-service-hr` (47 .java) | `views/hr/*` | ⚠️ 部分 | 对称 CRUD 脚手架，排班无算法、离职不回收账号 |
| 仪器管理（台账/通信） | `lis-service-instrument` (27 .java) | `views/base/instrument`、`views/instrument/config` | ✅ 完整 | ASTM/HL7 协议解析最扎实 |
| 仪器管理（校准/维护） | `lis-service-instrument-mgmt` (25 .java) | `views/instrument/{calibration,maintenance,plan,repair,status}` | ⚠️ 部分 | 缺校准实体、维修与状态管理完全缺失 |
| 微生物 | `lis-service-micro` (36 .java) | `views/micro/*` | ❌ 空壳 | 仅 CRUD，缺 CLSI 折点判读（核心价值缺失） |
| 基础/字典（隐含） | `lis-service-base` (44 .java) | `views/base/*` | ✅ 完整 | 检验项目/参考范围/标本类型真实逻辑 |
| 系统/权限（隐含） | `lis-service-system` (37 .java) | `views/system/*` | ✅ 完整 | 用户/角色/权限/备份真实，高风险逻辑零测试 |
| 统计（隐含） | `lis-service-stats` (14 .java) | `views/stats/*` | ⚠️ 部分 | 绕开 MyBatis-Plus 直接用 JdbcTemplate 裸 SQL，零校验 |
| HIS 集成（隐含） | `lis-service-integration` (18 .java) | — | ✅ 完整（略残缺） | MLLP/HL7v2/重试状态机真实，queryPatient 未实现 |

**结论：9 个用户列出的模块 + 4 个隐含支撑模块，前后端均"有对应物且路由已接通"。差异集中在实现深度与缺陷。**

---

## 二、关键缺陷（需修复）

| # | 位置 | 问题 | 严重度 |
|---|---|---|---|
| B-1 | `lis-service-report/.../MicroExpertRuleServiceImpl.java:66-69` | ESBL 判定运算符优先级错误：`&&` 先于 `||` 结合，致任何美罗培南敏感菌都误报 ESBL | 🔴 高 |
| B-2 | `lis-service-report/.../TechnicianServiceImpl.java:536-543,653-666` | `batchReprintBarcode`/`instrumentSignin`/`autoAuditSignin` 仅 `log.info` 后 `return`，无落库 —— 技师工作站核心动作失效 | 🔴 高 |
| B-3 | `lis-service-qc/.../QCChartServiceImpl.java:40,123` | L-J 图 `.last("LIMIT "+limit)` 字符串拼接 SQL，limit 来自入参且无上限校验 —— 注入面 | 🟠 中 |
| B-4 | `lis-service-hr/.../StaffServiceImpl.java:100` | `// TODO: 联动停用系统账号` 未实现，离职人员系统账号不回收 —— 合规风险 | 🟠 中 |
| B-5 | `lis-service-base/entity/Instrument.java` 与 `lis-service-instrument/.../entity/Instrument.java` | 同一实体重复定义，模块职责错位 | 🟡 低 |
| B-6 | `lis-service-instrument/.../InstrumentConnectionManager.java:60-63` | `@PostConstruct init()` 只打日志，未从台账自动连接仪器，生产无法自举 | 🟠 中 |
| B-7 | `lis-service-integration/.../HL7v2Adapter.java:257` | `queryPatient` 仅 `log.warn("暂未实现")`，患者查询缺口 | 🟡 低 |
| B-8 | `lis-service-stats` 全模块 | 绕开 MyBatis-Plus 用 `JdbcTemplate` 裸 SQL，`startDate` 等入参直接 `CAST` 进 SQL、零校验 | 🟠 中 |

---

## 三、测试覆盖（系统性缺口）

审计涉及的 12 个模块中，仅 4 个含 `src/test`：

| 模块 | 测试文件 | @Test 数 |
|---|---|---|
| lis-service-qc | 2 | 25 |
| lis-service-report | 3 | 27 |
| lis-service-integration | 1 | 6 |
| lis-service-instrument | 2 | 7 |
| 其余 8 个模块 | 无 | 0 |

**覆盖率极不均衡**：规则引擎（Westgard/DeltaCheck）与协议解析（ASTM/HL7）有单测；所有 Service / Controller 层、所有 CRUD 业务模块（sample/reagent/hr/instrument-mgmt/base/stats/system/micro）零测试。合计约 113 个 Java 文件、7000+ 行的模块完全没有测试。

---

## 四、整体结论

1. **架构完整、模块齐全**：你列出的 9 个模块在后端都有独立微服务、前端都有对应界面与路由，不存在"缺模块"的情况。
2. **实现深度分层明显**：
   - 扎实：标本流转（sample）、质控（qc）、试剂（reagent）、基础字典（base）、系统权限（system）、仪器通信（instrument）。
   - 半成品：检验报告（report，含 Bug）、技师工作站、人员管理（hr）、仪器校准维护（instrument-mgmt）、统计（stats）。
   - 空壳：微生物（micro）—— 仅 CRUD，缺 CLSI 折点判读引擎，是其核心价值所在。
3. **缺陷可定位**：8 处缺陷中明确 2 处高优（ESBL 误报、技师签到不落库），需优先修。
4. **测试是最大短板**：除规则引擎/解析器外几乎无测试，发布前建议至少补齐 sample / reagent / hr / instrument-mgmt 的 Service 单测。

## 五、建议优先级

| 优先级 | 动作 |
|---|---|
| P0 | 修 B-1（ESBL 误报）、B-2（技师签到落库） |
| P1 | 补 instrument-mgmt 的校准/维修/状态实体、micro 的 CLSI 判读引擎；修 B-6/B-8 |
| P2 | hr 排班算法、离职账号回收（B-4）、qc LIMIT 注入面（B-3） |
| P3 | 统一测试基线，补齐 8 个零测试模块；清理 base/instrument 重复实体（B-5） |
