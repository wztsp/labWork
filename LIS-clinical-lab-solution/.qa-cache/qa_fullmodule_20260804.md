# LIS 试剂管理 · 全模块测试数据与验证最终报告（2026-08-04）

- 环境：新单体 fat jar `http://localhost:8095`（副本构建 D:\lis-system-build），前端 dev `http://localhost:3000`，真库 `lis_db`，Redis 6379
- 范围：reagent 模块 **16 Controller / 约 80 端点** 全量验证

## 验证矩阵（16 组）

| 组 | 模块 | 结果 | 说明 |
|---|---|---|---|
| B-1 | 库存 | ⚠️ 1 FAIL → 已修 | 手动入库/FIFO 出库（先到期先出）/按行出库/近效期/锁定过期/流水守恒全 PASS；low-stock 500（BUG-1）→ 修复后 200 |
| B-2 | 消耗 | ✅ | 登记、自动扣减（FIFO）、流水 type=6 |
| B-3 | 期初/开瓶 | ✅ | 开瓶登记、过期锁定 |
| B-4 | 扫描（移动端） | ✅ | 扫码入/出/查，条码退化 FIFO |
| B-5 | 请领 | ✅ | 普通免审/危险品强制审批/执行守卫/退库调拨/作废（OBS-1 未用双节点） |
| B-6 | 采购申请补充 | ⚠️ 1 FAIL → 已修 | 驳回/取消释放预算、解冻重判 PASS；FROZEN 取消卡死（BUG-4）→ 修复后 200 |
| B-7 | 采购订单补充 | ✅ | 取消释放预算、比价 |
| B-8 | 预算补充 | ⚠️ 1 FAIL → 已修 | 判定边界/匹配 PASS；MANUAL 二次调整 500（BUG-2）→ 修复后连调 3 次 200 |
| B-9 | 分类 | ✅ | 树/CRUD/删除守卫 |
| B-10 | 仓库 | ✅ | 树/CRUD/默认库房不可删/余量拒删 |
| B-11 | 供应商 | ✅ | 更新重算证照状态、删除守卫、selectable/expiring |
| B-12 | 盘点补充 | ✅ | 作废、状态守卫 |
| B-13 | 看板 | ✅ | overview/near-expiry/low-stock/opened-expiry |
| B-14 | 成本 | ✅ | summary/detail（totalcost=0 为测试数据缺价） |
| B-15 | 字典 | ⚠️ 1 FAIL → 已修 | 列表过滤/CRUD PASS；DELETE 无守卫（BUG-3）→ 修复后 7 表引用拒删 |
| B-16 | 入库拒收 | ✅ | reject 不增库存 |

主链路回归：4 个预算对账全部 `consistent=true`，无回归。

## 4 个 P1 Bug（修复 + 回归全部 PASS）

| Bug | 位置 | 根因 | 修复 | 回归 |
|---|---|---|---|---|
| BUG-1 | ReagentMapper.java:50-51 | `#{minStockThreshold} IS NULL` 参数类型推断失败 | 加 `jdbcType=NUMERIC` | low-stock 200 ✓ |
| BUG-2 | ReagentBudgetServiceImpl adjust | MANUAL 流水 source_id=budgetId 撞唯一约束 | AtomicLong 唯一序号作 source_id | 连调 3 次 200 ✓ |
| BUG-3 | ReagentController.delete | 直调 removeById 无守卫 | ReagentService.delete + 7 表引用守卫 | 被引用拒删+无引用可删 ✓ |
| BUG-4 | ReagentPurchaseApplyServiceImpl.cancel | EXCEED 未冻结仍 release 抛错 | isFrozenForApply 按 FREEZE 流水判断 | FROZEN 申请9 取消 200 ✓ |

## 造数清单（演示数据保留）
- 供应商 62-66（含有效/临期/过期证照）；预算 8-11（生化组/门诊部/危险品/化学）；字典 1-3 及危险品 38(重建40)
- 库存批次 12-20（近效期 QA-NEAR-EXP、正常 QA-NORM-01/02、危险品 QA-HAZ-01、扫码 QA-SCAN-01、冷藏、过期已锁 QA-EXPIRED-01）
- 仓库 0 默认/2 冷藏/3 常温/4 冰箱A；请领单 1-7、采购申请 3-10、订单 1-2、入库 3-4、盘点 1-2、开瓶 2、消耗 2
- 存量数据未动；DB 断言：FIFO 顺序正确、数量守恒逐行核对、预算 frozen/used 账实一致

## 遗留/观察
1. OBS-1：请领危险品仅单次审批（未用审批节点表双节点安全员），建议产品确认
2. OBS-2：`below-safety` 为 deprecated 伪造实体，前端统一用 `low-stock`（已修复）
3. OBS-3：仓库 DTO 注释"whCode 自动生成"与实现"必填"不符
4. OBS-4：成本 summary totalcost=0 为测试数据缺 purchase 价格
5. 存量：`outStockFifo` 事务缺口（同 bean 自调用）、采购订单取消预算释放简化、22 节供应商迁移真实存量环境待客户库复验
