# LIS 临床检验系统 — Docker 一键部署

在**另一台电脑**上快速部署整套 LIS 系统的方案。基于 Docker Compose 编排，一条命令拉起全部组件。

## 一、目标电脑需要准备什么

只需安装 **Docker Desktop（Windows）** 并启动，无需单独安装 JDK / Node / PostgreSQL / Redis / RabbitMQ。

- Docker Desktop：https://www.docker.com/products/docker-desktop/
- 安装后确认 `docker --version` 与 `docker compose version` 可用。

## 二、目录结构

```
deploy/
├── docker-compose.yml       # 一键编排: postgres + redis + rabbitmq + backend + frontend
├── .env                     # 部署配置(数据库密码/账号/端口等, 换机时按需修改)
├── db/
│   └── init.sql             # 数据库全量初始化(结构+字典, 不含 demo 业务数据)
├── frontend/
│   └── nginx.conf           # 前端静态服务 + /api 反向代理到后端
├── 启动系统.bat              # 一键构建并启动
├── 停止系统.bat              # 停止(保留数据)
├── 构建前端.bat              # 重新构建前端 dist
└── README.md                # 本说明
```

后端 Dockerfile 位于 `lis-system/lis-monolith/Dockerfile`（构建 context 为 `lis-system`）。

## 三、部署步骤

1. **把整个项目目录拷贝到目标电脑**（含 `lis-system`、`lis-frontend`、`deploy`）。
2. **（可选但推荐）重新构建前端**：双击 `deploy\构建前端.bat`，生成最新 `lis-frontend\dist`。
   - 若 `lis-frontend\dist` 已存在且不需要更新，可跳过此步。
3. **双击 `deploy\启动系统.bat`**，或手动执行：

   ```bash
   cd deploy
   docker compose up -d --build
   ```

首次启动后端需在容器内 Maven 构建（下载依赖），耗时较长，属正常现象。

## 四、访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:3000 |
| 后端 HTTP | http://localhost:8080 |
| API 文档 | http://localhost:8080/doc.html |
| RabbitMQ 管理台 | http://localhost:15672（`lis` / `lis_dev_2026`） |
| 仪器 TCP 通信 | tcp://目标机IP:9100 |

默认登录账号：`admin` / `123456`

### HIS 对接（HIS 为 Oracle 时）

系统内置 HIS 适配器框架（`com.lis.integration.adapter`），支持两种方式把申请发给检验系统，二选一：

**方式一：数据库直连（推荐，无需 HIS 开发接口）**
- 由 LIS 通过 JDBC 直连 HIS 的 Oracle 库，定时增量读取检验申请。
- 在 `lis-service-integration/src/main/resources/application.yml` 中配置：
  - 将 `integration.his.default-vendor` 改为 `ORACLE_DB`
  - 配置 `vendors.ORACLE_DB.jdbc-url / username / password / order-sql / item-sql`
- 需向 HIS 申请一个**只读账号**，并能访问检验申请表（如 `HIS_ORDER` / `HIS_ORDER_ITEM`）。
- 增量去重：按时间字段（示例 `APPLY_TIME`）过滤，`OrderSyncJob` 每 5 分钟拉取一次。

**方式二：REST / WebService 接口（HIS 已提供接口时）**
- 复用内置 `UniversalRestAdapter`，无需改代码。
- 将 `default-vendor` 改为 `UNIVERSAL_REST`，配置 `vendors.UNIVERSAL_REST.base-url / auth-token`。
- HIS 需提供 `GET {base-url}/orders?start=&end=` 返回申请列表，`POST {base-url}/reports` 接收结果等。

> 两种方式最终都调用 `POST /api/sample/orders/receive` 落库，一个申请含多项目时自动拆分为多个独立申请。

## 五、数据库说明

- 数据库：PostgreSQL 15，库名 `lis_db`，用户 `lis_user`，密码 `lis_dev_2026`。
- 首次启动时自动执行 `deploy/db/init.sql`，完成**全量建表 + 基础字典数据**初始化（角色、管理员、检验项目、参考范围、试管、标本类型、抗生素折点、质控规则等）。
- **不含 demo/测试业务数据**（患者、标本、报告等），面向正式部署。
- 后端启动时 `DatabaseSchemaInitializer` 与 `schema.sql` 会再做幂等增量迁移，重复执行安全。
- 数据保存在 Docker 数据卷 `pg_data` 中，`停止系统.bat` 不会删除数据；如需清空数据库，执行 `docker compose down -v`。

## 六、常用命令

| 操作 | 命令 |
|------|------|
| 查看状态 | `docker compose ps` |
| 查看后端日志 | `docker compose logs -f backend` |
| 查看数据库日志 | `docker compose logs -f postgres` |
| 重启后端 | `docker compose restart backend` |
| 进入数据库 | `docker compose exec postgres psql -U lis_user -d lis_db` |
| 停止所有 | `docker compose down` |
| 停止并清空数据 | `docker compose down -v` |

## 七、修改配置（可选）

所有部署参数（数据库账号/密码/库名、Redis/RabbitMQ 连接、各服务端口等）统一放在 **`deploy/.env`** 文件中，改这一处即可，无需改 Dockerfile 或代码。

```bash
# 示例：修改数据库密码
POSTGRES_PASSWORD=你的新密码
```

修改后重新 `docker compose up -d` 生效。

> 注意：`POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD` 仅在数据库**首次初始化**时生效。若数据卷已存在，改密码不会自动重建；如需更换，先 `docker compose down -v` 清空数据后再启动。

## 八、常见问题

- **后端启动失败**：查看 `docker compose logs backend`。常见原因是 postgres/redis/rabbitmq 未就绪（等待超时），或数据库初始脚本执行报错。
- **前端空白 / 接口 404**：确认已执行 `构建前端.bat` 生成 `dist`，且 nginx 已正确代理 `/api` 到后端 8080。
- **端口占用**：若 3000/8080/5432 等端口被占用，修改 `docker-compose.yml` 的 `ports` 映射。