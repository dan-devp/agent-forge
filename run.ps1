#!/usr/bin/env pwsh
# Rebuilds backend + frontend and starts the stack.
# Usage: ./run.ps1        (build + start, follow logs)
#        ./run.ps1 -Down  (stop)
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
