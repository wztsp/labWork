#!/usr/bin/env bash
# =============================================================================
# 试剂管理系统（独立版）- 一键安装 / 启动（数据库由用户自行提供）
#
# 安装包已内置 JDK 17，无需联网、无需预装 Java；数据库需已就绪。
# 数据库参数请先修改 config/application.yml，再执行本脚本。
#
# 用法：
#   vi config/application.yml         # 填写数据库地址 / 库名 / 用户 / 密码
#   sudo bash bin/install.sh --yes    # 一键安装并启动
#   sudo bash bin/install.sh --dir /opt/lis-reagent --yes
#
# 参数：
#   --dir <路径>        安装目录，默认 /opt/lis-reagent
#   --conf <路径>       配置文件，默认取包内 config/application.yml
#   --jdk-path <路径>   JDK 压缩包或已解压目录（包内 JDK 缺失时用）
#   --skip-db-init      不导入表结构（表已存在时可跳过）
#   --force-config      覆盖安装目录中已有的配置文件
#   --no-service        不装 systemd 服务，仅用 bin/start.sh 后台启动
#   --no-start          只安装不启动
#   --no-firewall       不自动放行 firewalld 端口
#   --yes               跳过交互确认
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_VERSION="$(cat "$PKG_ROOT/VERSION" 2>/dev/null || echo "1.0.0")"

INSTALL_DIR="/opt/lis-reagent"
CONF_SRC="${PKG_ROOT}/config/application.yml"
JDK_PATH_ARG=""
DB_INIT="yes"
FORCE_CONFIG="no"
USE_SERVICE="yes"
USE_FIREWALL="yes"
DO_START="yes"
ASSUME_YES="no"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)              INSTALL_DIR="$2"; shift 2 ;;
    --conf)             CONF_SRC="$2"; shift 2 ;;
    --jdk-path)         JDK_PATH_ARG="$2"; shift 2 ;;
    --skip-db-init)     DB_INIT="no"; shift ;;
    --force-config)     FORCE_CONFIG="yes"; shift ;;
    --no-service)       USE_SERVICE="no"; shift ;;
    --no-start)         DO_START="no"; shift ;;
    --no-firewall)      USE_FIREWALL="no"; shift ;;
    --yes|-y)           ASSUME_YES="yes"; shift ;;
    -h|--help)          usage ;;
    *) err "未知参数: $1"; usage ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行：sudo bash bin/install.sh"; exit 1; }
[[ -f "$CONF_SRC" ]] || { err "配置文件不存在：${CONF_SRC}"; exit 1; }

echo "======================================================"
echo "  试剂管理系统（独立版）v${APP_VERSION} - 安装"
echo "======================================================"
info "配置文件：${CONF_SRC}"

if [[ "$ASSUME_YES" != "yes" ]]; then
  read -rp "安装目录 [${INSTALL_DIR}]: " _in; INSTALL_DIR="${_in:-$INSTALL_DIR}"
fi

# ---------- 1. 创建目录并复制程序 ----------
info "安装目录：${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"/{app,config,db,bin,logs,backup,runtime}
cp -f "${PKG_ROOT}/app/lis-reagent.jar" "${INSTALL_DIR}/app/"
cp -f "${PKG_ROOT}/db/01-schema.sql"    "${INSTALL_DIR}/db/"
for f in start.sh stop.sh update.sh uninstall.sh; do
  [[ -f "${PKG_ROOT}/bin/${f}" ]] && cp -f "${PKG_ROOT}/bin/${f}" "${INSTALL_DIR}/bin/"
done
cp -f "${PKG_ROOT}/VERSION" "${INSTALL_DIR}/VERSION" 2>/dev/null || true
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

  # 2.2 包内 / --jdk-path 指定的 JDK 压缩包
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
      JDK_EXTRACTED="$(find "${INSTALL_DIR}" -maxdepth 1 -mindepth 1 -type d -name 'jdk-1*' -print -quit)"
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
    err "1) 检查包内 runtime 目录：ls -lh ${PKG_ROOT}/runtime/"
    err "2) 把 openjdk-17.0.2_linux-x64_bin.tar.gz 放回 ${PKG_ROOT}/runtime/ 后重跑"
    err "3) 或指定 JDK 位置：sudo bash bin/install.sh --jdk-path /root/openjdk-17.0.2_linux-x64_bin.tar.gz"
    exit 1
  fi
