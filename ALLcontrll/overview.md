# 服务控制功能 — 交付概览

> 交付日期：2026-08-01 ｜ 团队：software-service-control ｜ SOP：标准流程（PM→架构→工程师→QA）

## TL;DR

为 lis-admin 运维后台新增「启用/暂停监控」+「启动/停止服务进程」双功能，17 个文件变更（3 新建 + 14 修改），130 测试全绿，无需新增依赖。

## 交付概览

| 项 | 状态 |
|---|---|
| 交付状态 | ✅ 完成 |
| 测试通过率 | 130/130 (100%) |
| 已知问题 | 0 |
| QA 轮次 | 2 轮（Round 1 发现 1 Bug → 修复 → Round 2 回归通过） |
| 新增依赖 | 无 |

## 功能说明

### 功能一：启用/暂停监控
- 服务总览页表格新增「监控」列，每行一个 Switch 开关
- 开启 = 纳入健康检测；关闭 = 排除检测，状态显示"已暂停"
- 概览卡片新增"已暂停 N"计数
- 复用现有 PUT /api/services/:id 接口

### 功能二：启动/停止服务进程
- 服务配置弹窗新增「启动命令」+「工作目录」输入框
- 通过 admin 后台直接 spawn/taskkill 微服务进程
- 进程状态实时展示：🟢运行中 / ⚫已停止 / 🔴已崩溃 / —未配置
- 操作前确认弹窗，防止误操作
- admin 重启后自动检测 PID 存活，修正僵尸状态

## 文件清单

### 新建文件（3）
| 文件 | 职责 |
|---|---|
| `server/src/services/processManager.ts` | ProcessManager 单例：spawn/kill/exit监听/reconcileOnStartup |
| `web/src/components/ProcessStatusBadge.tsx` | 进程状态徽标组件 |
| `web/src/components/ConfirmDialog.tsx` | 通用操作确认弹窗 |

### 修改文件（14）
| 文件 | 变更 |
|---|---|
| `server/src/db/init.ts` | DB 迁移：addColumnIfNotExists + service_processes 建表 + backfill |
| `server/src/types/index.ts` | 新增 ProcessStatus/ProcessInfo/StartResult/StopResult 类型 |
| `server/src/config/defaults.ts` | 新增 PROCESS_OUTPUT_BUFFER_LINES 常量 |
| `server/src/routes/services.ts` | SQL JOIN + summarize 改造 + POST /:id/start + POST /:id/stop |
| `server/src/index.ts` | 启动时调用 processManager.reconcileOnStartup() |
| `server/src/db/seed.ts` | INSERT SQL 新增 start_command/work_dir 列 |
| `server/src/routes/dashboard.ts` | summarize 调用适配 + 健康饼图增加"已暂停"分片 |
| `web/src/types/index.ts` | 类型镜像对齐 |
| `web/src/api/client.ts` | 新增 startService/stopService 方法 |
| `web/src/components/StatusBadge.tsx` | enabled=false 显示"已暂停" |
| `web/src/components/OverviewCards.tsx` | 5 列 Grid（新增"已暂停"卡片） |
| `web/src/components/ServiceConfigDialog.tsx` | 新增启动命令 + 工作目录输入框 |
| `web/src/pages/OverviewPage.tsx` | 9 列表格 + Switch + 启停按钮 + ConfirmDialog |
| `web/src/utils/mergeCheckResults.test.ts` | 测试工厂函数补充新字段 |

### QA 新增测试文件（2）
| 文件 | 测试数 |
|---|---|
| `server/tests/processManager.test.ts` | 25 |
| `server/tests/summarize.test.ts` | 10 |

## 设计文档
- PRD：`docs/prd-service-control.md`
- 架构设计：`docs/arch-service-control.md`
