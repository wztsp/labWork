# 临床实验室信息系统 (LIS) 解决方案

> 目标：构建一套高可用、可扩展、易开发维护的临床实验室信息系统，支持日均 5000 标本量，可无缝对接三方 HIS 系统。

---

## 一、系统总体架构

### 1.1 架构选型：前后端分离 + 微服务

采用 **前后端分离** 的 B/S 架构，后端按业务领域拆分为微服务，前端为 SPA 单页应用。部署层面推荐容器化（Docker + K8s），便于水平扩展应对标本高峰。

```
                        ┌─────────────────────────────────────────┐
                        │           客户端 (浏览器)                 │
                        └────────────────┬────────────────────────┘
                                         │ HTTPS
                        ┌────────────────┴────────────────────────┐
                        │            Nginx 反向代理 / API 网关       │
                        │   (路由分发 · JWT 鉴权 · 限流 · 日志)      │
                        └───┬────────┬────────┬─────────┬─────────┘
                            │        │        │         │
                 ┌──────────┴──┐ ┌───┴───┐ ┌──┴──┐ ┌───┴──────────┐
                 │ 标本管理服务  │ │报告服务│ │质控  │ │ 仪器通讯服务   │
                 │ (Sample)     │ │(Report)│ │服务  │ │ (Instrument) │
                 └──────────┬──┘ └───┬───┘ └──┬──┘ └───┬──────────┘
                            │        │        │         │
                 ┌──────────┴────────┴────────┴─────────┴──────────┐
                 │                消息中间件 (RabbitMQ / Kafka)     │
                 │         (异步任务 · 仪器数据缓冲 · 事件驱动)       │
                 └───────────────────────┬─────────────────────────┘
                                         │
                 ┌───────────────────────┴─────────────────────────┐
                 │               HIS 对接适配层 (Integration)       │
                 │   (HL7v2 · HL7 FHIR · REST API · Web Service)  │
                 └──────────┬──────────────────────┬─────────────┘
                            │                      │
                 ┌──────────┴──────────┐ ┌─────────┴──────────────┐
                 │   MySQL / PostgreSQL │ │     Redis 缓存集群      │
                 │    (主从复制/读写分离) │ │  (会话 · 热数据 · 分布式锁)│
                 └─────────────────────┘ └───────────────────────┘
```

### 1.2 为什么选择微服务而非单体

| 维度 | 单体 | 微服务 (推荐) |
|------|------|-------------|
| 日均 5000 标本 | 可支撑，但高峰期耦合风险高 | 仪器通讯服务独立扩展，不影响其他模块 |
| HIS 对接 | 一家 HIS 改接口需全量回归 | 仅改 Integration 服务，零影响核心 |
| 团队协作 | 代码冲突多 | 按模块分服务，并行开发 |
| 部署 | 全量发布 | 独立发布，降低风险 |
| 推荐度 | 小型实验室 (<1000/日) | **中大型实验室 (>=3000/日)** |

---

## 二、技术选型

### 2.1 后端技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 基础框架 | **Spring Boot 3.x + Spring Cloud Alibaba** | 主流 Java 微服务生态，社区活跃 |
| 服务注册/配置 | **Nacos** | 注册中心 + 动态配置管理 |
| API 网关 | **Spring Cloud Gateway** | 路由、鉴权、限流、日志 |
| 服务间调用 | **OpenFeign** | 声明式 HTTP 客户端 |
| ORM | **MyBatis-Plus** | 灵活 SQL + 代码生成，医疗报表友好 |
| 数据库 | **PostgreSQL 15+** (推荐) / MySQL 8.0 | PostgreSQL 对复杂查询、JSON、数组支持更好 |
| 缓存 | **Redis 7.x (集群模式)** | 会话管理、标本状态缓存、分布式锁 |
| 消息队列 | **RabbitMQ** (标本业务) / **Kafka** (仪器大数据) | 异步解耦 + 高吞吐仪器数据流 |
| 定时任务 | **XXL-JOB** | 质控统计、报告归档、数据清理 |
| 文件存储 | **MinIO** / 对象存储 | 报告 PDF、质控图、日志备份 |
| 认证鉴权 | **Spring Security + JWT** | 基于 RBAC 权限模型 |
| 接口文档 | **SpringDoc OpenAPI 3** | 自动生成 API 文档，便于 HIS 对接联调 |
| 日志 | **Logback + ELK** | 全链路追踪 (MDC traceId) |
| 监控 | **Prometheus + Grafana** | 服务健康、QPS、标本处理延迟 |

### 2.2 前端技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | **Vue 3 + TypeScript** | Composition API，类型安全 |
| 构建工具 | **Vite 5** | 快速热更新，开发体验好 |
| UI 组件库 | **Element Plus** | 企业级后台组件丰富，表格/表单能力强 |
| 状态管理 | **Pinia** | Vue 3 官方推荐，轻量 |
| 路由 | **Vue Router 4** | 动态路由 + 权限守卫 |
| HTTP 客户端 | **Axios** | 请求/响应拦截器统一处理 |
| 图表 | **ECharts 5** | 质控图、统计报表可视化 |
| 打印 | **vue-print-nb** / **LODOP** | 检验报告条码打印、报告打印 |
| 样式方案 | **SCSS + CSS Variables** | 主题定制、响应式 |

### 2.3 基础设施

| 工具 | 用途 |
|------|------|
| Docker + Docker Compose | 开发环境一键启动 |
| Jenkins / GitLab CI | CI/CD 自动化构建部署 |
| Harbor | 私有镜像仓库 |
| Nginx | 反向代理、静态资源、负载均衡 |
| SSL 证书 | 医疗数据传输加密 (TLS 1.2+) |

---

## 三、核心功能模块

### 3.1 模块全景图

