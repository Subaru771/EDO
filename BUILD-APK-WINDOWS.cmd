@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-windows.ps1"
if errorlevel 1 (
  echo.
  echo APK build failed. Review the error above and retry.
  pause
  exit /b 1
)
echo.
echo APK build complete.
pause
