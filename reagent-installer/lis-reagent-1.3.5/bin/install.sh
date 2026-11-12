#!/usr/bin/env bash
# =============================================================================
# 试剂管理系统（独立版）- Oracle Linux 8 离线一键安装
#
# 安装包已内置 JDK 17 与 PostgreSQL，全程无需联网、无需预装 Java/数据库。
#
# 用法：
#   sudo bash bin/install.sh                      # 交互式安装
#   sudo bash bin/install.sh --yes                # 全自动一键部署（默认参数）
#   sudo bash bin/install.sh --port 8092 \
#        --db-password 'YourPass2026!' --yes
#
# 参数：
#   --dir <路径>         安装目录，默认 /opt/lis-reagent
#   --port <端口>        服务端口，默认 8092
#   --jdk-path <路径>    JDK 压缩包或已解压 JDK 目录（包内没有 JDK 时用）
#   --db-port <端口>     数据库端口，默认 5432
#   --db-name <库名>     数据库名，默认 lis_reagent
#   --db-user <用户>     数据库用户，默认 lis_reagent
#   --db-password <密码> 数据库密码，默认 lis_reagent_2026
#   --skip-pg-install    不安装内置 PostgreSQL（复用目标机已安装的数据库）
#   --skip-db-init       不创建库/用户、不导入表结构
#   --force-config       重装时强制覆盖已有配置文件
#   --no-firewall        不自动放行 firewalld 端口
#   --no-service         不安装 systemd 服务
#   --yes                跳过交互确认
# =============================================================================
set -euo pipefail

APP_NAME="lis-reagent"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_VERSION="$(cat "$PKG_ROOT/VERSION" 2>/dev/null || echo "1.3.1")"

INSTALL_DIR="/opt/lis-reagent"
PORT="8092"
DB_HOST="127.0.0.1"
DB_PORT="5432"
DB_NAME="lis_reagent"
DB_USER="lis_reagent"
DB_PASSWORD="lis_reagent_2026"
PG_INSTALL="yes"
DB_INIT="yes"
FORCE_CONFIG="no"
USE_FIREWALL="yes"
USE_SERVICE="yes"
ASSUME_YES="no"
JDK_PATH_ARG=""

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)              INSTALL_DIR="$2"; shift 2 ;;
    --port)             PORT="$2"; shift 2 ;;
    --jdk-path)         JDK_PATH_ARG="$2"; shift 2 ;;
    --db-host)          DB_HOST="$2"; shift 2 ;;
    --db-port)          DB_PORT="$2"; shift 2 ;;
    --db-name)          DB_NAME="$2"; shift 2 ;;
    --db-user)          DB_USER="$2"; shift 2 ;;
    --db-password)      DB_PASSWORD="$2"; shift 2 ;;
    --skip-pg-install)  PG_INSTALL="no"; shift ;;
    --skip-db-init)     DB_INIT="no"; shift ;;
    --force-config)     FORCE_CONFIG="yes"; shift ;;
    --no-firewall)      USE_FIREWALL="no"; shift ;;
    --no-service)       USE_SERVICE="no"; shift ;;
    --yes|-y)           ASSUME_YES="yes"; shift ;;
    -h|--help)          usage ;;
    *) err "未知参数: $1"; usage ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行：sudo bash bin/install.sh"; exit 1; }

echo "======================================================"
echo "  试剂管理系统 离线版 v${APP_VERSION} - 安装向导"
echo "======================================================"
info "包目录：${PKG_ROOT}"
info "参数：安装目录=${INSTALL_DIR} 端口=${PORT} 库=${DB_NAME}@${DB_HOST}:${DB_PORT} 安装PG=${PG_INSTALL} 初始化库=${DB_INIT}"

if [[ "$ASSUME_YES" != "yes" ]]; then
  read -rp "安装目录 [${INSTALL_DIR}]: " _in; INSTALL_DIR="${_in:-$INSTALL_DIR}"
  read -rp "服务端口 [${PORT}]: " _in; PORT="${_in:-$PORT}"
  read -rp "数据库端口 [${DB_PORT}]: " _in; DB_PORT="${_in:-$DB_PORT}"
  read -rp "数据库名 [${DB_NAME}]: " _in; DB_NAME="${_in:-$DB_NAME}"
  read -rp "数据库用户 [${DB_USER}]: " _in; DB_USER="${_in:-$DB_USER}"
  read -rsp "数据库密码 [${DB_PASSWORD}]: " _in; echo; DB_PASSWORD="${_in:-$DB_PASSWORD}"
  if [[ "$PG_INSTALL" == "yes" ]]; then
    read -rp "是否安装内置 PostgreSQL 数据库？[Y/n]: " _in
    [[ -z "$_in" || "${_in,,}" == "y" ]] || PG_INSTALL="no"
  fi
