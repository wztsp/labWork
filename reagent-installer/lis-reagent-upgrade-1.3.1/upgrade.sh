#!/usr/bin/env bash
# =============================================================================
# 试剂管理系统 - 程序升级脚本（只替换 jar 包并重启服务）
#
# 特点：不改数据库结构、不改配置文件，只做 jar 备份/替换/重启/健康检查。
#
# 用法：
#   sudo bash upgrade.sh                          # 使用本包 app/lis-reagent.jar
#   sudo bash upgrade.sh --jar /root/lis-reagent.jar
#   sudo bash upgrade.sh --package /root/xxx-update.tar.gz
#   sudo bash upgrade.sh --dir /lis_reagent/lis-reagent-1.0.0
#   sudo bash upgrade.sh --rollback               # 回滚到最近一次备份
#
# 参数：
#   --jar <文件>       新程序包（默认取本脚本所在包的 app/lis-reagent.jar）
#   --package <包>     升级包 tar.gz（自动在包内查找 lis-reagent.jar）
#   --dir <目录>       安装目录，默认自动探测
#   --service <名称>   服务名，默认 lis-reagent
#   --port <端口>      健康检查端口，默认读取 config/application.yml
#   --no-restart       只替换程序包，不重启服务
#   --no-health        跳过升级后健康检查
#   --rollback         回滚到最近一次备份
#   --yes              跳过交互确认
#   -h, --help         显示本帮助
# =============================================================================
set -euo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="lis-reagent"
JAR_NAME="${APP_NAME}.jar"

DIR_ARG=""
JAR_SRC=""
PKG_SRC=""
SERVICE="lis-reagent"
PORT_ARG=""
DO_RESTART="yes"
DO_HEALTH="yes"
DO_ROLLBACK="no"
ASSUME_YES="no"
TMP_DIR=""

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }
cleanup() {
  if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then rm -rf "$TMP_DIR"; fi
  return 0
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)        JAR_SRC="${2:-}"; shift 2 ;;
    --package)    PKG_SRC="${2:-}"; shift 2 ;;
    --dir)        DIR_ARG="${2:-}"; shift 2 ;;
    --service)    SERVICE="${2:-}"; shift 2 ;;
    --port)       PORT_ARG="${2:-}"; shift 2 ;;
    --no-restart) DO_RESTART="no"; shift ;;
    --no-health)  DO_HEALTH="no"; shift ;;
    --rollback)   DO_ROLLBACK="yes"; shift ;;
    --yes|-y)     ASSUME_YES="yes"; shift ;;
    -h|--help)    usage ;;
    *) err "未知参数: $1"; usage ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行：sudo bash $0"; exit 1; }

# ---------- 定位安装目录 ----------
resolve_dir() {
  if [[ -n "$DIR_ARG" ]]; then echo "$DIR_ARG"; return; fi
  local unit="/etc/systemd/system/${SERVICE}.service" d=""
  if [[ -f "$unit" ]]; then
    d="$(sed -n 's/^WorkingDirectory=//p' "$unit" | head -1 | tr -d '\r')"
    [[ -n "$d" && -f "${d}/app/${JAR_NAME}" ]] && { echo "$d"; return; }
    d="$(sed -n 's/^ExecStart=.*-jar[[:space:]]\+\([^[:space:]]*\)\/app\/.*/\1/p' "$unit" | head -1 | tr -d '\r')"
    [[ -n "$d" && -f "${d}/app/${JAR_NAME}" ]] && { echo "$d"; return; }
  fi
  local c
  for c in /lis_reagent/lis-reagent-1.0.0 /opt/lis-reagent /lis_reagent /usr/local/lis-reagent; do
    if [[ -f "${c}/app/${JAR_NAME}" ]]; then echo "$c"; return; fi
  done
  echo ""
}

INSTALL_DIR="$(resolve_dir)"
if [[ -z "$INSTALL_DIR" ]]; then
  err "未找到试剂管理安装目录，请用 --dir 指定，例如：sudo bash $0 --dir /lis_reagent/lis-reagent-1.0.0"
  exit 1
