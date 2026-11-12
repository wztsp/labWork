# LIS 临床检验信息系统 - 工作目录结构

本文档说明 `labWork` 目录下各文件夹的用途与存放规则。

---

## 目录总览

```
labWork/
├── LIS-clinical-lab-solution/   # 主项目源码（LIS 系统前后端）
├── LIS-prototype/               # UI 原型设计稿（HTML 静态预览）
├── ALLcontrll/                  # 服务控制台（Node.js，服务启停管理）
├── infra/                       # 基础设施（Nacos 配置中心等）
├── scripts/                     # 脚本工具集
│   ├── build/                   #   编译构建脚本
│   ├── deploy/                  #   部署运维脚本
│   ├── test/                    #   功能测试脚本
│   └── utils/                   #   辅助工具（Java/Python 小工具）
├── sql/                         # 数据库 SQL 脚本
├── data/                        # 测试数据与配置文件
├── assets/                      # 图片素材（签名、图标等）
├── reports/                     # 测试报告与交付文档
├── tests/                       # 测试脚本集合
│   ├── dbverify/                #   数据库验证脚本（JS/Java）
│   ├── qa-tmp/                  #   QA 临时测试数据
│   └── tmp-verify/              #   临时验证代码
├── logs/                        # 运行日志
└── work/                        # Tomcat 运行时工作目录
```

---

## 各目录详细说明

### 1. `LIS-clinical-lab-solution/` — 主项目源码

LIS 临床检验信息系统的完整源码仓库，包含：

| 子目录 | 说明 |
|--------|------|
| `lis-frontend/` | Vue3 + TypeScript 前端（Vite 构建） |
| `lis-system/` | Spring Boot 后端多模块单体（Maven 构建） |
| `docs/` | 设计文档、测试报告、系统使用指南 |
| `deploy/` | Docker/Shell 部署脚本与配置 |
| `build/` | 构建检查与验证脚本 |
| `logs/` | 后端各服务运行日志 |
| `qa-payloads/` | QA 测试请求 Payload |
| `scripts/` | 数据库与辅助脚本 |
| `instrument-simulator/` | 仪器模拟器（Node.js） |

### 2. `LIS-prototype/` — UI 原型

基于 HTML/CSS 的界面原型设计稿，用于产品评审与 UI 预览：
- `pages/` — 各页面原型（报告查询、标本采集、标本签收等）
- `partials/` — 公共组件片段
- `*.css` — 原型样式表

### 3. `ALLcontrll/` — 服务控制台

Node.js + React 构建的本地服务管理工具，用于：
- 监控后端各微服务运行状态
- 查看日志、健康检查
- 管理标本转运规则
- 包含 `server/`（Koa 后端）和 `web/`（React 前端）

### 4. `infra/` — 基础设施

存放 Nacos 配置中心运行数据及配置：
- `nacos/` — Nacos 1.4.0 单机部署的数据文件、日志、配置

### 5. `scripts/` — 脚本工具集

#### `scripts/build/` — 编译构建
| 文件 | 说明 |
|------|------|
| `build-lis.ps1` | 一键编译前后端项目 |
| `compile_*.ps1` | 各模块单独编译（仪器/质控/报告/系统） |
| `find-mvn.ps1` / `find-pg.ps1` | 查找 Maven/PostgreSQL 安装路径 |
| `list-jars.ps1` | 列出后端 Jar 包 |
| `fix-tpl.ps1` | 修复报告模板 |

#### `scripts/deploy/` — 部署运维
| 文件 | 说明 |
|------|------|
| `start-all-services.ps1` | 启动所有后端服务 |
| `restart-all-services.ps1` | 重启所有服务 |
| `rebuild-and-start.ps1` | 重新构建并启动 |
| `setup_signatures.ps1` | 初始化电子签名数据 |

#### `scripts/test/` — 功能测试
| 文件 | 说明 |
|------|------|
| `test-all-api.ps1` | 全量 API 接口测试 |
| `test-login.ps1` | 登录接口测试 |
| `test-frontend.ps1` | 前端构建与冒烟测试 |
| `check-data.ps1` / `check-env.ps1` | 环境与数据检查 |

