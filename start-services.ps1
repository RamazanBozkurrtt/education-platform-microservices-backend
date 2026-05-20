$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$services = @(
  "auth-service",
  "user-service",
  "course-service",
  "enrollment-service",
  "payment-service",
  "search-service",
  "api-gateway"
)

Write-Host "Starting services..." -ForegroundColor Cyan
docker compose up -d $services

Write-Host ""
Write-Host "Service status:" -ForegroundColor Green
docker compose ps $services