fi
INSTALL_DIR="$(cd "$INSTALL_DIR" && pwd)"
if [[ ! -f "${INSTALL_DIR}/app/${JAR_NAME}" ]]; then
  err "不是有效的安装目录（缺少 app/${JAR_NAME}）：${INSTALL_DIR}"
  exit 1
fi

OLD_VERSION="$(cat "${INSTALL_DIR}/VERSION" 2>/dev/null | head -1 | tr -d '[:space:]' || true)"
NEW_VERSION=""
for _f in "${SELF_DIR}/VERSION" "${SELF_DIR}/../VERSION"; do
  [[ -f "$_f" ]] && { NEW_VERSION="$(head -1 "$_f" | tr -d '[:space:]')"; break; }
done

# ---------- 读取服务端口 ----------
detect_port() {
  if [[ -n "$PORT_ARG" ]]; then echo "$PORT_ARG"; return; fi
  local yml="${INSTALL_DIR}/config/application.yml" p=""
  if [[ -f "$yml" ]]; then
    p="$(sed -n 's/^[[:space:]]*port:[[:space:]]*\([0-9]\{2,5\}\).*/\1/p' "$yml" | head -1 | tr -d '[:space:]\r')"
    if [[ -z "$p" ]]; then
      p="$(grep -o 'SERVER_PORT:[0-9]\{2,5\}' "$yml" 2>/dev/null | head -1 | cut -d: -f2 | tr -d '[:space:]' || true)"
    fi
  fi
  echo "${p:-8092}"
}
PORT="$(detect_port)"

restart_service() {
  if command -v systemctl >/dev/null 2>&1 && [[ -f "/etc/systemd/system/${SERVICE}.service" ]]; then
    systemctl restart "$SERVICE"
  else
    [[ -x "${INSTALL_DIR}/bin/stop.sh" ]] && bash "${INSTALL_DIR}/bin/stop.sh" >/dev/null 2>&1 || true
    [[ -x "${INSTALL_DIR}/bin/start.sh" ]] && bash "${INSTALL_DIR}/bin/start.sh" >/dev/null 2>&1 || true
  fi
}

wait_healthy() {
  if ! command -v curl >/dev/null 2>&1; then
    warn "未安装 curl，跳过健康检查"
    return 0
  fi
  local i code
  for ((i = 1; i <= 60; i++)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" 2>/dev/null || true)"
    [[ "$code" == "200" ]] && return 0
    sleep 2
  done
  return 1
}

BACKUP_DIR="${INSTALL_DIR}/backup"
LOG_FILE="${INSTALL_DIR}/logs/lis-reagent.log"
mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d%H%M%S)"

echo "======================================================"
echo "  试剂管理系统 - 程序升级（仅替换 jar 并重启）"
echo "======================================================"
info "安装目录：${INSTALL_DIR}"
info "当前版本：${OLD_VERSION:-未知}"

# ---------- 回滚模式 ----------
if [[ "$DO_ROLLBACK" == "yes" ]]; then
  LAST="$(ls -1t "${BACKUP_DIR}"/${APP_NAME}.jar.20* 2>/dev/null | head -1 || true)"
  if [[ -z "$LAST" ]]; then
    err "没有可用的备份：${BACKUP_DIR}"
    exit 1
  fi
  cp -f "${INSTALL_DIR}/app/${JAR_NAME}" "${BACKUP_DIR}/${APP_NAME}.jar.pre-rollback.${TS}"
  cp -f "$LAST" "${INSTALL_DIR}/app/${JAR_NAME}"
  info "已回滚到：$(basename "$LAST")"
  if [[ "$DO_RESTART" == "yes" ]]; then
    info "重启服务 ..."
    restart_service
    if ! wait_healthy; then
      err "回滚后服务未就绪，请查看日志：${LOG_FILE}"
      exit 1
    fi
    info "服务已就绪：http://127.0.0.1:${PORT}/"
  fi
  info "回滚完成"
  exit 0
fi

# ---------- 定位新程序包 ----------
if [[ -n "$PKG_SRC" ]]; then
  [[ -f "$PKG_SRC" ]] || { err "升级包不存在：${PKG_SRC}"; exit 1; }
  TMP_DIR="$(mktemp -d)"
  info "解压升级包：$(basename "$PKG_SRC")"
  tar -xzf "$PKG_SRC" -C "$TMP_DIR"
  JAR_SRC="$(find "$TMP_DIR" -maxdepth 3 -type f -name "${APP_NAME}*.jar" -print -quit)"
