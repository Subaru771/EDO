@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-play-release-windows.ps1"
if errorlevel 1 echo Release build failed. See error above.
pause