```
┌──────────────────────────────────────────────────────────────────┐
│                        LIS 临床实验室信息系统                      │
├──────────┬──────────┬──────────┬──────────┬─────────────────────┤
│  标本管理  │  检验执行  │  报告管理  │  质量控制  │   仪器通讯 & 采集    │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│ ·申请接收  │ ·任务分配  │ ·报告生成  │ ·室内质控  │ ·LIS/ASTM 协议      │
│ ·条码打印  │ ·结果录入  │ ·审核发布  │ ·室间质控  │ ·串口/网络双通道     │
│ ·标本核收  │ ·自动采集  │ ·危急值    │ ·质控图    │ ·多仪器并发接收      │
│ ·标本追踪  │ ·复查管理  │ ·报告打印  │ ·多规则    │ ·数据自动匹配标本    │
│ ·退费管理  │ ·复核管理  │ ·历史查询  │ ·偏差分析  │ ·异常值自动标记      │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│  仪器管理  │  试剂管理  │  微生物   │  基础数据  │   统计分析 & 系统    │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│ ·仪器台账  │ ·试剂字典  │ ·细菌鉴定  │ ·项目字典  │ ·工作量统计         │
│ ·维护记录  │ ·库存管理  │ ·药敏分析  │ ·组合项目  │ ·收费统计           │
│ ·保养计划  │ ·效期预警  │ ·药敏打印  │ ·仪器字典  │ ·危急值统计         │
│ ·维修管理  │ ·出入库    │ ·多级报告  │ ·菌株字典  │ ·趋势分析           │
│ ·校准记录  │ ·试剂追溯  │ ·血培养    │ ·参考范围  │ ·用户权限/日志       │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│  人事管理  │  系统管理  │  HIS 对接  │  移动端 & 扩展            │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│ ·小组管理  │ ·用户权限  │ ·患者信息同步│ ·微信/钉钉告警          │
│ ·人员档案  │ ·科室管理  │ ·医嘱申请接收│ ·临床端报告查询         │
│ ·职称管理  │ ·操作日志  │ ·结果回传   │ ·标本物流追踪           │
│ ·资质证书  │ ·数据备份  │ ·费用同步   │ ·BI 报表大屏            │
│ ·文档资料  │ ·系统参数  │ ·HL7/FHIR  │                         │
└──────────┴──────────┴──────────┴──────────┴─────────────────────┘
```

### 3.2 核心业务流程

```
HIS 开检验申请 → LIS 接收申请 → 生成条码 → 打印条码贴管
       ↓
采样 / 送检 → 标本核收 → 分配到仪器 → 仪器检测
       ↓
自动采集结果 → 结果自动审核 → 人工复核 (异常/危急值)
       ↓
报告审核 → 报告发布 → 回传 HIS → 临床医生查看
```

### 3.3 模块详细设计

#### 3.3.1 标本管理模块

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 申请接收 | 从 HIS 接收检验申请，支持批量导入 | P0 |
| 条码生成与打印 | 根据标本类型/试管类别自动生成条码，支持预置条码和现场打印 | P0 |
| 标本核收 | 扫码核收，校验患者信息、标本类型、容器匹配 | P0 |
| 标本状态追踪 | 全生命周期状态：已申请→已采样→已核收→检测中→已报告 | P0 |
| 标本退费 | 支持退标本、退费、作废流程 | P1 |
| 标本合并 | 糖耐量等场景，多个标本结果合并到一张报告 | P2 |
| 急诊优先 | 急诊标本置顶处理、加急通道 | P1 |

**关键数据模型：**

```sql
-- 标本主表
CREATE TABLE lis_sample (
    id              BIGINT PRIMARY KEY,
    sample_barcode  VARCHAR(32) NOT NULL UNIQUE,  -- 标本条码
    patient_id      BIGINT NOT NULL,              -- 患者 ID
    visit_id        BIGINT,                       -- 就诊 ID
    patient_name    VARCHAR(50),
    sample_type     VARCHAR(20) NOT NULL,         -- 标本类型: 血清/全血/尿液/...
    container_type  VARCHAR(20),                   -- 容器类型: 普通管/抗凝管/...
    status          TINYINT DEFAULT 0,            -- 0申请 1采样 2核收 3检测中 4报告
    is_urgent       TINYINT DEFAULT 0,            -- 是否急诊
    receive_time    DATETIME,                      -- 核收时间
    report_time     DATETIME,                      -- 报告时间
    operator_id     BIGINT,                        -- 操作人
    department      VARCHAR(50),                   -- 送检科室
    remark          TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_barcode (sample_barcode),
    INDEX idx_receive_time (receive_time)
);
```

#### 3.3.2 报告管理模块

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 自动报告生成 | 仪器结果自动匹配标本，生成报告草稿 | P0 |
| 自动审核规则 | 基于参考范围、历史结果、Delta 检查自动审核 | P0 |
| 多级审核 | 初审 → 复审 → 发布，支持权限控制 | P0 |
| 危急值管理 | 自动识别危急值，弹窗/短信/电话通知临床 | P0 |
| 报告打印 | 支持条码打印、A4 报告打印、批量打印 | P0 |
| 报告查询 | 多条件组合查询、历史结果对比 | P0 |
| 报告修改 | 修改留痕，记录修改前后值、修改原因 | P1 |
| 微生物报告 | 支持细菌鉴定、药敏结果的 structured 报告 | P2 |

**自动审核规则引擎设计：**

```java
/**
 * 自动审核规则引擎 - 策略模式
 * 规则可配置化，通过后台管理界面维护
 */
public interface AutoAuditRule {
    /** 规则名称 */
    String ruleName();
    /** 规则优先级，数字越小越先执行 */
    int priority();
    /** 执行审核，返回审核结果 */
    AuditResult audit(SampleReport report);
    /** 是否阻断审核（只要一条阻断，整批不通过） */
    boolean isBlocking();
}

// 内置规则示例：
// 1. ReferenceRangeCheck   - 结果在参考范围内
// 2. CriticalValueCheck   - 非危急值
// 3. DeltaCheck           - 与历史结果偏差 < 阈值
// 4. AnalyticalErrorCheck  - 无仪器报警/异常标记
// 5. PatientInfoComplete   - 患者信息完整
// 6. ReproducibilityCheck  - 双管结果偏差符合要求
```

