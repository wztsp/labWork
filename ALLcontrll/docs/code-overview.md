# LIS 运维后台管理系统（lis-admin）代码概况

> 本文档基于仓库真实源码（后端 13 个 TS 源文件 + 前端 19 个 TS/TSX 源文件）梳理，非 README 复述。

## 一、项目定位

为现有 LIS 临床实验室系统提供**独立部署、零侵入**的运维后台：监控 15 个服务（14 后端 + 1 前端）存活状态，并支持上传 / 解析 / 检索异常日志。不读取 LIS 磁盘、不依赖其代码。

| 模块 | 技术栈 | 端口 |
|------|--------|------|
| 后端 `server/` | Express + TypeScript + better-sqlite3 + multer | **8900** |
| 前端 `web/` | Vite + React + MUI + Tailwind + Axios + ECharts + Zustand | **8901**（`/api` 代理到 8900） |

- 数据库：SQLite 文件 `server/data/lis-admin.db`，首次启动自动建表 + 写入 15 条种子服务。
- 生产模式：根目录 `npm run build` 后由 server 同源托管 `web/dist`（端口 8900）。

## 二、目录与职责

```
server/src/
  index.ts            启动入口：加载 env → 建库 → 种子 → listen → 优雅关闭
  app.ts              Express 装配：中间件 / 路由 / 静态托管 / 全局错误处理
  config/defaults.ts  全部可调常量 + 15 条默认服务注册表
  db/
    connection.ts     单例连接，WAL 模式 + 外键开启
    init.ts           DDL 幂等建表 + 索引
    seed.ts           仅首次写入 15 条种子服务
  routes/
    services.ts       服务 CRUD + 健康检测（单检 / 全检）
    logs.ts           日志上传、解析、检索
    dashboard.ts      仪表盘数据汇总
  services/
    healthChecker.ts  TCP / HTTP 探测（checkService / checkAll）
    logParser.ts      Spring Boot 日志三级正则解析
  utils/response.ts   统一响应 {code,message,data} + HttpError + asyncHandler
  types/index.ts      后端类型契约（snake_case 对齐 DB）

web/src/
  main.tsx / App.tsx   入口 + 路由（/ 总览，/logs 日志，/dashboard 仪表盘）
  api/client.ts        Axios 封装 + unwrap（code=0 成功）
  store/appStore.ts     Zustand 全局状态（服务 / 日志 / 仪表盘 / 自动刷新）
  types/index.ts       前端类型契约（与后端"镜像"，但已漂移，见第六节）
  theme/index.ts       MUI 主题（声明暗色，实际 mode:'light'）
  components/
    Layout.tsx             顶栏 + 侧边导航 + 自动刷新开关
    OverviewCards.tsx      服务健康概览卡片
    StatusBadge.tsx        状态徽章
    LogUploadZone.tsx      日志拖拽上传区
    ServiceConfigDialog.tsx 服务增删改弹窗
    ServiceDetailDrawer.tsx 服务检测历史抽屉
    LogDetailDrawer.tsx    日志条目详情抽屉
    charts/HealthPieChart.tsx  健康分布饼图
    charts/ErrorBarChart.tsx   各服务 ERROR 柱状图
  styles/globals.css    全局样式
  pages/
    OverviewPage.tsx    服务列表 + 立即检测
    LogsPage.tsx        日志上传 + 检索 + 详情
    DashboardPage.tsx   仪表盘图表聚合
```

## 三、数据模型（SQLite）

| 表 | 说明 | 关键字段 |
|----|------|---------|
| `services` | 被监控服务注册表 | `name` 唯一，`enabled` 0/1，`health_url`，`host/port` |
| `service_check_history` | 检测历史 | FK 级联删除，每服务裁剪保留 500 条 |
| `log_files` | 上传日志文件 | 含 `raw_content` 全文本 |
| `log_entries` | 解析后的结构化日志条目 | `level/pid/thread/logger/stack_trace…` |

- 索引覆盖 `type/enabled/service_id/level/timestamp`；查询全部参数化（无注入风险）。
- 外键 + WAL 开启；批量写入走事务；历史裁剪防膨胀。

## 四、关键流程

1. **启动**：`initDatabase()` 幂等建表 → `seedDatabase()` 仅首次写 15 条 → `createApp()` 注册路由 → `listen(8900)`。
2. **健康检测**：`checkService` 优先 `health_url`（HTTP 2xx），否则 TCP 端口探测（3s 超时）；`checkAll` 用 `Promise.all` 并发，结果入库。
3. **日志上传**：multer 内存存储（≤50MB）→ `logParser.parse()` 三级正则匹配 Spring Boot 格式 → 非主日志行按堆栈 / 缩进聚合 → 入库；`hs_err_pid*.log` 整文件作为单条崩溃条目。
4. **仪表盘**：聚合服务状态 + 日志统计 + 各服务 ERROR 计数，输出饼图 / 柱状图数据。

## 五、工程亮点

- 优雅关闭（SIGINT / SIGTERM + 5s 兜底）、WAL + 外键、事务批量写、历史裁剪防膨胀。
- 日志解析器容错较好：多 pattern 兼容、堆栈聚合、JVM 崩溃特判、`MAX_STACK_LINES=500` 防撑爆内存。
- 前端状态集中（Zustand）、自动刷新开关、离线行高亮置顶。

## 六、已知问题（待修复，见交付计划）

1. **🔴 手动检测状态不同步（功能性 Bug）**：`OverviewPage.checkAll` 用 `res.results.map(r => [r.id, r])` 建 Map，再用 `map.get(s.id)` 回写。但后端 `CheckResult` 只有 `service_id`、没有 `id`，前端类型却声明了 `id`。结果 `r.id` 为 `undefined` → Map 查不到 → 点"立即检测"后**列表徽章 / 耗时 / 最后检测时间不刷新**（仅顶部 summary 数字变化）。
2. **🟡 前后端类型契约漂移**：`DashboardSummary.log_summary` 前端多 `recent_files`（后端无）；`LogUploadResponse.parse_summary` 字段名对不上（前端 `entry_count/level_counts/parse_errors` vs 后端 `total_lines/parsed_entries/warn_count`）。当前因前端未消费这些字段暂未报错，但属于"谎言式"类型。
3. **🟡 无自动化测试**：server / web 均无 test 文件、无 test 脚本，QA 环节目前空白。
4. **🟡 Tailwind 基本冗余**：`web` 装了 Tailwind 但组件几乎只用 MUI `sx`；`theme/index.ts` 注释写"暗色主题"实为 `mode:'light'`。

## 七、如何运行

```bash
cd server && npm install && npm run dev      # 后端 8900
cd web   && npm install && npm run dev        # 前端 8901
# 或根目录：
npm install && npm run dev                    # 同时启动前后端
# 生产：
npm run build                                 # server 托管 web/dist（同源 8900）
```

---
*生成方式：主理人齐活林基于全量源码阅读汇编，供团队后续迭代参考。*
