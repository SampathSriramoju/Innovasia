<#
.SYNOPSIS
    Stops any running Bol Kisan FastAPI or Uvicorn server on port 8000.
#>
param(
    [int]$Port = 8000
)

Write-Host "Checking for active processes on port $Port..." -ForegroundColor Cyan
$Conns = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue

if ($Conns) {
    $Pids = $Conns.OwningProcess | Select-Object -Unique
    foreach ($p in $Pids) {
        $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping process: $($proc.ProcessName) (PID: $p)..." -ForegroundColor Yellow
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
            Write-Host "Successfully stopped PID $p." -ForegroundColor Green
        }
    }
} else {
    Write-Host "No process is currently listening on port $Port." -ForegroundColor Green
}