#### 3.3.3 质量控制模块

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 质控品管理 | 质控品批号、靶值、标准差维护 | P0 |
| 日常质控 | 日常质控数据录入/仪器自动采集 | P0 |
| 质控图绘制 | Levey-Jennings 图、Z-分数图，支持 ECharts 渲染 | P0 |
| 多规则质控 | Westgard 多规则 (1-2s, 1-3s, 2-2s, R-4s, 4-1s, 10x) | P0 |
| 室间质控 | 上报数据录入、成绩管理、合格率统计 | P1 |
| 质控偏差预警 | 连续趋势偏移自动预警 | P1 |
| 质控月报 | 月度质控统计报表自动生成 | P2 |

**Westgard 多规则引擎：**

```java
/**
 * Westgard 多规则质控判定引擎
 * 每条质控记录经过规则链逐一判定
 */
public class WestgardRuleEngine {
    private List<WestgardRule> rules;

    public QCResult evaluate(List<QCRecord> recentRecords) {
        for (WestgardRule rule : rules) {
            if (rule.matches(recentRecords)) {
                return QCResult.reject(rule.getRuleName(), rule.getSuggestion());
            }
        }
        return QCResult.accept();
    }
}

// 规则配置示例 (存储在数据库，可动态调整)：
// { rule: "1-2s",   type: "WARNING",  enabled: true }
// { rule: "1-3s",   type: "REJECT",   enabled: true }
// { rule: "2-2s",   type: "REJECT",   enabled: true }
// { rule: "R-4s",   type: "REJECT",   enabled: true }
// { rule: "4-1s",   type: "REJECT",   enabled: true }
// { rule: "10x",    type: "REJECT",   enabled: true }
```

#### 3.3.4 仪器通讯与数据采集模块

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|------|
| 多协议支持 | LIS-01/ASTM 协议、HL7、串口 RS-232/RS-485、TCP/IP | P0 |
| 双通道热备 | 串口 + 网络双通道，故障自动切换 | P0 |
| 多仪器并发 | 同时连接多台仪器，每台独立线程 | P0 |
| 结果自动匹配 | 条码/样本号自动关联标本 | P0 |
| 异常值标记 | 仪器报警信息 (如 ***、>xxx) 自动标记 | P0 |
| 通讯日志 | 全量原始报文记录，便于追溯 | P1 |
| 仪器状态监控 | 仪器在线/离线/通讯异常实时监控 | P1 |

#### 3.3.5 仪器管理模块

独立于人机交互的"仪器通讯"，本模块聚焦 **设备全生命周期与运维管理**。

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 仪器台账 | 设备基础信息、厂家、型号、序列号、安装日期、保修到期、使用科室 | P0 |
| 每日维护记录 | 每日开关机检查、清洗、耗材检查、温湿度/试剂余量记录；可配置维护项模板 | P0 |
| 保养计划 | 按周期 (日/周/月/季/年) 生成保养任务，到期提醒，执行记录 | P0 |
| 维修管理 | 故障报修、维修工单、备件更换、维修费用、维修商记录 | P1 |
| 校准记录 | 校准时间、校准品批号、校准结果、下次校准日期提醒 | P1 |
| 预防性维护 (PM) | PM 计划制定与执行，生成 PM 完成率报表 | P1 |
| 仪器状态看板 | 在线/离线/维护中/故障中状态集中展示 | P1 |
| 维护记录追溯 | 按仪器/时间段/维护类型查询导出，支持签名确认 | P1 |

**每日维护记录设计：**

```java
/**
 * 每日维护记录 (Daily Maintenance)
 * 可配置模板：不同仪器类型有不同的维护项清单
 */
public class InstrumentDailyMaintenance {
    private Long id;
    private Long instrumentId;           // 仪器 ID
    private LocalDate maintenanceDate;   // 维护日期
    private String templateCode;         // 维护模板编码
    private List<MaintenanceItem> items; // 维护项列表
    private Long operatorId;             // 执行人
    private String operatorName;
    private String reviewerName;         // 复核人 (双人核查)
    private LocalDateTime createTime;
    private String remark;
}

public class MaintenanceItem {
    private String itemCode;             // 维护项代码
    private String itemName;             // 维护项名称
    private Integer itemType;            // 1开关机 2清洗 3耗材 4质控 5其他
    private String expectedValue;        // 预期结果
    private String actualValue;          // 实际结果
    private Integer result;              // 0异常 1正常 2未执行
    private String abnormalReason;       // 异常原因
}
```

**维护计划生成示例：**

```java
/**
 * 保养计划生成器
 * 根据仪器保养规则，提前生成未来一段时间的保养任务
 */
@Service
public class MaintenancePlanGenerator {

    public List<MaintenanceTask> generateTasks(Long instrumentId, MaintenanceRule rule, LocalDate from, LocalDate to) {
        List<MaintenanceTask> tasks = new ArrayList<>();
        LocalDate cursor = rule.getFirstDate();
        while (!cursor.isAfter(to)) {
            if (!cursor.isBefore(from)) {
                tasks.add(new MaintenanceTask(instrumentId, rule.getCycle(), cursor));
            }
            cursor = advanceByCycle(cursor, rule.getCycle());
        }
        return tasks;
    }

    private LocalDate advanceByCycle(LocalDate date, CycleType cycle) {
        return switch (cycle) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
    }
}
```

#### 3.3.6 试剂管理模块

