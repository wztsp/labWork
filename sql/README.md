# SQL 脚本目录

存放数据库相关的 SQL 脚本文件。

## 分类

### 数据生成脚本
- `gen_test_samples.sql` — 生成 200 条标本测试数据（待采集/已采集/已签收/待审核各 50 条），含患者、就诊、申请单、标本等全链路数据
- `gen_reagent_data.sql` — 生成试剂管理基础数据（分类/供应商/仓库/字典/预算）及每个流程 30 条的业务数据
- `construct_test_data.sql` — 构造综合测试数据
- `insert-tubes.sql` — 插入试管配置数据
- `insert_report.sql` — 插入报告数据

### 数据修正脚本
- `fix_garbled.sql` — 修正中文乱码数据（GBK 双重编码导致的乱码）
- `add_comments.sql` — 添加数据库字段注释
- `add_comments_tube.sql` — 添加试管相关字段注释
- `fix_panel_names.sql` — 修正组合项目名称

### 数据检查脚本
- `check_garbled.sql` — 检查数据库中是否存在乱码记录
- `check_garbled2.sql` / `check_garbled3.sql` — 多轮乱码排查
- `check_reagent.sql` / `check_reagent2.sql` / `check_reagent3.sql` — 试剂数据完整性检查

### 其他
- `fill_template.sql` — 报告模板数据填充
- `signature_update.sql` — 签名数据更新

## 使用方法

```bash
# 使用 psql 执行（UTF-8 编码）
$env:PGCLIENTENCODING="UTF8"
psql -h localhost -U lis_user -d lis_db -f scripts/xxx.sql
```

## 命名规范

- `gen_*` — 数据生成脚本
- `fix_*` — 数据修复脚本
- `check_*` — 数据检查脚本
- `insert_*` — 数据插入脚本
- `add_*` — 结构补充脚本