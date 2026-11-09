# Bol Kisan - Offline Android Bundle Installer (PowerShell)
# Automates APK installation and model sideloading for field officers

param (
    [string]$DeviceSerial = "",
    [switch]$SkipModel
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkPath = Join-Path $ScriptDir "app-debug.apk"
$ModelPath = Join-Path $ScriptDir "qwen3_0_6b.litertlm"

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "       Bol Kisan Offline Field Bundle Deployment          " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

# 1. Check ADB
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[ERROR] 'adb' tool not found on PATH. Please install Android Platform Tools." -ForegroundColor Red
    exit 1
}

$adbTarget = if ($DeviceSerial) { "-s $DeviceSerial" } else { "" }

# 2. Check Device
$devices = adb devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }
if (-not $devices) {
    Write-Host "[ERROR] No connected Android device found. Connect device via USB and enable USB Debugging." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Connected device detected: $devices" -ForegroundColor Cyan

# 3. Install APK
if (Test-Path $ApkPath) {
    Write-Host "Installing Bol Kisan APK ($ApkPath)..." -ForegroundColor Yellow
    Invoke-Expression "adb $adbTarget install -r -d `"$ApkPath`""
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] APK installation failed." -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] App successfully installed." -ForegroundColor Green
} else {
    Write-Host "[WARN] app-debug.apk not found in bundle directory." -ForegroundColor Yellow
}

# 4. Sideload Model Artifact
if (-not $SkipModel) {
    if (Test-Path $ModelPath) {
        $modelSizeMb = [math]::Round((Get-Item $ModelPath).Length / 1MB, 1)
        Write-Host "Sideloading LiteRT-LM model ($modelSizeMb MB) to device storage..." -ForegroundColor Yellow
        Invoke-Expression "adb $adbTarget push `"$ModelPath`" /sdcard/Download/qwen3_0_6b.litertlm"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] Model successfully pushed to /sdcard/Download/qwen3_0_6b.litertlm." -ForegroundColor Green
        } else {
            Write-Host "[WARN] ADB push to /sdcard/Download/ failed. Trying app private storage..." -ForegroundColor Yellow
            Invoke-Expression "adb $adbTarget push `"$ModelPath`" /data/local/tmp/qwen3_0_6b.litertlm"
        }
    } else {
        Write-Host "[INFO] qwen3_0_6b.litertlm not found in bundle. Run download_model.py first to include SLM weights." -ForegroundColor Gray
    }
}

# 5. Launch App
Write-Host "Launching Bol Kisan..." -ForegroundColor Cyan
Invoke-Expression "adb $adbTarget shell am start -n com.bolkisan.farmer/.MainActivity"

Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Deployment Complete! App is ready for offline operation. " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
