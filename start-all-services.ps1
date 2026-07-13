# Start All Services Script for CHD-EPICS

Write-Host "Starting CHD-EPICS Services..." -ForegroundColor Green
Write-Host ""

# Get the script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $scriptDir "backend"
$mlServiceDir = Join-Path $scriptDir "ml-service"
$frontendDir = Join-Path $scriptDir "frontend"

# Start MinIO
Write-Host "Starting MinIO (port 9000)..." -ForegroundColor Yellow
$minioExe = Join-Path $backendDir "minio\minio.exe"
$minioData = Join-Path $backendDir "minio-data"
# Set required MinIO credentials (must match backend config)
$env:MINIO_ROOT_USER = "minio"
$env:MINIO_ROOT_PASSWORD = "minio12345"
Start-Process -FilePath $minioExe -ArgumentList "server", $minioData, "--console-address", ":9001" -WindowStyle Minimized
Start-Sleep -Seconds 2

# Start ML Service
Write-Host "Starting ML Service (port 8000)..." -ForegroundColor Yellow
$mlMain = Join-Path $mlServiceDir "main.py"
Start-Process -FilePath "python" -ArgumentList $mlMain -WorkingDirectory $mlServiceDir -WindowStyle Minimized
Start-Sleep -Seconds 3

# Start Spring Boot Backend
Write-Host "Starting Spring Boot Backend (port 8080)..." -ForegroundColor Yellow
$mvnw = Join-Path $backendDir "mvnw.cmd"
Set-Location $backendDir
Start-Process -FilePath $mvnw -ArgumentList "spring-boot:run" -WindowStyle Minimized

# Start Frontend (static files)
Write-Host "Starting Frontend static server (port 3000)..." -ForegroundColor Yellow
Start-Process -FilePath "python" -ArgumentList "-m", "http.server", "3000" -WorkingDirectory $frontendDir -WindowStyle Minimized

Write-Host ""
Write-Host "All services are starting in separate windows." -ForegroundColor Green
Write-Host "Services:" -ForegroundColor Cyan
Write-Host "  - MinIO: http://localhost:9000 (Console: http://localhost:9001)" -ForegroundColor White
Write-Host "  - ML Service: http://localhost:8000" -ForegroundColor White
Write-Host "  - Spring Boot Backend: http://localhost:8080" -ForegroundColor White
Write-Host "  - Frontend: http://localhost:3000/main.html" -ForegroundColor White
Write-Host ""
Write-Host "Note: Services are running in minimized windows. Check Task Manager to see running processes." -ForegroundColor Yellow

