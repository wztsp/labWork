@echo off
chcp 65001 >nul 2>&1
title LIS 实验室信息系统 - 构建前端

echo.
echo ============================================
echo    LIS 实验室信息系统 - 构建前端静态资源
echo    生成 dist 供 Docker 前端容器使用
echo ============================================
echo.

cd /d "%~dp0\..\lis-frontend"

echo [1/2] 安装依赖...
call npm install
if errorlevel 1 (
    echo [XX] npm install 失败
    pause
    exit /b 1
)

echo [2/2] 构建生产包...
call npm run build
if errorlevel 1 (
    echo [XX] npm run build 失败
    pause
    exit /b 1
)

echo.
echo [OK] 前端构建完成, 产物位于 lis-frontend\dist
echo      现在可以运行 "启动系统.bat" 部署系统。
echo.
pause