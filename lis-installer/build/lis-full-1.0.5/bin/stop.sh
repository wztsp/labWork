#!/usr/bin/env bash
# =============================================================================
# LIS 临床检验系统 - 停止后台进程
# 用法：bash bin/stop.sh
# =============================================================================
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="${INSTALL_DIR}/lis-full.pid"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }

if [[ ! -f "$PID_FILE" ]]; then
  warn "未找到 PID 文件，服务可能未通过 start.sh 启动"
  exit 0
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ -z "$PID" ]] || ! kill -0 "$PID" 2>/dev/null; then
  warn "进程不存在，清理 PID 文件"
  rm -f "$PID_FILE"
  exit 0
fi

info "停止进程 ${PID} ..."
kill "$PID" 2>/dev/null || true
for _ in $(seq 1 30); do
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$PID" 2>/dev/null; then
  warn "优雅停止超时，强制结束"
  kill -9 "$PID" 2>/dev/null || true
fi
rm -f "$PID_FILE"
info "已停止"
