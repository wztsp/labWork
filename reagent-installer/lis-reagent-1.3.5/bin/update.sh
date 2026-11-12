#!/usr/bin/env bash
# =============================================================================
# 试剂管理系统（独立版）- 一键升级脚本
#
# 用法：
#   # 方式一：上传并解压新版本包，在包目录内执行
#   tar -xzf lis-reagent-1.1.0.tar.gz && cd lis-reagent-1.1.0
#   sudo bash bin/update.sh
#
#   # 方式二：直接在服务器上指定新版本包（推荐）
#   sudo bash /opt/lis-reagent/bin/update.sh --package /root/lis-reagent-1.1.0.tar.gz
#
# 参数：
#   --package <路径>   新版本包（tar.gz 文件或已解压目录）
#   --dir <路径>       安装目录，默认从 systemd 单元读取，否则 /opt/lis-reagent
#   --no-db            不执行数据库脚本升级
#   --no-backup-db     不备份数据库（默认用 pg_dump 备份）
#   --yes              跳过交互确认
#
# 升级流程：停服 → 备份 jar/配置/数据库 → 替换 jar → 幂等执行建表脚本 →
#           启动 → 健康检查（失败自动回滚 jar 并重启旧版本）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PACKAGE=""
INSTALL_DIR=""
SKIP_DB="no"
BACKUP_DB="yes"
ASSUME_YES="no"
TMP_DIR=""

info()  { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn()  { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()   { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

usage() { sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }
cleanup() { if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then rm -rf "$TMP_DIR"; fi; return 0; }
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)     PACKAGE="$2"; shift 2 ;;
    --dir)         INSTALL_DIR="$2"; shift 2 ;;
    --no-db)       SKIP_DB="yes"; shift ;;
    --no-backup-db) BACKUP_DB="no"; shift ;;
    --yes|-y)      ASSUME_YES="yes"; shift ;;
    -h|--help)     usage ;;
    *) err "未知参数: $1"; usage ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行"; exit 1; }

# ---------- 1. 定位安装目录 ----------
if [[ -z "$INSTALL_DIR" ]]; then
  UNIT="/etc/systemd/system/lis-reagent.service"
  if [[ -f "$UNIT" ]]; then
    INSTALL_DIR="$(sed -n 's/^WorkingDirectory=//p' "$UNIT" | head -1)"
  fi
  [[ -z "$INSTALL_DIR" ]] && INSTALL_DIR="/opt/lis-reagent"
fi
[[ -d "$INSTALL_DIR" ]] || { err "安装目录不存在：${INSTALL_DIR}"; exit 1; }
INSTALL_DIR="$(cd "$INSTALL_DIR" && pwd)"
[[ -f "${INSTALL_DIR}/app/lis-reagent.jar" ]] || { err "未找到已安装系统：${INSTALL_DIR}/app/lis-reagent.jar"; exit 1; }
OLD_VERSION="$(cat "${INSTALL_DIR}/VERSION" 2>/dev/null || echo "unknown")"

# ---------- 2. 定位新版本包 ----------
if [[ -n "$PACKAGE" ]]; then
  if [[ -d "$PACKAGE" ]]; then
    SRC_ROOT="$(cd "$PACKAGE" && pwd)"
  elif [[ -f "$PACKAGE" && "$PACKAGE" == *.tar.gz ]]; then
    TMP_DIR="$(mktemp -d)"
    tar -xzf "$PACKAGE" -C "$TMP_DIR"
    SRC_ROOT="$(find "$TMP_DIR" -maxdepth 1 -mindepth 1 -type d -print -quit)"
    [[ -z "$SRC_ROOT" ]] && SRC_ROOT="$TMP_DIR"
  else
    err "无法识别的包路径：$PACKAGE（需为 .tar.gz 或已解压目录）"; exit 1
  fi
else
  SRC_ROOT="$PKG_ROOT"
fi
[[ -f "${SRC_ROOT}/app/lis-reagent.jar" ]] || { err "新版本包缺少 app/lis-reagent.jar：${SRC_ROOT}"; exit 1; }
if [[ "$(cd "$SRC_ROOT" && pwd)" == "$INSTALL_DIR" ]]; then
  err "新版本包与安装目录相同，无内容可升级。请在新包目录执行或使用 --package 指定新包"
  exit 1
fi
NEW_VERSION="$(cat "${SRC_ROOT}/VERSION" 2>/dev/null || echo "unknown")"

echo "======================================================"
echo "  试剂管理系统 一键升级"
echo "  当前版本 : ${OLD_VERSION}"
echo "  目标版本 : ${NEW_VERSION}"
echo "  安装目录 : ${INSTALL_DIR}"
echo "  升级来源 : ${SRC_ROOT}"
echo "======================================================"
if [[ "$ASSUME_YES" != "yes" ]]; then
  read -rp "确认开始升级？[Y/n]: " _in
  [[ -z "$_in" || "${_in,,}" == "y" ]] || { echo "已取消"; exit 1; }
fi