用于管理检验试剂的 **采购、入库、出库、库存、效期、追溯**，与仪器检测、质控、成本核算联动。

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 试剂字典 | 试剂名称、规格、厂家、CAS、储存条件、所属项目/仪器 | P0 |
| 库存管理 | 多库位管理 (仓库/冰箱/货架)、实时库存、安全库存预警 | P0 |
| 入库管理 | 采购入库、赠品入库、退库，记录批号/效期/数量/单价 | P0 |
| 出库管理 | 科室领用、仪器上机消耗、质控消耗、报损出库 | P0 |
| 效期预警 | 近效期 (30天/90天) 自动预警，过期自动锁定 | P0 |
| 条码/二维码 | 试剂瓶身二维码，扫码出入库、扫码上机 | P1 |
| 批次追溯 | 从试剂批号追溯到哪些标本/质控使用了该试剂 | P1 |
| 库存盘点 | 定期盘点，盈亏记录，盘点报表 | P1 |
| 成本核算 | 按项目/标本统计试剂消耗成本 | P2 |

**关键业务规则：**

- **先进先出 (FIFO)**：出库时优先消耗最早入库批次，支持手动选择批次。
- **效期锁定**：过期批次自动不能出库，界面标红提示。
- **上机绑定**：仪器检测时自动/手动扣减试剂库存，记录试剂批号到结果表，实现追溯。
- **安全库存**：库存低于安全阈值时，系统通知采购人员。

**试剂使用追溯流程：**

```
标本检测 → 选择试剂批号 → 上机消耗 → 记录到 lis_result.reagent_lot_id
                    ↓
          发现试剂质量问题 → 输入批号 → 反向查询所有使用该批次的标本/质控
```

#### 3.3.7 微生物业务模块

覆盖微生物检验的 **细菌鉴定、药敏试验、血培养、多级报告** 完整流程。

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 细菌鉴定 | 接收鉴定结果 (手工录入/仪器导入)，维护菌株字典 | P0 |
| 药敏试验 | 药敏板维护、药敏结果录入/仪器导入、S/I/R 判定 | P0 |
| 药敏数据获取 | 支持从 VITEK、Phoenix、MicroScan 等药敏仪自动采集 | P0 |
| 药敏报告打印设置 | 可配置报告模板、显示/隐藏抗生素、折点版本 (CLSI/EUCAST) | P0 |
| 多级报告 | 一级 (涂片/培养) → 二级 (鉴定) → 三级 (药敏) → 终报告，支持追加/修改 | P0 |
| 血培养业务 | 血培养瓶接种、阳性报警接收、转种记录、阴阳性结果、报阳时间 TTP | P0 |
| 危急值/特殊耐药 | MRSA、CRE、CRAB 等特殊耐药标记，触发危急值通知 | P1 |
| 专家规则 | 根据菌种自动推荐药敏药物、天然耐药规则校验 | P1 |
| 流行病学统计 | 阳性率、菌种分布、耐药率统计 | P2 |

**多级报告模型：**

```java
/**
 * 微生物多级报告
 * 一份标本可产生多个级别的报告，每个级别可独立发布
 */
public class MicroReport {
    private Long id;
    private Long sampleId;                  // 标本 ID
    private String reportLevel;             // LEVEL_1 涂片/培养
                                            // LEVEL_2 细菌鉴定
                                            // LEVEL_3 药敏结果
                                            // FINAL  终报告
    private String reportStatus;            // DRAFT / REVIEWED / PUBLISHED / AMENDED
    private List<MicroOrganism> organisms;  // 检出菌
    private String gramStainResult;         // 涂片结果
    private String cultureResult;           // 培养结果
    private LocalDateTime issueTime;        // 发布时间
    private Long publisherId;
    private Long parentReportId;            // 上级报告 ID (多级关联)
}

public class MicroOrganism {
    private Long id;
    private Long microReportId;
    private String organismCode;            // 菌株编码
    private String organismName;            // 菌株名称
    private Integer quantity;               // 菌量 (1+/2+/3+/4+)
    private Boolean isPathogen;             // 是否致病菌
    private List<AntibioticSensitivity> sensitivities; // 药敏列表
}

public class AntibioticSensitivity {
    private Long id;
    private Long organismId;
    private String antibioticCode;          // 抗生素代码
    private String antibioticName;          // 抗生素名称
    private String micValue;                // MIC 值
    private String kbValue;                 // 抑菌圈直径
    private String result;                  // S 敏感 / I 中介 / R 耐药
    private String breakpointVersion;       // CLSI 2024 / EUCAST 2024
    private String method;                  // 检测方法
}
```

**药敏数据获取方式：**

```
方式 1: 药敏仪自动接口
   VITEK / Phoenix / MicroScan → ASTM/HL7 → LIS 仪器通讯服务 → 解析 → 存储

方式 2: 手动录入
   界面选择菌种 → 选择药敏板 → 输入 MIC/KB → 自动 S/I/R 判定

方式 3: 文件导入
   支持 Excel/CSV/仪器中间文件导入，导入前预览校验
```

**药敏报告打印设置：**

```java
/**
 * 微生物报告打印模板配置
 */
public class MicroReportPrintConfig {
    private Long id;
    private String configName;              // 配置名称
    private Boolean showMic;                // 是否显示 MIC
    private Boolean showKb;                 // 是否显示抑菌圈
    private Boolean showBreakpoint;         // 是否显示折点
    private Boolean showMethod;             // 是否显示方法
    private List<String> hiddenAntibiotics; // 隐藏抗生素代码
    private List<String> orderByCategories; // 抗生素分类排序
    private String breakpointVersion;       // 默认折点版本
    private String paperSize;               // A4 / A5 / B5
    private Boolean isDefault;              // 是否默认模板
}
```

**血培养业务模型：**