fi
info "Java：$("${JDK_DIR}/bin/java" -version 2>&1 | head -1)"

# ---------- 3. 写入配置文件 ----------
CONF="${INSTALL_DIR}/config/application.yml"
if [[ -f "$CONF" && "$FORCE_CONFIG" != "yes" ]]; then
  info "保留已有配置：${CONF}（如需覆盖请加 --force-config）"
else
  cp -f "$CONF_SRC" "$CONF"
  chmod 600 "$CONF"
  info "已写入配置：${CONF}"
fi

# ---------- 4. 从配置解析数据库参数 ----------
yml_val() {
  grep -E "^[[:space:]]*$1:" "$CONF" 2>/dev/null | head -1 \
    | sed -E "s/^[[:space:]]*$1:[[:space:]]*//; s/[[:space:]]+$//" | tr -d '"' || true
}

DB_URL="$(grep -E '^[[:space:]]*url:[[:space:]]*jdbc:postgresql' "$CONF" | head -1 \
          | sed -E 's/^[[:space:]]*url:[[:space:]]*//' | tr -d '\r' || true)"
DB_USER="$(yml_val username)"
DB_PASSWORD="$(yml_val password)"
SERVER_PORT="$(yml_val port)"
SERVER_PORT="${SERVER_PORT:-8092}"

DB_HOST="127.0.0.1"
DB_PORT="5432"
DB_NAME=""
if [[ -n "$DB_URL" ]]; then
  DB_HP="$(echo "$DB_URL" | sed -E 's#^jdbc:postgresql://([^/?]+).*#\1#')"
  DB_HOST="${DB_HP%%:*}"
  if [[ "$DB_HP" == *:* ]]; then DB_PORT="${DB_HP##*:}"; fi
  DB_NAME="$(echo "$DB_URL" | sed -E 's#^[^?]*/([^/?]+)(\?.*)?$#\1#')"
fi
info "数据库：${DB_USER:-?}@${DB_HOST}:${DB_PORT}/${DB_NAME:-?}"
info "服务端口：${SERVER_PORT}"

# ---------- 5. 导入表结构（幂等，可重复执行） ----------
if [[ "$DB_INIT" == "yes" ]]; then
  if [[ -z "$DB_NAME" || -z "$DB_USER" ]]; then
    warn "未能从配置中解析出库名/用户，跳过表结构导入"
  elif ! command -v psql >/dev/null 2>&1; then
    warn "未找到 psql 客户端，跳过表结构导入"
    warn "请用数据库客户端手工执行：${INSTALL_DIR}/db/01-schema.sql"
  elif PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
       -tAc 'SELECT 1' >/dev/null 2>&1; then
    info "数据库连接正常，导入全量表结构（幂等，可重复执行）..."
    SCHEMA_LOG="${INSTALL_DIR}/logs/schema-install.log"
    DB_CONN=( -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" )
    # 不加 ON_ERROR_STOP：单条语句失败（如旧库表结构不同）不影响后续语句继续建表
    if PGPASSWORD="$DB_PASSWORD" psql "${DB_CONN[@]}" -q \
         -f "${INSTALL_DIR}/db/01-schema.sql" > "$SCHEMA_LOG" 2>&1; then
      info "表结构导入完成，无报错"
    else
      ERR_CNT="$(grep -c 'ERROR' "$SCHEMA_LOG" 2>/dev/null || true)"
      warn "导入过程中有 ${ERR_CNT:-?} 条语句报错，其余语句已继续执行"
      warn "报错明细见：${SCHEMA_LOG}"
    fi

    # 关键对象校验：这两个对象缺失会导致页面报「关系不存在 / 列不存在」
    CHK_TABLE="$(PGPASSWORD="$DB_PASSWORD" psql "${DB_CONN[@]}" -tAc \
      "SELECT to_regclass('public.lis_sys_config') IS NOT NULL" 2>/dev/null | tr -d ' ' || true)"
    CHK_COL="$(PGPASSWORD="$DB_PASSWORD" psql "${DB_CONN[@]}" -tAc \
      "SELECT count(*) FROM information_schema.columns WHERE table_name='lis_reagent' AND column_name='optimal_stock'" \
      2>/dev/null | tr -d ' ' || true)"
    TB_COUNT="$(PGPASSWORD="$DB_PASSWORD" psql "${DB_CONN[@]}" -tAc \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'lis%'" \
      2>/dev/null | tr -d ' ' || true)"
    if [[ "$CHK_TABLE" == "t" && -n "$CHK_COL" && "$CHK_COL" != "0" ]]; then
      info "表结构校验通过（lis* 表 ${TB_COUNT:-?} 张，lis_sys_config 与 lis_reagent.optimal_stock 均存在）"
    else
      err "表结构校验未通过：lis_sys_config=${CHK_TABLE:-?} optimal_stock列=${CHK_COL:-?}"
      err "请手工补执行：psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${DB_NAME} -f ${INSTALL_DIR}/db/01-schema.sql"
    fi
  else
    warn "用配置中的账号连接数据库失败：${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
    warn "请确认库/用户已创建、允许该主机登录（pg_hba.conf），然后手工执行："
    warn "  psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${DB_NAME} -f ${INSTALL_DIR}/db/01-schema.sql"
  fi