# ---------- 3. 读取数据库连接 ----------
DB_HOST=""; DB_PORT="5432"; DB_NAME=""; DB_USER=""; DB_PASSWORD=""
DB_ENV="${INSTALL_DIR}/config/db.env"
if [[ -f "$DB_ENV" ]]; then
  set -a; . "$DB_ENV"; set +a
fi
CONF="${INSTALL_DIR}/config/application.yml"
if [[ -z "${DB_NAME:-}" && -f "$CONF" ]]; then
  YML_URL="$(grep -E '^[[:space:]]*url:[[:space:]]*jdbc:postgresql' "$CONF" | head -1 || true)"
  if [[ -n "$YML_URL" ]]; then
    HP="$(echo "$YML_URL" | sed -E 's#.*jdbc:postgresql://([^/?]+).*#\1#')"
    DB_HOST="${HP%%:*}"
    if [[ "$HP" == *:* ]]; then DB_PORT="${HP##*:}"; else DB_PORT="5432"; fi
    DB_NAME="$(echo "$YML_URL" | sed -E 's#.*/([^/?]+)(\?.*)?$#\1#')"
  fi
  DB_USER="$(grep -E '^[[:space:]]*username:' "$CONF" | head -1 | sed -E 's/^[[:space:]]*username:[[:space:]]*//' || true)"
  DB_PASSWORD="$(grep -E '^[[:space:]]*password:' "$CONF" | head -1 | sed -E 's/^[[:space:]]*password:[[:space:]]*//' || true)"
fi
DEFAULT_PORT="$(grep -E '^[[:space:]]*port:' "$CONF" 2>/dev/null | head -1 | awk '{print $2}' || true)"
SERVICE_PORT="${DEFAULT_PORT:-8092}"

run_as_pg() {
  if [[ "$(id -un)" == "postgres" ]]; then "$@"
  elif command -v runuser >/dev/null 2>&1; then runuser -u postgres -- "$@"
  else sudo -u postgres -- "$@"; fi
}

# ---------- 4. 备份 ----------
BK="${INSTALL_DIR}/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BK"
info "备份当前版本到 ${BK}"
cp -f "${INSTALL_DIR}/app/lis-reagent.jar" "${BK}/lis-reagent.jar"
cp -f "${CONF}" "${BK}/application.yml" 2>/dev/null || true
cp -f "${INSTALL_DIR}/VERSION" "${BK}/VERSION" 2>/dev/null || echo "$OLD_VERSION" > "${BK}/VERSION"

if [[ "$BACKUP_DB" == "yes" ]]; then
  if command -v pg_dump >/dev/null 2>&1 && [[ -n "$DB_NAME" ]]; then
    info "备份数据库 ${DB_NAME} ..."
    if PGPASSWORD="${DB_PASSWORD}" pg_dump -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" \
         -U "${DB_USER}" -d "${DB_NAME}" -Fc -f "${BK}/db.dump" 2>/dev/null; then
      info "数据库备份完成：${BK}/db.dump"
    else
      warn "数据库备份失败（pg_dump 认证或连接问题），继续升级；如需跳过请加 --no-backup-db"
      rm -f "${BK}/db.dump"
    fi
  else
    warn "未找到 pg_dump 或数据库参数不完整，跳过数据库备份"
  fi
fi

# ---------- 5. 停止服务 ----------
info "停止服务 ..."
if [[ -f /etc/systemd/system/lis-reagent.service ]] && command -v systemctl >/dev/null 2>&1; then
  systemctl stop lis-reagent || true
else
  "${INSTALL_DIR}/bin/stop.sh" || true
fi

start_service() {
  if [[ -f /etc/systemd/system/lis-reagent.service ]] && command -v systemctl >/dev/null 2>&1; then
    systemctl start lis-reagent
  else
    "${INSTALL_DIR}/bin/start.sh"
  fi
}

rollback() {
  warn "升级失败，回滚到 ${OLD_VERSION} ..."
  cp -f "${BK}/lis-reagent.jar" "${INSTALL_DIR}/app/lis-reagent.jar"
  echo "$OLD_VERSION" > "${INSTALL_DIR}/VERSION"
  start_service || true
  err "已回滚。备份目录：${BK}"
  err "排查日志：journalctl -u lis-reagent -n 120 --no-pager"
  exit 1
}

# ---------- 6. 替换程序 ----------
info "替换程序文件 ..."
cp -f "${SRC_ROOT}/app/lis-reagent.jar" "${INSTALL_DIR}/app/lis-reagent.jar"
[[ -f "${SRC_ROOT}/db/01-schema.sql" ]] && cp -f "${SRC_ROOT}/db/01-schema.sql" "${INSTALL_DIR}/db/"
for f in start.sh stop.sh update.sh uninstall.sh; do
  [[ -f "${SRC_ROOT}/bin/${f}" ]] && cp -f "${SRC_ROOT}/bin/${f}" "${INSTALL_DIR}/bin/"
