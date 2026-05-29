@echo off
REM Windows cmd 默认 GBK 码页会让 UTF-8 中文 echo 乱码, 先切 UTF-8 (65001)
chcp 65001 >nul
REM nook-agent 打包脚本 (Windows cmd 版): 同时构建 frontline + landing (linux/amd64), 产物落到 <repo-root>\agent\.
REM
REM 用法:
REM   build.cmd                  REM version 默认 0.7.0
REM   build.cmd 0.7.1            REM 指定 version
REM   build.cmd 0.7.1 frontline  REM 只编一个 role (frontline | landing)

setlocal

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=0.8.3"
set "ONLY_ROLE=%~2"

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
for %%I in ("%SCRIPT_DIR%\..") do set "REPO_ROOT=%%~fI"
set "OUT_DIR=%REPO_ROOT%\agent"

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

where go >nul 2>nul
if not errorlevel 1 goto GO_OK

REM PATH 里没 go (典型情况: 父进程 env 没刷新). 试从注册表拉系统级 GOROOT 兜底.
for /f "tokens=2*" %%A in ('reg query "HKLM\System\CurrentControlSet\Control\Session Manager\Environment" /v GOROOT 2^>nul ^| findstr /i "GOROOT"') do set "REG_GOROOT=%%B"
if defined REG_GOROOT (
    if exist "%REG_GOROOT%\bin\go.exe" (
        set "PATH=%REG_GOROOT%\bin;%PATH%"
        goto GO_OK
    )
)
goto NO_GO

:GO_OK

cd /d "%SCRIPT_DIR%"

echo [build] go version:
go version
echo [build] 输出目录: %OUT_DIR%
echo [build] 版本: %VERSION%

REM 用 goto 分支替代嵌套括号; cmd parser 跟 `||` 在 `( )` 里冲突, 走 goto 稳
if "%ONLY_ROLE%"=="" goto BOTH
if /I "%ONLY_ROLE%"=="frontline" goto FRONTLINE
if /I "%ONLY_ROLE%"=="landing" goto LANDING
echo [X] 未知 role: %ONLY_ROLE% (只接受 frontline / landing 或留空)
exit /b 1

:BOTH
call :build_one frontline
if errorlevel 1 exit /b 1
call :build_one landing
if errorlevel 1 exit /b 1
goto DONE

:FRONTLINE
call :build_one frontline
if errorlevel 1 exit /b 1
goto DONE

:LANDING
call :build_one landing
if errorlevel 1 exit /b 1
goto DONE

:DONE
echo.
echo [OK] 完成. backend 由 nook.agent.bin-dir 指向 %OUT_DIR%, 启动后 AgentBinaryResolver 自动按 mtime 取最新.
endlocal
exit /b 0

:NO_GO
echo [X] 找不到 go.exe.
echo     1. 确认系统级 env 已配 GOROOT + Path 加了 %%GOROOT%%\bin
echo     2. 关掉所有终端 / IDE 重开 ^(进程启动时取一次 env, 后改不刷新^)
echo     3. 新 cmd 里跑 where go 验证
endlocal
exit /b 1

:build_one
set "ROLE=%~1"
set "OUT=%OUT_DIR%\nook-%ROLE%-%VERSION%-linux-amd64"

echo.
echo === 编 %ROLE% -^> nook-%ROLE%-%VERSION%-linux-amd64 ===

set "GOOS=linux"
set "GOARCH=amd64"
set "CGO_ENABLED=0"
go build -ldflags "-X main.Version=%ROLE%-%VERSION% -s -w" -o "%OUT%" ".\cmd\%ROLE%"
if errorlevel 1 (
    echo [X] 编 %ROLE% 失败
    exit /b 1
)

powershell -NoProfile -Command "$f = Get-Item '%OUT%'; $h = (Get-FileHash '%OUT%' -Algorithm SHA256).Hash.ToLower(); Write-Host ('  size:   {0} bytes ({1:N2} MB)' -f $f.Length, ($f.Length/1MB)); Write-Host ('  sha256: ' + $h); Write-Host ('  path:   ' + $f.FullName)"

exit /b 0