fi

# ---------- 1. 创建目录 ----------
info "安装目录：${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"/{app,config,db,bin,logs,backup,runtime}
cp -f "${PKG_ROOT}/app/lis-reagent.jar" "${INSTALL_DIR}/app/"
cp -f "${PKG_ROOT}/db/01-schema.sql"    "${INSTALL_DIR}/db/"
for f in start.sh stop.sh update.sh uninstall.sh; do
  [[ -f "${PKG_ROOT}/bin/${f}" ]] && cp -f "${PKG_ROOT}/bin/${f}" "${INSTALL_DIR}/bin/"
done
cp -f "$PKG_ROOT/VERSION" "${INSTALL_DIR}/VERSION" 2>/dev/null || true
chmod +x "${INSTALL_DIR}"/bin/*.sh

# ---------- 2. 定位 JDK：包内 → --jdk-path → 本机已装 17+ ----------
JDK_DIR="${INSTALL_DIR}/jdk"

java_major() {
  local v
  v="$("$1" -version 2>&1 | head -1 || true)"
  echo "$v" | sed -nE 's/.*version "1?([0-9]+).*/\1/p'
}

if [[ -x "${JDK_DIR}/bin/java" ]]; then
  info "已存在内置 JDK，跳过解压"
else
  # 2.1 --jdk-path 指向已解压的 JDK 目录
  if [[ -n "$JDK_PATH_ARG" && -d "$JDK_PATH_ARG" && -x "${JDK_PATH_ARG}/bin/java" ]]; then
    info "使用 --jdk-path 指定的 JDK：${JDK_PATH_ARG}"
    rm -rf "$JDK_DIR"
    ln -sfn "$JDK_PATH_ARG" "$JDK_DIR"
  fi

  # 2.2 包内 / --jdk-path 指定的 JDK 压缩包（含上级目录，防止拷层了目录）
  if [[ ! -x "${JDK_DIR}/bin/java" ]]; then
    JDK_PKG=""
    if [[ -n "$JDK_PATH_ARG" && -f "$JDK_PATH_ARG" && "$JDK_PATH_ARG" == *.tar.gz ]]; then
      JDK_PKG="$JDK_PATH_ARG"
    fi
    if [[ -z "$JDK_PKG" ]]; then
      for d in "${PKG_ROOT}/runtime" "${PKG_ROOT}" "${PKG_ROOT}/.."; do
        [[ -d "$d" ]] || continue
        JDK_PKG="$(find "$d" -maxdepth 2 -type f -name 'openjdk-*-linux-x64*.tar.gz' -print -quit 2>/dev/null || true)"
        if [[ -n "$JDK_PKG" ]]; then break; fi
      done
    fi
    if [[ -n "$JDK_PKG" ]]; then
      info "解压内置 JDK：$(basename "$JDK_PKG")"
      tar -xzf "$JDK_PKG" -C "${INSTALL_DIR}"
      JDK_EXTRACTED="$(find "${INSTALL_DIR}" -maxdepth 1 -mindepth 1 -type d -name 'jdk-[0-9]*' -print -quit)"
      if [[ -n "$JDK_EXTRACTED" ]]; then
        rm -rf "$JDK_DIR"
        mv "$JDK_EXTRACTED" "$JDK_DIR"
      fi
    fi
  fi

  # 2.3 本机已安装的 JDK 17+
  if [[ ! -x "${JDK_DIR}/bin/java" ]]; then
    SYS_JAVA="$(command -v java 2>/dev/null || true)"
    for c in /usr/lib/jvm/*/bin/java /usr/java/*/bin/java /usr/java/latest/bin/java "$SYS_JAVA"; do
      [[ -n "$c" && -x "$c" ]] || continue
      if [[ "$(java_major "$c")" -ge 17 ]] 2>/dev/null; then
        info "使用本机 JDK：$("$c" -version 2>&1 | head -1)"
        mkdir -p "${JDK_DIR}/bin"
        ln -sfn "$c" "${JDK_DIR}/bin/java"
        break
      fi
    done
  fi

  if [[ ! -x "${JDK_DIR}/bin/java" ]]; then
    err "未找到可用 JDK（包内无 JDK，本机也没有 Java 17+）"
    err "当前包目录：${PKG_ROOT}"
    err "1) 检查包内 runtime 目录：ls -lh ${PKG_ROOT}/runtime/"
    err "2) 把 openjdk-17.0.2_linux-x64_bin.tar.gz 放到 ${PKG_ROOT}/runtime/ 后重跑"
    err "3) 或指定 JDK 位置：sudo bash bin/install.sh --jdk-path /root/openjdk-17.0.2_linux-x64_bin.tar.gz"
    err "4) 或安装系统 JDK：sudo dnf install -y java-17-openjdk-headless"
    exit 1
  fi
