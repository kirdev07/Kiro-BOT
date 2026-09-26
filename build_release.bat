@echo off
rem Kiro Bot - signed release APK. ASCII only (cmd.exe misreads UTF-8 Cyrillic in .bat files).
setlocal
cd /d "%~dp0"
echo ========================================
echo    Kiro Bot - release build
echo ========================================

call "%~dp0gradle_env.bat"
if errorlevel 1 goto end_error

if not exist "keystore.properties" (
    echo [ERROR] keystore.properties not found.
    echo Copy keystore.properties.example to keystore.properties and fill in your signing key.
    echo To update over an installed version you need the SAME key that signed it.
    goto end_error
)

echo Building signed release APK, log: release_log.txt ...
call "%~dp0gradlew.bat" clean assembleRelease > release_log.txt 2>&1
if errorlevel 1 goto build_failed

for /f "tokens=2 delims== " %%V in ('findstr /r /c:"versionName" app\build.gradle') do set "VERSION=%%~V"
copy /y "app\build\outputs\apk\release\app-release.apk" "KiroBot-%VERSION%.apk" >nul
if errorlevel 1 goto build_failed

echo.
echo ========================================
echo    DONE: KiroBot-%VERSION%.apk
echo ========================================
pause
exit /b 0

:build_failed
echo.
echo [ERROR] Build failed, see release_log.txt
start "" release_log.txt

:end_error
pause
exit /b 1
