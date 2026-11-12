"""T-02 一致性自检：实体 @TableName / 字段 是否与 DDL 一一对应。"""
import re
import os
import glob

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


def camel_to_snake(name):
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name).lower()


ENTITY_DIR = ("lis-system/lis-service/lis-service-reagent/src/main/java/"
              "com/lis/reagent/entity")

bad = 0
checked = 0
for path in sorted(glob.glob(os.path.join(ENTITY_DIR, "*.java"))):
    with open(path, encoding="utf-8", errors="ignore") as fh:
        src = fh.read()
    tm = re.search(r'@TableName\("([a-z_0-9]+)"\)', src)
    if not tm:
        print("  [NO-TABLE  ] %s 未声明 @TableName" % os.path.basename(path))
        bad += 1
        continue
    table = tm.group(1)
    cols = tables.get(table)
    if cols is None:
        print("  [MISS-TABLE] %-32s 表 %s 不存在于 DDL" % (os.path.basename(path), table))
        bad += 1
        continue

    # 逐字段判定：跳过 @TableField(exist = false) 标注的字段
    lines = src.split("\n")
    missing = []
    nfield = 0
    skip_next = False
    for line in lines:
        s = line.strip()
        if "exist = false" in s or "exist=false" in s:
            skip_next = True
            continue
        fm = re.match(r"^private\s+(?:static\s+final\s+)?[\w<>\[\], .]+\s+(\w+)\s*(?:=|;)", s)
        if not fm:
            continue
        if "static final" in s:
            continue
        if skip_next:
            skip_next = False
            continue
        field = fm.group(1)
        nfield += 1
        col = camel_to_snake(field)
        if col not in cols:
            missing.append("%s -> %s" % (field, col))
    checked += 1
    if missing:
        print("  [MISS-COL  ] %-32s (%s) 缺列: %s"
              % (os.path.basename(path), table, missing))
        bad += 1
    else:
        print("  [OK        ] %-32s (%s) %d 个字段全部匹配"
              % (os.path.basename(path), table, nfield))

print("")
print("已校验实体数: %d" % checked)
print("不一致项: %d" % bad)
