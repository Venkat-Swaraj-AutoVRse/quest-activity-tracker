@echo off
title Quest Activity Tracker - Web Companion Launcher
color 0B

echo ===================================================
echo   🪐 QUEST ACTIVITY TRACKER - WEB COMPANION LAUNCHER 🪐
echo ===================================================
echo.
echo This script will set up port forwarding via ADB so you can 
echo view your Quest screen time stats in your computer's browser.
echo.
echo Please ensure:
echo  1. Your Quest headset is connected via USB or Wi-Fi ADB.
echo  2. The Quest Activity Tracker app is running on the headset.
echo  3. The Web Companion server is enabled in the app settings.
echo.
echo ---------------------------------------------------
echo.

echo [+] Checking for connected devices...
adb devices
echo.

echo [+] Setting up port forwarding (local 8080 -> Quest 8080)...
adb forward tcp:8080 tcp:8080
if %errorlevel% neq 0 (
    echo [!] ERROR: Failed to set up port forwarding. Is the Quest connected?
    echo.
    pause
    exit /b
)
echo [+] Port forwarding established successfully!
echo.

echo [+] Launching companion dashboard in your default browser...
start http://localhost:8080
echo.
echo ---------------------------------------------------
echo Keep this window open while using the web companion.
echo Press any key to stop port forwarding and close.
echo ---------------------------------------------------
pause > nul

echo [+] Cleaning up port forwarding...
adb forward --remove tcp:8080
echo [+] Done. Goodbye!
