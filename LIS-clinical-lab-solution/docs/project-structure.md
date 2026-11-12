# LIS 项目工程结构说明

> 本文档详述 LIS 系统前后端工程目录结构、关键配置文件及启动方式，便于开发团队快速上手。

---

## 一、后端工程结构 (lis-system)

### 1.1 Maven 多模块总览

```
lis-system/
├── pom.xml                              # 父 POM: 统一版本管理、模块声明
│
├── lis-common/                           # ① 公共模块 (jar)
│   ├── pom.xml
│   └── src/main/java/com/lis/common/
│       ├── core/                         #   核心工具
│       │   ├── result/                   #   统一响应 R<T>、PageResult
│       │   ├── exception/                #   业务异常 BusinessException
│       │   ├── constant/                 #   系统常量 CacheKey、RegexConstant
│       │   └── util/                     #   DateUtil、StringUtil、AESUtil
│       ├── redis/                        #   Redis 配置、RedisUtil
│       ├── mybatis/                      #   MyBatis-Plus 配置、分页插件
│       ├── security/                     #   JWT 工具、SecurityContext
│       └── swagger/                      #   OpenAPI 文档配置
│
├── lis-api/                               # ② 接口定义模块 (Feign Client, jar)
│   ├── lis-api-sample/                   #   标本服务接口定义
│   │   └── src/main/java/com/lis/api/sample/
│   │       ├── dto/                      #   SampleDTO、SampleQueryDTO
│   │       ├── vo/                       #   SampleVO
│   │       └── feign/SampleFeignClient.java
│   ├── lis-api-report/                   #   报告服务接口定义
│   ├── lis-api-qc/                       #   质控服务接口定义
│   ├── lis-api-instrument/               #   仪器服务接口定义
│   ├── lis-api-reagent/                  #   试剂服务接口定义
│   └── lis-api-micro/                    #   微生物服务接口定义
│
├── lis-service/                           # ③ 业务服务模块 (可独立部署)
│   │
│   ├── lis-service-sample/               #   标本管理服务 [端口 8081]
│   │   ├── src/main/java/com/lis/sample/
│   │   │   ├── LisSampleApplication.java #   启动类
│   │   │   ├── controller/               #   REST 接口层
│   │   │   │   ├── SampleController.java
│   │   │   │   └── BarcodeController.java
│   │   │   ├── service/                  #   业务逻辑层
│   │   │   │   ├── SampleService.java
│   │   │   │   ├── impl/SampleServiceImpl.java
│   │   │   │   └── BarcodeService.java
│   │   │   ├── mapper/                   #   数据访问层
│   │   │   │   └── SampleMapper.java
│   │   │   ├── entity/                   #   数据库实体
│   │   │   │   └── Sample.java
│   │   │   ├── mq/                       #   消息队列消费
│   │   │   │   └── SampleReceivedListener.java
│   │   │   └── config/                   #   服务配置
│   │   └── src/main/resources/
│   │       ├── application.yml           #   主配置
│   │       ├── application-dev.yml       #   开发环境
│   │       ├── application-prod.yml      #   生产环境
│   │       └── mapper/                   #   MyBatis XML
│   │
│   ├── lis-service-report/               #   报告管理服务 [端口 8082]
│   │   └── src/main/java/com/lis/report/
│   │       ├── controller/ReportController.java
│   │       ├── service/ReportService.java
│   │       ├── service/AutoAuditEngine.java      # 自动审核引擎
│   │       ├── service/CriticalValueService.java # 危急值管理
│   │       └── service/PdfGenerateService.java   # 报告 PDF 生成
│   │
│   ├── lis-service-qc/                   #   质量控制服务 [端口 8083]
│   │   └── src/main/java/com/lis/qc/
│   │       ├── controller/QCController.java
│   │       ├── service/WestgardRuleEngine.java   # Westgard 多规则引擎
│   │       └── service/QCChartService.java       # 质控图数据
│   │
│   ├── lis-service-instrument/           #   仪器通讯服务 [端口 8084]
│   │   └── src/main/java/com/lis/instrument/
│   │       ├── comm/                     #   通讯核心
│   │       │   ├── serial/SerialConnector.java   # 串口连接
│   │       │   ├── tcp/TcpConnector.java          # TCP 连接
│   │       │   └── InstrumentConnectionManager.java
│   │       ├── protocol/                 #   协议解析
│   │       │   ├── astm/ASTMParser.java           # ASTM 协议
│   │       │   ├── hl7/HL7Parser.java             # HL7 协议
│   │       │   └── ProtocolParserFactory.java
│   │       ├── handler/                  #   仪器处理器 (每台仪器一个)
│   │       │   ├── MindrayHandler.java             # 迈瑞
│   │       │   ├── SysmexHandler.java              # 希森美康
│   │       │   ├── RocheHandler.java               # 罗氏
│   │       │   └── AbbottHandler.java              # 雅培
│   │       └── matching/ResultMatchingService.java # 结果匹配标本
│   │
│   ├── lis-service-instrument-mgmt/      #   仪器管理服务 [端口 8085]
│   │   └── src/main/java/com/lis/instrumentmgmt/
│   │       ├── controller/
│   │       │   ├── InstrumentController.java     # 仪器台账
│   │       │   ├── MaintenanceController.java    # 维护记录
│   │       │   ├── MaintenancePlanController.java # 保养计划
│   │       │   └── RepairController.java         # 维修校准
│   │       ├── service/
│   │       │   ├── MaintenanceTemplateService.java # 维护模板
│   │       │   ├── MaintenancePlanGenerator.java   # 计划生成
│   │       │   └── MaintenanceTaskService.java     # 任务管理
│   │       └── job/
│   │           └── MaintenanceReminderJob.java     # 维护提醒
│   │
│   ├── lis-service-reagent/              #   试剂管理服务 [端口 8086]
│   │   └── src/main/java/com/lis/reagent/
│   │       ├── controller/
│   │       │   ├── ReagentController.java     # 试剂字典
│   │       │   ├── StockController.java       # 库存管理
│   │       │   ├── StockRecordController.java # 出入库
│   │       │   └── InventoryController.java   # 盘点
│   │       ├── service/
│   │       │   ├── ReagentStockService.java   # 库存计算
│   │       │   ├── ReagentFifoService.java    # 先进先出
│   │       │   └── ExpiryAlertService.java    # 效期预警
│   │       └── job/
│   │           ├── ExpiryAlertJob.java        # 效期提醒
│   │           └── SafetyStockAlertJob.java   # 安全库存提醒
│   │
│   ├── lis-service-micro/                #   微生物服务 [端口 8087]
│   │   └── src/main/java/com/lis/micro/
│   │       ├── controller/
│   │       │   ├── MicroReportController.java        # 微生物报告
│   │       │   ├── AntibioticController.java         # 抗生素字典
│   │       │   ├── SensitivityController.java        # 药敏结果
│   │       │   ├── BloodCultureController.java       # 血培养
│   │       │   └── PrintConfigController.java        # 打印配置
│   │       ├── service/
│   │       │   ├── MicroReportService.java           # 多级报告
│   │       │   ├── SensitivityAnalysisService.java   # 药敏 S/I/R 判定
│   │       │   ├── BloodCultureService.java          # 血培养流程
│   │       │   ├── ResistanceRuleEngine.java         # 特殊耐药规则
│   │       │   └── MicroReportPrintService.java      # 报告打印
│   │       └── parser/
│   │           ├── VitekParser.java                  # VITEK 数据解析
│   │           ├── PhoenixParser.java                # Phoenix 数据解析
│   │           └── MicroScanParser.java              # MicroScan 数据解析
│   │
│   ├── lis-service-base/                 #   基础数据服务 [端口 8088]
│   │   └── src/main/java/com/lis/base/
│   │       ├── controller/TestItemController.java    # 检验项目
│   │       ├── controller/ReferenceRangeController.java
│   │       ├── controller/InstrumentController.java  # 仪器字典 (通讯用)
│   │       ├── controller/OrganismController.java    # 菌株字典
│   │       └── controller/AntibioticController.java  # 抗生素字典
│   │
│   ├── lis-service-hr/                   #   人事管理服务 [端口 8089]
│   │   └── src/main/java/com/lis/hr/
│   │       ├── controller/
│   │       │   ├── GroupController.java         # 小组/科室管理
│   │       │   ├── StaffController.java         # 人员档案
│   │       │   ├── TitleController.java         # 职称管理
│   │       │   ├── CertificateController.java   # 资质证书
│   │       │   ├── AuthorizationController.java # 岗位授权
│   │       │   ├── TrainingController.java      # 培训记录
│   │       │   ├── DocumentController.java      # 文档资料
│   │       │   └── ScheduleController.java      # 排班管理
│   │       ├── service/
│   │       │   ├── StaffService.java            # 人员档案
│   │       │   ├── CertificateAlertService.java # 资质到期预警
│   │       │   ├── AuthorizationCheckService.java # 授权校验
│   │       │   └── DocumentVersionService.java  # 文档版本
│   │       └── job/
│   │           ├── CertificateExpiryAlertJob.java # 资质到期提醒
│   │           └── ScheduleReminderJob.java       # 排班提醒
│   │
│   ├── lis-service-integration/          #   HIS 对接服务 [端口 8090]
│   │   └── src/main/java/com/lis/integration/
│   │       ├── adapter/                  #   HIS 适配器
│   │       │   ├── HisAdapter.java                  # 统一接口
│   │       │   ├── UniversalRestAdapter.java        # 通用 REST
│   │       │   ├── HL7v2Adapter.java                # HL7 v2
│   │       │   ├── FhirAdapter.java                 # FHIR R4
│   │       │   └── HisAdapterFactory.java           # 适配器工厂
│   │       ├── message/                  #   HL7 消息构建/解析
│   │       │   ├── HL7MessageBuilder.java
│   │       │   └── HL7MessageParser.java
│   │       └── job/                      #   定时同步任务
│   │           ├── OrderSyncJob.java                # 申请同步
│   │           └── ResultSyncJob.java               # 结果回传
│   │
│   └── lis-service-system/               #   系统管理服务 [端口 8092]
│       └── src/main/java/com/lis/system/
│           ├── controller/AuthController.java       # 登录认证
│           ├── controller/UserController.java       # 用户管理
│           ├── controller/RoleController.java       # 角色权限
│           └── controller/LogController.java        # 日志查询
│
├── lis-gateway/                           # ④ API 网关 [端口 8080]
│   └── src/main/java/com/lis/gateway/
│       ├── LisGatewayApplication.java
│       ├── filter/                       #   过滤器
│       │   ├── AuthFilter.java                       # JWT 鉴权
│       │   ├── LogFilter.java                        # 请求日志
│       │   └── RateLimitFilter.java                  # 限流
│       └── config/                       #   路由配置
│           └── RouteConfig.java
│
├── lis-auth/                              # ⑤ 认证服务 [端口 8090]
│   └── src/main/java/com/lis/auth/
│       ├── controller/LoginController.java
│       └── service/AuthService.java
│
└── docs/                                  # ⑥ 项目文档
    ├── database/
    │   ├── init-schema.sql               #   建表脚本
    │   ├── init-data.sql                 #   初始化数据
    │   └── database-design.md            #   ER 设计文档
    ├── api/
    │   └── api-spec.yaml                 #   OpenAPI 规范
    └── deploy/
        ├── docker-compose.yml            #   开发环境编排
        ├── Dockerfile                    #   服务镜像构建
        └── nginx.conf                    #   Nginx 配置
```