fi
info "Java：$("${JDK_DIR}/bin/java" -version 2>&1 | head -1)"

# ---------- 3. 安装内置 PostgreSQL（离线 RPM） ----------
run_as_pg() {
  if [[ "$(id -un)" == "postgres" ]]; then "$@"
  elif command -v runuser >/dev/null 2>&1; then runuser -u postgres -- "$@"
  else sudo -u postgres -- "$@"; fi
}

# 定位 psql：优先 PATH，其次常见安装路径（PGDG 为 /usr/pgsql-N/bin）
find_psql() {
  local c
  if command -v psql >/dev/null 2>&1; then command -v psql; return 0; fi
  for c in /usr/bin/psql /usr/local/bin/psql /usr/pgsql-*/bin/psql /usr/lib/postgresql/*/bin/psql; do
    [[ -x "$c" ]] && { echo "$c"; return 0; }
  done
  return 1
}

# 服务端是否就绪：psql 可执行 + postgres 可执行或已有数据目录
pg_server_ready() {
  local p
  p="$(find_psql || true)"
  [[ -n "$p" ]] || return 1
  [[ -x "$(dirname "$p")/postgres" ]] && return 0
  command -v postgres >/dev/null 2>&1 && return 0
  [[ -f /var/lib/pgsql/data/PG_VERSION ]] && return 0
  compgen -G '/var/lib/pgsql/*/data/PG_VERSION' >/dev/null && return 0
  return 1
}

PGDIR="${PKG_ROOT}/runtime/pg-rpms"
PG_LOG="${INSTALL_DIR}/logs/pg-install.log"

