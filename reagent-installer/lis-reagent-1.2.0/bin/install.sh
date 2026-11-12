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
APP_VERSION="$(cat "$PKG_ROOT/VERSION" 2>/dev/null || echo "1.2.0")"

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

if [[ "$PG_INSTALL" == "yes" ]]; then
  if rpm -q postgresql-server >/dev/null 2>&1; then
    info "系统已安装 $(rpm -q postgresql-server)，跳过内置 RPM 安装"
  else
    PGDIR="${PKG_ROOT}/runtime/pg-rpms"
    [[ -d "$PGDIR" ]] || { err "安装包缺少 PostgreSQL RPM：runtime/pg-rpms/"; exit 1; }
    shopt -s nullglob
    # 主包：postgresql（psql/pg_dump）、postgresql-private-libs（libpq）、postgresql-server
    PG_MAIN=( "$PGDIR"/postgresql-private-libs-*.rpm "$PGDIR"/postgresql-1[0-9]*.rpm "$PGDIR"/postgresql-server-*.rpm )
    [[ ${#PG_MAIN[@]} -ge 3 ]] || { err "runtime/pg-rpms 缺少 PostgreSQL 主包"; exit 1; }

    if rpm -Uvh --replacepkgs --test "${PG_MAIN[@]}" >/dev/null 2>&1; then
      info "离线安装 PostgreSQL（3 个主包）..."
      rpm -Uvh --replacepkgs "${PG_MAIN[@]}"
    else
      # 仅补装目标机缺失的运行库，已装同名包一律跳过（不做降级/覆盖）
      warn "PostgreSQL 主包依赖不完整，按需补装随包运行库 ..."
      PG_EXTRA=()
      for r in "$PGDIR"/*.rpm; do
        case "$(basename "$r")" in postgresql-*) continue ;; esac
        _n="$(rpm -qp --qf '%{NAME}' "$r" 2>/dev/null || true)"
        [[ -n "$_n" ]] || continue
        rpm -q "$_n" >/dev/null 2>&1 || PG_EXTRA+=("$r")
      done
      if [[ ${#PG_EXTRA[@]} -gt 0 ]]; then info "补齐运行库：${#PG_EXTRA[@]} 个"; fi
      PG_ALL=( "${PG_MAIN[@]}" )
      if [[ ${#PG_EXTRA[@]} -gt 0 ]]; then PG_ALL+=( "${PG_EXTRA[@]}" ); fi
      rpm -Uvh --replacepkgs "${PG_ALL[@]}" \
        || { err "PostgreSQL 离线安装失败，请手工检查依赖：rpm -Uvh ${PGDIR}/*.rpm"; exit 1; }
    fi
  fi
fi

# ---------- 4. 初始化数据库 ----------
if [[ "$DB_INIT" == "yes" ]]; then
  if ! command -v psql >/dev/null 2>&1; then
    err "未找到 psql，无法初始化数据库。请确认已安装 PostgreSQL 或使用内置安装"
    exit 1
  fi

  PGDATA_DIR=/var/lib/pgsql/data
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
    sed -i -E 's/^(host[[:space:]]+all[[:space:]]+all[[:space:]]+(127\.0\.0\.1/32|::1/128)[[:space:]]+)ident/\1md5/' "$HBA"
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

  info "导入表结构（幂等）..."
  run_as_pg psql -d "$DB_NAME" -v ON_ERROR_STOP=1 -q \
    -c "SET ROLE ${DB_USER};" -f "${INSTALL_DIR}/db/01-schema.sql"

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
