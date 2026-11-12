# 试剂管理系统（独立版）部署说明

> **服务安装包**：已内置 JDK 17，无需联网、无需预装 Java。
> **数据库由用户自行安装**（PostgreSQL），本包只负责服务端程序、建表脚本与启停脚本。
> 包内自带前端页面，单端口（默认 8092）提供前后端。

---

## 一、包内容

```
lis-reagent-1.0.0/
├── VERSION                          # 程序版本号
├── app/lis-reagent.jar              # 可执行程序（内嵌前端页面）
├── config/application.yml           # 运行配置（数据库参数在这里改）
├── db/01-schema.sql                 # 全量建表脚本（基础表 + 增强表/字段，幂等可重复执行）
├── bin/install.sh                   # 一键安装（解压 JDK、导表、装服务、启动）
├── bin/start.sh                     # 一键启动（后台运行 + 健康检查）
├── bin/stop.sh                      # 停止
├── bin/update.sh                    # 一键升级（自动备份 + 失败回滚）
├── bin/uninstall.sh                 # 卸载（不删数据库）
├── systemd/lis-reagent.service      # systemd 服务模板
├── runtime/openjdk-17.0.2_linux-x64_bin.tar.gz   # 内置 JDK 17
└── README.md
```

包体积约 225 MB（JDK 178 MB + 程序 45 MB），可通过 U 盘 / 内网共享拷贝。

---

## 二、环境要求

| 项目 | 要求 |
| --- | --- |
| 操作系统 | Oracle Linux 8 / RHEL 8 / Rocky 8 / Alma 8（x86_64） |
| JDK | **无需预装**（使用包内 JDK 17） |
| 数据库 | **需自行安装** PostgreSQL（建议 12 及以上），并已创建库与用户 |
| 内存 | ≥ 2 GB（JVM 默认 `-Xms256m -Xmx1g`） |
| 端口 | 默认 8092（需放行） |

数据库侧预置（示例，按实际改）：

```sql
CREATE USER lis_reagent WITH PASSWORD 'lis_reagent_2026';
CREATE DATABASE lis_reagent OWNER lis_reagent ENCODING 'UTF8';
```

> 表结构不用手工建，`bin/install.sh` 会用配置里的账号自动执行 `db/01-schema.sql`。

---

## 三、部署（三步）

```bash
# 1. 解压到 /lis_reagent/lis-reagent-1.0.0
cd /lis_reagent
tar -xzf lis-reagent-1.0.0-service.tar.gz

# 2. 修改数据库参数（只改 spring.datasource 下的 url / username / password）
cd /lis_reagent/lis-reagent-1.0.0
vi config/application.yml

# 3. 一键安装并启动
sudo bash bin/install.sh --yes
```

安装完成会打印访问地址：`http://<服务器IP>:8092/`。

默认安装目录 `/opt/lis-reagent`；如需装到别处：

```bash
sudo bash bin/install.sh --dir /lis_reagent/lis-reagent --yes
```

---

## 四、配置说明（config/application.yml）

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `server.port` | 服务端口 | `8092` |
| `spring.datasource.url` | `jdbc:postgresql://主机:端口/库名?characterEncoding=utf8` | `127.0.0.1:5432/lis_reagent` |
| `spring.datasource.username` | 数据库用户 | `lis_reagent` |
| `spring.datasource.password` | 数据库密码 | `lis_reagent_2026` |

改完配置后重启服务生效：

```bash
sudo systemctl restart lis-reagent
# 或（未装 systemd 时）
sudo bash /opt/lis-reagent/bin/stop.sh ; sudo bash /opt/lis-reagent/bin/start.sh
```

---

## 五、一键启动 / 停止

```bash
sudo bash bin/start.sh          # 后台启动 + 健康检查（自动解压包内 JDK）
sudo bash bin/start.sh -f       # 前台运行，便于看日志（Ctrl+C 退出）
sudo bash bin/stop.sh           # 停止
```

装了 systemd 的机器仍可用：

```bash
sudo systemctl start|stop|restart|status lis-reagent
journalctl -u lis-reagent -n 100 --no-pager
```

---

## 六、install.sh 参数

| 参数 | 说明 |
| --- | --- |
| `--dir <路径>` | 安装目录，默认 `/opt/lis-reagent` |
| `--conf <路径>` | 指定配置文件（默认取包内 `config/application.yml`） |
| `--jdk-path <路径>` | 包内 JDK 缺失时，指定 JDK 压缩包或已解压目录 |
| `--skip-db-init` | 不导入表结构（表已存在时可用） |
| `--force-config` | 覆盖安装目录中已有的 `application.yml` |
| `--no-service` | 不安装 systemd，仅用 `bin/start.sh` 启动 |
| `--no-start` | 只安装不启动 |
| `--no-firewall` | 不自动放行 firewalld 端口 |
| `--yes` | 跳过交互确认 |

---

## 七、日常运维

| 操作 | 命令 |
| --- | --- |
| 看日志 | `tail -f /opt/lis-reagent/logs/lis-reagent.log` |
| 看控制台输出 | `tail -f /opt/lis-reagent/logs/console.log` |
| 升级 | `sudo bash /opt/lis-reagent/bin/update.sh --package /root/lis-reagent-1.0.0-service.tar.gz` |
| 卸载（不删库） | `sudo bash /opt/lis-reagent/bin/uninstall.sh` |

升级流程：停服 → 备份（jar / 配置 / `pg_dump`）→ 替换程序 → 幂等执行建表脚本 → 启动 → 健康检查，失败自动回滚。

---

## 八、故障排查

| 现象 | 处理 |
| --- | --- |
| 提示 `未找到可用 JDK` | 确认 `ls -lh runtime/` 有 `openjdk-17.0.2_linux-x64_bin.tar.gz`（约 178 MB）；或用 `--jdk-path` 指定 |
| 提示数据库连接失败 | 核对 `config/application.yml` 的 url/用户/密码；确认 `pg_hba.conf` 允许该主机登录；`psql -h 主机 -U 用户 -d 库 -c 'select 1'` 测试 |
| 启动后健康检查超时 | 看 `logs/console.log` 与 `logs/lis-reagent.log`，常见为端口被占、数据库未连通 |
| 端口被占用 | `ss -lntp \| grep 8092`，改 `server.port` 后重启 |
| 页面打不开 | 放行端口：`firewall-cmd --permanent --add-port=8092/tcp && firewall-cmd --reload` |
| 表结构没建 | 手工执行：`psql -h 主机 -U 用户 -d 库 -f db/01-schema.sql` |
| 报 `关系 "lis_sys_config" 不存在` 或 `optimal_stock 不存在` | 说明建表脚本没跑完（旧库导入中断）。重跑一次全量脚本即可补齐：`psql -h 主机 -p 5432 -U 用户 -d 库 -v ON_ERROR_STOP=0 -f /opt/lis-reagent/db/01-schema.sql`。脚本幂等，重复执行不会重复建表 |
