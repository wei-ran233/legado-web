@echo off
chcp 65001 >nul
setlocal
title Legado-Cache-Cleaner

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

:: 参数支持：
:: clean-cache.bat [DATA_DIR] [DAYS_TO_KEEP] [INTERVAL_HOURS] [--daemon]
set "DATA_DIR=%ROOT_DIR%\data"
if not "%~1"=="" set "DATA_DIR=%~1"

set "EXPIRE_DAYS=7"
if not "%~2"=="" set "EXPIRE_DAYS=%~2"

set "INTERVAL_HOURS=24"
if not "%~3"=="" set "INTERVAL_HOURS=%~3"

set "DAEMON_FLAG="
if /i "%~4"=="--daemon" set "DAEMON_FLAG=-Daemon"
if /i "%~3"=="--daemon" (
    set "DAEMON_FLAG=-Daemon"
    set "INTERVAL_HOURS=24"
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%\clean-cache.ps1" -DataDir "%DATA_DIR%" -ExpireDays %EXPIRE_DAYS% -IntervalHours %INTERVAL_HOURS% %DAEMON_FLAG%
