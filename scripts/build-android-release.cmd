@echo off
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-play-release-windows.ps1"
if errorlevel 1 (echo Android release build failed & pause & exit /b 1)
echo Signed Android build ready.
pause
