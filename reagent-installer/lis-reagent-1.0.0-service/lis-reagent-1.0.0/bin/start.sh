#!/usr/bin/env bash
# =============================================================================
# 试剂管理系统（独立版）- 一键启动
#
# 用法：
#   bash bin/start.sh          # 后台启动 + 健康检查
#   bash bin/start.sh -f       # 前台运行（调试，Ctrl+C 退出）
#   bash bin/stop.sh           # 停止
#
# 说明：包内自带 JDK，未解压时自动解压到 <安装目录>/jdk。
# =============================================================================
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${INSTALL_DIR}/app/lis-reagent.jar"
CONF="${APP_CONF:-${INSTALL_DIR}/config/application.yml}"
PID_FILE="${INSTALL_DIR}/lis-reagent.pid"
LOG_DIR="${INSTALL_DIR}/logs"
FOREGROUND="no"

[[ "${1:-}" == "-f" || "${1:-}" == "--foreground" ]] && FOREGROUND="yes"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

[[ -f "$JAR" ]]  || { err "找不到程序：${JAR}"; exit 1; }
[[ -f "$CONF" ]] || { err "找不到配置：${CONF}"; exit 1; }

# ---------- JDK ----------
JAVA_BIN="${INSTALL_DIR}/jdk/bin/java"
if [[ ! -x "$JAVA_BIN" ]]; then
  JDK_PKG="$(find "${INSTALL_DIR}/runtime" -maxdepth 1 -type f -name 'openjdk-*-linux-x64*.tar.gz' -print -quit 2>/dev/null || true)"
  if [[ -n "$JDK_PKG" ]]; then
    info "解压内置 JDK：$(basename "$JDK_PKG")"
    tar -xzf "$JDK_PKG" -C "$INSTALL_DIR"
    JDK_EXTRACTED="$(find "$INSTALL_DIR" -maxdepth 1 -mindepth 1 -type d -name 'jdk-1*' -print -quit)"
    if [[ -n "$JDK_EXTRACTED" ]]; then
      rm -rf "${INSTALL_DIR}/jdk"
      mv "$JDK_EXTRACTED" "${INSTALL_DIR}/jdk"
    fi
  fi
fi
if [[ ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java 2>/dev/null || true)"
  [[ -n "$JAVA_BIN" ]] || { err "未找到 JDK：包内 runtime 无 JDK 且本机没有 java"; exit 1; }
  warn "内置 JDK 缺失，改用本机 Java：$("$JAVA_BIN" -version 2>&1 | head -1)"
fi

# ---------- 端口（取自配置） ----------
SERVER_PORT="$(grep -E '^[[:space:]]*port:' "$CONF" 2>/dev/null | head -1 \
                | sed -E 's/^[[:space:]]*port:[[:space:]]*//' | tr -d '" \r' || true)"
SERVER_PORT="${SERVER_PORT:-8092}"

mkdir -p "$LOG_DIR"
cd "$INSTALL_DIR"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  info "服务已在运行，PID=$(cat "$PID_FILE")，端口 ${SERVER_PORT}"
  exit 0
fi

JAVA_CMD=( "$JAVA_BIN" -Xms256m -Xmx1g -Dfile.encoding=UTF-8 -jar "$JAR"
           --spring.config.additional-location="file:${CONF}" )

if [[ "$FOREGROUND" == "yes" ]]; then
  info "前台启动（Ctrl+C 退出）"
  exec "${JAVA_CMD[@]}"
fi

nohup "${JAVA_CMD[@]}" > "${LOG_DIR}/console.log" 2>&1 &
echo $! > "$PID_FILE"
info "启动中，PID=$(cat "$PID_FILE")，日志：${LOG_DIR}/console.log"

if ! command -v curl >/dev/null 2>&1; then
  info "未找到 curl，跳过健康检查"
  exit 0
fi

for _ in $(seq 1 60); do
  if curl -s "http://127.0.0.1:${SERVER_PORT}/api/reagent/dashboard/overview" 2>/dev/null \
       | grep -q '"success":true'; then
    IP_ADDR="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
    info "启动成功：http://${IP_ADDR:-127.0.0.1}:${SERVER_PORT}/"
    exit 0
  fi
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    err "进程已退出，最后 40 行日志："
    tail -n 40 "${LOG_DIR}/console.log" || true
    rm -f "$PID_FILE"
    exit 1
  fi
  sleep 2
done

warn "健康检查超时，请查看日志：tail -f ${LOG_DIR}/console.log"
exit 1
