<#
.SYNOPSIS
    LocalMind Development Orchestrator & Local Stack Launcher
.DESCRIPTION
    Bootstraps infrastructure containers (MySQL, Qdrant, SearXNG), verifies
    local Ollama models, and launches backend and frontend development servers
    in dedicated PowerShell console windows.
.EXAMPLE
    .\scripts\run-local.ps1
#>

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$ComposeFile = Join-Path $RootDir "docker\docker-compose.yml"

$OllamaUrl = "http://127.0.0.1:11434"
$ChatModel = "gemma3:1b"
$EmbeddingModel = "nomic-embed-text"

function Write-Header {
    param([string]$Text)
    Write-Host "`n=================================================================" -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host "=================================================================`n" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Text)
    Write-Host " [OK] $Text" -ForegroundColor Green
}

function Write-Info {
    param([string]$Text)
    Write-Host " [..] $Text" -ForegroundColor Yellow
}

function Write-Warning-Custom {
    param([string]$Text)
    Write-Host " [WARN] $Text" -ForegroundColor Magenta
}

function Write-Failure {
    param([string]$Text)
    Write-Host " [ERR] $Text" -ForegroundColor Red
}

function Test-PortInUse {
    param([string]$HostName, [int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        $inUse = $connect.AsyncWaitHandle.WaitOne(500, $false)
        if ($inUse -and $client.Connected) {
            $client.Close()
            return $true
        }
        $client.Close()
    } catch {
        # ignore
    }
    return $false
}

function Wait-For-Port {
    param([string]$HostName, [int]$Port, [string]$ServiceName, [int]$TimeoutSec = 30)
    Write-Info "Verifying $ServiceName connectivity on port $Port..."
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $connect = $client.BeginConnect($HostName, $Port, $null, $null)
            $success = $connect.AsyncWaitHandle.WaitOne(1000, $false)
            if ($success -and $client.Connected) {
                $client.Close()
                Write-Success "$ServiceName is reachable on port $Port."
                return $true
            }
            $client.Close()
        } catch {
            # continue polling
        }
        Start-Sleep -Seconds 1
    }
    Write-Failure "Timed out waiting for $ServiceName on port $Port."
    return $false
}