fi

if [[ -z "$JAR_SRC" ]]; then
  for _c in "${SELF_DIR}/app/${JAR_NAME}" "${SELF_DIR}/${JAR_NAME}" \
            "./app/${JAR_NAME}" "./${JAR_NAME}"; do
    [[ -f "$_c" ]] && { JAR_SRC="$_c"; break; }
  done
fi

if [[ -z "$JAR_SRC" || ! -f "$JAR_SRC" ]]; then
  err "未找到新程序包。请把 ${JAR_NAME} 放到本脚本同目录的 app/ 下，或用 --jar 指定"
  exit 1
fi

ABS_SRC="$(cd "$(dirname "$JAR_SRC")" && pwd)/$(basename "$JAR_SRC")"
if [[ "$ABS_SRC" == "${INSTALL_DIR}/app/${JAR_NAME}" ]]; then
  err "新程序包与当前运行的程序包是同一个文件：${ABS_SRC}"
  exit 1
fi

JAR_SIZE="$(stat -c %s "$JAR_SRC" 2>/dev/null || echo 0)"
if [[ "$JAR_SIZE" -lt 5000000 ]]; then
  err "文件过小，可能不是完整的程序包：${JAR_SRC}（${JAR_SIZE} 字节）"
  exit 1
fi
MAGIC="$(head -c 2 "$JAR_SRC" | od -An -tx1 | tr -d ' \n')"
if [[ "$MAGIC" != "504b" ]]; then
  err "文件不是有效的 jar 包（ZIP 头校验失败）：${JAR_SRC}"
  exit 1
fi

if [[ "$ASSUME_YES" != "yes" ]]; then
  echo "----------------------------------------"
  echo "  新程序包：${JAR_SRC}（$(du -h "$JAR_SRC" | cut -f1)）"
  echo "  升级版本：${NEW_VERSION:-未知}"
  echo "  重启服务：${DO_RESTART}"
  read -rp "确认升级？[y/N]: " _in || _in=""
  if [[ "${_in,,}" != "y" ]]; then
    info "已取消"
    exit 0
  fi
fi

# ---------- 备份并替换 ----------
cp -f "${INSTALL_DIR}/app/${JAR_NAME}" "${BACKUP_DIR}/${APP_NAME}.jar.${TS}"
info "旧程序已备份：backup/${APP_NAME}.jar.${TS}"

cp -f "$JAR_SRC" "${INSTALL_DIR}/app/${JAR_NAME}.new"
mv -f "${INSTALL_DIR}/app/${JAR_NAME}.new" "${INSTALL_DIR}/app/${JAR_NAME}"
info "新程序已替换：$(basename "$JAR_SRC")"

if [[ -n "$NEW_VERSION" ]]; then
  echo "$NEW_VERSION" > "${INSTALL_DIR}/VERSION"
  info "版本号已更新：${OLD_VERSION:-未知} -> ${NEW_VERSION}"
fi

# ---------- 重启并校验 ----------
if [[ "$DO_RESTART" != "yes" ]]; then
  info "已跳过重启（--no-restart），请手动执行：systemctl restart ${SERVICE}"
  exit 0
fi

info "重启服务 ..."
restart_service

if [[ "$DO_HEALTH" == "yes" ]]; then
  if wait_healthy; then
    info "服务已就绪：http://127.0.0.1:${PORT}/"
  else
    err "升级后服务未在预期时间内就绪，自动回滚 ..."
    cp -f "${INSTALL_DIR}/app/${JAR_NAME}" "${BACKUP_DIR}/${APP_NAME}.jar.failed.${TS}"
    cp -f "${BACKUP_DIR}/${APP_NAME}.jar.${TS}" "${INSTALL_DIR}/app/${JAR_NAME}"
    restart_service
    if wait_healthy; then
      err "已回滚到升级前的程序包，请检查日志：${LOG_FILE}"
    else
      err "回滚后服务仍未就绪，请查看日志：${LOG_FILE}"
    fi
    exit 1
  fi
fi

info "升级完成。数据库结构与配置文件未改动。"
