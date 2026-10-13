@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Legado 多用户书城服务 - 停止服务

set "PORT=9000"
if not "%~1"=="" set "PORT=%~1"

echo ============================================================
echo   Legado 多用户书城服务 - 一键停止器
echo ============================================================
echo.

set "PID="
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /C:":%PORT% " ^| findstr "LISTENING"') do (
    set "PID=%%a"
)

if not defined PID goto :not_found

echo 正在停止占用端口 %PORT% 的服务 [PID: %PID%] ...
taskkill /F /PID %PID% >nul 2>&1
if %errorlevel% equ 0 (
    echo [成功] 服务已成功停止 [PID: %PID%]。
) else (
    echo [警告] 尝试终止进程失败，可能需要以管理员身份运行。
)

:: 停止后台定时缓存清理守护任务
set "DATA_DIR=%~dp0data"
if not "%~2"=="" set "DATA_DIR=%~2"

if exist "%DATA_DIR%\cleaner.pid" (
    for /f "usebackq tokens=*" %%p in ("%DATA_DIR%\cleaner.pid") do (
        echo 正在停止后台定时缓存清理任务 [PID: %%p] ...
        taskkill /F /PID %%p >nul 2>&1
    )
    del "%DATA_DIR%\cleaner.pid" >nul 2>&1
)
for /f "tokens=2" %%i in ('tasklist /nh /fi "windowtitle eq Legado-Cache-Cleaner*" 2^>nul') do (
    taskkill /F /PID %%i >nul 2>&1
)

goto :finish

:not_found
echo [提示] 端口 %PORT% 当前未运行任何服务。

:finish
echo.
ping 127.0.0.1 -n 3 >nul
