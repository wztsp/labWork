"""T-05 一致性自检：Mapper 自定义 SQL 引用的表/列是否都存在于 DDL。"""
import re
import os

DDL_FILES = [
    "lis-system/docs/database/add-reagent-enhance.sql",
    "lis-system/docs/database/reagent-core-tables.sql",
    "lis-system/docs/database/add-reagent-stock-check.sql",
    "lis-system/docs/database/add-reagent-lot-trace.sql",
    "lis-system/docs/database/init-schema.sql",
]

ddl = ""
for f in DDL_FILES:
    if os.path.exists(f):
        with open(f, encoding="utf-8", errors="ignore") as fh:
            ddl += fh.read() + "\n"

RESERVED = {"PRIMARY", "UNIQUE", "CONSTRAINT", "KEY", "INDEX", "FOREIGN", "CHECK"}
tables = {}

for m in re.finditer(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_0-9]+)\s*\((.*?)\n\s*\)\s*;",
        ddl, re.S | re.I):
    name = m.group(1).lower()
    cols = set()
    for line in m.group(2).split("\n"):
        cm = re.match(r"^([a-z_][a-z_0-9]*)\s+", line.strip(), re.I)
        if cm and cm.group(1).upper() not in RESERVED:
            cols.add(cm.group(1).lower())
    tables.setdefault(name, set()).update(cols)

for m in re.finditer(
        r"ALTER\s+TABLE\s+([a-z_0-9]+)\s+ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_0-9]+)",
        ddl, re.I):
    tables.setdefault(m.group(1).lower(), set()).add(m.group(2).lower())

REFS = {
    "ReagentBudgetMapper": ("lis_reagent_budget",
        ["id", "budget_year", "dept_id", "category_id", "total_amount", "frozen_amount", "used_amount"]),
    "ReagentBudgetRecordMapper": ("lis_reagent_budget_record",
        ["budget_id", "change_type", "amount"]),
    "ReagentPurchaseApplyMapper": ("lis_reagent_purchase_apply",
        ["id", "budget_id", "total_amount", "status"]),
    "ReagentPurchaseApprovalMapper": ("lis_reagent_purchase_approval",
        ["apply_id", "round_no", "node_level"]),
    "ReagentApprovalNodeMapper": ("lis_reagent_approval_node",
        ["biz_type", "status", "node_level"]),
    "ReagentPurchaseOrderMapper": ("lis_reagent_purchase_order",
        ["id", "apply_id", "status"]),
    "ReagentRequisitionMapper-a": ("lis_reagent_requisition",
        ["id", "source_req_id", "req_type", "status"]),
    "ReagentRequisitionMapper-b": ("lis_reagent_requisition_item",
        ["req_id", "quantity"]),
    "ReagentWarehouseMapper-a": ("lis_reagent_warehouse",
        ["id", "path", "sort_order", "parent_id", "status"]),
    "ReagentWarehouseMapper-b": ("lis_reagent_stock",
        ["warehouse_id", "quantity"]),
    "SupplierMapper-a": ("lis_supplier",
        ["id", "status", "license_expiry", "medical_cert_expiry", "qual_status", "score"]),
    "SupplierMapper-b": ("lis_reagent_purchase_order_item",
        ["id", "order_id", "reagent_id", "unit_price", "quantity", "arrived_qty"]),
    "SupplierMapper-c": ("lis_reagent_purchase_order",
        ["id", "supplier_id", "supplier_name", "status", "order_date"]),
    "ReagentInboundMapper": ("lis_reagent_inbound", ["id", "order_id"]),
    "ReagentInboundItemMapper": ("lis_reagent_inbound_item", ["id", "inbound_id"]),
    "ReagentStockCheckItemMapper": ("lis_reagent_stock_check_item",
        ["check_id", "actual_qty", "diff_qty"]),
    "ReagentStockRecordMapper": ("lis_reagent_stock_record",
        ["related_type", "related_id", "quantity", "dept_id", "record_type"]),
}

bad = 0
for key in sorted(REFS):
    table, cols = REFS[key]
    have = tables.get(table)
    if have is None:
        print("  [MISS-TABLE] %-30s 表 %s 未在 DDL 中找到" % (key, table))
        bad += 1
        continue
    missing = [c for c in cols if c not in have]
    if missing:
        print("  [MISS-COL  ] %-30s %s 缺列 %s" % (key, table, missing))
        bad += 1
    else:
        print("  [OK        ] %-30s %s 全部 %d 列匹配" % (key, table, len(cols)))

print("")
print("DDL 中解析到的表数: %d" % len(tables))
print("不一致项: %d" % bad)
