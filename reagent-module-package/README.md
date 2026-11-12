# LIS 试剂管理模块 - 独立部署包

> 版本：v1.0 ｜ 生成日期：2026-10-25
> 从 LIS 临床实验室信息系统中独立拆分的「试剂管理」模块，包含前端页面、后端服务、数据库脚本。

---

## 一、模块概述

试剂管理模块覆盖试剂全生命周期管理：**字典 → 采购 → 入库 → 库存 → 消耗 → 盘点 → 报废 → 追溯 → 统计**，共 20 个前端页面、23 张数据库表。

### 前端页面清单（20 个）

| 序号 | 页面 | 路由 | 说明 |
| --- | --- | --- | --- |
| 1 | 库存看板 | `/reagent/dashboard` | 库存总量、效期预警、低库存告警 |
| 2 | 试剂字典 | `/reagent/list` | 试剂主数据维护（含 MSDS、危险品） |
| 3 | 试剂库存 | `/reagent/stock` | 批次库存查询、条码追溯 |
| 4 | 入库验收 | `/reagent/inbound` | 采购入库验收、条码自动生成 |
| 5 | 消耗登记 | `/reagent/consumption` | 手工/自动消耗登记 |
| 6 | 出入库记录 | `/reagent/record` | 全量流水查询 |
| 7 | 库存盘点 | `/reagent/check` | 盘点单、盘盈盘亏 |
| 8 | 成本核算 | `/reagent/cost` | 按项目/科室核算试剂成本 |
| 9 | 采购申请 | `/reagent/purchase-apply` | 采购申请+审批流程 |
| 10 | 采购订单 | `/reagent/purchase-order` | 采购订单管理 |
| 11 | 请领业务单 | `/reagent/requisition` | 领用/退库/报废/调拨/借用/归还 |
| 12 | 供应商管理 | `/reagent/supplier` | 供应商主数据+资质证照 |
| 13 | 分类管理 | `/reagent/category` | 试剂分类树 |
| 14 | 仓库管理 | `/reagent/warehouse` | 库房/冰箱/层架三级树 |
| 15 | 预算管理 | `/reagent/budget` | 年度预算+占用+实扣 |
| 16 | 试剂报表 | `/reagent/report` | 消耗/库存/成本统计报表 |
| 17 | 移动扫码 | `/reagent/mobile` | PDA 扫码出入库 |
| 18 | 供应商评价 | `/reagent/supplier-evaluate` | 供应商评分 |
| 19 | 环保处置台账 | `/reagent/scrap-disposal` | 危险品报废处置 |
| 20 | 开瓶记录 | （内嵌） | 试剂开瓶效期管理 |

### 数据库表清单（23 张 + 1 处加列）

| 类别 | 表名 | 说明 |
| --- | --- | --- |
| 基础 | `lis_reagent` | 试剂字典表 |
| 基础 | `lis_reagent_stock` | 试剂库存表（按批次） |
| 基础 | `lis_reagent_stock_record` | 出入库流水 |
| 核心 | `lis_reagent_inbound` | 入库单 |
| 核心 | `lis_reagent_inbound_item` | 入库明细 |
| 核心 | `lis_reagent_consumption` | 消耗单 |
| 核心 | `lis_reagent_opening` | 开瓶记录 |
| 盘点 | `lis_reagent_stock_check` | 盘点单 |
| 盘点 | `lis_reagent_stock_check_item` | 盘点明细 |
| 增强 | `lis_reagent_category` | 试剂分类（树形） |
| 增强 | `lis_supplier` | 供应商主数据 |
| 增强 | `lis_reagent_warehouse` | 仓库/冰箱/层架（三级树） |
| 增强 | `lis_reagent_budget` | 预算主表 |
| 增强 | `lis_reagent_budget_record` | 预算变动流水 |
| 增强 | `lis_reagent_approval_node` | 审批节点配置 |
| 增强 | `lis_reagent_purchase_apply` | 采购申请单 |
| 增强 | `lis_reagent_purchase_apply_item` | 采购申请明细 |
| 增强 | `lis_reagent_purchase_approval` | 采购审批流水 |
| 增强 | `lis_reagent_purchase_order` | 采购订单 |
| 增强 | `lis_reagent_purchase_order_item` | 采购订单明细 |
| 增强 | `lis_reagent_requisition` | 出入库业务单（六类合一） |
| 增强 | `lis_reagent_requisition_item` | 业务单明细 |
| 增强 | `lis_reagent_scrap_disposal` | 报废环保处置扩展 |
| 追溯 | `lis_result`（加列） | 试剂批次追溯字段 `reagent_lot_id` / `reagent_lot_no` |

---

## 二、目录结构

```
reagent-module-package/
├── db/
│   └── reagent-module-schema.sql      # 独立数据库脚本（23 张表+加列，幂等可重复执行）
├── frontend/
│   └── src/views/reagent/             # 前端 20 个页面（Vue3 + Element Plus）
├── backend/
│   └── lis-service-reagent/           # 后端 Spring Boot 服务
│       ├── src/main/java/com/lis/reagent/
│       │   ├── controller/            # 20 个 Controller
│       │   ├── service/               # 18 个 Service 接口 + impl
│       │   ├── mapper/                # 25 个 Mapper
│       │   ├── entity/                # 24 个实体
│       │   ├── dto/ vo/ enums/        # DTO / VO / 枚举
│       │   ├── support/               # 状态机、守卫、工具类
│       │   └── job/                   # 效期预警定时任务
│       ├── src/main/resources/application.yml
│       └── pom.xml
└── README.md                          # 本文档
```

---

## 三、数据库脚本使用

### 执行方式

```bash
psql -h <host> -p <port> -U <user> -d <db> -v ON_ERROR_STOP=1 -f db/reagent-module-schema.sql
```

