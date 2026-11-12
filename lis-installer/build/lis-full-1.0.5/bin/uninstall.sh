#!/usr/bin/env bash
# =============================================================================
# LIS 临床检验系统 - 卸载
#
# 用法：
#   sudo bash bin/uninstall.sh            # 停止服务并移除 systemd，保留程序与数据
#   sudo bash bin/uninstall.sh --purge    # 同时删除安装目录（数据库/Redis/RabbitMQ 不删）
# =============================================================================
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PURGE="no"
[[ "${1:-}" == "--purge" ]] && PURGE="yes"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行：sudo bash bin/uninstall.sh"; exit 1; }

if [[ -f /etc/systemd/system/lis-full.service ]] && command -v systemctl >/dev/null 2>&1; then
  info "停止并禁用服务 lis-full ..."
  systemctl stop lis-full >/dev/null 2>&1 || true
  systemctl disable lis-full >/dev/null 2>&1 || true
  rm -f /etc/systemd/system/lis-full.service
  systemctl daemon-reload
else
  bash "${INSTALL_DIR}/bin/stop.sh" >/dev/null 2>&1 || true
fi

if [[ "$PURGE" == "yes" ]]; then
  warn "删除安装目录：${INSTALL_DIR}"
  cd /
  rm -rf "$INSTALL_DIR"
  info "已删除安装目录"
else
  info "已停止服务并移除 systemd 单元，安装目录保留：${INSTALL_DIR}"
fi

echo ""
echo "提示：数据库（lis_db）、Redis、RabbitMQ 数据未被删除。"
echo "如需彻底清理，请自行执行："
echo "  sudo -u postgres psql -c 'DROP DATABASE ${DB_NAME:-lis_db};'"
echo "  sudo systemctl stop redis rabbitmq-server postgresql"