# 三级策略安装内置 PostgreSQL：就近依赖 → 全量强制重装 → dnf 解析依赖
install_pg_rpm() {
  local r n
  local -a all main extra allin
  command -v rpm >/dev/null 2>&1 || { err "未找到 rpm，请确认系统为 Oracle Linux / RHEL 系列"; return 1; }
  [[ -d "$PGDIR" ]] || { err "安装包缺少 PostgreSQL RPM：runtime/pg-rpms/"; return 1; }
  shopt -s nullglob
  all=( "$PGDIR"/*.rpm )
  main=( "$PGDIR"/postgresql-private-libs-*.rpm "$PGDIR"/postgresql-1[0-9]*.rpm "$PGDIR"/postgresql-server-*.rpm )
  shopt -u nullglob
  [[ ${#all[@]} -gt 0 ]] || { err "RPM 目录为空：${PGDIR}"; return 1; }
  [[ ${#main[@]} -ge 3 ]] || { err "runtime/pg-rpms 缺少 PostgreSQL 主包"; return 1; }

  {
    echo "===== $(date '+%F %T') 开始安装 PostgreSQL ====="
    cat /etc/oracle-release 2>/dev/null || (cat /etc/os-release 2>/dev/null | head -5)
    rpm -qa 2>/dev/null | grep -i postgres | sort
  } >> "$PG_LOG" 2>&1

  # 1) 主包 + 目标机缺失的运行库（已装同名包跳过，不覆盖、不降级）
  extra=()
  for r in "${all[@]}"; do
    case "$(basename "$r")" in postgresql-*) continue ;; esac
    n="$(rpm -qp --qf '%{NAME}' "$r" 2>/dev/null || true)"
    [[ -n "$n" ]] || continue
    rpm -q "$n" >/dev/null 2>&1 || extra+=( "$r" )
  done
  allin=( "${main[@]}" )
  if [[ ${#extra[@]} -gt 0 ]]; then allin+=( "${extra[@]}" ); fi
  info "离线安装 PostgreSQL（主包 ${#main[@]} 个 + 缺失依赖 ${#extra[@]} 个）..."
  if rpm -Uvh --replacepkgs "${allin[@]}" >> "$PG_LOG" 2>&1 && find_psql >/dev/null 2>&1; then
    return 0
  fi

  # 2) 强制重装全部内置 RPM：修复「rpm 记录存在但文件被裁剪」的精简镜像
  warn "标准安装未成功，尝试强制重装全部内置 RPM（修复缺失文件）..."
  if rpm -Uvh --replacepkgs "${all[@]}" >> "$PG_LOG" 2>&1 && find_psql >/dev/null 2>&1; then
    return 0
  fi

  # 3) dnf/yum 解析依赖顺序安装
  if command -v dnf >/dev/null 2>&1; then
    warn "改用 dnf 解析依赖安装 ..."
    if dnf -y --nogpgcheck install "${all[@]}" >> "$PG_LOG" 2>&1 && find_psql >/dev/null 2>&1; then
      return 0
    fi
  elif command -v yum >/dev/null 2>&1; then
    warn "改用 yum 解析依赖安装 ..."
    if yum -y --nogpgcheck localinstall "${all[@]}" >> "$PG_LOG" 2>&1 && find_psql >/dev/null 2>&1; then
      return 0
    fi
  fi
  return 1
}

# 本机数据库地址判定：本地库才允许自动安装内置 PostgreSQL
LOCAL_DB="no"
case "$DB_HOST" in 127.0.0.1|localhost|::1) LOCAL_DB="yes" ;; esac

if ! pg_server_ready; then
  # 需要初始化本地库时，即使带了 --skip-pg-install 也必须先装 PG，否则后续必然失败
  if [[ "$PG_INSTALL" != "yes" && "$DB_INIT" == "yes" && "$LOCAL_DB" == "yes" ]]; then
    warn "已指定 --skip-pg-install，但本机未检测到 psql 且需要初始化本地数据库"
    warn "自动改为安装内置 PostgreSQL"
    PG_INSTALL="yes"
  fi
  if [[ "$PG_INSTALL" == "yes" ]]; then
    if ! install_pg_rpm; then
      err "PostgreSQL 离线安装失败，安装日志：${PG_LOG}"
      err "最近 20 行日志："
      tail -n 20 "$PG_LOG" >&2 2>/dev/null || true
      err "手工命令：rpm -Uvh --replacepkgs ${PGDIR}/*.rpm"
      exit 1
    fi
  fi
else
  info "系统已具备 PostgreSQL（$(find_psql)），跳过内置 RPM 安装"
fi

# 统一 psql 路径：PGDG 装在 /usr/pgsql-N/bin，需显式加入 PATH
if [[ "$PG_INSTALL" == "yes" || "$DB_INIT" == "yes" ]]; then
  PSQL_BIN="$(find_psql || true)"
  if [[ -n "$PSQL_BIN" ]]; then
    export PATH="$(dirname "$PSQL_BIN"):${PATH}"
    info "PostgreSQL 客户端：${PSQL_BIN}（$("$PSQL_BIN" --version 2>/dev/null | head -1)）"
  fi
fi

# ---------- 4. 初始化数据库 ----------
if [[ "$DB_INIT" == "yes" ]]; then
  if ! command -v psql >/dev/null 2>&1; then
    err "未找到 psql 客户端，无法初始化数据库"
    err "当前 PostgreSQL 安装状态："
    rpm -qa 2>/dev/null | grep -i postgres | sort || true
    err "1) 若使用了 --skip-pg-install：去掉该参数重跑 sudo bash bin/install.sh --yes"
    err "2) 手工安装内置 PostgreSQL：sudo rpm -Uvh --replacepkgs ${PGDIR}/*.rpm"
    err "3) 已安装但不在 PATH：export PATH=\$(dirname \$(ls /usr/pgsql-*/bin/psql /usr/bin/psql 2>/dev/null | head -1)):\$PATH"
    exit 1
  fi

  PGDATA_DIR=/var/lib/pgsql/data
  if [[ ! -f "${PGDATA_DIR}/PG_VERSION" ]]; then
    for d in /var/lib/pgsql/*/data; do
      [[ -f "$d/PG_VERSION" ]] && { PGDATA_DIR="$d"; break; }
    done
  fi
  if [[ ! -f "${PGDATA_DIR}/PG_VERSION" ]]; then
    info "初始化 PostgreSQL 数据目录 ..."
    if command -v postgresql-setup >/dev/null 2>&1; then
      postgresql-setup --initdb >/dev/null
    else
      install -d -o postgres -g postgres -m 0750 /var/lib/pgsql
      install -d -o postgres -g postgres -m 0700 "$PGDATA_DIR"
      run_as_pg initdb -D "$PGDATA_DIR" --encoding=UTF8 --locale=C >/dev/null
    fi
  fi

  # 非默认端口时同步修改 postgresql.conf
  PGCONF="${PGDATA_DIR}/postgresql.conf"
  if [[ "$DB_PORT" != "5432" && -f "$PGCONF" ]]; then
    sed -i -E "s/^#?port[[:space:]]*=.*/port = ${DB_PORT}/" "$PGCONF"
    info "PostgreSQL 监听端口已设置为 ${DB_PORT}"
  fi

  # 本机 TCP 认证改为 md5，保证 JDBC 可用（RHEL 系列默认可能是 ident）
  HBA="${PGDATA_DIR}/pg_hba.conf"
  if [[ -f "$HBA" ]] && grep -qE '^host[[:space:]]+all[[:space:]]+all[[:space:]]+(127\.0\.0\.1/32|::1/128)[[:space:]]+ident' "$HBA"; then
    sed -i -E 's#^(host[[:space:]]+all[[:space:]]+all[[:space:]]+(127\.0\.0\.1/32|::1/128)[[:space:]]+)ident#\1md5#' "$HBA"
    info "已调整 pg_hba.conf 本机认证方式为 md5"
  fi

  systemctl enable --now postgresql >/dev/null 2>&1 || systemctl restart postgresql

  info "等待数据库就绪 ..."
  for _ in $(seq 1 30); do
    run_as_pg psql -tAc 'SELECT 1' >/dev/null 2>&1 && break
    sleep 2
  done

  info "创建数据库用户与库 ..."
  run_as_pg psql -v ON_ERROR_STOP=1 -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" 2>/dev/null | grep -q 1 \
    || run_as_pg psql -v ON_ERROR_STOP=1 -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  run_as_pg psql -v ON_ERROR_STOP=1 -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" 2>/dev/null | grep -q 1 \
    || run_as_pg psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER} ENCODING 'UTF8';"

  # 非停止模式导入：单条语句失败不影响后续建表，导入后统一校验关键表
  DB_LOG="${INSTALL_DIR}/logs/db-import.log"
  info "导入表结构（幂等，非停止模式）..."
  set +e
  run_as_pg psql -d "$DB_NAME" -q \
    -c "SET ROLE ${DB_USER};" -f "${INSTALL_DIR}/db/01-schema.sql" > "$DB_LOG" 2>&1
  DB_RC=$?
  set -e
  if [[ $DB_RC -ne 0 ]]; then
    warn "导入过程存在失败语句（已继续执行后续建表），详情：${DB_LOG}"
  fi

  info "校验关键表 ..."
  MISSING=""
  for t in lis_reagent lis_reagent_stock lis_reagent_inbound lis_reagent_purchase_order \
           lis_reagent_requisition lis_sys_config lis_print_template lis_reagent_thirdparty_config; do
    run_as_pg psql -d "$DB_NAME" -tAc "SELECT to_regclass('public.${t}') IS NOT NULL" 2>/dev/null \
      | grep -q t || MISSING="${MISSING} ${t}"
  done
  if [[ -n "$MISSING" ]]; then
    err "关键表缺失：${MISSING}"
    err "请查看导入日志：${DB_LOG}"
    err "补执行命令：sudo -u postgres psql -d ${DB_NAME} -f ${INSTALL_DIR}/db/01-schema.sql"
    exit 1
  fi

  TB_COUNT="$(run_as_pg psql -d "$DB_NAME" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'lis%'" 2>/dev/null | tr -d ' ' || echo '?')"
  info "数据库初始化完成（lis* 表数量：${TB_COUNT}）"

  # 校验 TCP 连接（与应用程序一致）
  if ! PGPASSWORD="${DB_PASSWORD}" psql -h 127.0.0.1 -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
       -tAc 'SELECT 1' >/dev/null 2>&1; then
    warn "数据库 TCP 认证校验未通过，请检查 ${HBA} 与监听端口"
  fi
