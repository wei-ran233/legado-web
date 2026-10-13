@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Legado 多用户书城服务

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

:: 默认端口 9000（支持通过第一个参数自定义，例如: start-server.bat 8080）
set "PORT=9000"
if not "%~1"=="" set "PORT=%~1"

:: 数据目录（支持通过第二个参数自定义）
set "DATA_DIR=%ROOT_DIR%\data"
if not "%~2"=="" set "DATA_DIR=%~2"

:: 缓存定时清理配置（支持参数3与参数4自定义）
:: CACHE_EXPIRE_DAYS: 历史缓存保留天数（默认保留 7 天内有效缓存）
set "CACHE_EXPIRE_DAYS=7"
if not "%~3"=="" set "CACHE_EXPIRE_DAYS=%~3"

:: CACHE_CLEAN_INTERVAL_HOURS: 后台自动清理执行周期（默认每 24 小时自动巡检一次）
set "CACHE_CLEAN_INTERVAL_HOURS=24"
if not "%~4"=="" set "CACHE_CLEAN_INTERVAL_HOURS=%~4"

:: ENABLE_AUTO_CLEAN_CACHE: 自动缓存清理总开关（1: 启用，0: 禁用）
set "ENABLE_AUTO_CLEAN_CACHE=1"

set "JWT_SECRET=smoke-test-secret-key-0123456789abcdef-0123456789abcdef"

:: 定位服务端二进制位置（兼容根目录或子目录）
if exist "%ROOT_DIR%\server\build\install\server\bin\server.bat" (
    set "SERVER_BIN=%ROOT_DIR%\server\build\install\server\bin\server.bat"
    set "PROJECT_DIR=%ROOT_DIR%"
) else if exist "%ROOT_DIR%\legado-E\server\build\install\server\bin\server.bat" (
    set "SERVER_BIN=%ROOT_DIR%\legado-E\server\build\install\server\bin\server.bat"
    set "PROJECT_DIR=%ROOT_DIR%\legado-E"
) else (
    set "PROJECT_DIR=%ROOT_DIR%"
    set "SERVER_BIN=%ROOT_DIR%\server\build\install\server\bin\server.bat"
)

echo ============================================================
echo   Legado 多用户书城服务 - 一键启动器
echo ============================================================
echo.

:: 1. 检查 Java 环境
where java >nul 2>nul
if %errorlevel% equ 0 goto :java_ok
if exist "C:\Program Files\Java\jdk-24\bin\java.exe" (
    set "PATH=C:\Program Files\Java\jdk-24\bin;%PATH%"
    goto :java_ok
)
if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
    set "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
    goto :java_ok
)
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "PATH=C:\Program Files\Java\jdk-17\bin;%PATH%"
    goto :java_ok
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    goto :java_ok
)
echo [错误] 未检测到 Java 运行环境！
echo 请确保已安装 JDK 17+ 并将其配置到环境变量 PATH 中。
echo.
pause
exit /b 1

:java_ok

:: 2. 检查服务端二进制包
if exist "%SERVER_BIN%" goto :bin_ok
echo [提示] 检测到服务端尚未编译，正在执行首次编译打包（需要几分钟）...
pushd "%PROJECT_DIR%"
call gradlew.bat :server:installDist
popd
if not exist "%SERVER_BIN%" (
    echo [错误] 服务端编译失败，请检查编译日志。
    echo.
    pause
    exit /b 1
)
echo [提示] 编译构建完成！
echo.

:bin_ok

:: 3. 检查并同步前端静态资源
set "WEB_DIST="
if exist "%PROJECT_DIR%\modules\web\dist\index.html" (
    set "WEB_DIST=%PROJECT_DIR%\modules\web\dist"
)
if defined WEB_DIST (
    echo [提示] 正在同步最新前端构建产物至数据目录...
    if not exist "%DATA_DIR%\webapp" mkdir "%DATA_DIR%\webapp"
    xcopy /E /I /Y "%WEB_DIST%" "%DATA_DIR%\webapp" >nul
    echo [提示] 前端资源同步完成。
    echo.
)

:: 3.1 执行启动期缓存检查与启动后台定时清理守护进程
if "%ENABLE_AUTO_CLEAN_CACHE%"=="1" (
    if exist "%ROOT_DIR%\clean-cache.bat" (
        echo [提示] 正在执行启动期缓存检查（保留 %CACHE_EXPIRE_DAYS% 天内有效缓存）...
        call "%ROOT_DIR%\clean-cache.bat" "%DATA_DIR%" %CACHE_EXPIRE_DAYS%
        echo [提示] 正在拉起后台自动定时清理守护任务（每 %CACHE_CLEAN_INTERVAL_HOURS% 小时自动巡检）...
        start "" /min cmd /c "call \"%ROOT_DIR%\clean-cache.bat\" \"%DATA_DIR%\" %CACHE_EXPIRE_DAYS% %CACHE_CLEAN_INTERVAL_HOURS% --daemon"
        echo.
    )
)

:: 4. 检查端口是否被占用
set "OLD_PID="
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /C:":%PORT% " ^| findstr "LISTENING"') do (
    set "OLD_PID=%%a"
)

if not defined OLD_PID goto :port_ready

echo [提示] 端口 %PORT% 当前已被进程 [PID: %OLD_PID%] 占用。
set "CHOICE="
set /p "CHOICE=是否结束旧进程并重新启动服务？[Y/N] (回车默认 Y): "
if "%CHOICE%"=="" set "CHOICE=Y"
if /i "%CHOICE%"=="Y" (
    echo 正在终止旧进程 [PID: %OLD_PID%] ...
    taskkill /F /PID %OLD_PID% >nul 2>&1
    ping 127.0.0.1 -n 2 >nul
    echo 旧进程已终止。
    echo.
    goto :port_ready
)
echo [提示] 已取消启动。
pause
exit /b 0

:port_ready

:: 5. 启动服务与唤起浏览器
echo ------------------------------------------------------------
echo   服务配置：
echo   - 监听端口 (PORT)       : %PORT%
echo   - 数据目录 (DATA_DIR)   : %DATA_DIR%
echo   - 本地访问地址          : http://127.0.0.1:%PORT%
echo   - 健康检查接口          : http://127.0.0.1:%PORT%/api/health
echo   - 缓存自动清理          : 启用（保留 %CACHE_EXPIRE_DAYS% 天，每 %CACHE_CLEAN_INTERVAL_HOURS% 小时自动巡检）
echo ------------------------------------------------------------
echo.
echo 服务正在启动，2 秒后将自动在浏览器中打开...
echo （若需停止服务，可直接关闭此窗口，或按 Ctrl+C）
echo.

:: 延时 2 秒自动唤起浏览器
start "" cmd /c "ping 127.0.0.1 -n 3 >nul && start http://127.0.0.1:%PORT%"

:: 启动服务进程
pushd "%PROJECT_DIR%"
call "%SERVER_BIN%"
popd

if %errorlevel% neq 0 (
    echo.
    echo [提示] 服务已退出 [退出码: %errorlevel%]。
    pause
)
