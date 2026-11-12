# LIS 临床检验系统 — 离线安装包 v1.0.5

面向 **Oracle Linux 8 / Rocky Linux 8 / CentOS 8（x86_64）** 的整机离线部署包。
后端、前端、JDK、数据库、缓存、消息队列全部内置，目标机 **无需联网、无需预装任何运行环境**。

## 一、包内组成

```
lis-full-1.0.5/
├── VERSION                      # 版本号
├── README.md                    # 本说明
├── app/lis-monolith.jar         # 后端 + 内嵌前端（单端口访问整套系统）
├── config/application.yml.tpl   # 配置模板（安装时按参数生成正式配置）
├── db/01-init.sql               # 全量建表 + 基础字典（114 张表，不含演示业务数据）
├── bin/install.sh               # 一键安装（含数据库初始化）
├── bin/start.sh · stop.sh       # 后台启停（不使用 systemd 时）
├── bin/update.sh                # 升级（只换 jar + 重启，不动数据库）
├── bin/uninstall.sh             # 卸载
├── systemd/lis-full.service     # systemd 服务模板
└── runtime/                     # 全部离线运行环境
    ├── openjdk-17.0.2_linux-x64_bin.tar.gz
    ├── pg-rpms/                 # PostgreSQL 16（含依赖）
    ├── redis-rpms/              # Redis 6.2 + jemalloc
    └── rabbitmq-rpms/           # Erlang + RabbitMQ + socat/logrotate
```

## 二、目标机要求

- 操作系统：Oracle Linux 8 / Rocky Linux 8 / CentOS 8（x86_64），root 或 sudo 权限。
- 磁盘：安装目录约 1.5 GB（JDK + 程序），数据库数据另计。
- 端口：`8080`（系统访问）、`9100`（仪器 TCP）、`15672`（RabbitMQ 管理台），
  以及仅本机使用的 `5432`、`6379`、`5672`。

## 三、安装

1. 把 `lis-full-1.0.5.tar.gz` 上传到目标机并解压：

   ```bash
   tar -xzf lis-full-1.0.5.tar.gz -C /opt
   cd /opt/lis-full-1.0.5
   ```

2. 一键安装（默认参数即可直接跑通）：

   ```bash
   sudo bash bin/install.sh --yes
   ```

   自定义端口 / 数据库密码：

   ```bash
   sudo bash bin/install.sh \
        --dir /opt/lis-full \
        --port 8080 \
        --db-password 'YourPass2026!' \
        --yes
   ```

3. 安装脚本会依次完成：解压 JDK → 离线安装 PostgreSQL/Redis/RabbitMQ（已装同名包则跳过）
   → 初始化数据库并导入表结构与字典 → 创建 RabbitMQ 用户与 vhost → 生成配置
   → 安装 `lis-full.service` 并启动 → 放行防火墙 → 健康检查。

4. 浏览器访问：`http://<服务器IP>:8080/`，默认账号 **admin / 123456**。

## 四、变更说明

### v1.0.5
- 修复目标机未安装 PostgreSQL 时可能出现的「未找到 psql，无法初始化数据库」误报：
  内置 PostgreSQL 是否已就绪改为以 `psql` 是否存在为准，不再依据 `rpm -q postgresql-server`；
  兼容 psql 位于 `/usr/pgsql-N/bin` 的安装方式。
- 内置 PostgreSQL 改为三级安装策略：① 主包 + 缺失依赖库 → ② 全量强制重装（修复精简镜像缺失文件）
  → ③ dnf/yum 解析依赖；过程写入 `<安装目录>/logs/pg-install.log`，失败时打印日志尾部并终止。
- 数据目录自动识别 `/var/lib/pgsql/data` 与 `/var/lib/pgsql/<版本>/data`。
- 只要需要初始化本地库（默认 `127.0.0.1`）而本机没有 psql，即使误带 `--skip-pg-install`，
  也会提示并自动改为安装内置 PostgreSQL；启动时打印包目录与有效参数，便于排查。
- 修复改写 `pg_hba.conf` 认证方式时 sed 分隔符冲突：`sed: -e expression #1, char 75: unknown option to 's'`。

### v1.0.1

