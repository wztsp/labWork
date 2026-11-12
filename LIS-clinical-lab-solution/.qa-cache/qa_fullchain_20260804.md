# LIS 试剂管理 · 真库采购闭环全链路验证报告（2026-08-04）

- 环境：新单体 fat jar（副本构建）`http://localhost:8095`，前端 dev `http://localhost:3000`（proxy /api → 8095），真库 `lis_db`（PostgreSQL 13）
- 验证基线：预算 0 / 供应商 0 / 申请 0 / 订单 0 / 入库 0 / 盘点 0；存量库存 4 条（id 1-4，340/200/120/50，**全程未修改**）

## 闭环 9 步结果

| 步骤 | 操作 | 结果 | 判定 |
|---|---|---|---|
| 1 | POST /api/reagent/supplier 创建 | 修复前 500（Bug S-1）；修复后 **200 id=63**；同名再建被拒"供应商名称已存在" | PASS（修复后） |
| 2 | POST /api/reagent/budget 建 2026 预算 deptId=2 | 200，budgetNo=BUD2026-2，totalAmount=100000 | PASS |
| 3 | POST /api/reagent/purchase-apply 草稿（2 明细 900 元） | 200，applyNo=PA202608040001，status=0 | PASS |
| 4 | POST /submit（冻结预算） | verdict=PASS 待审批；DB: budget8.frozen=900，流水 FREEZE | PASS |
| 5 | 审批链 | 单节点：approve→已批准；WARN 场景 2 节点（科室主任→分管院长 OVER_BUDGET）全流转 | PASS |
| 6 | 建单 + 下单 | PUR202608040001，place 后 status=1 已下单 | PASS |
| 7 | 入库验收 | 库存 +2 批（QA-LOT-001/002）、arrivedQty 回写、订单已到货、预算 frozen 80900→80000 + used 0→900 实扣 | PASS |
| 8 | 盘点调账 | 盘点单→submit 盘亏 3→库存 5→2，流水 recordType=5 quantity=-3，状态已完成 | PASS |
| 9 | 预算对账 reconcile | masterFrozen=80000/masterUsed=900/record 一致，consistent=true | PASS |

## 红线验证

| 红线 | 操作 | 结果 | 判定 |
|---|---|---|---|
| T-08 预算缺失 | 门诊部(deptId=6 无预算)提交 | 明确抛"预算记录不存在，请先为科室[门诊部]建立2026 年度预算"，frozen 未多冻一分 | PASS |
| 状态机非法迁移 | 已批准后再 approve | 抛"单据当前状态[已批准]不允许执行[通过]操作" | PASS |

## 源码 Bug（已修复闭环）

**Bug S-1（阻断级，已修）**：`SupplierMapper.countByNameNorm`（L112-115）`fn_supplier_name_norm(#{name})` + `#{excludeId} IS NULL` 无法推断参数类型 → 创建供应商必 500。
修复：参数加 `jdbcType=VARCHAR` / `jdbcType=BIGINT`。回归：创建 200 + 重名拒绝 ✓。

## 观察项（非阻塞）

1. 业务失败统一 HTTP 200 + body.code=500（GlobalExceptionHandler 无 @ResponseStatus）——逻辑正确，仅 REST 语义偏宽松。
2. 前端 `ReagentInbound` 接口缺 orderId/orderNo/supplierId 字段（后端支持，前端类型待补）。
3. 审批链 2 节点为条件触发（OVER_BUDGET 仅占用率≥预警线追加）。

## 数据卫生

- 存量未动；新增业务数据保留（供应商 62/63、预算 8、申请 3/4/5、订单 1、入库 3、盘点 1、库存 2 批、流水多条），作为真实业务数据供界面联调展示。
