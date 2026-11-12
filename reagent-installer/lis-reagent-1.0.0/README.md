# 试剂管理系统（独立版）v1.2.0 离线部署说明

> **完全离线包**：已内置 JDK 17 与 PostgreSQL 16 全部安装文件，目标机**无需联网、无需预装 Java / 数据库**。
> 独立部署，不依赖 LIS 主系统与其他中间件；内嵌前端页面，单端口（默认 8092）提供前后端。
> 支持**一键安装**与**一键升级**（含自动备份、健康检查、失败回滚）。

---

## 一、包内容

```
lis-reagent-1.0.0/
├── VERSION                          # 版本号（升级时显示 旧版本 → 新版本）
├── app/lis-reagent.jar              # 可执行程序（内嵌前端页面）
├── config/application.yml.tpl       # 配置模板（安装时自动渲染）
├── db/01-schema.sql                 # 数据库脚本（27 张表，幂等，可重复执行）
├── bin/install.sh                   # 一键安装（含离线装 JDK / PostgreSQL）
├── bin/update.sh                    # 一键升级（自动备份 + 回滚）
├── bin/start.sh / stop.sh           # 手工启停（无 systemd 时）
├── bin/uninstall.sh                 # 卸载（不删数据库）
├── systemd/lis-reagent.service      # systemd 服务模板
├── runtime/
│   ├── openjdk-17.0.2_linux-x64_bin.tar.gz   # 内置 JDK 17（免安装，解压即用）
│   └── pg-rpms/*.rpm                        # PostgreSQL 16.15 及运行库（离线 RPM）
└── README.md                        # 本文档
```

包体积约 270 MB（JDK 179 MB + RPM 53 MB + 程序 45 MB）。建议通过 U 盘 / 内网共享拷贝到目标机。

---

## 二、环境要求

| 项目 | 要求 |
| --- | --- |
| 操作系统 | Oracle Linux 8 / RHEL 8 / Rocky 8 / Alma 8（**x86_64**） |
| JDK | **无需预装**（使用包内 JDK 17） |
| 数据库 | **无需预装**（使用包内 PostgreSQL 16.15） |
| 内存 | ≥ 2 GB（JVM 默认 `-Xms256m -Xmx1g`） |
| 磁盘 | ≥ 3 GB（程序 + JDK + 数据库数据 + 升级备份，备份保留 5 份） |
| 端口 | 默认 8092（数据库 5432，仅本机） |
| 网络 | **完全离线**，无需外网 / 无需 yum 源 |

> 目标机若已安装 PostgreSQL，可加 `--skip-pg-install` 复用现有数据库。

---

## 三、一键部署

```bash
# 1. 上传安装包（目标机已存在 /lis_reagent 时，直接覆盖解压到该目录）
scp lis-reagent-1.0.0.tar.gz root@<服务器IP>:/lis_reagent/

# 2. 解压并一键安装（离线装 JDK、PostgreSQL、建库建用户、导表、装服务、放行防火墙）
cd /lis_reagent
tar -xzf lis-reagent-1.0.0.tar.gz
cd /lis_reagent/lis-reagent-1.0.0
sudo bash bin/install.sh --yes
```

安装完成后浏览器访问 `http://<服务器IP>:8092/`。

默认参数：安装目录 `/opt/lis-reagent`、数据库 `lis_reagent`、用户 `lis_reagent`、
密码 `lis_reagent_2026`、服务端口 `8092`、数据库端口 `5432`。

### 自定义参数

```bash
sudo bash bin/install.sh \
  --dir /opt/lis-reagent \
  --port 8092 \
  --db-port 5432 \
  --db-name lis_reagent \
  --db-user lis_reagent \
  --db-password 'YourPass2026!' \
  --yes
```

### 交互式安装

```bash
sudo bash bin/install.sh
```

按提示依次确认安装目录、端口、数据库参数。

### 安装脚本做了什么

