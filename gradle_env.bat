@echo off
rem Build environment, called from build_and_run.bat and build_release.bat.
rem ASCII only: cmd.exe misreads UTF-8 batch files with Cyrillic text.
rem 1) JDK 21: if JAVA_HOME is not set, look in %USERPROFILE%\.jdks (Android Studio installs JDKs there).
rem 2) ASCII paths: Java/Gradle fail when the Windows user name has non-Latin letters.

if not defined JAVA_HOME (
    for /d %%D in ("%USERPROFILE%\.jdks\*21*") do (
        if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
    )
)
if not defined JAVA_HOME (
    echo [ERROR] JDK 21 not found. Install it in Android Studio: Settings - Build Tools - Gradle - Gradle JDK
    exit /b 1
)

if not exist "C:\Temp" mkdir "C:\Temp"
set "TEMP=C:\Temp"
set "TMP=C:\Temp"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=C:\GradleHome"
exit /b 0
