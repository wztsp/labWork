@echo off
chcp 65001 >nul 2>&1
title LIS 实验室信息系统 - 停止服务

echo.
echo ============================================
echo    LIS 实验室信息系统 - 一键停止
echo    停止: 前端 :3000 / 后端 :8080 / TCP :9100
echo ============================================
echo.

cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "stop.ps1"

echo.
pause
