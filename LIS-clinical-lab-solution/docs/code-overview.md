# LIS 临床实验室信息系统 — 代码概况（实测版）

> 生成时间：2026-07-31
> 探查范围：`D:\traeitem\labWork\LIS-clinical-lab-solution`
> 说明：本文基于磁盘上**真实存在**的源代码梳理，不照搬 README / project-structure.md 的设计描述（这两份文档已与代码明显脱节，见第七节）。

---

## 一、项目定位与规模

一个面向临床实验室的 LIS 系统，前后端分离 + 多微服务形态，**业务覆盖度很高**：标本全流程、报告与自动审核、质控、仪器通讯、试剂、微生物、人事（ISO 15189）、HIS 对接、统计分析等均有实现。

| 指标 | 数值 |
|---|---:|
| 后端 `.java` 文件 | **412** 个 |
| 后端 Java 代码行数 | **≈ 21,685 行** |
| 前端 `src` 文件（`.vue/.ts/.scss`） | **83 个 / ≈ 24,983 行** |
| ├ 其中 `.vue` | 56 个 / 22,356 行 |
| ├ 其中 `.ts`（API + 工具） | 24 个 / 2,570 行 |
| 数据库表 | **51 张**（`lis_*` 业务表 + `sys_*` 系统表） |
| REST 端点 | **≈ 317 个**（57 个 Controller） |
| 后端服务（可独立启动 jar） | **14 个** |
| 前端业务页面 | **49 个路由 / 52 个 `.vue`** |
| 测试代码 | **0**（前后端均无测试框架/用例） |
| **代码总量（Java + 前端）** | **≈ 46,700 行** |

---

## 二、真实技术栈

### 后端（`lis-system/pom.xml` 实测）
- **Java 17 + Spring Boot 3.2.0 + Spring Cloud 2023.0.0 + Spring Cloud Alibaba 2023.0.1**
- **MyBatis-Plus 3.5.5**（全注解 `BaseMapper`，**0 个 XML Mapper**）
- **PostgreSQL 15+**（全线统一，代码库内**无任何 MySQL**）
- **Redis 7**（12 个服务引入）、**RabbitMQ**（6 服务引 amqp，5 个真实 `@RabbitListener`）
- **JWT**（jjwt 0.11.5）、**Hutool**、**SpringDoc OpenAPI 3**
- 依赖声明了 Nacos / Spring Cloud 全家桶，但**运行时全部禁用**（见第七节）

### 前端（`lis-frontend/package.json` 实测）
- **Vue 3.4 + TypeScript 5.3 + Vite 5**
- **Pinia 2 + Vue Router 4 + Element Plus 2.5**（组件自动按需导入）
- **Axios**（统一 `request.ts` 拦截器）、**ECharts 5.4 + vue-echarts**（质控图/统计）
- **dayjs**、**jsbarcode**（条码打印）
- 构建做了 `manualChunks` 三方分包；**无测试框架**

### 基础设施
- PostgreSQL / Redis / RabbitMQ，Docker Compose 编排脚本存在（`docs/deploy/`）
- Nginx 反代配置存在，但**无 K8s 清单**

---

## 三、后端服务架构（真实模块 + 端口）

父 POM 声明 5 个顶层模块：`lis-common`、`lis-api`、`lis-gateway`、`lis-auth`、`lis-service`；`lis-service` 聚合 12 个业务服务。

> 注意：`lis-common` 与 `lis-api` 均为**扁平单模块**（只是包目录，并非文档声称的多子模块）。

| 服务 | 端口 | java 文件 | 行数 | 核心职责 / 代表性类 |
|---|---:|---:|---:|---|
| lis-gateway | 8080 | 4 | 237 | 15 条**硬编码 IP** 路由、`AuthFilter`、`LogFilter` |
| lis-auth | 8090 | 4 | 313 | `LoginController`、`AuthService`、`TokenAuthenticationFilter` |
| lis-service-system | 8087 | 35 | 1,692 | RBAC：用户/角色/权限/科室/参数/审计日志（8 Entity） |
| lis-service-base | 8085 | 41 | 2,145 | 检验项目/指标、参考范围、标本类型、试管、仪器字典、校准/维修 |
| lis-service-sample | 8081 | 23 | 2,191 | 标本全流程：`SampleServiceImpl` 达 820 行（全库最大）；条码、MQ 监听×2 |
| lis-service-report | 8082 | 38 | 2,690 | `AutoAuditEngine`+5 校验、PDF 生成、微生物报告、危急值、MQ 监听×2 |
| lis-service-qc | 8083 | 33 | 1,744 | **Westgard 六规则引擎**（12s/13s/22s/41s/R4s/10x）真实实现、质控图、EQA |
| lis-service-instrument | 8084 | 24 | 1,492 | `TcpConnector`/`SerialConnector`、`ASTMParser`/`HL7Parser`、四大厂商 Handler、`ResultMatchingService` |
| lis-service-instrument-mgmt | 8091 | 24 | 865 | 仪器保养：维护模板/计划/任务/日志（启动脚本**漏列**此服务） |
| lis-service-integration | 8086 | 17 | 1,218 | `HisAdapterFactory` + HL7v2/FHIR/UniversalRest 适配器；**无 Entity/Mapper**（纯适配） |
| lis-service-reagent | 8092 | 35 | 1,653 | 试剂字典/库存/出入库/开瓶/消耗/看板 + `ExpiryAlertJob` |
| lis-service-micro | 8093 | 25 | 961 | 血培养及转种、抗生素字典、耐药规则、报告打印配置 |
| lis-service-hr | 8094 | 43 | 1,603 | 员工/职称/证书/授权/培训/排班/文档（9 Entity，模块数最多） |
| lis-service-stats | 8088 | 14 | 661 | 工作量/危急值/趋势/阳性率/收费；**无 Entity/Mapper**（直查 SQL） |
| **合计** | | **412** | **21,685** | |

