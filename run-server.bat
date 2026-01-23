@echo off
title Heronix Talk Server
echo ============================================
echo    Heronix Talk - Chat Messaging Server
echo ============================================
echo.

:: Set JAVA_HOME if needed (uncomment and set path if Java is not in PATH)
:: set JAVA_HOME=C:\Program Files\Java\jdk-21

:: Navigate to project directory
cd /d "%~dp0"

:: Check if target jar exists, if not build first
if not exist "target\heronix-talk-1.0.0-SNAPSHOT.jar" (
    echo Building project...
    echo.
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo.
        echo Build failed! Please check the errors above.
        pause
        exit /b 1
    )
    echo.
    echo Build successful!
    echo.
)

echo Starting Heronix Talk Server...
echo Server will be available at: http://localhost:9680
echo.
echo Press Ctrl+C to stop the server
echo ============================================
echo.

:: Run the Spring Boot application
java -jar target\heronix-talk-1.0.0-SNAPSHOT.jar

pause
