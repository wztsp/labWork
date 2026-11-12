# 脚本工具集

本目录存放 LIS 系统的运维脚本，按用途分为四类。

## 子目录

### `build/` — 编译构建（11 个脚本）

| 脚本 | 说明 |
|------|------|
| `build-lis.ps1` | **一键全量编译**：前端 npm build + 后端 Maven package |
| `compile_inst.ps1` | 仅编译仪器管理模块 |
| `compile_int.ps1` | 仅编译集成模块 |
| `compile_qc.ps1` | 仅编译质控模块 |
| `compile_qc_app.ps1` | 仅编译质控 App 模块 |
| `compile_rep.ps1` | 仅编译报告模块 |
| `compile_sys.ps1` | 仅编译系统管理模块 |
| `fix-tpl.ps1` | 修复报告模板数据 |
| `list-jars.ps1` | 列出后端 target 下的 Jar 包 |
| `find-mvn.ps1` | 查找 Maven 安装路径 |
| `find-pg.ps1` | 查找 PostgreSQL 安装路径 |

### `deploy/` — 部署运维（8 个脚本）

| 脚本 | 说明 |
|------|------|
| `start-all-services.ps1` | 启动所有后端微服务（网关+各业务模块） |
| `restart-all-services.ps1` | 一键重启全部服务 |
| `rebuild-and-start.ps1` | 重新编译并启动 |
| `start-system.ps1` | 启动系统管理模块 |
| `start-gateway.ps1` | 单独启动网关服务 |
| `start-nacos.ps1` | 启动 Nacos 配置中心 |
| `start_services.ps1` | 基础服务启动（Nacos+PostgreSQL+Redis+RabbitMQ） |
| `setup_signatures.ps1` | 初始化医生电子签名数据 |

### `test/` — 功能测试（12 个脚本）

| 脚本 | 说明 |
|------|------|
| `test-all-api.ps1` | **全量 API 测试**：覆盖所有业务接口 |
| `test-all-apis.ps1` | 全量 API 测试（备用版本） |
| `test-core-features.ps1` | 核心功能测试 |
| `test-direct.ps1` | 直接 API 测试（不经过网关） |
| `test-frontend.ps1` | 前端构建与冒烟测试 |
| `test-login.ps1` | 登录接口专项测试 |
| `test-stats.ps1` | 统计模块测试 |
| `test-system-api.ps1` | 系统管理 API 测试 |
| `check-data.ps1` | 数据一致性检查 |
| `check-env.ps1` | 环境依赖检查（Java/Node/DB） |
| `show-log.ps1` | 查看服务实时日志 |
| `show-failed-logs.ps1` | 查看失败请求日志 |

### `utils/` — 辅助工具（6 个文件）

| 文件 | 说明 |
|------|------|
| `RunSql.java` | **通用 SQL 执行器**：通过 JDBC 执行任意 SQL |
| `StatsDiag.java` | 统计数据诊断工具 |
| `VerifyResultCols.java` | 检验结果列数据验证 |
| `query` | 快速查询脚本 |
| `inner-sample.jar` | 标本处理内部库 |
| `inner-sample2.jar` | 标本处理内部库（v2） |

## 使用方法

```powershell
# 在 labWork 根目录执行
cd d:\traeitem\labWork

# 编译全项目
.\scripts\build\build-lis.ps1

# 启动服务
.\scripts\deploy\start-all-services.ps1

# 运行测试
.\scripts\test\test-all-api.ps1
```

## 前置条件

- PowerShell 5+
- Java 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 13+（运行前需启动）