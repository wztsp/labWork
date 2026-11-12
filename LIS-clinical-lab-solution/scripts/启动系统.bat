@echo off
chcp 65001 >nul 2>&1
title LIS 实验室信息系统 - 一键启动 (前端 + 后端 + TCP)

echo.
echo ============================================
echo    LIS 实验室信息系统 - 一键启动
echo    前端 Vite :3000
echo    后端 Spring Boot :8080
echo    TCP 仪器通信 :9100
echo ============================================
echo.

cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "start.ps1"

echo.
pause