### 1.2 关键配置文件示例

#### 父 POM 依赖管理 (pom.xml 核心片段)

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
    <spring-cloud.version>2023.0.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <hutool.version>5.8.25</hutool.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 标本服务配置 (application.yml)

```yaml
server:
  port: 8081

spring:
  application:
    name: lis-service-sample
  datasource:
    master:
      url: jdbc:postgresql://localhost:5432/lis_db
      username: lis_user
      password: ${DB_PASSWORD}
      driver-class-name: org.postgresql.Driver
    slave:
      url: jdbc:postgresql://localhost:5433/lis_db
      username: lis_readonly
      password: ${DB_PASSWORD}
  redis:
    host: localhost
    port: 6379
    database: 1
  rabbitmq:
    host: localhost
    port: 5672
    username: lis
    password: ${MQ_PASSWORD}

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# Nacos 配置
cloud:
  nacos:
    discovery:
      server-addr: localhost:8848
    config:
      server-addr: localhost:8848
      file-extension: yaml
```

#### 网关路由配置 (RouteConfig.java)

```java
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("sample-service", r -> r
                .path("/api/sample/**")
                .uri("lb://lis-service-sample"))
            .route("report-service", r -> r
                .path("/api/report/**")
                .uri("lb://lis-service-report"))
            .route("qc-service", r -> r
                .path("/api/qc/**")
                .uri("lb://lis-service-qc"))
            .route("instrument-service", r -> r
                .path("/api/instrument/**")
                .uri("lb://lis-service-instrument"))
            .route("instrument-mgmt-service", r -> r
                .path("/api/instrument-mgmt/**")
                .uri("lb://lis-service-instrument-mgmt"))
            .route("reagent-service", r -> r
                .path("/api/reagent/**")
                .uri("lb://lis-service-reagent"))
            .route("micro-service", r -> r
                .path("/api/micro/**")
                .uri("lb://lis-service-micro"))
            .route("base-service", r -> r
                .path("/api/base/**")
                .uri("lb://lis-service-base"))
            .route("hr-service", r -> r
                .path("/api/hr/**")
                .uri("lb://lis-service-hr"))
            .route("integration-service", r -> r
                .path("/api/integration/**")
                .uri("lb://lis-service-integration"))
            .build();
    }
}
```