1. 复制程序 / 脚本 / SQL 到安装目录
2. 解压包内 JDK 17 到 `<安装目录>/jdk`
3. 离线 `rpm` 安装包内 PostgreSQL 16.15（3 个主包；若目标机缺少运行库，自动从随包 RPM 中按需补齐，**已装同名包一律跳过**，不做降级覆盖）
4. `initdb` 初始化 `/var/lib/pgsql/data`，必要时把 `pg_hba.conf` 本机认证改为 `md5`，设置监听端口
5. 创建业务用户与数据库，以 `SET ROLE` 方式导入 27 张表（表归属业务用户）
6. 生成 `config/application.yml`（600）与 `config/db.env`（600）
7. 安装 `lis-reagent.service`（systemd，开机自启），ExecStart 使用包内 JDK
8. firewalld 运行中时自动放行服务端口
9. 健康检查：`GET /api/reagent/dashboard/overview` 返回体含 `"success":true` 视为成功

> 重复执行会**保留已有配置文件**（避免覆盖真实密码），需要按新参数重建请加 `--force-config`。
> 数据库脚本幂等，重复执行不会清空数据。

### 常用可选参数

| 参数 | 说明 |
| --- | --- |
| `--jdk-path <路径>` | 指定 JDK 压缩包（`.tar.gz`）或已解压 JDK 目录，包内 JDK 缺失时使用 |
| `--skip-pg-install` | 不安装内置 PostgreSQL（复用目标机已有库） |
| `--skip-db-init` | 不建库、不导表（仅装程序） |
| `--force-config` | 强制用本次参数重写配置 |
| `--no-firewall` | 不放行 firewalld |
| `--no-service` | 不装 systemd 服务，改为后台脚本启动 |
| `--yes` | 全自动，跳过所有交互确认 |

---

## 四、一键升级

```bash
# 方式 A：服务器上直接指定新包（推荐）
scp lis-reagent-1.3.0.tar.gz root@<服务器IP>:/root/
sudo bash /opt/lis-reagent/bin/update.sh --package /root/lis-reagent-1.3.0.tar.gz

# 方式 B：在新包目录内执行
tar -xzf lis-reagent-1.3.0.tar.gz && cd lis-reagent-1.3.0
sudo bash bin/update.sh
```

### 升级流程

1. 识别安装目录（读取 systemd 单元，默认 `/opt/lis-reagent`）
2. 读取 `config/db.env` 中的数据库连接（旧版本无该文件时解析 `application.yml`）
3. **停服**
4. 备份到 `backup/<时间戳>/`：旧 jar、application.yml、VERSION、数据库 `db.dump`（`pg_dump -Fc`）
5. 替换 jar / SQL / 运维脚本 / VERSION（**保留配置文件**）；旧安装缺少内置 JDK 时自动从新包补齐
6. 幂等执行 `db/01-schema.sql` 完成表结构升级
7. 启动服务并健康检查（最多约 90 秒）
8. **健康检查失败自动回滚**：恢复旧 jar → 重启旧版本 → 输出备份目录与日志命令
9. 清理旧备份，仅保留最近 5 份

可用参数：

```bash
--package <tar.gz|目录>   # 指定新版本包
--dir <路径>              # 指定安装目录
--no-db                   # 跳过数据库脚本升级
--no-backup-db            # 跳过数据库备份
--yes                     # 跳过确认
```

### 手工回滚

```bash
BK=/opt/lis-reagent/backup/<时间戳>
sudo systemctl stop lis-reagent
sudo cp $BK/lis-reagent.jar /opt/lis-reagent/app/
sudo cp $BK/application.yml /opt/lis-reagent/config/
sudo systemctl start lis-reagent

# 如需恢复数据库（谨慎，会覆盖当前数据）
# sudo -u postgres pg_restore -d lis_reagent --clean -Fc $BK/db.dump
```

---

## 五、验证

