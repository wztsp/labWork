#!/usr/bin/env bash
# =============================================================================
# LIS 临床检验系统 - 后台一键启动（不走 systemd 时使用）
# 用法：bash bin/start.sh [-f|--foreground]
# =============================================================================
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="${APP_CONF:-${INSTALL_DIR}/config/application.yml}"
JAR="${INSTALL_DIR}/app/lis-monolith.jar"
PID_FILE="${INSTALL_DIR}/lis-full.pid"
LOG_DIR="${INSTALL_DIR}/logs"
JDK_DIR="${INSTALL_DIR}/jdk"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

FOREGROUND="no"
case "${1:-}" in
  -f|--foreground) FOREGROUND="yes" ;;
esac

mkdir -p "$LOG_DIR"

# ---------- JDK ----------
if [[ ! -x "${JDK_DIR}/bin/java" ]]; then
  JDK_PKG="$(find "${INSTALL_DIR}/runtime" -maxdepth 1 -type f -name 'openjdk-*-linux-x64*.tar.gz' -print -quit 2>/dev/null || true)"
  if [[ -n "$JDK_PKG" ]]; then
    info "解压内置 JDK：$(basename "$JDK_PKG")"
    tar -xzf "$JDK_PKG" -C "$INSTALL_DIR"
    JDK_EXTRACTED="$(find "$INSTALL_DIR" -maxdepth 1 -mindepth 1 -type d -name 'jdk-[0-9]*' -print -quit)"
    if [[ -n "$JDK_EXTRACTED" ]]; then
      rm -rf "$JDK_DIR"
      mv "$JDK_EXTRACTED" "$JDK_DIR"
    fi
  fi
fi
if [[ -x "${JDK_DIR}/bin/java" ]]; then
  JAVA_BIN="${JDK_DIR}/bin/java"
else
  JAVA_BIN="$(command -v java 2>/dev/null || true)"
  [[ -n "$JAVA_BIN" ]] || { err "未找到 JDK：请先执行 bin/install.sh 或安装 Java 17+"; exit 1; }
fi

# ---------- 端口 ----------
PORT="$(grep -E '^[[:space:]]*port:' "$CONF" 2>/dev/null | head -1 \
        | sed -E 's/^[[:space:]]*port:[[:space:]]*//; s/[[:space:]]+$//' | tr -d '"\r' || true)"
PORT="${PORT:-8080}"

# ---------- 已在运行？ ----------
if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$OLD_PID" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    warn "服务已在运行（PID ${OLD_PID}），如需重启请先执行 bin/stop.sh"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

JAVA_CMD=( "$JAVA_BIN" -Xms512m -Xmx2g -Dfile.encoding=UTF-8 -jar "$JAR"
           --spring.config.additional-location="file:${CONF}" )

cd "$INSTALL_DIR"

if [[ "$FOREGROUND" == "yes" ]]; then
  info "前台启动（Ctrl+C 退出）..."
  exec "${JAVA_CMD[@]}"
fi

info "后台启动 ..."
nohup "${JAVA_CMD[@]}" > "${LOG_DIR}/console.log" 2>&1 &
echo $! > "$PID_FILE"
info "进程已启动（PID $(cat "$PID_FILE")），日志：${LOG_DIR}/console.log"

if ! command -v curl >/dev/null 2>&1; then
  warn "未找到 curl，跳过健康检查"
  exit 0
fi

info "等待服务就绪 ..."
for _ in $(seq 1 60); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/index.html" 2>/dev/null || true)"
  if [[ "$CODE" == "200" ]]; then
    info "启动成功：http://127.0.0.1:${PORT}/"
    exit 0
  fi
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    err "进程已退出，日志末尾："
    tail -n 40 "${LOG_DIR}/console.log" >&2 || true
    exit 1
  fi
  sleep 2
done

err "服务未在预期时间内就绪，请查看：tail -n 100 ${LOG_DIR}/console.log"
exit 1
