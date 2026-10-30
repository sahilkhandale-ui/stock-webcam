@echo off
title Stock Android Webcam - 1-Click USB Bridge (Zero Tethering)
color 0b

echo ======================================================================
echo       STOCK ANDROID WEBCAM - DIRECT USB BRIDGE (ZERO TETHERING)
echo ======================================================================
echo.

:: 1. Locate ADB
set ADB_CMD=adb
where adb >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo [!] ADB not found in PATH or standard Android SDK.
        echo [*] Please ensure Android platform-tools or ADB is installed.
        pause
        exit /b 1
    )
)

echo [*] Using ADB: %ADB_CMD%
echo [*] Checking connected Android devices via USB...

:: 2. Wait for device
"%ADB_CMD%" devices
echo.
echo [*] Forwarding port 8080 over physical USB cable...
"%ADB_CMD%" forward tcp:8080 tcp:8080

if %ERRORLEVEL% EQU 0 (
    echo [+] Success: Direct USB tunnel established!
    echo [+] No USB Tethering or special network drivers needed.
    echo.
    echo [*] Opening webcam stream at http://localhost:8080 ...
    start "" "http://localhost:8080"
) else (
    echo.
    echo [!] Warning: Could not connect to device via ADB.
    echo [*] Please check:
    echo     1. Is your phone connected via USB cable?
    echo     2. Is "USB Debugging" enabled in Developer Options?
    echo     3. Did you tap "Allow USB Debugging" on your phone's screen?
    echo.
)

echo.
echo ======================================================================
echo  DIRECT USB URL: http://localhost:8080
echo.
echo  To use directly in OBS Studio:
echo    1. Add Source -^> Browser
echo    2. Enter URL: http://localhost:8080/stream
echo    3. Width: 1920, Height: 1080, FPS: 60
echo    4. Click 'Start Virtual Camera' in OBS for Zoom/Teams/Discord!
echo ======================================================================
echo.
pause