```bash
# 服务状态与端口
systemctl status lis-reagent --no-pager
ss -lntp | grep 8092

# 内置 JDK / 数据库
/opt/lis-reagent/jdk/bin/java -version
/usr/bin/psql --version
sudo -u postgres psql -c '\l'

# 页面（200）
curl -I http://127.0.0.1:8092/

# 接口（返回体含 "success":true）
curl -s http://127.0.0.1:8092/api/reagent/dashboard/overview

# 前端路由刷新（应 200，不应 404）
curl -I http://127.0.0.1:8092/reagent/inbound

# 科室下拉（采购申请 / 预算页面使用）
curl -s http://127.0.0.1:8092/api/reagent/dept/options
```

---

## 六、防火墙与 SELinux

```bash
# firewalld（安装脚本已自动放行；手工放行命令如下）
sudo firewall-cmd --permanent --add-port=8092/tcp
sudo firewall-cmd --reload
```

SELinux（Oracle Linux 8 默认 enforcing）：本服务为 Java 进程直接监听端口，不经过 httpd/nginx，
默认策略下无需额外配置。若把 `/opt/lis-reagent` 放在自定义 SELinux 上下文的目录，可执行：

```bash
sudo semanage fcontext -a -t usr_t "/opt/lis-reagent(/.*)?"
sudo restorecon -Rv /opt/lis-reagent
```

---

## 七、常用运维命令

| 操作 | 命令 |
| --- | --- |
| 启动 | `sudo systemctl start lis-reagent` |
| 停止 | `sudo systemctl stop lis-reagent` |
| 重启 | `sudo systemctl restart lis-reagent` |
| 状态 | `sudo systemctl status lis-reagent --no-pager` |
| 实时日志 | `tail -f /opt/lis-reagent/logs/lis-reagent.log` |
| 系统日志 | `journalctl -u lis-reagent -n 200 --no-pager` |
| 数据库状态 | `sudo systemctl status postgresql --no-pager` |
| 数据库备份 | `sudo -u postgres pg_dump -Fc lis_reagent -f /root/lis_reagent_$(date +%F).dump` |
| 升级 | `sudo bash /opt/lis-reagent/bin/update.sh --package <新包>` |
| 查看备份 | `ls -lt /opt/lis-reagent/backup` |
| 卸装（保留数据） | `sudo bash /opt/lis-reagent/bin/uninstall.sh` |
| 改端口 | 改 `config/application.yml` 的 `server.port` → `systemctl restart lis-reagent`（同时放行新端口） |

---

## 八、功能范围（26 个页面）

| 分组 | 页面 |
| --- | --- |
| 概览 | 库存看板 |
| 主数据 | 试剂字典、分类管理、供应商管理、供应商评价、仓库管理、预算管理 |
| 采购与出入库 | 采购申请、采购订单、入库验收、试剂库存、消耗登记、请领业务单、出入库记录、库存盘点 |
| 库存分析 | 开封管理、批次库存查询 |
| 分析与处置 | 试剂报表、成本核算、环保处置台账、发票台账、移动扫码作业（扫码盘点 / 扫码领用 / 库存查询） |
| 系统维护 | 参数配置 |

### v1.1 增强要点

- **采购**：多张采购申请按供应商合并生成订单；订单审批/驳回、审核/取消审核；采购建议量自动计算；试剂支持最佳库存、采购周期、条码别名、质控品与分包份数。
- **入库**：规则扫码（前缀+序号批量生成条码）、逐个扫码、扫码选择（条码别名识别）三种方式。
- **出库**：新增「退货出库（退供应商）」与「作废出库」。
- **使用**：开封支持二次开封、闭封、耗尽；质控品分包自动生成 n+1 条记录；化学危险品走申请→审批→开封。
- **库存**：高库存预警（可开关）、批次库存查询（含零库存批次）、盘点差异原因/审核/CSV 导出。
- **统计**：台账/总账、供应商应付、领用小组汇总、发票汇总、科室入库、供应商入库、小组出库、实际消耗量，均支持 CSV 导出与打印。
- **系统参数**：`reagent.purchase_order_need_approval`、`reagent.scrap_need_approval`、`reagent.high_stock_warning_enabled`。
- **修复**：采购申请/预算页面不再依赖主系统 `/api/system/departments`，改用试剂自有接口 `/api/reagent/dept/options`（从预算/申请/业务单的科室快照抽取，独立部署可用）。

