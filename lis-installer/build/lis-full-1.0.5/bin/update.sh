#!/usr/bin/env bash
# =============================================================================
# LIS 临床检验系统 - 程序升级（只替换 jar 包并重启，不改数据库与配置）
#
# 用法：
#   sudo bash upgrade.sh                          # 自动查找当前目录下的新 jar
#   sudo bash upgrade.sh --jar /root/lis-monolith.jar
#   sudo bash upgrade.sh --package lis-full-1.0.1-update.tar.gz
#   sudo bash upgrade.sh --rollback               # 回滚到最近一次备份
#
# 参数：
#   --jar <文件>      新程序包
#   --package <包>    升级包 tar.gz（自动在包内查找 lis-monolith.jar）
#   --dir <目录>      安装目录，默认自动探测
#   --port <端口>     健康检查端口，默认读取 config/application.yml
#   --no-restart      只替换程序包，不重启服务
#   --no-health       跳过升级后健康检查
#   --rollback        回滚到最近一次备份
#   --yes             跳过交互确认
#   -h, --help        显示本帮助
# =============================================================================
set -euo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DIR_ARG=""
JAR_SRC=""
PKG_SRC=""
PORT_ARG=""
DO_RESTART="yes"
DO_HEALTH="yes"
DO_ROLLBACK="no"
ASSUME_YES="no"
TMP_DIR=""

VERSION_SRC=""
APP_VERSION=""
for _f in "${SELF_DIR}/VERSION" "${SELF_DIR}/../VERSION"; do
  if [[ -f "$_f" ]]; then
    VERSION_SRC="$_f"
    APP_VERSION="$(head -1 "$_f" | tr -d '[:space:]')"
    break
  fi
done

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)        JAR_SRC="$2"; shift 2 ;;
    --package)    PKG_SRC="$2"; shift 2 ;;
    --dir)        DIR_ARG="$2"; shift 2 ;;
    --port)       PORT_ARG="$2"; shift 2 ;;
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
  if [[ -f "${SELF_DIR}/../app/lis-monolith.jar" ]]; then
    (cd "${SELF_DIR}/.." && pwd); return
  fi
  if [[ -f "${SELF_DIR}/app/lis-monolith.jar" ]]; then echo "$SELF_DIR"; return; fi
  local unit=/etc/systemd/system/lis-full.service
  if [[ -f "$unit" ]]; then
    local d
    d="$(sed -n 's/^ExecStart=.*-jar[[:space:]]\+\([^[:space:]]*\)\/app\/lis-monolith.jar.*/\1/p' "$unit" | head -1)"
    if [[ -n "$d" && -f "${d}/app/lis-monolith.jar" ]]; then echo "$d"; return; fi
  fi
  local c
  for c in /opt/lis-full /opt/lis /lis_full /usr/local/lis-full; do
    if [[ -f "${c}/app/lis-monolith.jar" ]]; then echo "$c"; return; fi
  done
  echo ""
}

INSTALL_DIR="$(resolve_dir)"
if [[ -z "$INSTALL_DIR" ]]; then
  err "未找到 LIS 安装目录，请用 --dir 指定，例如：sudo bash $0 --dir /opt/lis-full"
  exit 1
fi
if [[ ! -f "${INSTALL_DIR}/app/lis-monolith.jar" ]]; then
  err "不是有效的安装目录（缺少 app/lis-monolith.jar）：${INSTALL_DIR}"
  exit 1
fi

# ---------- 读取服务端口 ----------
detect_port() {
  if [[ -n "$PORT_ARG" ]]; then echo "$PORT_ARG"; return; fi
  local yml="${INSTALL_DIR}/config/application.yml" p=""
  if [[ -f "$yml" ]]; then
    p="$(sed -n 's/^[[:space:]]*port:[[:space:]]*\([0-9]\{2,5\}\).*/\1/p' "$yml" | head -1 | tr -d '[:space:]\r')"
  fi
  echo "${p:-8080}"
}
PORT="$(detect_port)"

restart_service() {
  if command -v systemctl >/dev/null 2>&1 && [[ -f /etc/systemd/system/lis-full.service ]]; then
    systemctl restart lis-full
  else
    if [[ -x "${INSTALL_DIR}/bin/stop.sh" ]]; then
      bash "${INSTALL_DIR}/bin/stop.sh" >/dev/null 2>&1 || true
    fi
    if [[ -x "${INSTALL_DIR}/bin/start.sh" ]]; then
      bash "${INSTALL_DIR}/bin/start.sh" >/dev/null 2>&1 || true
    fi
  fi
}

