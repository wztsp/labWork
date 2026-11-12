# 试剂管理增量开发 · 第一批地基层验收报告

> 日期：2026-08-04　|　范围：T-01 ~ T-05（DDL + 实体/枚举 + support + DTO/VO + Mapper）
> 状态：**通过，放行业务层**　|　所有验证结论均为交付总监亲手实测，非成员自述

---

## 一、验收结论

| 闸门 | 方法 | 结果 |
|---|---|---|
| Java 编译 | `javac` 全量显式编译 110 个源文件 | **退出码 0 / 0 error / 121 class** |
| 实体 ↔ DDL 一致性 | `entitycheck.py` 机器比对 | 25 实体、361 字段，**不一致 0** |
| Mapper SQL ↔ DDL | `ddlcheck.py` 机器比对 | 12 组表×列，**不一致 0** |
| DDL 可执行性 | 真实 PostgreSQL `lis_db`，`ON_ERROR_STOP=1` | 退出码 0，**ERROR 0** |
| DDL 幂等性 | 同脚本连跑第二遍 | 退出码 0，**ERROR 0**，零副作用 |
| 架构约束 C-9 | 全模块扫 `deleted` / `@TableLogic` | **0 命中** |
| 架构约束 C-10 | entity/dto/vo/support/mapper/enums 扫 Lombok | **0 命中** |
| support 零 Spring | 6 个类 import 全量核对 | 仅 `BusinessException` + JDK + 自身枚举 |
| SQL 安全 | 扫字符串拼接与 `${}` | **0 命中** |

**库最终态**：88 表 / reagent 4 / stock 4 / warehouse 1 / category 5 / approval_node 4 / supplier 0，**零测试残留**。

---

## 二、交付内容

**DDL**：`lis-system/docs/database/add-reagent-enhance.sql`（710+ 行）
- 14 张新表：分类 / 供应商 / 仓库 / 预算 / 预算流水 / 审批节点 / 采购申请 + 明细 / 审批记录 / 采购订单 + 明细 / 领用单 + 明细 / 报废处置
- 6 张存量表改造：`lis_reagent`(+7 列) / `lis_reagent_stock` / `lis_reagent_inbound` / `lis_reagent_inbound_item` / `lis_reagent_stock_record` / `lis_reagent_stock_check`

**Java**（reagent 模块 110 个源文件）
| 层 | 内容 |
|---|---|
| entity | 14 新业务实体 + 2 跨模块只读投影 + 6 存量扩展 |
| enums | 12 个（含本轮补的 `OrderStatus` / `ReqStatus`） |
| support | 6 个零 Spring 纯 POJO：`BudgetGuard` `PurchaseApplyStateMachine` `ApprovalNodeResolver` `RequisitionTypeSpec` `StockCheckCalculator` `ReagentWarehouseGuard` |
| dto / vo | 11 / 6 |
| mapper | 14 新增 + 4 存量扩展 |

---

## 三、本轮修正的重要缺陷

### 1. C-9「逻辑删除地雷」是误报（架构师主张被推翻）

架构师主张「所有表无 `deleted` 列 → 任何 `remove`/`page` 必报错」，建议给 9 张存量表加列并挂 `@TableLogic`。**三方交叉印证证明该主张不成立**：

- 全局 `logic-delete-field: deleted` 确实配了（reagent 模块 `application.yml` L29-30）
- 但全项目 `@TableLogic` **仅 1 处命中**：`Sample.java:119`
- `init-schema.sql:113` 的 `deleted SMALLINT DEFAULT 0` 正是 `lis_sample`，与上条严格配对

**根因**：MyBatis-Plus 的 `logic-delete-field` 由 `TableInfoHelper` **按实体字段名惰性匹配**，实体未声明该字段时配置完全惰性、不注入 SQL。系统长期正常运行即是反证。

**真实雷点方向相反**：Java 实体声明了 `deleted` 而 DB 没建列，才会注入 `AND deleted = 0` 并抛 `column "deleted" does not exist`。

**裁决**：14 张新表不建 `deleted`、新实体不声明该字段、不挂 `@TableLogic`；存量 9 张表一列不动。逻辑删除语义由 `status` 承担。
**若误采纳原方案**：会把已上线的 `deleteById` 从物理删除**静默改成 UPDATE**，属生产事故。

### 2. 缺陷 K（多仓库同批号）需要三层修复，缺一层等于没修

| 层 | 内容 | 不做的后果 |
|---|---|---|
| ① | `DROP` 两列索引 → 建三列 `uk_lis_reagent_stock_lot_wh` | 多仓库上线即 insert 崩溃 |
| ② | 存量行用 **`warehouse_id = 0` 哨兵，禁 NULL** | PG 中 `NULL <> NULL`，唯一索引对存量行**静默失效** |
| ③ | `SET DEFAULT 0` → `UPDATE` → **`SET NOT NULL`**，且排在建索引之前 | legacy 环境实测 `dup_rows_admitted=2`，索引完全没拦住 |

第 ③ 层是本轮 QA 挖出的：仅 UPDATE 已有行不改列约束，之后新插入的 NULL 照样击穿索引。

### 3. 供应商迁移三个坑（被真实数据现状掩盖）

库内 `supplier` 字符串实际 0 条，迁移代码路径**从未执行**，"跑通了"是假象。造合成数据后暴露：