fi

# ---------- 6. 安装 systemd 服务 ----------
SVC_INSTALLED="no"
if [[ "$USE_SERVICE" == "yes" ]] && command -v systemctl >/dev/null 2>&1; then
  info "安装 systemd 服务 lis-reagent.service"
  sed -e "s|__INSTALL_DIR__|${INSTALL_DIR}|g" \
      "${PKG_ROOT}/systemd/lis-reagent.service" > /etc/systemd/system/lis-reagent.service
  systemctl daemon-reload
  systemctl enable lis-reagent >/dev/null 2>&1 || true
  SVC_INSTALLED="yes"
else
  info "跳过 systemd，使用 bin/start.sh 后台启动"
fi

# ---------- 7. 防火墙放行 ----------
if [[ "$USE_FIREWALL" == "yes" ]] && command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-port="${SERVER_PORT}/tcp" >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
  info "firewalld 已放行 ${SERVER_PORT}/tcp"
fi

# ---------- 8. 启动 ----------
if [[ "$DO_START" == "yes" ]]; then
  if [[ "$SVC_INSTALLED" == "yes" ]]; then
    info "启动服务 ..."
    systemctl restart lis-reagent || warn "systemd 启动失败，查看：journalctl -u lis-reagent -n 100 --no-pager"
  else
    bash "${INSTALL_DIR}/bin/start.sh" || warn "启动失败，查看：${INSTALL_DIR}/logs/console.log"
  fi

  info "等待服务就绪 ..."
  READY="no"
  if ! command -v curl >/dev/null 2>&1; then
    warn "未找到 curl，跳过健康检查"
    READY="yes"
  else
    for _ in $(seq 1 45); do
      if curl -s "http://127.0.0.1:${SERVER_PORT}/api/reagent/dashboard/overview" 2>/dev/null \
           | grep -q '"success":true'; then
        READY="yes"; break
      fi
      sleep 2
    done
  fi
  if [[ "$READY" == "yes" ]]; then
    info "服务健康检查通过"
  else
    warn "服务未在预期时间内就绪"
    if [[ "$SVC_INSTALLED" == "yes" ]]; then
      warn "查看日志：journalctl -u lis-reagent -n 100 --no-pager"
    else
      warn "查看日志：tail -n 100 ${INSTALL_DIR}/logs/console.log"
    fi
  fi
fi

IP_ADDR="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
echo ""
echo "======================================================"
echo "  安装完成  v${APP_VERSION}"
echo "  访问地址 : http://${IP_ADDR:-<服务器IP>}:${SERVER_PORT}/"
echo "  安装目录 : ${INSTALL_DIR}"
echo "  配置文件 : ${CONF}（改完重启服务生效）"
echo "  内置 JDK : ${JDK_DIR}"
echo "  日志文件 : ${INSTALL_DIR}/logs/"
if [[ "$SVC_INSTALLED" == "yes" ]]; then
  echo "  启停命令 : systemctl start|stop|restart lis-reagent"
else
  echo "  启停命令 : bash ${INSTALL_DIR}/bin/start.sh | bash ${INSTALL_DIR}/bin/stop.sh"
fi
echo "======================================================"