`lis-common`：统一响应 `R<T>`/`PageResult`、异常体系、JWT、Redis 工具、MyBatis 分页、AES 加密、钉钉/企微通知器（20 文件 / 895 行）。
`lis-api`：纯 DTO/VO 契约 + 3 个 `@FeignClient` 接口（**未被任何服务引用**，死代码）。

---

## 四、前端工程结构

`src/` 目录树（实测，**注意：无 `components/`、`composables/`、`styles/` 目录**，UI 全靠 Element Plus 自动导入，零自研公共组件）：

```
src/
├── main.ts / App.vue / env.d.ts
├── api/        (18 个 .ts)  request.ts + 各业务域封装
├── assets/styles/  (3)  index.scss / reset.scss / variables.scss
├── layouts/    (3)  DefaultLayout.vue + Header.vue + Sidebar.vue
├── router/     (1)  index.ts（49 个路由，默认重定向 /sample/barcode）
├── stores/     (2)  user.ts（token/权限）/ app.ts
├── utils/      (1)  format.ts
└── views/      (52 .vue)  业务页面
```

`views/` 页面分布（按业务域）：

| 域 | 页面数 | 行数 | 重点页面 |
|---|---:|---:|---|
| report | 6 | 3,822 | technician(1,869)、micro(797)、list、query、audit、critical |
| sample | 6 | 3,555 | barcode(1,142)、sign-off(987)、deliver、pending、receive、tracking |
| base | 6 | 3,266 | test-item(896)、instrument(697)、panel、ref-range、test-tube、specimen-type |
| reagent | 6 | 2,203 | inbound、record、stock、list、consumption、dashboard |
| system | 6 | 1,820 | user、role、permission、log、department、config |
| qc | 5 | 1,767 | eqa(690)、chart、daily、report、rule-config |
| instrument | 5 | 1,734 | repair、calibration、config、status、log |
| stats | 5 | 1,538 | workload、charge、critical、trend、positive-rate |
| micro | 3 | 1,124 | blood-culture、antibiotic、resistance |
| hr | 3 | 1,024 | certificate、schedule、staff |
| login | 1 | 146 | login |

`api/` 封装（18 个）：`sample`(402) · `base`(323) · `reagent`(314) · `qc`(219) · `system`(191) · `instrument`(184) · `hr`(165) · `micro`(134) · `qc/eqa`(119) · `report/micro`(116) · `instrument/calibration`(89) · `request`(59) 等，与后端 57 个 Controller 前缀基本一一对应。

**孤儿页面**（已写但路由未注册）：`base/ref-range`、`report/list`、`sample/receive`。

---

## 五、数据层

- **数据库**：PostgreSQL 全线统一（14 个服务均 `jdbc:postgresql://localhost:5432/lis_db`，用户 `lis_user`）。建表脚本 `lis-system/docs/database/init-schema.sql`（51 张表）+ 14 个增量补丁。
- **ORM**：MyBatis-Plus 全注解，无任何 XML Mapper（`resources/mapper/` 不存在）。
- **缓存**：Redis（会话/字典/标本状态）。
- **消息**：RabbitMQ，真实消费者覆盖标本创建/接收、报告发布、危急值、仪器告警。
- **配置**：14 个 `application.yml`，**无任何 `application-dev/prod.yml`，无多环境 profile**。

---

## 六、已落地的专业内核（亮点 — 真实存在）

这些是本项目最有价值、且确有代码的部分：

