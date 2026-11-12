# LIS 运维后台管理系统（lis_admin）

为现有 LIS 临床实验室信息系统提供**独立部署**的运维后台：集中查看 14 个后端微服务 + 1 个前端服务的运行状态，并支持上传/解析/检索异常日志。与 LIS 源码**完全分离、零依赖、可独立部署**。

## 技术栈

- **后端** `server/`：Node.js + Express + TypeScript + better-sqlite3 + multer，端口 `8900`
- **前端** `web/`：Vite + React + TypeScript + MUI + Tailwind CSS + Axios + ECharts + Zustand，开发端口 `8901`
- **数据库**：SQLite（文件 `server/data/lis-admin.db`），首次启动自动建表 + 写入 15 条默认服务

## 目录结构

```
ALLcontrll/
├── server/          # 后端（Express + SQLite）
├── web/             # 前端（React + MUI）
├── docs/            # PRD / 架构设计
└── README.md
```

## 快速开始

```bash
# 1. 安装依赖（后端 + 前端）
cd server && npm install
cd ../web && npm install

# 2. 启动后端（端口 8900，自动建库 + 种子数据）
cd server && npm run dev        # 开发 (tsx watch)
# 或 npm run build && npm start  # 生产

# 3. 启动前端（端口 8901，/api 代理到 8900）
cd web && npm run dev

# 4. 浏览器访问 http://localhost:8901
```

## 生产部署

```bash
# 构建前端
cd web && npm run build         # 产出 web/dist

# 启动后端，Express 自动托管 web/dist（同源，无 CORS）
cd server && npm run build && npm start
# 访问 http://localhost:8900
```

## 功能

- **服务状态总览**：列表展示全部被监控服务（在线/离线/未知徽标、最后检测时间、耗时）；离线行置顶高亮；支持「立即检测」与自动刷新（10/30/60s）。
- **服务配置**：新增 / 编辑被监控服务（名称、类型、主机、端口、可选 HTTP 健康探测 URL），持久化到 SQLite。
- **服务详情**：查看服务配置与最近检测历史。
- **异常日志**：上传 `.log/.txt/.out/.err` 文件（≤50MB），按 Spring Boot 标准格式解析为结构化条目；支持按级别/关键字/是否含堆栈筛选、分页、详情抽屉查看完整堆栈并复制。
- **仪表盘**：在线率 / 离线数 / 日志数 / ERROR 数汇总卡片 + 健康率饼图 + 各服务异常计数柱状图（ECharts）。

## 健康检查说明

- 默认 **TCP 端口探测**（超时 3s 判离线）。
- 若服务配置了 `health_url`（HTTP GET），则优先使用该 URL 判定（2xx 为在线）。
- 15 个服务（14 后端 + 1 前端）默认注册，首次启动写入，可在界面增删改。

## 与 LIS 的关系

本系统**只读**被监控服务的存活状态（TCP/HTTP 探测）与**接收用户上传的日志文件**，不读取 LIS 磁盘、不依赖其代码、不修改其任何文件。被监控服务当前未启用 Actuator，故采用端口探测方式。
