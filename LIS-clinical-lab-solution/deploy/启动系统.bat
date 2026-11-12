@echo off
chcp 65001 >nul 2>&1
title LIS 实验室信息系统 - 一键启动 (Docker Compose)

echo.
echo ============================================
echo    LIS 实验室信息系统 - 一键启动
echo    Docker Compose 部署
echo ============================================
echo.

cd /d "%~dp0"

echo [1/3] 检查 Docker 是否可用...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [XX] 未检测到 Docker, 请先安装 Docker Desktop 并启动。
    echo      下载地址: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)
echo [OK] Docker 可用

echo [2/3] 检查前端构建产物 dist...
if not exist "..\lis-frontend\dist\index.html" (
    echo [!!] 未找到前端构建产物, 请先运行 "构建前端.bat" 生成 dist。
    echo      也可以手动执行: cd lis-frontend ^&^& npm install ^&^& npm run build
    pause
    exit /b 1
)
echo [OK] 前端产物存在

echo [3/3] 构建并启动全部服务 (首次构建后端镜像较慢, 请耐心等待)...
echo.
docker compose up -d --build

echo.
echo ============================================
echo    启动完成, 服务地址如下:
echo    前端        : http://localhost:3000
echo    后端 HTTP   : http://localhost:8080
echo    API 文档    : http://localhost:8080/doc.html
echo    RabbitMQ 台 : http://localhost:15672  (lis / lis_dev_2026)
echo    默认账号    : admin / 123456
echo ============================================
echo.
echo 查看状态: docker compose ps
echo 查看日志: docker compose logs -f backend
echo 停止系统: 停止系统.bat
echo.
pause