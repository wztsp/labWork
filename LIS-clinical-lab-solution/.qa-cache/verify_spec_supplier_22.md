# 供应商迁移逻辑验收规格（第 22 / 22.1 / 22.2 节）

> 依据：QA 6 项回归报告（② ③ ④a/b/c ⑥a 全 PASS，新发现 P2-01）+ 主理人 P2-01 修复方案。
> 本规格为 QA（严过关）复验脚本的输入。工程师已完成 P2-01（事务包裹 + 守卫前移）与 P4-01（BTRIM）。
> 真实库 lis_db，psql：`PGPASSWORD=lis_dev_2026 "D:/Program Files/PostgreSQL/13/bin/psql.exe" -h localhost -p 5432 -U lis_user -d lis_db -v ON_ERROR_STOP=1`

## 验收前提
1. 工程师已在真库连跑两遍全脚本（幂等 0 error），确认 P2-01 事务包裹与 P4-01 生效。
2. 本验收用**种子数据**触发迁移逻辑（真实库常态 0 供应商，需手动造场景）。
3. 每个场景用**独立事务**：跑完断言后 `ROLLBACK`（或显式 DELETE 种子），禁止污染真库。
4. ④d 场景必须验证「整体回滚」——这是 P2-01 的唯一新增证据点。

---

## 场景 ④a：1 人工 + 1 草稿，同规范名（合法归并，必须成功）
种子：
```sql
-- 人工记录（source_type=1）
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9001,'SUP_0001','Roche Diagnostics',1,1,1,'manual');
-- 自动迁移草稿（source_type=2），含前导空格+尾部 Tab 变体
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9002,'SUP_MIG_0001',' roche diagnostics'||chr(9),0,2,1,'draft');
-- 存量试剂字符串（供 22.4/22.5 回填）
INSERT INTO lis_reagent(id, reagent_name, reagent_code, supplier, supplier_id)
 VALUES (90001,'Test Reagent A','REA_90001',' roche diagnostics ',NULL);
```
执行：仅抽取脚本第 22 节（22.0~22.6）语句运行（或整脚本，因表已存在幂等）。
断言（期望）：
- `SELECT COUNT(*) FROM lis_supplier WHERE id IN (9001,9002)` → **1**（草稿 9002 被 22.1.4 删除）。
- 存活者为 9001（人工优先）。
- `SELECT supplier_id FROM lis_reagent WHERE id=90001` → **9001**（22.5 回填成功）。
- 无 RAISE EXCEPTION。
- （P4-01 验证）若 22.4 生成了新草稿（本场景不生成，因 reagent.supplier 已能匹配 9001），其显示名应被 BTRIM（无前导/尾部空格）。

## 场景 ④d：2 人工，同规范名（P2-01 回滚复验，必须整体回滚）
种子：
```sql
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9003,'SUP_0003','Roche',1,1,1,'manual A');
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9004,'SUP_0004',' roche ',1,1,1,'manual B');
```
执行：跑**整脚本**（P2-01 已 `BEGIN;` 包裹，`-v ON_ERROR_STOP=1`）。
断言（期望）：
- psql 退出码 **非 0**，报 `存在人工录入的重名供应商...`（22.2 前移守卫触发）。
- **关键（P2-01 证据）**：事务整体回滚 ——
  `SELECT COUNT(*) FROM lis_supplier WHERE id IN (9003,9004)` → **2**（两条人工记录均完好，22.1.1~22.1.4 破坏性语句一条未落库）。
  `SELECT COUNT(*) FROM lis_reagent WHERE supplier_id IS NOT NULL` 不出现因本场景产生的异常外键指向。
- 对照（修复前）：旧脚本无事务，9003/9004 中可能一条被删/外键被改，留下半迁移态。本场景必须证明此问题已消除。

## 场景 ②：规范化合并（fn_supplier_name_norm 折叠）
- 全角空格 U+3000 / 不断行空格 U+00A0 / Tab / 大小写 变体应被折叠为同一规范名。
- 用 ④a 的 ` roche diagnostics<Tab>` 与 `Roche Diagnostics` 验证已归并 → 证明折叠生效。
- 可选单测：`SELECT fn_supplier_name_norm('罗　氏　诊断'), fn_supplier_name_norm('ROCHE'), fn_supplier_name_norm('roche'||chr(160));` 三者应相等。

## 场景 ③：表达式唯一索引拦截
- ④a 归并后，手动插入同规范名新记录应被 `uk_lis_supplier_name_norm` 拦截：
```sql
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9099,'SUP_9099','ROCHE DIAGNOSTICS',0,2,1,'should fail');
```
- 期望：报 `duplicate key value violates unique constraint "uk_lis_supplier_name_norm"`，插入被拒（静默错绑根治证据）。

## 场景 ⑥a：编号续号（防撞键）
种子：先植入一个已存在的迁移编号：
```sql
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9010,'SUP_MIG_0007','Some Existing Migrated',0,2,1,'preexist');
INSERT INTO lis_reagent(id, reagent_name, reagent_code, supplier, supplier_id)
 VALUES (90002,'Test Reagent B','REA_90002','Brand New Supplier XYZ',NULL);
```
执行 22.4 后：
- `SELECT supplier_code FROM lis_supplier WHERE supplier_name='Brand New Supplier XYZ'` → 应为 **SUP_MIG_0008**（基于库内 MAX 0007 续号，而非从 0001 重排撞键）。

---

## 验收结论模板
| 场景 | 期望 | 实测 | 结论 |
|------|------|------|------|
| ④a 合法归并 | 人工存活/草稿删/回填成功/0 异常 | | PASS/FAIL |
| ④d 整体回滚(P2-01) | 退出码≠0 + 9003/9004 均完好 | | PASS/FAIL |
| ② 规范合并 | 全角/nbsp/Tab/大小写折叠同归 | | PASS/FAIL |
| ③ 唯一索引拦截 | 重复插入被拒 | | PASS/FAIL |
| ⑥a 编号续号 | 新编号=SUP_MIG_0008 | | PASS/FAIL |

最终判定：5/5 PASS 则第 22/22.1/22.2 节供应商迁移逻辑**通过**（P2-01 修复生效）。
遗留提醒：本地库 0 条供应商，本逻辑尚未在「真实带存量供应商」环境跑过，上线前需在客户库补一轮真实数据复验。
