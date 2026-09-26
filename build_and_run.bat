@echo off
rem Kiro Bot - build, install and launch on a connected device.
rem ASCII only (cmd.exe misreads UTF-8 Cyrillic in .bat files).
rem Signing: keystore.properties (see keystore.properties.example). Without it a Debug build is made.
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem Optional: path to Android SDK or platform-tools if adb is not found automatically
set "MANUAL_SDK_PATH="

echo.
echo === Kiro Bot: build, install, launch ===
echo.

call "%~dp0gradle_env.bat"
if errorlevel 1 goto end_error

rem 1. Find adb
echo [1/5] Looking for adb...
set "ADB_PATH="
if defined MANUAL_SDK_PATH (
    if exist "%MANUAL_SDK_PATH%\platform-tools\adb.exe" set "ADB_PATH=%MANUAL_SDK_PATH%\platform-tools\adb.exe"
    if exist "%MANUAL_SDK_PATH%\adb.exe" set "ADB_PATH=%MANUAL_SDK_PATH%\adb.exe"
)
if not defined ADB_PATH (
    where adb >nul 2>&1
    if !ERRORLEVEL! EQU 0 set "ADB_PATH=adb"
)
if not defined ADB_PATH if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_PATH if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_PATH if exist "C:\Android\Sdk\platform-tools\adb.exe" set "ADB_PATH=C:\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_PATH (
    echo [ERROR] adb not found. Set MANUAL_SDK_PATH at the top of this file.
    goto end_error
)
echo [OK] adb: %ADB_PATH%

rem 2. Build
echo [2/5] Building, log: build_log.txt ...
if exist "keystore.properties" (
    echo [INFO] keystore.properties found - signed Release build
    set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
    call "%~dp0gradlew.bat" clean assembleRelease > build_log.txt 2>&1
) else (
    echo [INFO] No keystore.properties - Debug build
    set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
    call "%~dp0gradlew.bat" clean assembleDebug > build_log.txt 2>&1
)
if errorlevel 1 (
    echo [ERROR] Build failed:
    type build_log.txt
    goto end_error
)
if not exist "!APK_PATH!" (
    echo [ERROR] APK not found after build: !APK_PATH!
    goto end_error
)
echo [OK] Build finished

rem 3. Devices
echo [3/5] Checking devices...
set "DEVICE_COUNT=0"
for /f "skip=1 tokens=2" %%i in ('"%ADB_PATH%" devices') do (
    if "%%i"=="device" set /a DEVICE_COUNT+=1
)
if !DEVICE_COUNT! EQU 0 (
    echo [ERROR] No devices. Connect a phone and enable USB debugging.
    goto end_error
)
echo [OK] Devices: !DEVICE_COUNT!

rem 4. Install
echo [4/5] Installing !APK_PATH! ...
"%ADB_PATH%" install -r "!APK_PATH!"
if errorlevel 1 (
    echo [ERROR] Install failed. If the app was signed with another key, uninstall it first.
    goto end_error
)

rem 5. Launch
echo [5/5] Launching...
"%ADB_PATH%" shell am start -n com.vkbot.manager/.MainActivity
echo.
pause
exit /b 0

:end_error
pause
exit /b 1
