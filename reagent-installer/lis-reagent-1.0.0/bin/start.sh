#!/usr/bin/env bash
# 前台/后台启动（不使用 systemd 时）
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${INSTALL_DIR}/app/lis-reagent.jar"
PID_FILE="${INSTALL_DIR}/lis-reagent.pid"
LOG_DIR="${INSTALL_DIR}/logs"
CONF="${INSTALL_DIR}/config/application.yml"

[[ -f "$JAR" ]] || { echo "找不到 $JAR"; exit 1; }
mkdir -p "$LOG_DIR"

JAVA_BIN="${INSTALL_DIR}/jdk/bin/java"
[[ -x "$JAVA_BIN" ]] || JAVA_BIN="java"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "服务已在运行，PID=$(cat "$PID_FILE")"
  exit 0
fi

nohup "$JAVA_BIN" -Xms256m -Xmx1g \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR" \
  --spring.config.additional-location="file:${CONF}" \
  > "${LOG_DIR}/console.log" 2>&1 &

echo $! > "$PID_FILE"
echo "启动中，PID=$(cat "$PID_FILE")"
echo "日志：tail -f ${LOG_DIR}/console.log"