Write-Host @"
  _                     _ __  __ _           _ 
 | |    ___   ___  __ _| |  \/  (_)_ __   __| |
 | |   / _ \ / __|/ _` | | |\/| | | '_ \ / _` |
 | |__| (_) | (__| (_| | | |  | | | | | | (_| |
 |_____\___/ \___|\__,_|_|_|  |_|_|_| |_|\__,_|
 Sovereign, Air-Gapped AI Orchestration Suite
"@ -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 1. Start Docker Infrastructure
# -----------------------------------------------------------------------------
Write-Header "Step 1: Starting Docker Infrastructure"

try {
    docker info >$null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Failure "Docker daemon is not running. Please start Docker Desktop and rerun."
        exit 1
    }
    Write-Success "Docker daemon is responsive."
} catch {
    Write-Failure "Docker CLI not found or Docker daemon not reachable."
    exit 1
}

if (-not (Test-Path $ComposeFile)) {
    Write-Failure "Docker compose file not found at: $ComposeFile"
    exit 1
}

Write-Info "Starting containers via docker compose with healthcheck synchronization..."
docker compose -f $ComposeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
    Write-Failure "Failed to start Docker Compose infrastructure."
    exit 1
}
Write-Success "Docker Compose services launched and healthy."

$mysqlOk = Wait-For-Port -HostName "127.0.0.1" -Port 3306 -ServiceName "MySQL" -TimeoutSec 30
$qdrantOk = Wait-For-Port -HostName "127.0.0.1" -Port 6333 -ServiceName "Qdrant HTTP" -TimeoutSec 30
$searxngOk = Wait-For-Port -HostName "127.0.0.1" -Port 8081 -ServiceName "SearXNG" -TimeoutSec 30

if (-not ($mysqlOk -and $qdrantOk -and $searxngOk)) {
    Write-Failure "One or more infrastructure services failed to respond in time."
    exit 1
}

# -----------------------------------------------------------------------------
# 2. Check Local Ollama Connectivity & Models
# -----------------------------------------------------------------------------
Write-Header "Step 2: Checking Ollama Engine & Models"

try {
    $tagsRes = Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Success "Ollama is responding at $OllamaUrl."
} catch {
    Write-Failure "Unable to connect to Ollama at $OllamaUrl - $($_.Exception.Message)"
    Write-Host "Please ensure Ollama is installed and running ('ollama serve')." -ForegroundColor Yellow
    exit 1
}

$installedModels = @()
if ($tagsRes.models) {
    $installedModels = $tagsRes.models | ForEach-Object { $_.name }
}

# Check & Pull Chat Model
$chatModelFound = $installedModels | Where-Object { $_ -like "$ChatModel*" }
if ($chatModelFound) {
    Write-Success "Chat model '$ChatModel' is available ($($chatModelFound -join ', '))."
} else {
    Write-Info "Chat model '$ChatModel' not found. Pulling via 'ollama pull $ChatModel'..."
    ollama pull $ChatModel
    if ($LASTEXITCODE -ne 0) {
        Write-Failure "Failed to pull chat model '$ChatModel'."
        exit 1
    }
    Write-Success "Chat model '$ChatModel' successfully pulled."
}

# Check & Pull Embedding Model
$embModelFound = $installedModels | Where-Object { $_ -like "$EmbeddingModel*" }
if ($embModelFound) {
    Write-Success "Embedding model '$EmbeddingModel' is available ($($embModelFound -join ', '))."
} else {
    Write-Info "Embedding model '$EmbeddingModel' not found. Pulling via 'ollama pull $EmbeddingModel'..."
    ollama pull $EmbeddingModel
    if ($LASTEXITCODE -ne 0) {
        Write-Failure "Failed to pull embedding model '$EmbeddingModel'."
        exit 1
    }
    Write-Success "Embedding model '$EmbeddingModel' successfully pulled."
}

# -----------------------------------------------------------------------------
# 3. Launch Backend & Frontend Services
# -----------------------------------------------------------------------------
if (-not (Test-Path $BackendDir)) {
    Write-Failure "Backend directory not found: $BackendDir"
    exit 1
}
if (-not (Test-Path $FrontendDir)) {
    Write-Failure "Frontend directory not found: $FrontendDir"
    exit 1
}

# Pre-flight check for occupied ports
if (Test-PortInUse -HostName "127.0.0.1" -Port 8080) {
    Write-Warning-Custom "Port 8080 is already occupied. Ensure no previous backend instance is running."
}
if (Test-PortInUse -HostName "127.0.0.1" -Port 3000) {
    Write-Warning-Custom "Port 3000 is already occupied. Next.js may fall back to port 3001."
}

Write-Header "Step 3: Launching Backend & Frontend Services"

# Launch Backend in new window
Write-Info "Launching Spring Boot backend in a dedicated window..."
$backendScript = @"
Set-Location '$BackendDir'
`$host.UI.RawUI.WindowTitle = 'LocalMind Backend (Spring Boot :8080)'
Write-Host 'Starting LocalMind Backend on port 8080...' -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run
"@
Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendScript

# Ensure frontend dependencies exist
Set-Location $FrontendDir
if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
    Write-Info "Installing frontend dependencies (first-time setup)..."
    npm install
}

# Launch Frontend in new window
Write-Info "Launching Next.js frontend in a dedicated window..."
$frontendScript = @"
Set-Location '$FrontendDir'
`$host.UI.RawUI.WindowTitle = 'LocalMind Frontend (Next.js :3000)'
Write-Host 'Starting LocalMind Frontend on port 3000...' -ForegroundColor Cyan
npm run dev
"@
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendScript

Set-Location $RootDir

Write-Header "LocalMind Stack Launched Successfully!"
Write-Host @"
Access Points:
  * LocalMind Web Application : http://localhost:3000
  * Backend REST API          : http://localhost:8080/api
  * Qdrant Vector Console     : http://localhost:6333/dashboard
  * SearXNG Search Engine     : http://localhost:8081
  * Local Ollama Instance     : $OllamaUrl

Quick Commands:
  - Run backend unit/e2e tests : cd backend && .\mvnw.cmd test
  - Run frontend test suite   : cd frontend && npm test
  - Stop docker infrastructure: docker compose -f docker\docker-compose.yml down
"@ -ForegroundColor Green
