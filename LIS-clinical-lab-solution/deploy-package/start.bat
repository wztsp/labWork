@echo off
chcp 65001 >nul
REM ========== LIS 一体化部署包 - 一键启动 (Windows) ==========
REM 前置条件: JDK 17+, PostgreSQL 14+ (已启动)
REM PostgreSQL 初始化: init-db.ps1  首次部署后只需执行一次

setlocal
set BASE_DIR=%~dp0
set JAR=%BASE_DIR%backend\lis-monolith-fresh.jar
set APP_YML=%BASE_DIR%backend\application.yml

echo ================================================
echo   LIS 实验室信息系统 - 一体化部署包
echo ================================================
echo.

REM ---- 检查 JAR ----
if not exist "%JAR%" (
    echo [错误] 找不到 %JAR%
    echo 请确认后端 JAR 文件存在
    pause
    exit /b 1
)

REM ---- 检查 Java ----
where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java, 请先安装 JDK 17 并配置 PATH
    pause
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
echo [环境] Java %JAVA_VER%

REM ---- 检查 PostgreSQL ----
where psql >nul 2>&1
if errorlevel 1 (
    echo [提示] 未检测到 psql 命令行工具
    echo        请确保 PostgreSQL 已启动, 数据库 lis_db 已创建
)

REM ---- 启动 ----
echo.
echo [启动] LIS 一体化服务...
echo.
echo   后端 API:    http://localhost:8080/api/
echo   前端界面:    http://localhost:8080/
echo   配置文件:    %APP_YML%
echo.
echo 按 Ctrl+C 停止服务
echo ================================================

cd /d "%BASE_DIR%backend"
java -jar lis-monolith-fresh.jar --spring.config.location=application.yml

endlocal
