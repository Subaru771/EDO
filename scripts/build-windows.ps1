# Edo v4.0 Windows SDK/bootstrap builder
# Run by double-clicking BUILD-APK-WINDOWS.cmd from the project root.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Fail([string]$message) { throw $message }
function Download-File([string]$url, [string]$target) {
  Write-Host "Downloading $([IO.Path]::GetFileName($target))" -ForegroundColor Cyan
  $parent = Split-Path -Parent $target
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
  # Windows PowerShell 5.1 requires TLS 1.2 for Google/Gradle downloads.
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  try { Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing -MaximumRedirection 10 }
  catch { Remove-Item $target -Force -ErrorAction SilentlyContinue; Fail "Download failed: $url`n$($_.Exception.Message)`nCheck your connection and try again." }
}
function Run-Checked([string]$exe, [string[]]$toolArgs, [string]$description) {
  Write-Host $description -ForegroundColor Cyan
  & $exe @toolArgs
  if ($LASTEXITCODE -ne 0) { Fail "$description failed (exit code $LASTEXITCODE)." }
}

Write-Host "`nEdo v5.4 - Android TV / Phone APK Builder" -ForegroundColor Green
Write-Host "This builder installs missing Android SDK components automatically."
$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $java -or -not $javac) { Fail 'A full JDK 17+ is required. Please install Eclipse Temurin JDK 17.' }
Run-Checked $java.Source @('-version') 'Checking Java'
Run-Checked $javac.Source @('-version') 'Checking Java compiler'

$platform = 'platforms\android-35\android.jar'
$candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
  Where-Object { $_ -and $_.Trim() } | Select-Object -Unique
$sdk = $null
foreach ($candidate in $candidates) {
  if (Test-Path (Join-Path $candidate $platform)) { $sdk = $candidate; break }
}
if (-not $sdk) {
  foreach ($candidate in $candidates) {
    if (Test-Path $candidate) { $sdk = $candidate; break }
  }
}
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
New-Item -ItemType Directory -Path $sdk -Force | Out-Null
$sdk = (Resolve-Path $sdk).Path
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
Write-Host "Android SDK: $sdk"

