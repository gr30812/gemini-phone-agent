@echo off
title Gemini Phone Agent - APK Builder
color 0A
echo ========================================================
echo        Gemini Phone Agent - APK Build Process
echo ========================================================
echo.

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PROJECT_DIR=C:\Users\GEORGE\.gemini\antigravity\scratch\gemini-phone-agent"
set "DESKTOP_DIR=C:\Users\GEORGE\Desktop\Gemini_Phone_Agent"

cd /d "%PROJECT_DIR%"

echo [1/3] Compiling and Building APK with Gradle...
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Build failed! Check the errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Copying fresh APK to Desktop folder...
copy /y "app\build\outputs\apk\debug\app-debug.apk" "%DESKTOP_DIR%\GeminiAgent.apk" >nul
copy /y "app\build\outputs\apk\debug\app-debug.apk" "GeminiAgent.apk" >nul

echo.
echo [3/3] DONE! 
echo ========================================================
echo Fresh signed APK created successfully!
echo Location: %DESKTOP_DIR%\GeminiAgent.apk
echo ========================================================
echo.
pause
