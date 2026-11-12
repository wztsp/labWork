#!/usr/bin/env bash
# =============================================================================
# LIS 中联新标准服务（检验）数据库脚本执行  S1001 ~ S4020
#
# 作用：在已安装的 LIS 上执行 db/02-his-standard.sql
#       —— 创建 S4009/S3103 回写进度表，写入 his.zl.* 参数（幂等，可重复执行）
#
# 用法：
#   sudo bash apply-his-standard.sh                       # 自动探测安装目录与数据库
#   sudo bash apply-his-standard.sh --dir /opt/lis-full
#   sudo bash apply-his-standard.sh --db-pass 'xxx'
#   sudo bash apply-his-standard.sh --no-restart          # 只执行 SQL，不重启服务
#   sudo bash apply-his-standard.sh --sql /root/02-his-standard.sql
#
# 参数：
#   --dir <目录>       安装目录，默认自动探测
#   --sql <文件>       SQL 脚本，默认 <安装目录>/db/02-his-standard.sql
#   --db-host <主机>   默认 127.0.0.1
#   --db-port <端口>   默认 5432
#   --db-name <库名>   默认 lis_db
#   --db-user <用户>   默认 lis_user
#   --db-pass <密码>   默认读取 config/application.yml
#   --no-restart       执行后不重启服务
#   --yes              跳过交互确认
#   -h, --help         显示本帮助
# =============================================================================
set -euo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DIR_ARG=""
SQL_FILE=""
DB_HOST="127.0.0.1"
DB_PORT="5432"
DB_NAME="lis_db"
DB_USER="lis_user"
DB_PASS=""
DO_RESTART="yes"
ASSUME_YES="no"

info() { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
usage() { sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)         DIR_ARG="$2"; shift 2 ;;
    --sql)         SQL_FILE="$2"; shift 2 ;;
    --db-host)     DB_HOST="$2"; shift 2 ;;
    --db-port)     DB_PORT="$2"; shift 2 ;;
    --db-name)     DB_NAME="$2"; shift 2 ;;
    --db-user)     DB_USER="$2"; shift 2 ;;
    --db-pass)     DB_PASS="$2"; shift 2 ;;
    --no-restart)  DO_RESTART="no"; shift ;;
    --yes|-y)      ASSUME_YES="yes"; shift ;;
    -h|--help)     usage ;;
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
[[ -n "$SQL_FILE" ]] || SQL_FILE="${INSTALL_DIR}/db/02-his-standard.sql"
if [[ ! -f "$SQL_FILE" ]]; then
  err "SQL 脚本不存在：${SQL_FILE}（可用 --sql 指定）"
  exit 1
fi

# ---------- 读取数据库连接（config/application.yml） ----------
CONF="${INSTALL_DIR}/config/application.yml"
if [[ -f "$CONF" ]]; then
  url="$(sed -n 's/^[[:space:]]*url:[[:space:]]*jdbc:postgresql:\/\/\([^\/]*\)\/\([^?\r]*\).*/\1 \2/p' "$CONF" | head -1)"
  if [[ -n "$url" ]]; then
    hostport="${url%% *}"; dbname="${url##* }"
    DB_HOST="${hostport%%:*}"
    [[ "$hostport" == *:* ]] && DB_PORT="${hostport##*:}"
    DB_NAME="${dbname//[[:space:]]/}"
  fi
  _u="$(sed -n 's/^[[:space:]]*username:[[:space:]]*\([^[:space:]]*\).*/\1/p' "$CONF" | head -1 | tr -d '\r')"
  [[ -n "$_u" ]] && DB_USER="$_u"
  if [[ -z "$DB_PASS" ]]; then
    _p="$(sed -n 's/^[[:space:]]*password:[[:space:]]*//p' "$CONF" | head -1 | tr -d '\r')"
    # 支持 ${ENV:默认值} 写法
    if [[ "$_p" == *'${'*'}'* ]]; then
      _inner="${_p#*\${}"; _inner="${_inner%%\}*}"
      _p="${_inner#*:}"
    fi
    DB_PASS="$_p"
  fi
fi

# ---------- 定位 psql ----------
PSQL_BIN="$(command -v psql 2>/dev/null || true)"
if [[ -z "$PSQL_BIN" ]]; then
  for c in /usr/bin/psql /usr/local/bin/psql /usr/pgsql-*/bin/psql /usr/lib/postgresql/*/bin/psql; do
    [[ -x "$c" ]] && { PSQL_BIN="$c"; break; }
  done
fi
[[ -n "$PSQL_BIN" ]] || { err "未找到 psql 客户端，请安装 postgresql 客户端后重试"; exit 1; }

echo "======================================================"
echo "  LIS 中联新标准服务（S1001 ~ S4020）数据库脚本"
echo "======================================================"
info "安装目录：${INSTALL_DIR}"
info "SQL 脚本：${SQL_FILE}"
info "数据库：${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

if [[ "$ASSUME_YES" != "yes" ]]; then
  read -rp "确认执行？[y/N]: " _in
  [[ "${_in,,}" == "y" ]] || { info "已取消"; exit 0; }
fi

# ---------- 执行 SQL ----------
info "执行 SQL ..."
if ! PGPASSWORD="$DB_PASS" "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
     -v ON_ERROR_STOP=1 -f "$SQL_FILE"; then
  err "SQL 执行失败，请检查数据库账号权限（需为表属主或有建表/授权权限）"
  exit 1
fi
info "SQL 执行完成"

# ---------- 校验 ----------
TB_COUNT="$(PGPASSWORD="$DB_PASS" "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' \
        AND table_name IN ('lis_his_sample_status_push','lis_his_critical_push')" 2>/dev/null | tr -d '[:space:]')"
if [[ "$TB_COUNT" == "2" ]]; then
  info "校验通过：his 进度表已就绪"
else
  err "校验未通过：只找到 ${TB_COUNT:-0}/2 张表，请查看上面的 SQL 输出"
  exit 1
fi

# ---------- 重启服务（参数即时生效） ----------
if [[ "$DO_RESTART" != "yes" ]]; then
  info "已跳过重启（--no-restart），请手动执行：systemctl restart lis-full"
  exit 0
fi

info "重启服务 ..."
if command -v systemctl >/dev/null 2>&1 && [[ -f /etc/systemd/system/lis-full.service ]]; then
  systemctl restart lis-full
elif [[ -x "${INSTALL_DIR}/bin/stop.sh" && -x "${INSTALL_DIR}/bin/start.sh" ]]; then
  bash "${INSTALL_DIR}/bin/stop.sh" >/dev/null 2>&1 || true
  bash "${INSTALL_DIR}/bin/start.sh" >/dev/null 2>&1 || true
else
  warn "未找到 systemd 服务或启停脚本，请手动重启后端"
  exit 0
fi

PORT="$(sed -n 's/^[[:space:]]*port:[[:space:]]*\([0-9]\{2,5\}\).*/\1/p' "$CONF" 2>/dev/null | head -1 | tr -d '[:space:]\r')"
PORT="${PORT:-8080}"
if command -v curl >/dev/null 2>&1; then
  for _ in $(seq 1 60); do
    [[ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/index.html" 2>/dev/null || true)" == "200" ]] && break
    sleep 2
  done
fi

info "完成。请在 LIS【系统设置 -> 参数配置】填写 his.zl.url 后启用 his.zl.enabled"