```java
/**
 * 血培养记录
 */
public class BloodCultureRecord {
    private Long id;
    private Long sampleId;                  // 标本 ID
    private String bottleNo;                // 培养瓶号
    private String mediumType;              // 培养基类型
    private LocalDateTime inoculationTime;  // 接种时间
    private LocalDateTime alarmTime;        // 仪器报警阳性时间
    private String result;                  // POSITIVE / NEGATIVE / CONTAMINATED
    private LocalDateTime finalTime;        // 最终报告时间
    private Long instrumentId;              // 血培养仪
    private String ttpHours;                // 报警时间 - 接种时间 (小时)
    private List<BloodCultureSubculture> subcultures; // 转种记录
}
```

**仪器通讯架构：**

```
┌─────────────────────────────────────────────────────────┐
│                   Instrument Communication Server       │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ 血细胞仪  │  │ 生化仪   │  │ 免疫仪   │   ...        │
│  │ COM1/TC  │  │ COM2/TC  │  │ TCP:8081 │              │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘              │
│        │             │             │                    │
│  ┌─────┴─────────────┴─────────────┴─────┐              │
│  │     Protocol Parser (LIS-01/ASTM/HL7) │              │
│  │     每台仪器独立 Parser 实例           │              │
│  └─────────────────┬────────────────────┘              │
│                    │                                     │
│  ┌─────────────────┴────────────────────┐              │
│  │     Message Queue (RabbitMQ/Kafka)   │              │
│  │     仪器数据 → 异步缓冲 → 业务处理    │              │
│  └─────────────────┬────────────────────┘              │
│                    │                                     │
│  ┌─────────────────┴────────────────────┐              │
│  │     Result Matching Service          │              │
│  │     条码匹配 → 标本关联 → 存储结果     │              │
│  └──────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

#### 3.3.8 人事与小组管理模块

用于管理实验室 **组织架构、人员档案、职称晋升、资质证书、文档资料**，满足 ISO 15189 对人员能力和档案管理的要求。

**功能清单：**

| 功能 | 描述 | 优先级 |
|------|------|-------|
| 小组/科室管理 | 检验小组 (如生化组、免疫组、微生物组)、科室层级、小组长、所属仪器 | P0 |
| 人员档案 | 姓名、工号、所属小组、联系方式、学历、专业、入职日期、账号状态 | P0 |
| 职称管理 | 职称字典 (检验士/检验师/主管技师/副主任技师/主任技师)、职称晋升记录、有效期 | P0 |
| 资质证书 | 执业资格证、PCR 上岗证、HIV 检测证、输血证、特种设备证等；支持证书到期预警 | P0 |
| 培训记录 | 岗前培训、在岗培训、继续教育、考核记录；与资质授权关联 | P1 |
| 授权管理 | 岗位授权 (如报告审核权、仪器操作权、项目操作权)、授权有效期 | P1 |
| 文档资料 | SOP、质量手册、程序文件、表单模板的版本管理与在线预览 | P1 |
| 电子签名 | 人员数字签名采集，与报告审核、操作记录绑定 | P1 |
| 排班管理 | 人员排班、值班记录、交接班管理 | P2 |

**数据模型设计：**

```java
/**
 * 小组/科室
 */
public class LabGroup {
    private Long id;
    private String groupCode;           // 小组编码
    private String groupName;           // 小组名称
    private Long parentId;              // 父级小组 (支持层级)
    private Long leaderId;              // 组长
    private String leaderName;
    private String location;            // 工作地点
    private List<Long> instrumentIds;   // 所属仪器
    private Integer sortOrder;
    private Integer status;
}

/**
 * 人员档案
 */
public class Staff {
    private Long id;
    private String staffNo;             // 工号
    private String name;
    private String phone;
    private String email;
    private Long groupId;               // 所属小组
    private String groupName;
    private Long userId;                // 关联系统账号
    private String education;           // 学历
    private String major;               // 专业
    private LocalDate entryDate;        // 入职日期
    private LocalDate leaveDate;        // 离职日期
    private String idCard;              // 身份证号
    private String signatureImage;      // 电子签名图片路径
    private Integer status;             // 0离职 1在职 2试用期
}

/**
 * 职称记录
 */
public class StaffTitle {
    private Long id;
    private Long staffId;
    private String titleCode;           // 职称编码
    private String titleName;           // 职称名称: 检验士/检验师/主管技师...
    private LocalDate acquireDate;      // 获得日期
    private LocalDate expiryDate;       // 有效期 (部分职称需定期认定)
    private String certificateNo;       // 证书编号
    private String attachmentUrl;       // 证书扫描件
}

/**
 * 资质证书
 */
public class StaffCertificate {
    private Long id;
    private Long staffId;
    private String certType;            // 资质类型: PCR/HIV/输血/执业资格...
    private String certName;            // 证书名称
    private String certNo;              // 证书编号
    private LocalDate issueDate;        // 发证日期
    private LocalDate expiryDate;       // 到期日期
    private String issueOrg;            // 发证机构
    private String attachmentUrl;       // 附件
    private Integer alertDays;          // 提前预警天数
}

/**
 * 岗位授权
 */
public class StaffAuthorization {
    private Long id;
    private Long staffId;
    private String authType;            // REPORT_AUDIT / INSTRUMENT_OP / PROJECT_OP
    private String authScope;           // 授权范围 (仪器编码/项目编码)
    private LocalDate startDate;        // 授权开始
    private LocalDate endDate;          // 授权结束
    private Long grantorId;             // 授权人
    private String grantorName;
}

/**
 * 培训记录
 */
public class StaffTraining {
    private Long id;
    private Long staffId;
    private String trainingName;        // 培训名称
    private String trainingType;        // 岗前/在岗/继续教育/外部培训
    private LocalDate trainingDate;
    private String trainer;
    private String result;              // 合格/不合格
    private String attachmentUrl;
}

/**
 * 文档资料 (SOP/质量手册等)
 */
