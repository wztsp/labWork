#!/usr/bin/env bash
# =============================================================================
# LIS 中联新标准服务（检验）一键升级  S1001 ~ S4020
#
# 完成三件事：
#   1) 把 db/02-his-standard.sql 与 bin/apply-his-standard.sh 复制到安装目录
#   2) 执行 SQL：创建 S4009/S3103 回写进度表 + 写入 his.zl.* 参数（幂等）
#   3) 用 update.sh 替换 app/lis-monolith.jar 并重启（自动备份/健康检查/失败回滚）
#
# 用法：
#   sudo bash apply-his-standard.sh                 # 自动探测安装目录
#   sudo bash apply-his-standard.sh --dir /opt/lis-full
#   sudo bash apply-his-standard.sh --no-restart    # 只执行 SQL，不换程序不重启
#   sudo bash apply-his-standard.sh --no-jar        # 只执行 SQL，程序包已单独升级过
#   -h, --help
# =============================================================================
set -euo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DIR_ARG=""
DO_RESTART="yes"
DO_JAR="yes"
ASSUME_YES="no"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)         DIR_ARG="$2"; shift 2 ;;
    --no-restart)  DO_RESTART="no"; shift ;;
    --no-jar)      DO_JAR="no"; shift ;;
    --yes|-y)      ASSUME_YES="yes"; shift ;;
    -h|--help)     usage ;;
    *) err "未知参数: $1"; usage ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || { err "请使用 root 或 sudo 执行：sudo bash $0"; exit 1; }

resolve_dir() {
  if [[ -n "$DIR_ARG" ]]; then echo "$DIR_ARG"; return; fi
  local unit=/etc/systemd/system/lis-full.service d c
  if [[ -f "$unit" ]]; then
    d="$(sed -n 's/^ExecStart=.*-jar[[:space:]]\+\([^[:space:]]*\)\/app\/lis-monolith.jar.*/\1/p' "$unit" | head -1)"
    if [[ -n "$d" && -f "${d}/app/lis-monolith.jar" ]]; then echo "$d"; return; fi
  fi
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

JAR_SRC="${SELF_DIR}/app/lis-monolith.jar"
SQL_SRC="${SELF_DIR}/db/02-his-standard.sql"
TOOL_SRC="${SELF_DIR}/bin/apply-his-standard.sh"
for f in "$SQL_SRC" "$TOOL_SRC"; do
  [[ -f "$f" ]] || { err "升级包缺少文件：$f"; exit 1; }
done
if [[ "$DO_JAR" == "yes" && ! -f "$JAR_SRC" ]]; then
  err "升级包缺少程序包：${JAR_SRC}（如程序已单独升级，可加 --no-jar）"
  exit 1
fi

echo "======================================================"
echo "  LIS 中联新标准服务（S1001 ~ S4020）一键升级"
echo "======================================================"
info "安装目录：${INSTALL_DIR}"
info "执行 SQL：是"
info "替换程序：${DO_JAR}"
info "重启服务：${DO_RESTART}"

if [[ "$ASSUME_YES" != "yes" ]]; then
  read -rp "确认升级？[y/N]: " _in
  [[ "${_in,,}" == "y" ]] || { info "已取消"; exit 0; }
fi

# ---------- 1. 分发脚本 ----------
mkdir -p "${INSTALL_DIR}/db" "${INSTALL_DIR}/bin"
cp -f "$SQL_SRC" "${INSTALL_DIR}/db/02-his-standard.sql"
cp -f "$TOOL_SRC" "${INSTALL_DIR}/bin/apply-his-standard.sh"
chmod +x "${INSTALL_DIR}/bin/apply-his-standard.sh"
info "已复制 db/02-his-standard.sql 与 bin/apply-his-standard.sh"

# ---------- 2. 执行 SQL ----------
bash "${INSTALL_DIR}/bin/apply-his-standard.sh" --dir "$INSTALL_DIR" --yes --no-restart

# ---------- 3. 替换程序并重启 ----------
if [[ "$DO_RESTART" != "yes" ]]; then
  info "已跳过替换程序与重启（--no-restart）"
  exit 0
fi
if [[ "$DO_JAR" != "yes" ]]; then
  info "已跳过替换程序（--no-jar），请手动重启服务使新参数生效"
  exit 0
fi

if [[ -x "${INSTALL_DIR}/bin/update.sh" || -f "${INSTALL_DIR}/bin/update.sh" ]]; then
  bash "${INSTALL_DIR}/bin/update.sh" --dir "$INSTALL_DIR" --jar "$JAR_SRC" --yes
else
  err "未找到 ${INSTALL_DIR}/bin/update.sh，请手工替换 app/lis-monolith.jar 后重启服务"
  exit 1
fi

info "升级完成。请在 LIS【系统设置 -> 参数配置】填写 his.zl.url 后启用 his.zl.enabled"