- 程序更新为 2026-11-10 构建的单体包，包含微生物报告撤销（撤销最终报告时清空审核人/审核时间/发布时间）修复。
- 数据库脚本补齐 16 张业务表、23 个运行时字段与内置字典，已与生产库结构逐列对齐：
  - 新增表：`lis_sample_order_item`、`lis_barcode_rule`、`lis_instrument_specimen_map`、
    `lis_instrument_unmatched_result`、`lis_certificate_attachment`、`lis_micro_qc_record`、
    `lis_micro_alert`、`lis_medium_type`、`lis_culture_plan`、`lis_culture_plan_item`、
    `lis_sample_plate`、`lis_qc_occ_event`、`lis_qc_occ_step`、`lis_qc_occ_approval`、
    `lis_sys_config`、`lis_reagent_invoice`
  - 内置字典：4 条条码规则、20 种培养基、10 套培养方案（27 条明细）、3 条试剂系统参数
  - 补齐字段：仪器文件/数据库采集通道、试剂扩展（最佳库存/条码别名/审批/盘点差异/开封分包）、
    标本拒收与签收人、质控软删除、报告患者信息、微生物报告条码等
- 安装脚本的数据库校验阈值不变（public 下 ≥ 90 张表），实际建表 114 张。

## 五、常用命令

| 操作 | 命令 |
|------|------|
| 查看状态 | `systemctl status lis-full` |
| 查看日志 | `journalctl -u lis-full -n 100 --no-pager` |
| 重启 | `systemctl restart lis-full` |
| 停止 | `systemctl stop lis-full` |
| 不使用 systemd | `bash bin/start.sh` / `bash bin/stop.sh` |
| 数据库客户端 | `sudo -u postgres psql -d lis_db` |
| Redis | `redis-cli -n 3` |
| RabbitMQ 管理台 | `http://<服务器IP>:15672`（`lis` / 安装时设定的密码） |

## 六、配置文件

安装后配置位于 `<安装目录>/config/application.yml`，可改：

- `server.port`：系统端口
- `spring.datasource.*`：数据库地址 / 库名 / 用户 / 密码
- `spring.redis.*`：Redis 地址与端口（database 固定 3）
- `spring.rabbitmq.*`：RabbitMQ 地址 / 用户 / 密码 / vhost
- `report.pdf.storage-path`：报告 PDF 存储目录（默认 `./data/pdf`，相对安装目录）
- `tcp.server.port`：仪器 TCP 端口

改完执行 `systemctl restart lis-full` 生效。重新安装时加 `--force-config` 才会覆盖已有配置。

## 七、升级

只替换程序、不改数据库、不改配置：

```bash
sudo bash bin/update.sh --jar /path/to/lis-monolith.jar
# 或
sudo bash bin/update.sh --package /path/to/lis-full-1.0.5-update.tar.gz
# 或把新 jar 放到当前目录，直接执行
sudo bash bin/update.sh
```

流程：备份旧 jar 到 `<安装目录>/backup/` → 校验新包 → 替换 → 重启 → 健康检查。
健康检查失败会自动回滚到升级前的程序包。手动回滚：

```bash
sudo bash bin/update.sh --rollback
```

## 八、卸载

```bash
sudo bash bin/uninstall.sh          # 停止服务、移除 systemd，保留程序与数据
sudo bash bin/uninstall.sh --purge  # 额外删除安装目录（数据库数据不删）
```

## 九、常见问题

- **安装时提示找不到 JDK**：确认 `runtime/openjdk-17.0.2_linux-x64_bin.tar.gz` 存在且未在传输中损坏
  （`ls -lh runtime/` 应约 178MB）；也可用 `--jdk-path` 指定已有 JDK。
- **数据库表数量不足**：查看 `<安装目录>/logs/db-init.log`，或手工补执行
  `sudo -u postgres psql -d lis_db -f db/01-init.sql`（脚本幂等，可重复执行）。
- **页面打不开**：确认 `systemctl status lis-full`、`ss -lntp | grep 8080`；首次启动需 30–60 秒。
- **RabbitMQ 起不来**：`journalctl -u rabbitmq-server -n 100 --no-pager`；Erlang 与 RabbitMQ
  均已内置，若目标机已装其它版本 Erlang 请先卸载冲突版本。
- **仪器连不上**：确认 9100 端口放行、仪器 IP 可达；`firewall-cmd --list-ports`。
- **时间/时区**：建议 `timedatectl set-timezone Asia/Shanghai`，避免报告时间偏移。