public class LabDocument {
    private Long id;
    private String docCode;             // 文档编码
    private String docName;             // 文档名称
    private String docType;             // SOP / MANUAL / PROCEDURE / FORM
    private String version;             // 版本号
    private Long groupId;               // 适用小组
    private String fileUrl;             // 文件路径
    private Long uploaderId;
    private LocalDateTime uploadTime;
    private Integer status;             // 0作废 1现行 2草稿
}
```

**业务规则：**

- 人员离职后，系统账号自动停用，历史操作记录保留不可删。
- 资质证书到期前 30 天（可配置）自动提醒本人和科室主任。
- 报告审核权限必须与职称/资质授权匹配：未取得审核资质的人员不能参与报告终审。
- 电子签名与报告审核、标本操作绑定，满足审计追溯要求。

---

## 四、HIS 对接方案

### 4.1 对接架构

采用 **适配器模式 (Adapter Pattern)**，将 HIS 对接逻辑隔离在独立的 Integration 服务中，核心业务不依赖任何特定 HIS 厂商。

```
┌────────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│   三方 HIS 系统  │────→│  HIS Integration    │────→│   LIS Core      │
│  (多种厂商/版本) │←────│  Adapter Layer      │←────│   Services      │
└────────────────┘     └──────────────────────┘     └─────────────────┘
                              │
                              ├─ HadoopAdapter     (HIS 厂商 A)
                              ├─ NeusoftAdapter    (HIS 厂商 B)
                              ├─ DHCAdapter       (HIS 厂商 C)
                              ├─ UniversalAdapter  (通用 REST/HL7)
                              └─ 自定义 Adapter     (新厂商接入)
```

### 4.2 对接接口规范

#### 4.2.1 标准协议支持

| 协议 | 场景 | 说明 |
|------|------|------|
| **HL7 v2.x** | 传统 HIS 对接 | MLMLP/TCP 最常用，支持 ADT^A01/A08 (患者), ORM^O01 (医嘱), ORU^R01 (结果) |
| **HL7 FHIR R4** | 新一代 HIS 对接 | RESTful 风格，JSON 格式，未来趋势 |
| **REST API** | 现代 HIS/互联网医院 | JSON/HTTP，自定义接口契约 |
| **Web Service** | 老旧 HIS 对接 | SOAP/XML，兼容遗留系统 |
| **数据库直连 (视图)** | 无接口的 HIS | 只读视图方式，最末选择 |

#### 4.2.2 核心数据交换接口

| 接口方向 | 接口名称 | 说明 | HL7 消息类型 |
|---------|---------|------|-------------|
| HIS→LIS | 患者信息同步 | 患者基本信息、就诊信息 | ADT^A01, ADT^A08 |
| HIS→LIS | 检验申请下发 | 医嘱/申请单信息 | ORM^O01 |
| HIS→LIS | 申请作废/撤销 | 取消已下发的检验申请 | ORM^O01 (cancel) |
| LIS→HIS | 患者信息查询 | LIS 主动查询患者信息 | ADT^QRY |
| LIS→HIS | 标本状态回传 | 标本核收、拒收状态通知 | ACK |
| LIS→HIS | 检验结果回传 | 报告审核后回传结果 | ORU^R01 |
| LIS→HIS | 危急值通知 | 危急值即时推送到临床 | ORU^R01 (urgent) |
| LIS→HIS | 费用信息回传 | 检验项目计费信息 | ADT^A04 (billing) |

### 4.3 适配器接口设计

```java
/**
 * HIS 适配器统一接口 - 策略模式
 * 每家 HIS 厂商实现此接口，通过配置切换
 */
public interface HisAdapter {

    /** 适配器标识 */
    String getAdapterId();

    /** 健康检查 */
    boolean healthCheck();

    // ===== HIS → LIS 方向 =====

    /** 接收检验申请 */
    List<LabOrder> receiveOrders(Date startTime, Date endTime);

    /** 查询患者信息 */
    PatientInfo queryPatient(String patientId, String visitId);

    // ===== LIS → HIS 方向 =====

    /** 回传检验结果 */
    boolean sendResults(LabReport report);

    /** 回传标本状态 */
    boolean sendSampleStatus(SampleStatusUpdate update);

    /** 发送危急值通知 */
    boolean sendCriticalValueAlert(CriticalValueAlert alert);
}

/**
 * 适配器工厂 - 根据配置实例化对应厂商适配器
 */
@Component
public class HisAdapterFactory {

    private Map<String, HisAdapter> adapterMap;