### 1.3 开发环境启动 (Docker Compose)

```yaml
# docs/deploy/docker-compose.yml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: lis_db
      POSTGRES_USER: lis_user
      POSTGRES_PASSWORD: lis_dev_2026
    ports: ["5432:5432"]
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ../database/init-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
      - ../database/init-data.sql:/docker-entrypoint-initdb.d/02-data.sql

  redis:
    image: redis:7
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]

  nacos:
    image: nacos/nacos-server:v2.3.0
    environment:
      MODE: standalone
    ports: ["8848:8848"]

volumes:
  pg_data:
```

启动命令：

```bash
cd docs/deploy
docker-compose up -d
```

---

## 二、前端工程结构 (lis-frontend)

### 2.1 目录结构

```
lis-frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── main.ts                          # 应用入口
│   ├── App.vue                          # 根组件
│   │
│   ├── api/                              # API 请求层
│   │   ├── request.ts                   #   Axios 实例、拦截器
│   │   ├── types.ts                     #   通用响应类型
│   │   ├── sample/
│   │   │   ├── index.ts                 #   标本相关 API
│   │   │   └── types.ts                 #   标本相关 TS 类型
│   │   ├── report/
│   │   ├── qc/
│   │   ├── instrument/                  #   仪器通讯 API
│   │   ├── instrument-mgmt/             #   仪器管理 API
│   │   ├── reagent/                     #   试剂管理 API
│   │   ├── micro/                       #   微生物 API
│   │   ├── hr/                          #   人事管理 API
│   │   ├── base/                        #   基础数据 API
│   │   ├── system/                      #   系统管理 API
│   │   └── auth/                        #   认证 API
│   │
│   ├── assets/                           # 静态资源
│   │   ├── images/
│   │   └── styles/
│   │       ├── variables.scss           #   SCSS 变量
│   │       ├── reset.scss               #   样式重置
│   │       └── index.scss               #   全局样式
│   │
│   ├── components/                       # 公共组件
│   │   ├── Print/                       #   打印组件
│   │   │   ├── BarcodePrint.vue         #     条码打印
│   │   │   └── ReportPrint.vue          #     报告打印
│   │   ├── Charts/                      #   图表组件
│   │   │   ├── QCChart.vue              #     质控图 (Levey-Jennings)
│   │   │   ├── TrendChart.vue           #     趋势图
│   │   │   └── StatChart.vue            #     统计图表
│   │   ├── Table/                       #   表格组件
│   │   │   └── ProTable.vue             #     业务表格 (封装分页/排序)
│   │   └── Form/                        #   表单组件
│   │       └── SchemaForm.vue           #     动态表单
│   │
│   ├── composables/                      # 组合式函数
│   │   ├── useTable.ts                  #   表格逻辑 (查询/分页)
│   │   ├── usePermission.ts             #   权限判断
│   │   ├── useBarcode.ts                #   条码生成
│   │   └── useWebSocket.ts              #   WebSocket (仪器状态推送)
│   │
│   ├── layouts/                          # 布局组件
│   │   ├── DefaultLayout.vue            #   默认布局 (侧边栏+顶栏)
│   │   ├── components/
│   │   │   ├── Sidebar.vue              #     侧边导航
│   │   │   ├── Header.vue               #     顶部栏
│   │   │   ├── TagsView.vue             #     标签页
│   │   │   └── Breadcrumb.vue           #     面包屑
│   │   └── PrintLayout.vue              #   打印专用布局
│   │
│   ├── router/                           # 路由
│   │   ├── index.ts                     #   路由实例
│   │   ├── guards.ts                    #   路由守卫 (鉴权)
│   │   └── modules/                     #   各模块路由
│   │       ├── sample.ts
│   │       ├── report.ts
│   │       ├── qc.ts
│   │       └── system.ts
│   │
│   ├── stores/                           # Pinia 状态管理
│   │   ├── user.ts                      #   用户信息、权限
│   │   ├── app.ts                       #   应用配置 (侧边栏状态)
│   │   └── instrument.ts                #   仪器状态 (WebSocket)
│   │
│   ├── utils/                            # 工具函数
│   │   ├── auth.ts                      #   Token 管理
│   │   ├── format.ts                    #   格式化 (日期/数字)
│   │   ├── validate.ts                  #   校验规则
│   │   └── print.ts                     #   打印工具
│   │
│   └── views/                            # 页面视图
│       ├── login/
│       │   └── index.vue                #   登录页
│       │
│       ├── sample/                       # 标本管理
│       │   ├── receive/                 #   标本核收
│       │   │   └── index.vue
│       │   ├── tracking/                #   标本追踪
│       │   │   └── index.vue
│       │   ├── barcode/                 #   条码打印
│       │   │   └── index.vue
│       │   └── components/
│       │       └── SampleDetail.vue     #     标本详情抽屉
│       │
│       ├── report/                       # 报告管理
│       │   ├── list/                    #   报告列表
│       │   ├── audit/                   #   报告审核
│       │   ├── critical/                #   危急值处理
│       │   ├── query/                   #   报告查询
│       │   └── components/
│       │       ├── ReportPreview.vue    #     报告预览
│       │       └── CriticalDialog.vue   #     危急值弹窗
│       │
│       ├── qc/                           # 质量控制
│       │   ├── daily/                   #   日常质控
│       │   ├── chart/                   #   质控图
│       │   ├── rule-config/             #   规则配置
│       │   └── report/                  #   质控月报
│       │
│       ├── instrument/                   # 仪器通讯 (状态/配置/日志)
│       │   ├── status/                  #   仪器状态监控
│       │   ├── config/                  #   仪器配置
│       │   └── log/                     #   通讯日志
│       │
│       ├── instrument-mgmt/              # 仪器管理 (台账/维护/维修/校准)
│       │   ├── ledger/                  #   仪器台账
│       │   ├── daily/                   #   每日维护记录
│       │   ├── maintenance/             #   保养/维修/校准
│       │   ├── plan/                    #   维护计划
│       │   └── dashboard/               #   状态看板
│       │
│       ├── reagent/                      # 试剂管理
│       │   ├── dictionary/              #   试剂字典
│       │   ├── stock/                   #   库存管理
│       │   ├── inout/                   #   出入库
│       │   ├── inventory/               #   盘点
│       │   └── expiry/                  #   效期预警
│       │
│       ├── micro/                        # 微生物
│       │   ├── report/                  #   微生物报告
│       │   ├── sensitivity/             #   药敏结果
│       │   ├── blood-culture/           #   血培养
│       │   ├── resistance/              #   特殊耐药/危急值
│       │   └── print-config/            #   报告打印配置
│       │
│       ├── base/                         # 基础数据
│       │   ├── test-item/               #   检验项目
│       │   ├── ref-range/               #   参考范围
│       │   ├── instrument/              #   仪器字典
│       │   ├── organism/                #   菌株字典
│       │   └── antibiotic/              #   抗生素字典
│       │
│       ├── hr/                           # 人事管理
│       │   ├── group/                   #   小组/科室管理
│       │   ├── staff/                   #   人员档案
│       │   ├── title/                   #   职称管理
│       │   ├── certificate/             #   资质证书
│       │   ├── authorization/           #   岗位授权
│       │   ├── training/                #   培训记录
│       │   ├── document/                #   文档资料
│       │   └── schedule/                #   排班管理
│       │
│       ├── stats/                        # 统计分析
│       │   ├── workload/                #   工作量统计
│       │   ├── critical-stats/          #   危急值统计
│       │   └── trend/                   #   趋势分析
│       │
│       └── system/                       # 系统管理
│           ├── user/
│           ├── role/
│           ├── permission/
│           └── log/
│
├── .env.development                      # 开发环境变量
├── .env.production                       # 生产环境变量
├── .eslintrc.js                          # ESLint 配置
├── .prettierrc                           # Prettier 配置
├── vite.config.ts                        # Vite 配置
├── tsconfig.json                         # TS 配置
└── package.json
```

