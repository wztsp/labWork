@echo off
chcp 65001 >nul 2>&1
title LIS 实验室信息系统 - 停止 (Docker Compose)

echo.
echo ============================================
echo    LIS 实验室信息系统 - 停止服务
echo    停止所有容器 (保留数据库数据卷)
echo ============================================
echo.

cd /d "%~dp0"
docker compose down

echo.
echo [OK] 系统已停止。容器已移除, PostgreSQL 数据卷保留。
echo      如需彻底删除数据库数据, 执行: docker compose down -v
echo.
pause