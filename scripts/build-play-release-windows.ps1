# Create signed Google Play Android TV AAB + companion APK, from Edo source.
# Keystore is stored outside this project. NEVER commit it or share passwords.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
function Abort([string]$m){ throw $m }
function Secret([string]$prompt){
    $s=Read-Host -Prompt $prompt -AsSecureString
    $p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p) }
}
Write-Host "Edo v1.0 - SIGNED Android TV release builder" -ForegroundColor Cyan
Write-Host 'This creates an AAB for Google Play, and a signed APK for separate distribution.'
$gradle=Join-Path (Get-Location).Path '.build-tools\gradle-8.9\bin\gradle.bat'
if (!(Test-Path $gradle)) {
    Write-Host 'First-run setup: installing Android SDK/Gradle via the existing debug builder...' -ForegroundColor Yellow
    & (Join-Path (Get-Location).Path 'scripts\build-windows.ps1')
    if ($LASTEXITCODE -ne 0) { Abort 'Android build-tool preparation failed.' }
}
if (!(Test-Path $gradle)){Abort 'Gradle not found after SDK setup.'}
$keystoreDir=Join-Path $env:USERPROFILE 'EdoSigningKeys'
$keystore=Join-Path $keystoreDir 'edo-upload-key.jks'
$alias='edo-upload'
if (!(Test-Path $keystore)) {
    Write-Host 'No Edo upload key found. A permanent signing key must be created.' -ForegroundColor Yellow
    Write-Host "KEY LOCATION: $keystore" -ForegroundColor Yellow
    Write-Host 'Back up the key AND its password securely. Losing it can prevent future updates.'
    $confirm=Read-Host 'Type CREATE to generate a new key (or anything else to cancel)'
    if ($confirm -cne 'CREATE') { Abort 'No key created.' }
    New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
    Write-Host 'Keytool will ask for a password and certificate owner details.'
    & keytool -genkeypair -v -keystore $keystore -alias $alias -keyalg RSA -keysize 3072 -validity 10000
    if ($LASTEXITCODE -ne 0 -or !(Test-Path $keystore)){Abort 'Keytool did not create an upload key.'}
}
$env:EDO_SIGNING_STORE_FILE=$keystore
$env:EDO_SIGNING_KEY_ALIAS=$alias
try {
    $env:EDO_SIGNING_STORE_PASSWORD=Secret 'Upload keystore password'
    $env:EDO_SIGNING_KEY_PASSWORD=Secret 'Upload key password (can equal keystore password)'
    if (!$env:EDO_SIGNING_STORE_PASSWORD -or !$env:EDO_SIGNING_KEY_PASSWORD){Abort 'Empty signing password.'}
    Write-Host 'Building signed release AAB + APK...' -ForegroundColor Cyan
    & $gradle ':app:bundleRelease' ':app:assembleRelease' '--no-daemon' '--stacktrace'
    if ($LASTEXITCODE -ne 0){Abort 'Gradle release build failed.'}
    $aab=Join-Path (Get-Location).Path 'app\build\outputs\bundle\release\app-release.aab'
    $apk=Join-Path (Get-Location).Path 'app\build\outputs\apk\release\app-release.apk'
    if (!(Test-Path $aab) -or !(Test-Path $apk)){Abort 'Release build finished without both artifacts.'}
    Write-Host 'Verifying AAB JAR signature...' -ForegroundColor Cyan
    & jarsigner -verify $aab
    if ($LASTEXITCODE -ne 0){Abort 'AAB signature verification failed.'}
    $sdk=$env:ANDROID_HOME
    if (!$sdk){$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
    $apksigner=Join-Path $sdk 'build-tools\35.0.0\apksigner.bat'
    if (Test-Path $apksigner){
        Write-Host 'Verifying APK signature...' -ForegroundColor Cyan
        & $apksigner verify '--verbose' $apk
        if ($LASTEXITCODE -ne 0){Abort 'APK signature verification failed.'}
    }
    Write-Host "SUCCESS: signed AAB -> $aab" -ForegroundColor Green
    Write-Host "SUCCESS: signed APK -> $apk" -ForegroundColor Green
    Write-Host 'Before uploading, test the APK on Android TV and obtain an actual TV screenshot.' -ForegroundColor Yellow
    Write-Host "Keep the upload key safe: $keystore"
    Invoke-Item (Split-Path -Parent $aab)
}
finally {
    Remove-Item Env:EDO_SIGNING_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:EDO_SIGNING_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:EDO_SIGNING_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:EDO_SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
}
