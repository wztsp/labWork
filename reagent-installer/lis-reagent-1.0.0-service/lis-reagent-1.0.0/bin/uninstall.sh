#!/usr/bin/env bash
# 卸载：停止并移除 systemd 服务（不删除数据库数据）
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

[[ "$(id -u)" -eq 0 ]] || { echo "请使用 sudo 执行"; exit 1; }

read -rp "确认卸载？数据库数据不会被删除 [y/N]: " ans
[[ "${ans,,}" == "y" ]] || { echo "已取消"; exit 1; }

if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files | grep -q '^lis-reagent.service'; then
  systemctl stop lis-reagent || true
  systemctl disable lis-reagent || true
  rm -f /etc/systemd/system/lis-reagent.service
  systemctl daemon-reload
fi

"${INSTALL_DIR}/bin/stop.sh" || true
cd /
rm -rf "$INSTALL_DIR"
echo "已卸载。如需删除数据库，请手工执行：DROP DATABASE lis_reagent;"