- **编号非幂等**：`ROW_NUMBER()` 在 `WHERE` 后求值，每次从 1 重排，重跑撞唯一约束 → 改 `MAX(...) + ROW_NUMBER()` 续号 + 正则守卫
- **`TRIM()` 只剥 ASCII 空格**：全角空格 / tab 幸存，`罗氏诊断` 5 变体裂成 5 条、`roche/Roche/ROCHE` 裂成 3 条
- **`supplier_name` 无唯一索引**：与手工录入重名时绑到优化器选的**任意赢家，全程无报错**

**合并解法**：`fn_supplier_name_norm()` IMMUTABLE 函数（连续空白折叠为**单空格**而非全删 + BTRIM + LOWER），同时用于**表达式唯一索引**与匹配。数据库层生效，界面新建也管得住；`supplier_name` 仍存原始串，展示不变小写。

**实测**（全部 `t`）：
```
全角空格合并 | 制表符合并 | 半角空格合并 | 大小写合并 | 内部空格不误合
      t      |     t      |      t       |     t      |       t
折叠结果：[北京 生物 科技]
```
重复变体插入被拒：`重复键违反唯一约束"uk_lis_supplier_name_norm"`

---

## 四、归并逻辑破坏性边界实测（重点）

工程师额外实现了重名归并（自动改指外键 + 删除输家草稿）。**因涉及删除数据，逐条实证**：

| 场景 | 期望 | 实测 |
|---|---|---|
| 人工记录(source_type=1) + 自动草稿(=2) 规范名重复 | 人工存活、草稿删除 | ✅ 人工 `QA_MAN_01` 带证照存活，草稿 `SUP_MIG_9001` 删除 |
| 外键指向被删草稿 | 自动改指赢家 | ✅ `supplier_id` 由 23 → 22 |
| 悬空引用 | 0 | ✅ 0 |
| 两条人工重名 | 中断，不自动删 | ✅ psql 退出码 3，守卫触发 |

守卫报错原文（可读性合格）：
```
错误: 存在人工录入的重名供应商，需先在页面人工归并后再执行本脚本：安捷伦科技(2条)
HINT: 这些记录 source_type <> 2，脚本不会自动删除，避免误删人工维护的证照数据
```

覆盖完整性核对：全库仅 `lis_reagent` / `lis_reagent_inbound` / `lis_reagent_purchase_order` 三表带 `supplier_id`，脚本三张全覆盖，无遗漏。

---

## 五、工程师自查发现的 4 个状态码缺陷

均属「不报错、不崩溃、只静默算错数」类型，最难排查：

| 位置 | 错误 | 后果 |
|---|---|---|
| `sumOccupiedAmountByBudget` | `NOT IN (1,5,7)`，状态 7 不存在 | 预算对账**永远不平** |
| `countUnfinishedByApplyId` | `NOT IN (4,5,6)` 应为 `(3,4)` | 申请单**永远无法结案** |
| `comparePriceByReagent` | 排除"已下单"、计入"已取消" | 比价结果**完全错误** |
| `sumReturnedQtyBySource` | `status=4`(已驳回) 应为 `3`(已执行) | 借用超量校验**恒失效** |

**根因**：`lis_reagent_purchase_order` 与 `lis_reagent_requisition` 缺枚举类，只能猜码值。已在本轮补齐 `OrderStatus` / `ReqStatus` 并在 Mapper javadoc 中 `{@link}` 引用常量，从源头消除魔数。

---

## 六、可复用产出

| 文件 | 用途 |
|---|---|
| `build/selfcheck/entitycheck.py` | 实体字段 ↔ DDL 列 全量机器比对 |
| `build/selfcheck/ddlcheck.py` | Mapper SQL 引用的表/列 ↔ DDL 比对 |
| `build/selfcheck/cp.txt` | reagent 模块完整编译 classpath |

编译复现：
```bash
find <module>/src/main/java -name "*.java" > list.txt
javac -d out -encoding UTF-8 -classpath "$(cat build/selfcheck/cp.txt)" @list.txt
```
⚠️ 新增文件后**必须重新 `find`**，不能复用旧 args 文件，否则漏编。

---

## 七、遗留事项

| # | 事项 | 归属 |
|---|---|---|
| 1 | `Reagent.safetyStock` 已 `@Deprecated`，3 处调用点待迁移到 `minStock`（`ReagentDashboardServiceImpl:91`、`ReagentStockServiceImpl:178/186/190`） | T-06 或 T-13 |
| 2 | `BudgetGuard` 不处理「预算记录缺失(null)」——须决定策略，否则新科室采购申请**全部被拒** | T-08 |
| 3 | 22.4/22.5 的 `supplier_id` 回填在**真实带供应商存量的环境**仍未验证（本地库 0 条） | 上线前 |
| 4 | 预算并发须 `selectForUpdate`(悲观锁) + `tryFreeze`(条件 CAS) **成对使用**，返回 0 必须抛异常回滚 | T-08 |
| 5 | 六类业务单严禁 `switch(reqType)`，一律走 `RequisitionTypeSpec.of()` | T-05+ |

---

## 八、下一步

**T-06 ~ T-13 业务层**：存量控制器治理（删 6 个裸奔端点 + 前端 4 函数、补 `@Transactional`）、预算动态管控、采购闭环、供应商管理、分类与危险品、多仓库、统一业务单、盘点调账。

**T-14 ~ T-19 第二批**：调拨借用、高危审批 + 环保处置、供应商评价、移动端、报表导出、检索增强。
