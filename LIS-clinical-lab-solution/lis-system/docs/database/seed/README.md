# 本地 SQL 初始化脚本说明

本目录集中了**原本由 Spring 启动时自动执行**的数据库初始化/种子脚本，已全部改版为
**幂等 PostgreSQL 脚本**，由你在本地按需手动执行（`psql -f <file>` 或你在 psql 里执行），
应用重启时**不再自动灌数据**。

## 执行顺序

按编号顺序执行一次即可（每条都幂等，可安全重跑）：

| 序号 | 文件 | 内容 | 原启动器 |
|---|---|---|---|
| 01 | `01-schema-migration.sql` | 建表/加列的 DDL 迁移 + 基础字典种子（灭菌规则、采集方式、条码规则等） | `DatabaseSchemaInitializer` / `DatabaseMigrationRunner` / `SampleTableInitializer` |
| 02 | `02-bacteria.sql` | 细菌字典（约 180 菌种） | `BacteriaDataInitializer` |
| 03 | `03-antibiotics.sql` | 抗生素字典 + CLSI 折点 | `AntibioticDataInitializer` |
| 04 | `04-micro-culture.sql` | 培养基字典 + 培养方案 + 培养皿/取样表 | `MicroCultureDataInitializer` |
| 05 | `05-micro-test-items.sql` | 微生物检验项目 | `MicroTestItemInitializer` |
| 06 | `06-micro-report-test-data.sql` | 微生物报告示例数据（革兰染色/血培养/药敏） | `MicroReportTestDataInitializer` |
| 07 | `07-qc-data.sql` | 质控批号与质控记录示例 | `QCDataInitializer` |
| 08 | `08-expert-rules.sql` | 内置专家/耐药规则种子（MRSA/CRE/VRE/CRAB/最后防线耐药） | 原硬编码 `MicroExpertRuleServiceImpl` |

## 注意事项

- 脚本均为 PostgreSQL（BIGSERIAL / ON CONFLICT）。
- `02`、`03` 等字典脚本总体上只在对应表为空时插入（配合 `ON CONFLICT DO NOTHING`），不会覆盖你已有的业务数据。
- `06`、`07` 为**演示/测试数据**，非生产必需，按需执行；带 `平常` 保护（仅当为空时插入）。
- 若你已运行过旧版启动器并已有数据，脚本会自动跳过已存在项。