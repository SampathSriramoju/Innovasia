<#
.SYNOPSIS
    Starts the Bol Kisan FastAPI backend on 0.0.0.0:8000 for local Wi-Fi and physical phone access.
.DESCRIPTION
    1. Validates and activates the Python virtual environment (.venv).
    2. Validates backend dependencies (FastAPI, Uvicorn, SQLAlchemy).
    3. Checks port availability.
    4. Validates configuration settings without leaking secrets.
    5. Detects active Wi-Fi LAN IPv4 address.
    6. Displays actionable URLs for Samsung phone, emulator, health check, and docs.
    7. Starts Uvicorn on 0.0.0.0:8000.
#>

[CmdletBinding()]
param(
    [int]$Port = 8000,
    [string]$HostAddress = "0.0.0.0",
    [switch]$NoReload
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Split-Path -Parent $ScriptDir

Write-Host "============================================================" -ForegroundColor Green
Write-Host "   BOL KISAN - FASTAPI LOCAL LAN BACKEND SERVER            " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "Backend Directory: $BackendDir"

Set-Location $BackendDir

# 1. Virtual Environment Activation
$VenvPython = Join-Path $BackendDir ".venv\Scripts\python.exe"
if (Test-Path $VenvPython) {
    Write-Host "[+] Using virtual environment (.venv)..." -ForegroundColor Cyan
    $PythonExe = $VenvPython
} else {
    Write-Host "[*] No local .venv found; checking system Python..." -ForegroundColor Yellow
    $PythonExe = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $PythonExe) {
        Write-Error "CRITICAL: Python executable not found. Please install Python or set up backend\.venv."
        exit 1
    }
}

# 2. Dependency Verification
Write-Host "[+] Validating backend dependencies..." -ForegroundColor Cyan
& $PythonExe -c "
import sys
required = ['fastapi', 'uvicorn', 'sqlalchemy', 'pydantic']
missing = []
for pkg in required:
    try:
        __import__(pkg)
    except ImportError:
        missing.append(pkg)
if missing:
    print('MISSING: ' + ', '.join(missing))
    sys.exit(1)
"
if ($LASTEXITCODE -ne 0) {
    Write-Error "CRITICAL: Required dependencies are missing in $PythonExe. Run: pip install -r requirements.txt"
    exit 1
}

# 3. Port Availability Check
Write-Host "[+] Checking port $Port availability..." -ForegroundColor Cyan
$PortInUse = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
if ($PortInUse) {
    $Pids = ($PortInUse.OwningProcess | Select-Object -Unique) -join ', '
    Write-Host "[!] Port $Port is currently in use by PID(s): $Pids" -ForegroundColor Yellow
    Write-Host "    Run 'backend\scripts\stop_lan_server.ps1' to stop previous instances." -ForegroundColor Yellow
    exit 1
}

# 4. Environment Variables Validation
Write-Host "[+] Validating environment settings..." -ForegroundColor Cyan
if (Test-Path ".env") {
    Write-Host "    Configuration loaded from: $BackendDir\.env" -ForegroundColor Gray
} else {
    Write-Host "    [!] Warning: .env file not found. Default settings will be used." -ForegroundColor Yellow
}

# 5. Determine active Wi-Fi LAN IPv4 Address
$WifiIps = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
    $_.InterfaceAlias -match "Wi-Fi|WLAN" -and $_.IPAddress -notmatch "^169\.254\." 
} | Select-Object -ExpandProperty IPAddress

$LanIp = if ($WifiIps) {
    $WifiIps[0]
} else {
    $FallbackIp = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
        $_.IPAddress -notmatch "^(127\.|169\.254\.)" -and $_.InterfaceAlias -notmatch "vEthernet|Virtual|Loopback"
    } | Select-Object -ExpandProperty IPAddress -First 1
    if ($FallbackIp) { $FallbackIp } else { "127.0.0.1" }
}

# 6. Windows Firewall Guidance Check
Write-Host "[+] Checking Windows Firewall configuration for port $Port..." -ForegroundColor Cyan
$FwRule = Get-NetFirewallRule -DisplayName "*Bol Kisan*" -ErrorAction SilentlyContinue
if (-not $FwRule) {
    Write-Host "    [i] TIP: If physical phone cannot reach this laptop, run PowerShell as Administrator once:" -ForegroundColor Yellow
    Write-Host "        New-NetFirewallRule -DisplayName 'Bol Kisan Backend Port 8000' -Direction Inbound -LocalPort $Port -Protocol TCP -Action Allow -Profile Private" -ForegroundColor White
} else {
    Write-Host "    [✓] Windows Firewall rule active: $($FwRule.DisplayName)" -ForegroundColor Green
}

# 7. Display Connection Summary
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " SERVER READY FOR CONNECTIONS                               " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Laptop Wi-Fi LAN IPv4:    $LanIp" -ForegroundColor White
Write-Host "  Listening Address:        http://${HostAddress}:${Port}" -ForegroundColor White
Write-Host "  Physical Android Phone:   http://${LanIp}:${Port}/api/v1/" -ForegroundColor Yellow
Write-Host "  Android Emulator:         http://10.0.2.2:${Port}/api/v1/" -ForegroundColor Gray
Write-Host "  Health Endpoint:          http://${LanIp}:${Port}/health" -ForegroundColor Cyan
Write-Host "  Interactive API Docs:     http://${LanIp}:${Port}/docs" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Green
Write-Host "Press Ctrl+C to terminate the server." -ForegroundColor Gray
Write-Host ""

$env:PYTHONPATH = $BackendDir

$UvicornArgs = @("app.main:app", "--host", $HostAddress, "--port", $Port)
if (-not $NoReload) {
    $UvicornArgs += "--reload"
}

& $PythonExe -m uvicorn @UvicornArgs
