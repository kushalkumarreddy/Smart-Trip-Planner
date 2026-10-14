@echo off
title VoyageCraft - AI Smart Travel Planner
color 0A
cls
echo ============================================================
echo   VoyageCraft: AI Smart Travel Planner ^& Route Optimizer
echo ============================================================
echo.
cd /d "%~dp0"

echo [1/3] Verifying project environment...
if not exist "mvnw.cmd" (
    echo [ERROR] mvnw.cmd not found in %CD%
    pause
    exit /b 1
)

echo [2/3] Scheduling browser auto-open at http://localhost:8080 ...
start "" cmd /c "timeout /t 8 /nobreak >nul & start http://localhost:8080"

echo [3/3] Launching VoyageCraft Spring Boot Application...
echo ------------------------------------------------------------
echo Local URL: http://localhost:8080
echo Press Ctrl+C in this window anytime to gracefully shut down.
echo ------------------------------------------------------------
echo.

call mvnw.cmd spring-boot:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] VoyageCraft stopped with exit code %ERRORLEVEL%.
    pause
)