wait_healthy() {
  if ! command -v curl >/dev/null 2>&1; then
    warn "未安装 curl，跳过健康检查"
    return 0
  fi
  local i code
  for ((i = 1; i <= 60; i++)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/index.html" 2>/dev/null || true)"
    if [[ "$code" == "200" ]]; then return 0; fi
    sleep 2
  done
  return 1
}

BACKUP_DIR="${INSTALL_DIR}/backup"
LOG_FILE="${INSTALL_DIR}/logs/lis-backend.log"
mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d%H%M%S)"

echo "======================================================"
echo "  LIS 临床检验系统 - 程序升级"
echo "======================================================"
info "安装目录：${INSTALL_DIR}"

# ---------- 回滚模式 ----------
if [[ "$DO_ROLLBACK" == "yes" ]]; then
  LAST="$(ls -1t "${BACKUP_DIR}"/lis-monolith.jar.20* 2>/dev/null | head -1 || true)"
  if [[ -z "$LAST" ]]; then
    err "没有可用的备份：${BACKUP_DIR}"
    exit 1
  fi
  cp -f "${INSTALL_DIR}/app/lis-monolith.jar" "${BACKUP_DIR}/lis-monolith.jar.pre-rollback.${TS}"
  cp -f "$LAST" "${INSTALL_DIR}/app/lis-monolith.jar"
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
  if [[ ! -f "$PKG_SRC" ]]; then
    err "升级包不存在：${PKG_SRC}"
    exit 1
  fi
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  info "解压升级包：$(basename "$PKG_SRC")"
  tar -xzf "$PKG_SRC" -C "$TMP_DIR"
  JAR_SRC="$(find "$TMP_DIR" -maxdepth 3 -type f -name 'lis-monolith*.jar' -print -quit)"
fi

if [[ -z "$JAR_SRC" ]]; then
  for _c in "${SELF_DIR}/app/lis-monolith.jar" "${SELF_DIR}/lis-monolith.jar" "./app/lis-monolith.jar" "./lis-monolith.jar"; do
    if [[ -f "$_c" ]]; then JAR_SRC="$_c"; break; fi
  done
fi

if [[ -z "$JAR_SRC" || ! -f "$JAR_SRC" ]]; then
  err "未找到新程序包。请把 lis-monolith.jar 放到本脚本同目录，或用 --jar 指定"
  exit 1
fi

ABS_SRC="$(cd "$(dirname "$JAR_SRC")" && pwd)/$(basename "$JAR_SRC")"
if [[ "$ABS_SRC" == "${INSTALL_DIR}/app/lis-monolith.jar" ]]; then
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
  echo "  重启服务：${DO_RESTART}"
  echo "  新版本号：${APP_VERSION:-未提供}"
  read -rp "确认升级？[y/N]: " _in
  if [[ "${_in,,}" != "y" ]]; then
    info "已取消"
    exit 0
  fi
fi

# ---------- 备份并替换 ----------
cp -f "${INSTALL_DIR}/app/lis-monolith.jar" "${BACKUP_DIR}/lis-monolith.jar.${TS}"
info "旧程序已备份：backup/lis-monolith.jar.${TS}"

cp -f "$JAR_SRC" "${INSTALL_DIR}/app/lis-monolith.jar.new"
mv -f "${INSTALL_DIR}/app/lis-monolith.jar.new" "${INSTALL_DIR}/app/lis-monolith.jar"
info "新程序已替换：$(basename "$JAR_SRC")"

if [[ -n "$VERSION_SRC" ]]; then
  cp -f "$VERSION_SRC" "${INSTALL_DIR}/VERSION"
  info "版本号已更新为：${APP_VERSION}"
fi

# ---------- 重启并校验 ----------
if [[ "$DO_RESTART" != "yes" ]]; then
  info "已跳过重启（--no-restart），请手动执行：systemctl restart lis-full"
  exit 0
fi

info "重启服务 ..."
restart_service

if [[ "$DO_HEALTH" == "yes" ]]; then
  if wait_healthy; then
    info "服务已就绪：http://127.0.0.1:${PORT}/"
  else
    err "升级后服务未在预期时间内就绪，自动回滚 ..."
    cp -f "${INSTALL_DIR}/app/lis-monolith.jar" "${BACKUP_DIR}/lis-monolith.jar.failed.${TS}"
    cp -f "${BACKUP_DIR}/lis-monolith.jar.${TS}" "${INSTALL_DIR}/app/lis-monolith.jar"
    restart_service
    if wait_healthy; then
      err "已回滚到升级前的程序包，请检查日志：${LOG_FILE}"
    else
      err "回滚后服务仍未就绪，请查看日志：${LOG_FILE}"
    fi
    exit 1
  fi
fi

info "升级完成。数据库结构与数据未改动。"