done
chmod +x "${INSTALL_DIR}"/bin/*.sh
echo "$NEW_VERSION" > "${INSTALL_DIR}/VERSION"

# 若旧安装没有内置 JDK（早期系统 Java 版本），从新包补齐
if [[ ! -x "${INSTALL_DIR}/jdk/bin/java" ]]; then
  JDK_PKG="$(find "${SRC_ROOT}" -maxdepth 3 -type f -name 'openjdk-*-linux-x64*.tar.gz' -print -quit 2>/dev/null || true)"
  if [[ -n "$JDK_PKG" ]]; then
    info "补齐内置 JDK ..."
    tar -xzf "$JDK_PKG" -C "$INSTALL_DIR"
    JDK_EXTRACTED="$(find "$INSTALL_DIR" -maxdepth 1 -mindepth 1 -type d -name 'jdk-17*' -print -quit)"
    [[ -n "$JDK_EXTRACTED" ]] && mv "$JDK_EXTRACTED" "${INSTALL_DIR}/jdk"
  fi
fi

# ---------- 7. 数据库结构升级（脚本幂等，非停止模式） ----------
if [[ "$SKIP_DB" != "yes" && -f "${INSTALL_DIR}/db/01-schema.sql" ]]; then
  if command -v psql >/dev/null 2>&1 && [[ -n "$DB_NAME" ]]; then
    DB_LOG="${INSTALL_DIR}/logs/db-upgrade.log"
    MISSING=""
    info "执行数据库结构升级（幂等，非停止模式） ..."
    if [[ "$DB_HOST" == "127.0.0.1" || "$DB_HOST" == "localhost" ]] \
       && run_as_pg psql -d "$DB_NAME" -c 'SELECT 1' >/dev/null 2>&1; then
      set +e
      run_as_pg psql -d "$DB_NAME" -q \
        -c "SET ROLE ${DB_USER};" -f "${INSTALL_DIR}/db/01-schema.sql" > "$DB_LOG" 2>&1
      DB_RC=$?
      set -e
      [[ $DB_RC -ne 0 ]] && warn "升级脚本存在失败语句（已继续执行后续语句），详情：${DB_LOG}"
      for t in lis_reagent lis_reagent_stock lis_reagent_inbound lis_reagent_purchase_order \
               lis_reagent_requisition lis_sys_config lis_print_template lis_reagent_thirdparty_config; do
        run_as_pg psql -d "$DB_NAME" -tAc "SELECT to_regclass('public.${t}') IS NOT NULL" 2>/dev/null \
          | grep -q t || MISSING="${MISSING} ${t}"
      done
    else
      set +e
      PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" \
        -U "${DB_USER}" -d "${DB_NAME}" -q \
        -f "${INSTALL_DIR}/db/01-schema.sql" > "$DB_LOG" 2>&1
      DB_RC=$?
      set -e
      [[ $DB_RC -ne 0 ]] && warn "升级脚本存在失败语句（已继续执行后续语句），详情：${DB_LOG}"
      for t in lis_reagent lis_reagent_stock lis_reagent_inbound lis_reagent_purchase_order \
               lis_reagent_requisition lis_sys_config lis_print_template lis_reagent_thirdparty_config; do
        PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" \
          -U "${DB_USER}" -d "${DB_NAME}" -tAc "SELECT to_regclass('public.${t}') IS NOT NULL" 2>/dev/null \
          | grep -q t || MISSING="${MISSING} ${t}"
      done
    fi
    if [[ -n "$MISSING" ]]; then
      err "数据库升级后关键表缺失：${MISSING}"
      err "详情：${DB_LOG}"
      rollback
    fi
    info "数据库结构升级完成"
  else
    warn "未找到 psql 或数据库参数不完整，跳过数据库升级，可稍后手工执行 01-schema.sql"
  fi
fi

# ---------- 8. 启动与健康检查 ----------
info "启动服务 ..."
start_service || rollback
READY="no"
if ! command -v curl >/dev/null 2>&1; then
  warn "未找到 curl，跳过健康检查（不会自动回滚）"
  READY="yes"
else
  for _ in $(seq 1 45); do
    BODY="$(curl -s "http://127.0.0.1:${SERVICE_PORT}/api/reagent/dashboard/overview" 2>/dev/null || true)"
    if echo "$BODY" | grep -q '"success":true'; then READY="yes"; break; fi
    sleep 2
  done
fi
[[ "$READY" == "yes" ]] || rollback

# ---------- 9. 清理旧备份（保留最近 5 份） ----------
find "${INSTALL_DIR}/backup" -maxdepth 1 -mindepth 1 -type d | sort -r | tail -n +6 | xargs -r rm -rf

IP_ADDR="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
echo ""
echo "======================================================"
echo "  升级完成：${OLD_VERSION} → ${NEW_VERSION}"
echo "  访问地址 : http://${IP_ADDR:-<服务器IP>}:${SERVICE_PORT}/"
echo "  本次备份 : ${BK}"
echo "  日志查看 : journalctl -u lis-reagent -n 100 --no-pager"
echo "  回滚方法 : cp ${BK}/lis-reagent.jar ${INSTALL_DIR}/app/ && systemctl restart lis-reagent"
echo "======================================================"