### 脚本特性

- **幂等**：全部建表 `CREATE TABLE IF NOT EXISTS`，加列 `ADD COLUMN IF NOT EXISTS`，索引 `IF NOT EXISTS`，可重复执行。
- **独立**：不依赖 LIS 其他模块表，仅需 PostgreSQL 14+。
- **单事务**：增强功能部分（`add-reagent-enhance.sql`）包裹在 `BEGIN; ... COMMIT;` 中，失败整体回滚。
- **安全**：对 `lis_result` 的加列做了 `IF EXISTS` 守卫，目标表不存在时自动跳过。

### 执行顺序（脚本内部已编排）

1. 基础表（3 张）
2. 核心扩展（入库/消耗/开瓶，4 张）
3. 库存盘点（2 张）
4. 增强功能（分类/供应商/仓库/预算/采购/出入库/报废，14 张 + 存量数据迁移）
5. 批次追溯（`lis_result` 加列）

---

## 四、前端集成

将 `frontend/src/views/reagent/` 目录复制到 LIS 前端项目的 `src/views/` 下，然后在路由配置中添加：

```js
// src/router/index.js 中 reagent 模块路由
{
  path: '/reagent',
  component: Layout,
  meta: { title: '试剂管理' },
  children: [
    { path: 'dashboard',        component: () => import('@/views/reagent/dashboard/index.vue') },
    { path: 'list',             component: () => import('@/views/reagent/list/index.vue') },
    { path: 'stock',            component: () => import('@/views/reagent/stock/index.vue') },
    { path: 'inbound',          component: () => import('@/views/reagent/inbound/index.vue') },
    { path: 'consumption',      component: () => import('@/views/reagent/consumption/index.vue') },
    { path: 'record',           component: () => import('@/views/reagent/record/index.vue') },
    { path: 'check',            component: () => import('@/views/reagent/check/index.vue') },
    { path: 'cost',             component: () => import('@/views/reagent/cost/index.vue') },
    { path: 'purchase-apply',   component: () => import('@/views/reagent/purchaseApply/index.vue') },
    { path: 'purchase-order',   component: () => import('@/views/reagent/purchaseOrder/index.vue') },
    { path: 'requisition',      component: () => import('@/views/reagent/requisition/index.vue') },
    { path: 'supplier',         component: () => import('@/views/reagent/supplier/index.vue') },
    { path: 'category',         component: () => import('@/views/reagent/category/index.vue') },
    { path: 'warehouse',        component: () => import('@/views/reagent/warehouse/index.vue') },
    { path: 'budget',           component: () => import('@/views/reagent/budget/index.vue') },
    { path: 'report',           component: () => import('@/views/reagent/report/index.vue') },
    { path: 'mobile',           component: () => import('@/views/reagent/mobile/index.vue') },
    { path: 'supplier-evaluate',component: () => import('@/views/reagent/supplierEvaluate/index.vue') },
    { path: 'scrap-disposal',   component: () => import('@/views/reagent/scrapDisposal/index.vue') }
  ]
}
```

**技术栈**：Vue 3 + Element Plus + Axios + Vue Router。

---

## 五、后端集成

### 模块依赖

`lis-service-reagent` 依赖以下 LIS 内部模块（需在父工程 `lis-service` 下编译）：

- `lis-common`：通用工具（R 结果封装、Redis 工具）
- `lis-api`：DTO / VO 定义

第三方依赖：Spring Boot Web、Spring Cloud Alibaba Nacos、Redis、Apache POI（Excel 导入）、MyBatis-Plus。

### 编译运行

```bash
# 在父工程 lis-service 目录下编译
cd lis-system/lis-service
mvn clean install -pl lis-service-reagent -am

# 运行（需先配置 application.yml 中的数据库、Nacos、Redis 连接）
java -jar lis-service-reagent/target/lis-service-reagent-1.0.0.jar
```

### API 前缀

所有 Controller 统一前缀为 `/api/reagent`（参考各 Controller 类上的 `@RequestMapping`）。

---

## 六、数据库表 ER 关系（核心）

```
lis_reagent (试剂字典)
    │
    ├── lis_reagent_stock (批次库存) ── lis_reagent_stock_record (流水)
    │
    ├── lis_reagent_category (分类)
    │
    ├── lis_supplier (供应商)
    │
    └── lis_reagent_warehouse (仓库)

采购链路:
lis_reagent_purchase_apply (申请) → lis_reagent_purchase_approval (审批)
    → lis_reagent_purchase_order (订单) → lis_reagent_inbound (入库)
        → lis_reagent_inbound_item (入库明细) → lis_reagent_stock (库存)

出入库业务单:
lis_reagent_requisition (六类合一) → lis_reagent_requisition_item (明细)
    └── lis_reagent_scrap_disposal (报废处置扩展)

预算域:
lis_reagent_budget (预算) ← lis_reagent_budget_record (流水)

盘点:
lis_reagent_stock_check (盘点单) → lis_reagent_stock_check_item (明细)
```

---

## 七、注意事项

1. **数据库版本**：PostgreSQL 14+（使用了 `BIGSERIAL`、`COALESCE` 表达式索引、`DO $$ ... $$` 块等特性）。
2. **事务警告**：`add-reagent-enhance.sql` 部分为单事务，执行期间会锁表，建议在停机窗口执行。
3. **供应商迁移**：脚本会自动将存量 `lis_reagent.supplier` 字符串迁移为 `lis_supplier` 主数据草稿；若存在人工录入的重名供应商，脚本会主动报错中断，需先在页面归并。
4. **逻辑删除**：本模块不使用 `deleted` 逻辑删除字段，统一用 `status` 字段控制启用/停用。