#### `scripts/utils/` — 辅助工具
| 文件 | 说明 |
|------|------|
| `RunSql.java` | 通用 SQL 执行器（JDBC） |
| `StatsDiag.java` | 统计数据诊断工具 |
| `VerifyResultCols.java` | 检验结果列验证 |
| `inner-sample.jar` | 标本处理内部库 |

### 6. `sql/` — 数据库脚本

| 类型 | 文件示例 | 说明 |
|------|---------|------|
| 数据生成 | `gen_test_samples.sql` | 生成标本测试数据（4种状态各50条） |
| 数据生成 | `gen_reagent_data.sql` | 生成试剂管理基础与流程数据 |
| 数据生成 | `construct_test_data.sql` | 构造综合测试数据 |
| 数据修正 | `fix_garbled.sql` | 修正中文乱码数据 |
| 数据修正 | `add_comments.sql` | 添加数据库字段注释 |
| 数据检查 | `check_garbled.sql` | 检查乱码记录 |
| 数据检查 | `check_reagent.sql` | 检查试剂数据完整性 |
| 其他 | `fill_template.sql` | 报告模板数据填充 |

### 7. `data/` — 数据文件

| 文件 | 说明 |
|------|------|
| `eqa-test-body.json` | EQA 测试请求体 |
| `new-samples.json` | 新增标本测试数据 |
| `_cols_dump.txt` | 数据库列信息导出 |
| `_report.json` | 报告数据快照 |

### 8. `assets/` — 图片素材

| 文件 | 说明 |
|------|------|
| `sig_li.jpg` | 李医生电子签名 |
| `sig_zhang.jpg` | 张医生电子签名 |

### 9. `reports/` — 报告文档

| 文件 | 说明 |
|------|------|
| `测试报告_LIS全功能业务逻辑测试.md` | 全功能业务逻辑测试报告 |

### 10. `tests/` — 测试脚本集合

#### `tests/dbverify/` — 数据库验证
~225 个文件，覆盖各模块的数据库验证：
- `tests/` — 各业务模块 API 验证脚本（JS）
- `*.java` — Java 版数据验证工具
- `img/` — 血涂片等测试图片
- `tat_seed/` — 质控/TAT 数据种子

#### `tests/qa-tmp/` — QA 临时数据
QA 过程中的临时测试数据与日志。

#### `tests/tmp-verify/` — 临时验证
标本图片插入等一次性验证代码。

### 11. `logs/` — 运行日志

存放服务运行期间的日志文件：
- 后端各服务 err/out 日志
- 仪器管理、微生物、试剂等模块日志
- 网关、鉴权、统计等中间件日志

### 12. `work/` — Tomcat 工作目录

Tomcat 运行时产生的临时文件与缓存。

---

## 快速入口

| 操作 | 路径/命令 |
|------|----------|
| 编译前后端 | `scripts/build/build-lis.ps1` |
| 启动所有服务 | `scripts/deploy/start-all-services.ps1` |
| 重启所有服务 | `scripts/deploy/restart-all-services.ps1` |
| 全量 API 测试 | `scripts/test/test-all-api.ps1` |
| 生成测试数据 | `sql/gen_test_samples.sql` + `sql/gen_reagent_data.sql` |
| 修正乱码数据 | `sql/fix_garbled.sql` |
| 查看项目文档 | `LIS-clinical-lab-solution/docs/` |

---

## 命名规范

- 构建脚本：`build-*` / `compile-*`
- 部署脚本：`start-*` / `restart-*` / `rebuild-*`
- 测试脚本：`test-*` / `check-*`
- SQL 脚本：`gen_*`（生成）/ `fix_*`（修复）/ `check_*`（检查）/ `insert_*`（插入）
- 日志文件：`{service-name}.{out|err}.log`

---

## 维护说明

- 新增 SQL 脚本放入 `sql/` 目录
- 新增测试脚本放入 `scripts/test/` 或 `tests/` 对应子目录
- 临时文件使用完毕请清理，避免根目录堆积
- `.class` 编译产物勿提交到版本库