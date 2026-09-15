#!/usr/bin/env pwsh
# Baut Backend + Frontend neu und startet den Stack.
# Nutzung: ./run.ps1        (bauen + starten, Logs folgen)
#          ./run.ps1 -Down  (stoppen)
param([switch]$Down)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($Down) {
    docker compose down
    exit $LASTEXITCODE
}

docker compose up --build -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Frontend : http://localhost:3000" -ForegroundColor Green
Write-Host "Backend  : http://localhost:8080/api/status" -ForegroundColor Green
Write-Host ""
docker compose logs -f