fi

# ---------- 5. 生成配置文件 ----------
CONF="${INSTALL_DIR}/config/application.yml"
DB_ENV="${INSTALL_DIR}/config/db.env"
if [[ -f "$CONF" && "$FORCE_CONFIG" != "yes" ]]; then
  info "检测到已有配置，保留 ${CONF}（如需覆盖请加 --force-config）"
  if [[ -f "$DB_ENV" ]]; then set -a; . "$DB_ENV"; set +a; fi
else
  info "生成配置文件 config/application.yml"
  sed -e "s|__PORT__|${PORT}|g" \
      -e "s|__DB_HOST__|${DB_HOST}|g" \
      -e "s|__DB_PORT__|${DB_PORT}|g" \
      -e "s|__DB_NAME__|${DB_NAME}|g" \
      -e "s|__DB_USER__|${DB_USER}|g" \
      -e "s|__DB_PASSWORD__|${DB_PASSWORD}|g" \
      -e "s|__LOG_DIR__|${INSTALL_DIR}/logs|g" \
      "${PKG_ROOT}/config/application.yml.tpl" > "$CONF"
  chmod 600 "$CONF"
  {
    printf 'DB_HOST=%q\n' "$DB_HOST"
    printf 'DB_PORT=%q\n' "$DB_PORT"
    printf 'DB_NAME=%q\n' "$DB_NAME"
    printf 'DB_USER=%q\n' "$DB_USER"
    printf 'DB_PASSWORD=%q\n' "$DB_PASSWORD"
  } > "$DB_ENV"
  chmod 600 "$DB_ENV"