### 2.2 关键配置文件

#### package.json (核心依赖)

```json
{
  "name": "lis-frontend",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext .vue,.ts --fix"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.5",
    "pinia": "^2.1.7",
    "element-plus": "^2.5.0",
    "axios": "^1.6.5",
    "echarts": "^5.4.3",
    "vue-echarts": "^6.6.8",
    "dayjs": "^1.11.10",
    "jsbarcode": "^3.11.6",
    "lodop-print": "^1.0.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.0.0",
    "typescript": "^5.3.0",
    "vue-tsc": "^1.8.27",
    "sass": "^1.69.0",
    "eslint": "^8.56.0",
    "eslint-plugin-vue": "^9.20.0",
    "prettier": "^3.2.0",
    "@types/node": "^20.10.0"
  }
}
```

#### vite.config.ts

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 网关地址
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-vendor': ['element-plus'],
          'echarts-vendor': ['echarts', 'vue-echarts']
        }
      }
    }
  }
})
```

#### Axios 请求封装 (api/request.ts)

```typescript
import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器: 注入 Token
service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器: 统一错误处理
service.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200) return data
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  error => {
    if (error.response?.status === 401) {
      // Token 过期，跳转登录
      window.location.href = '/login'
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
```

---

## 三、开发启动指南

### 3.1 环境准备

```bash
# 1. 安装基础软件
# JDK 17+, Node 18+, Docker, Git

# 2. 克隆代码
git clone <repo-url> lis-system
cd lis-system

# 3. 启动基础设施 (PostgreSQL + Redis + RabbitMQ + Nacos)
cd docs/deploy
docker-compose up -d

# 4. 初始化数据库 (docker-compose 已自动执行 init-schema.sql)
# 如需手动: psql -h localhost -U lis_user -d lis_db -f docs/database/init-schema.sql
```

### 3.2 后端启动

```bash
# 1. 编译公共模块
mvn clean install -pl lis-common -am

# 2. 启动各服务 (开发时按需启动)
# 启动认证服务
mvn spring-boot:run -pl lis-auth

# 启动网关
mvn spring-boot:run -pl lis-gateway

# 启动标本服务
mvn spring-boot:run -pl lis-service/lis-service-sample

# 启动报告服务
mvn spring-boot:run -pl lis-service/lis-service-report
```

### 3.3 前端启动

```bash
cd lis-frontend

# 安装依赖
npm install

# 启动开发服务器 (默认 3000 端口)
npm run dev

# 打包生产环境
npm run build
```

### 3.4 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost:3000 | 开发环境 |
| API 网关 | http://localhost:8080 | 后端入口 |
| API 文档 | http://localhost:8080/doc.html | OpenAPI 文档 |
| Nacos 控制台 | http://localhost:8848/nacos | 服务注册管理 |
| RabbitMQ 控制台 | http://localhost:15672 | 消息队列管理 |
| 默认账号 | admin / 123456 | 初始管理员 |
