$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$services = @(
  "auth-service",
  "user-service",
  "course-service",
  "enrollment-service",
  "search-service",
  "api-gateway"
)

Write-Host "Rebuilding services..." -ForegroundColor Cyan
docker compose build $services

Write-Host ""
Write-Host "Recreating services with fresh images..." -ForegroundColor Cyan
docker compose up -d --no-deps $services

Write-Host ""
Write-Host "Service status:" -ForegroundColor Green
docker compose ps $services
