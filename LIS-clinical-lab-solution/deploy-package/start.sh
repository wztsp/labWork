#!/bin/bash
# ========== LIS 一体化部署包 - 一键启动 (Linux) ==========
# 前置条件: JDK 17+, PostgreSQL 14+ (已启动)
# DB 初始化: psql -h <host> -U <user> -d lis_db -f db/init.sql

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/backend/lis-monolith-fresh.jar"

echo "================================================"
echo "  LIS 一体化服务启动中..."
echo "================================================"
echo ""

if [ ! -f "$JAR" ]; then
    echo "[错误] 找不到 $JAR"
    exit 1
fi

if ! command -v java &>/dev/null; then
    echo "[错误] 未检测到 java, 请先安装 JDK 17"
    exit 1
fi

echo "  Java: $(java -version 2>&1 | head -1)"
echo "  启动后访问: http://<服务器IP>:8080/"
echo "  按 Ctrl+C 停止"
echo "================================================"
echo ""

cd "$SCRIPT_DIR/backend"
exec java -jar lis-monolith-fresh.jar --spring.config.location=application.yml