### v1.2 变更要点

- **离线化**：内置 JDK 17 与 PostgreSQL 16.15 全部安装文件，目标机无外网也可一键部署。
- systemd 单元与启停脚本改为使用内置 JDK，不再依赖系统 Java。

---

## 九、数据库说明

初始化脚本共建 **27 张表**：

- **25 张试剂业务表**：试剂字典、批次库存、出入库流水、入库单及明细、消耗单、开瓶记录、盘点单及明细、分类、供应商、仓库、预算及流水、审批节点、采购申请/订单及明细、请领业务单及明细、报废处置、系统参数（`lis_sys_config`）、供应商发票（`lis_reagent_invoice`）。
- **2 张外部桩表**：`lis_instrument`（成本核算按仪器汇总）、`lis_result`（批次追溯查检测结果），无对应业务时保持空表即可。

脚本全部使用 `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`，幂等，可重复执行。
安装与升级均会自动执行，不会清空数据。

数据目录：`/var/lib/pgsql/data`（属主 `postgres`）。

---

## 十、故障排查（离线环境）

| 现象 | 排查 |
| --- | --- |
| 安装报 `未找到可用 JDK` | 安装脚本会从 `runtime/`、包目录、上级目录、`/usr/lib/jvm`、PATH 依次查找；仍找不到时用 `--jdk-path` 指定压缩包或 JDK 目录，或 `dnf install -y java-17-openjdk-headless`。先确认 `ls -lh <包目录>/runtime/` 有 `openjdk-17.0.2_linux-x64_bin.tar.gz`（约 178 MB） |
| 安装脚本打印的版本是 `v1.0.0` | 说明用的是旧程序（`app/lis-reagent.jar` 或 `VERSION` 未更新），请用离线包完整覆盖一次（含 `app/`、`db/`、`VERSION`、`runtime/`） |
| 安装报 `rpm` 依赖失败 | 输出会列出缺失依赖；执行 `sudo rpm -Uvh /root/lis-reagent-1.0.0/runtime/pg-rpms/*.rpm` 后重跑安装脚本 |
| 安装报 `postgresql-server already installed` | 系统已有 PostgreSQL，属正常跳过；如需用内置版本先 `sudo rpm -e` 旧版本 |
| `psql: 无法连接` / 启动报 `Connection refused` | `systemctl status postgresql --no-pager`；确认 `/var/lib/pgsql/data/postgresql.conf` 的 `port` 与配置一致 |
| `password authentication failed` | 密码与 `config/application.yml`、`config/db.env` 不一致；或 `pg_hba.conf` 本机仍为 `ident`，改为 `md5` 后 `systemctl restart postgresql` |
| 服务起不来、日志无输出 | `/opt/lis-reagent/jdk/bin/java -version` 是否可执行；`journalctl -u lis-reagent -n 120 --no-pager` |
| 升级后页面 404 | 确认访问根路径 `http://ip:8092/`；确认启动的是 `/opt/lis-reagent/app/lis-reagent.jar` |
| 页面空白、assets 404 | 通过根路径访问，不要带子路径；确认 jar 已包含前端静态资源 |
| 中文乱码 | 数据库编码需 UTF8；启动参数已含 `-Dfile.encoding=UTF-8` |
| 端口被占用 | `ss -lntp \| grep 8092`，改配置端口或释放占用进程 |
| 启动后接口 500 | 查看 `logs/lis-reagent.log` 首个异常堆栈；多为表未初始化，重跑 `db/01-schema.sql` |
| 升级失败已回滚 | 查看终端输出的备份目录；`journalctl -u lis-reagent -n 120 --no-pager` |
