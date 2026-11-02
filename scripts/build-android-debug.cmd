@echo off
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-windows.ps1"
if errorlevel 1 (echo Android debug build failed & pause & exit /b 1)
echo Android debug APK ready.
pause