$platformJar = Join-Path $sdk $platform
$buildTools = Join-Path $sdk 'build-tools\35.0.0\aapt2.exe'
if (-not (Test-Path $platformJar) -or -not (Test-Path $buildTools)) {
  # Google Android command-line tools 22+ ship a legacy sdkmanager wrapper.
  # Prefer the real Android CLI; it uses slash-separated package paths.
  $cmdlineBins = @()
  $latestBin = Join-Path $sdk 'cmdline-tools\latest\bin'
  if (Test-Path $latestBin) { $cmdlineBins += $latestBin }
  $cmdlineRoot = Join-Path $sdk 'cmdline-tools'
  if (Test-Path $cmdlineRoot) {
    $cmdlineBins += @(Get-ChildItem $cmdlineRoot -Directory | Where-Object { $_.Name -ne 'latest' } |
      Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
  }
  $androidCli = $null
  $sdkmanager = $null
  foreach ($bin in $cmdlineBins) {
    # Android command-line tools 22+ commonly ship android.exe, not android.bat.
    # Prefer the native executable to the deprecated sdkmanager compatibility shim.
    foreach ($cliName in @('android.exe', 'android.bat', 'android.cmd')) {
      $cliPath = Join-Path $bin $cliName
      if (-not $androidCli -and (Test-Path $cliPath)) { $androidCli = $cliPath }
    }
    if (-not $sdkmanager -and (Test-Path (Join-Path $bin 'sdkmanager.bat'))) { $sdkmanager = Join-Path $bin 'sdkmanager.bat' }
  }
  # If only sdkmanager exists, fetch the verified modern CLI instead of relying
  # on its deprecated compatibility wrapper (which split package paths before).
  if (-not $androidCli) {
    $cliZip = Join-Path $env:TEMP 'dropwave-android-commandline-tools.zip'
    # Official Windows command-line tools from developer.android.com/studio.
    # Correct published SHA-256: https://developer.android.com/studio#command-line-tools-only
    $cliUrl = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
    $cliSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if (-not (Test-Path $cliZip) -or (Get-FileHash $cliZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $cliSha256) {
      Download-File $cliUrl $cliZip
    }
    if ((Get-FileHash $cliZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $cliSha256) {
      Remove-Item $cliZip -Force -ErrorAction SilentlyContinue
      Fail 'Android command-line tools download failed SHA-256 verification.'
    }
    $stage = Join-Path $env:TEMP 'edo-sdktools-extract'
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    Write-Host 'Extracting official Android command-line tools...' -ForegroundColor Cyan
    Expand-Archive -Path $cliZip -DestinationPath $stage
    $from = Join-Path $stage 'cmdline-tools'
    if (-not (Test-Path (Join-Path $from 'bin\sdkmanager.bat'))) { Fail 'Downloaded SDK archive layout was not recognized.' }
    $to = Join-Path $sdk 'cmdline-tools\latest'
    New-Item -ItemType Directory -Path (Split-Path -Parent $to) -Force | Out-Null
    if (Test-Path $to) { Remove-Item $to -Recurse -Force }
    Move-Item -Path $from -Destination $to
    Remove-Item $stage -Recurse -Force
    $sdkmanager = Join-Path $to 'bin\sdkmanager.bat'
    foreach ($cliName in @('android.exe', 'android.bat', 'android.cmd')) {
      $modern = Join-Path $to "bin\$cliName"
      if (-not $androidCli -and (Test-Path $modern)) { $androidCli = $modern }
    }
  }

  Write-Host 'Android SDK Platform 35 / Build Tools 35 missing. Installing...' -ForegroundColor Yellow
  Write-Host 'Android SDK licenses: https://developer.android.com/studio/terms' -ForegroundColor Yellow
  $response = Read-Host 'Do you agree to the Android SDK licenses and want to continue? (Y/N)'
  if ($response -notmatch '^[Yy]$') { Fail 'Android SDK installation cancelled; no licenses were accepted.' }

  if ($androidCli) {
    # Android CLI 2026 accepts paths such as platforms/android-35, NOT the old
    # sdkmanager semicolon notation. Calling it directly avoids wrapper bugs.
    Write-Host "Using modern Android CLI: $androidCli" -ForegroundColor Cyan
    Run-Checked $androidCli @("--sdk=$sdk", 'sdk', 'install', 'platforms/android-35', 'build-tools/35.0.0', 'platform-tools') 'Installing Android SDK packages'
  } elseif ($sdkmanager) {
    # SDK Command-Line Tools 22+ includes sdkmanager.bat as a deprecated wrapper
    # that delegates to Android CLI. Older installations use the legacy syntax.
    $wrapperText = Get-Content -LiteralPath $sdkmanager -Raw -ErrorAction SilentlyContinue
    $isNewWrapper = $wrapperText -match 'Android CLI will be used instead|SDK Manager CLI tool.*deprecated'
    if ($isNewWrapper) {
      Write-Host "Using new SDK manager compatibility wrapper: $sdkmanager" -ForegroundColor Cyan
      Run-Checked $sdkmanager @("--sdk_root=$sdk", 'platforms/android-35', 'build-tools/35.0.0', 'platform-tools') 'Installing Android SDK packages'
    } else {
      Write-Host "Using old SDK manager: $sdkmanager" -ForegroundColor Cyan
      Write-Host 'Accepting SDK license prompts...' -ForegroundColor Cyan
      $answers = 1..50 | ForEach-Object { 'y' }
      $answers | & $sdkmanager "--sdk_root=$sdk" '--licenses'
      if ($LASTEXITCODE -ne 0) { Fail 'SDK license acceptance failed.' }
      Run-Checked $sdkmanager @("--sdk_root=$sdk", '--install', 'platforms;android-35', 'build-tools;35.0.0', 'platform-tools') 'Installing Android SDK packages'
    }
  } else {
    Fail 'No Android SDK installer found.'
  }
  if (-not (Test-Path $platformJar) -or -not (Test-Path $buildTools)) { Fail 'Android SDK install did not create Platform 35 / Build Tools 35. See the SDK installation output above.' }
} else {
  Write-Host 'Android SDK Platform 35 and Build Tools 35 found.' -ForegroundColor Green
}

# Use the project-pinned Gradle 8.9, not an unrelated system Gradle version.
$toolsDir = Join-Path (Get-Location).Path '.build-tools'
$gradleExe = Join-Path $toolsDir 'gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradleExe)) {
  New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
  $gradleZip = Join-Path $toolsDir 'gradle-8.9-bin.zip'
  Download-File 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' $gradleZip
  Write-Host 'Extracting Gradle 8.9...' -ForegroundColor Cyan
  Expand-Archive -Path $gradleZip -DestinationPath $toolsDir -Force
  Remove-Item $gradleZip -Force
}
if (-not (Test-Path $gradleExe)) { Fail 'Gradle 8.9 was not installed correctly.' }

Run-Checked $gradleExe @(':app:assembleDebug', '--no-daemon', '--stacktrace') 'Building native Edo Android APK'
$apk = Join-Path (Get-Location).Path 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { Fail "Gradle completed but APK not found: $apk" }
Write-Host "`nSUCCESS: APK created at $apk" -ForegroundColor Green
Write-Host 'Copy this APK to your Google TV to install it.'
Invoke-Item (Split-Path -Parent $apk)