1. **Westgard 多规则质控引擎**（`lis-service-qc`）：12s/13s/22s/41s/R4s/10x 六规则真实实现，支持 Levey-Jennings 图。
2. **仪器协议解析**（`lis-service-instrument`）：ASTM / HL7 解析器 + 迈瑞/希森美康/罗氏/雅培四大厂商 Handler + TCP/串口双通道 + 结果自动匹配标本。
3. **报告自动审核引擎**（`lis-service-report`）：参考范围 / Delta Check / 危急值 / 分析错误 / 患者信息完整性 五类校验策略。
4. **HIS 适配器**（`lis-service-integration`）：`HisAdapterFactory` + HL7v2 / FHIR R4 / UniversalRest 三种适配器 + 定时同步任务（注意：目前**无 Entity/Mapper**，仅靠适配层逻辑）。
5. **试剂效期预警**（`lis-service-reagent`）：`ExpiryAlertJob` 定时扫描近效期/过期批次。

---

## 七、文档与实现的差异（重要 — 会减少踩坑）

| 文档/README 声明 | 磁盘实际 | 结论 |
|---|---|---|
| Nacos 服务注册 + 动态配置 | 依赖与 `@EnableDiscoveryClient` 都在，但**所有服务运行时 `enabled=false`**（启动脚本强制注入禁用参数） | **Nacos 全线禁用** |
| 服务间 Feign 调用 | 定义了 3 个 `@FeignClient`，**全库无 `@EnableFeignClients`、无引用** | **Feign 是死代码** |
| 网关按服务名负载均衡 (`lb://`) | `RouteConfig` 15 条路由**全部硬编码 `http://localhost:8xxx`** | 退化为静态反代 |
| Kafka（仪器大数据）/ MinIO / ES / Sentinel / Seata | 关键字命中 **0** | **均未实现** |
| `lis-api` 拆 6 子模块、`lis-common` 多子模块 | 实际各 1 个扁平模块 | 文档夸大 |
| 端口标注 | 文档：`instrument-mgmt:8085`、`reagent:8086` | 实际：`instrument-mgmt:8091`、`reagent:8092`、`integration:8086` |
| 文档点名但不存在的类 | `MaintenanceReminderJob`、`ReagentFifoService`、`ExpiryAlertService`、`InventoryController`、`StockController`、`QCController`、`MaintenanceController`、`RepairController` | **均不存在**（功能可能以其他类名实现或缺失） |
| `docs/database-design.md` | 设计 49 表 vs 实际脚本 51 表，命名/字段高度一致 | **最可信文档** |

> 文档最贴近落地的是 `docs/系统使用指南.md`（含真实端口探测、启动脚本用法、admin 密码重置 SQL）。

---

## 八、工程化缺口与运行风险

1. **零测试**：前后端均无测试框架与用例。
2. **无多环境配置**：无 dev/prod profile，配置写死 localhost。
3. **架构边界被打破**：`Instrument` 实体同时存在于 `lis-service-base` 与 `lis-service-instrument`，两服务**共享同一张 `lis_instrument` 表** → 微服务边界名存实亡，本质是"披着微服务外壳的分布式单体"。
4. **运行不稳定痕迹**：`lis-system/` 根目录留有 `hs_err_pid12008.log`(51KB) 与 `replay_pid12008.log`(234KB)，`logs/lis-sample.out.log` 高达 8.3MB → 存在 JVM 崩溃/运行期异常残留。
5. **启动脚本缺陷**：① 漏列 `instrument-mgmt`(8091)；② 末尾健康检查表把 `auth` 写成 8086（实际 8090），每次启动会误报 auth 未就绪。
6. **孤儿端点**：`ClinicalReportController` 暴露 `/api/clinical/reports`，但网关无对应路由、前端未调用 → 外部不可达。
7. **孤儿前端页面**：3 个 `.vue` 未注册路由（见第四节）。

---

## 九、成熟度结论

> **高完成度的"演示级" LIS**：业务纵深真实、代码量扎实（≈4.7 万行、14 个可独立启动服务、317 个端点、51 张表、49 个前端页面），Westgard 质控、ASTM/HL7 仪器协议、自动审核、HIS 适配器等**专业内核确有实现**。但工程化仍停留在原型阶段——Nacos/Feign 形式为虚、网关硬编码 IP、共享单库同表、零测试、零多环境、文档与代码脱节。距离生产可用，还差**服务治理（注册发现/配置中心）、测试体系、配置与环境管理、服务边界治理**这四步。

---

### 建议的下一步（按优先级）
1. **补齐测试**：先给 `AutoAuditEngine`、`WestgardRuleEngine`、`ResultMatchingService` 等核心引擎加单元测试（后端 JUnit5，前端可上 Vitest）。
2. **修正启动脚本**：补 `instrument-mgmt` 服务、修 `auth` 端口，避免误报。
3. **治理文档**：以 `docs/database-design.md` 为准，重写 `project-structure.md` 与 README，使端口/模块/类名与代码一致。
4. **清理残留**：删除 `hs_err_*`/`replay_*` 崩溃日志，核查 `lis-sample.out.log` 异常来源。
5. **架构收口（中期）**：明确服务边界、统一通过网关路由、考虑引入真实注册中心与配置中心后再谈"微服务"。
