@echo off
title Gemini Phone Agent - Push Update to GitHub
color 0B
echo ========================================================
echo       Gemini Phone Agent - Push Update to GitHub
echo ========================================================
echo.

set "GIT_CMD=C:\Users\GEORGE\AppData\Local\Programs\Git\cmd\git.exe"
set "PROJECT_DIR=C:\Users\GEORGE\.gemini\antigravity\scratch\gemini-phone-agent"

cd /d "%PROJECT_DIR%"

echo Enter update description / commit message:
set /p COMMIT_MSG="> "

if "%COMMIT_MSG%"=="" (
    set "COMMIT_MSG=Update Gemini Phone Agent features"
)

echo.
echo [1/3] Updating version code in version.json...
powershell -NoProfile -Command ^
    "$path = 'version.json';" ^
    "$json = Get-Content $path -Raw | ConvertFrom-Json;" ^
    "$json.version_code += 1;" ^
    "$json.changelog = '%COMMIT_MSG%';" ^
    "$json | ConvertTo-Json -Depth 4 | Set-Content $path -Encoding UTF8"

echo.
echo [2/3] Adding files and committing...
"%GIT_CMD%" add .
"%GIT_CMD%" commit -m "%COMMIT_MSG%"

echo.
echo [3/3] Pushing to GitHub (main branch)...
"%GIT_CMD%" push origin main

if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Push failed! Check your internet connection or git status.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo [SUCCESS] Update successfully pushed to GitHub!
echo.
echo 1. GitHub Actions is now building the APK in the cloud.
echo 2. Within 2 minutes, open the app on your phone.
echo 3. You will see: 'New update available! [Update Now]'
echo ========================================================
echo.
pause