    public HisAdapter getAdapter(String hisVendorCode) {
        HisAdapter adapter = adapterMap.get(hisVendorCode);
        if (adapter == null) {
            throw new UnsupportedHisVendorException(hisVendorCode);
        }
        return adapter;
    }
}
```

### 4.4 HL7 v2.x 消息示例

```
# LIS → HIS 检验结果回传 (ORU^R01)
MSH|^~\&|LIS|HOSPITAL|HIS|HOSPITAL|20260728143000||ORU^R01|MSG00001|P|2.4
PID|1||P202607280001||张三||19850315|M|||门诊||13800138000
PV1|1|O|门诊内科||||医生A||||||||V0001
ORC|RE||ORD20260728001||CM|||20260728143000
OBR|1||ORD20260728001|血常规|||20260728143000|20260728143500|LIS^检验科|||||||F|||^张技师
OBX|1|NM|WBC^白细胞||6.5|10^9/L|4.0-10.0|N||F
OBX|2|NM|RBC^红细胞||4.5|10^12/L|3.5-5.5|N||F
OBX|3|NM|HGB^血红蛋白||135|g/L|120-160|N||F
OBX|4|NM|PLT^血小板||220|10^9/L|100-300|N||F
```

---

## 五、性能保障方案 (日均 5000 标本)

### 5.1 性能指标目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 标本接收吞吐量 | >= 30 标本/分钟 (峰值 5000/日 约 8h 处理窗口) | 含条码生成、核收 |
| 仪器数据采集延迟 | < 3 秒 (从仪器出结果到入库) | 含解析 + 匹配 |
| 报告生成延迟 | < 5 秒 (结果确认到报告生成) | 含自动审核 |
| HIS 结果回传延迟 | < 10 秒 | 含网络 + HIS 处理 |
| 危急值通知延迟 | < 30 秒 | 含人工确认 + 推送 |
| 页面响应时间 | < 500ms (P95) | 核心 CRUD 页面 |
| 系统可用性 | >= 99.9% | 年停机 < 8.76 小时 |

### 5.2 数据库优化策略

```
┌─────────────────────────────────────────────────────────┐
│                   数据库优化分层                          │
│                                                         │
│  1. 读写分离                                             │
│     Master (写) ──同步──→ Slave1 (读)                    │
│                   ──同步──→ Slave2 (读)                 │
│     路由: ShardingSphere-JDBC / MyCat                    │
│                                                         │
│  2. 分表策略 (按月/按年)                                  │
│     lis_sample_202607                                    │
│     lis_sample_202608                                    │
│     lis_result_202607                                    │
│     ... 自动化创建，查询自动路由                          │
│                                                         │
│  3. 索引优化                                             │
│     - 标本条码唯一索引 (高频扫码查询)                      │
│     - 患者 ID + 时间联合索引 (结果查询)                   │
│     - 状态 + 时间联合索引 (工作列表过滤)                   │
│     - 覆盖索引避免回表 (高频只读查询)                       │
│                                                         │
│  4. 缓存层                                               │
│     Redis 缓存:                                          │
│     - 标本状态 (高频轮询)                                 │
│     - 项目字典 / 组合字典 (极少变更)                      │
│     - 参考范围 (按年龄/性别)                              │
│     - 用户会话 / 权限                                     │
│     - 危急值规则 (配置)                                   │
└─────────────────────────────────────────────────────────┘
```

### 5.3 仪器通讯高可用

```
┌──────────────────────────────────────────────────────────┐
│                仪器通讯保障策略                             │
│                                                          │
│  1. 消息缓冲                                              │
│     仪器数据 → RabbitMQ Queue → 异步消费                   │
│     队列持久化，服务重启不丢失                              │
│                                                          │
│  2. 断线重连                                              │
│     心跳检测 (每 30s) → 超时自动重连 (3次指数退避)          │
│     重连期间数据暂存仪器本地 → 恢复后自动补传                │
│                                                          │
│  3. 双通道热备                                            │
│     主通道: TCP/IP 网络                                    │
│     备通道: RS-232 串口                                   │
│     主通道异常 → 自动切换备通道 → 故障告警                  │
│                                                          │
│  4. 通讯进程独立                                          │
│     每台仪器独立线程/进程 → 单台故障不影响其他仪器           │
│     通讯服务可独立重启 → 不影响核心业务                     │
└──────────────────────────────────────────────────────────┘
```

### 5.4 部署架构 (生产环境)

```
                        ┌─────────────────────┐
                        │   负载均衡器 (LB)     │
                        │   Nginx / F5         │
                        └─────────┬───────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
     ┌────────┴─────┐    ┌────────┴─────┐    ┌────────┴─────┐
     │  Node A      │    │  Node B      │    │  Node C      │
     │  API Gateway  │    │  API Gateway  │    │  API Gateway  │
     │  标本服务 x2   │    │  标本服务 x2   │    │  报告服务 x2   │
     │  报告服务 x1   │    │  质控服务 x1   │    │  质控服务 x1   │
     └────────┬──────┘    └────────┬──────┘    └────────┬──────┘
              │                   │                   │
     ┌────────┴───────────────────┴───────────────────┴────┐
     │                  仪器通讯集群                        │
     │          Instrument Service x 3 (独立部署)           │
     │    每台实例连接不同仪器，互不干扰                       │
     └─────────────────────┬──────────────────────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
     ┌────────┴───┐ ┌─────┴────┐ ┌──────┴────┐
     │ PostgreSQL  │ │   Redis  │ │ RabbitMQ  │
     │ Master      │ │  Cluster │ │  Cluster  │
     │ └── Slave   │ │          │ │           │
     └─────────────┘ └──────────┘ └───────────┘
```

### 5.5 异步化与削峰

```java
/**
 * 标本处理异步化方案
 * 核心：同步只做"收"和"状态更新"，重的操作全异步
 */
@Service
public class SampleProcessService {

    // 同步：核收 (毫秒级)
    @Transactional
    public void receiveSample(SampleReceiveDTO dto) {
        // 1. 更新标本状态
        // 2. 记录核收日志
        // 3. 发消息到 MQ
        rabbitTemplate.convertAndSend("sample.received", dto);
    }

    // 异步消费：分配仪器
    @RabbitListener(queues = "sample.received")
    public void assignToInstrument(SampleReceiveDTO dto) {
        // 根据标本类型+组合项目 → 匹配仪器 → 发送检测指令
    }