fi

# ---------- 6. 安装 systemd 服务 ----------
if [[ "$USE_SERVICE" == "yes" ]] && command -v systemctl >/dev/null 2>&1; then
  info "安装 systemd 服务 lis-reagent.service"
  sed -e "s|__INSTALL_DIR__|${INSTALL_DIR}|g" \
      "${PKG_ROOT}/systemd/lis-reagent.service" > /etc/systemd/system/lis-reagent.service
  systemctl daemon-reload
  systemctl enable lis-reagent >/dev/null 2>&1 || true
  systemctl restart lis-reagent
else
  info "跳过 systemd 安装，手工启动：${INSTALL_DIR}/bin/start.sh"
  "${INSTALL_DIR}/bin/start.sh"
fi

# ---------- 7. 防火墙 ----------
if [[ "$USE_FIREWALL" == "yes" ]] && command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-port="${PORT}/tcp" >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
  info "firewalld 已放行 ${PORT}/tcp"
fi

# ---------- 8. 健康检查 ----------
info "等待服务就绪 ..."
READY="no"
if ! command -v curl >/dev/null 2>&1; then
  warn "未找到 curl，跳过健康检查"
  READY="yes"
else
  for _ in $(seq 1 45); do
    BODY="$(curl -s "http://127.0.0.1:${PORT}/api/reagent/dashboard/overview" 2>/dev/null || true)"
    if echo "$BODY" | grep -q '"success":true'; then READY="yes"; break; fi
    sleep 2
  done
fi
if [[ "$READY" == "yes" ]]; then
  info "服务健康检查通过"
else
  warn "服务未在预期时间内就绪，请查看日志：journalctl -u lis-reagent -n 100 --no-pager"
fi

IP_ADDR="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
echo ""
echo "======================================================"
echo "  安装完成  v${APP_VERSION}（离线版）"
echo "  访问地址 : http://${IP_ADDR:-<服务器IP>}:${PORT}/"
echo "  安装目录 : ${INSTALL_DIR}"
echo "  内置 JDK : ${JDK_DIR}"
echo "  配置文件 : ${CONF}"
echo "  日志文件 : ${INSTALL_DIR}/logs/lis-reagent.log"
echo "  启动/停止: systemctl start|stop lis-reagent"
echo "  后续升级 : sudo bash ${INSTALL_DIR}/bin/update.sh --package <新版本tar.gz>"
echo "======================================================"
