@echo off
title Heronix Talk Server (Dev Mode)
echo ============================================
echo    Heronix Talk - Development Mode
echo ============================================
echo.

:: Navigate to project directory
cd /d "%~dp0"

echo Starting Heronix Talk Server in development mode...
echo Server will be available at: http://localhost:9680
echo.
echo Press Ctrl+C to stop the server
echo ============================================
echo.

:: Run with Maven Spring Boot plugin (auto-compiles on start)
mvn spring-boot:run

pause