    // 异步消费：结果回传 HIS
    @RabbitListener(queues = "report.published")
    public void syncToHis(LabReport report) {
        hisAdapter.sendResults(report);
    }
}
```

---

## 六、安全与合规

| 维度 | 措施 | 说明 |
|------|------|------|
| 身份认证 | JWT Token + 刷新令牌 | 无状态认证，适合微服务 |
| 权限控制 | RBAC + 数据权限 | 角色-权限-菜单 + 科室级数据隔离 |
| 传输安全 | HTTPS (TLS 1.2+) | 全链路加密 |
| 数据安全 | 敏感字段 AES 加密存储 | 患者身份证号、手机号等 |
| 审计日志 | 全操作日志 | 谁 + 何时 + 做了什么 + 改前/改后 |
| 数据备份 | 每日增量 + 每周全量 | 保留 90 天，异地灾备 |
| 合规 | 符合等保三级、电子签名法 | 报告电子签名、时间戳 |
| 危急值追溯 | 全链路记录 | 从识别 → 通知 → 确认 → 处置 |

---

## 七、项目工程结构

### 7.1 后端 Maven 多模块结构

```
lis-system/
├── pom.xml                          # 父 POM，统一依赖管理
├── lis-common/                       # 公共模块
│   ├── lis-common-core/              #   基础工具类、常量、统一响应
│   ├── lis-common-redis/            #   Redis 配置、工具
│   ├── lis-common-mybatis/          #   MyBatis-Plus 配置、分页
│   ├── lis-common-security/         #   安全框架、JWT 工具
│   └── lis-common-swagger/          #   API 文档配置
├── lis-api/                          # 接口定义模块 (Feign Client)
│   ├── lis-api-sample/              #   标本服务接口
│   ├── lis-api-report/              #   报告服务接口
│   ├── lis-api-qc/                   #   质控服务接口
│   └── lis-api-instrument/          #   仪器服务接口
├── lis-service/                      # 业务服务模块
│   ├── lis-service-sample/          #   标本管理服务 (端口 8081)
│   ├── lis-service-report/          #   报告管理服务 (端口 8082)
│   ├── lis-service-qc/              #   质量控制服务 (端口 8083)
│   ├── lis-service-instrument/      #   仪器通讯服务 (端口 8084)
│   ├── lis-service-base/            #   基础数据服务 (端口 8085)
│   ├── lis-service-integration/     #   HIS 对接服务 (端口 8086)
│   └── lis-service-system/          #   系统管理服务 (端口 8087)
├── lis-gateway/                      # API 网关 (端口 8080)
├── lis-auth/                          # 认证服务 (端口 8090)
├── lis-monitor/                      # 监控服务
└── docs/                             # 项目文档
    ├── database/                     #   数据库脚本
    ├── api/                          #   接口文档
    └── deploy/                       #   部署文档
```

### 7.2 前端项目结构

```
lis-frontend/
├── public/
├── src/
│   ├── api/                          # API 请求封装
│   │   ├── sample.ts                #   标本相关 API
│   │   ├── report.ts                #   报告相关 API
│   │   ├── qc.ts                    #   质控相关 API
│   │   └── instrument.ts            #   仪器相关 API
│   ├── assets/                       # 静态资源
│   ├── components/                   # 公共组件
│   │   ├── Print/                   #   打印组件
│   │   ├── Charts/                  #   图表组件 (质控图)
│   │   └── Upload/                  #   上传组件
│   ├── composables/                 # 组合式函数
│   ├── layouts/                      # 布局组件
│   ├── router/                       # 路由配置
│   ├── stores/                       # Pinia 状态管理
│   ├── styles/                       # 全局样式
│   ├── utils/                        # 工具函数
│   └── views/                        # 页面视图
│       ├── sample/                   #   标本管理页面
│       ├── report/                   #   报告管理页面
│       ├── qc/                       #   质控管理页面
│       ├── instrument/               #   仪器管理页面
│       ├── base/                     #   基础数据页面
│       ├── stats/                    #   统计分析页面
│       └── system/                   #   系统管理页面
├── .env.development                  # 开发环境变量
├── .env.production                   # 生产环境变量
├── vite.config.ts
├── tsconfig.json
└── package.json
```

---

## 八、开发规范与建议

### 8.1 代码规范

| 维度 | 规范 |
|------|------|
| 后端代码风格 | Alibaba Java Coding Guidelines + Checkstyle |
| 前端代码风格 | ESLint + Prettier (Vue 官方推荐规则) |
| 接口契约 | RESTful，统一响应体 `{code, message, data, traceId}` |
| 版本控制 | Git Flow 分支策略 (feature/develop/release/hotfix) |
| 代码审查 | MR/PR 必须 >= 1 人 Review |

### 8.2 开发优先级路线图

```
Phase 1 (MVP - 8周)                    Phase 2 (完善 - 6周)                   Phase 3 (增强 - 4周)
━━━━━━━━━━━━━━━━━━━━                   ━━━━━━━━━━━━━━━━━━                   ━━━━━━━━━━━━━━━━━━
 基础数据管理                             质控模块完整功能                       移动端告警
 标本全流程 (申请→核收→报告)              Westgard 多规则引擎                    BI 统计大屏
 仪器通讯 (2-3台主流仪器)                微生物报告模块                         室间质控
 报告生成+打印                           Delta Check 规则                      标本物流追踪
 HIS 对接 (1家，REST/HL7)              HIS 对接 (第2家，适配器切换)             FHIR 支持
 用户权限+日志                           数据备份与恢复                         性能压测优化
```

### 8.3 推荐开发团队配置

| 角色 | 人数 | 职责 |
|------|------|------|
| 项目经理 | 1 | 需求管理、进度控制、HIS 对接协调 |
| 后端开发 | 3-4 | 核心业务模块开发 |
| 前端开发 | 2 | Web 端页面开发 |
| 仪器通讯开发 | 1-2 | 仪器协议对接、硬件调试 |
| 测试工程师 | 1 | 功能测试、性能测试、UAT |
| 实施工程师 | 1 | 部署、HIS 联调、现场支持 |

---

## 九、总结

本方案为临床实验室设计了一套 **前后端分离、微服务架构** 的 LIS 系统，核心特点：

1. **开发友好** — Spring Boot + Vue 3 主流技术栈，学习成本低；模块清晰，团队可并行开发
2. **高性能** — 日均 5000 标本量通过异步化、缓存、读写分离、消息队列削峰保障
3. **HIS 对接灵活** — 适配器模式支持多厂商 HIS，HL7/REST/WS 多协议，新增厂商仅加 Adapter
4. **可扩展** — 微服务架构，各模块独立扩展；仪器通讯服务可按需扩容
5. **安全合规** — 等保三级要求，审计日志全链路，危急值闭环管理
