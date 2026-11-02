@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
 echo Edo Desktop requires Java 21 or later.
 echo Install it from https://adoptium.net/temurin/releases/?version=21
 pause
 exit /b 1
)
echo Starting Edo Desktop v1.0...
java -jar "%~dp0Edo-Desktop.jar"
if errorlevel 1 (
 echo.
 echo Edo could not start. Please check that Java 21 or newer is active.
 java -version
 pause
 exit /b 1
)
endlocal
