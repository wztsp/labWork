# ============================================================
# LIS Laboratory System - Start Script
# Starts: Backend (Spring Boot Monolith :8080) + TCP Instrument (:9100) + Frontend (Vite :3000)
# Usage:
#   powershell -ExecutionPolicy Bypass -File start.ps1
#   powershell -ExecutionPolicy Bypass -File start.ps1 -SkipBuild
#   powershell -ExecutionPolicy Bypass -File start.ps1 -SkipFrontend
#   powershell -ExecutionPolicy Bypass -File start.ps1 -AutoKill
# ============================================================

param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$AutoKill
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$PSScriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$ProjectRoot  = Split-Path -Parent $PSScriptRoot
$BackendDir   = Join-Path $ProjectRoot "lis-system\lis-monolith\target"
$BackendSrc   = Join-Path $ProjectRoot "lis-system"
$FrontendDir  = Join-Path $ProjectRoot "lis-frontend"
$LogDir       = Join-Path $ProjectRoot "logs"

$BackendJar   = "lis-monolith-1.0.0.jar"
$BackendPort  = 8080
$TcpPort      = 9100
$FrontendPort = 3000

function Write-Step($Msg)   { Write-Host "  [*] $Msg" -ForegroundColor Cyan }
function Write-Ok($Msg)     { Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Warn($Msg)   { Write-Host "  [!!] $Msg" -ForegroundColor Yellow }
function Write-Fail($Msg)   { Write-Host "  [XX] $Msg" -ForegroundColor Red }
function Write-Info($Msg)   { Write-Host "  ->  $Msg" -ForegroundColor Gray }

function Test-PortListening($Port) {
    $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -ne $c
}

function Get-PidByPort($Port) {
    $c = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if (-not $c) { return $null }
    return $c | Select-Object -ExpandProperty OwningProcess -Unique
}

function Stop-ProcessByPort($Port) {
    $procIds = Get-PidByPort $Port
    if (-not $procIds) { return 0 }
    $killed = 0
    foreach ($procId in $procIds) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Warn "Killing PID=$procId ($($proc.ProcessName)) on port $Port"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            $killed++
        }
    }
    if ($killed -gt 0) { Start-Sleep -Seconds 2 }
    return $killed
}

function Wait-Port($Port, $TimeoutSec, $Label) {
    $waited = 0
    while ($waited -lt $TimeoutSec) {
        Start-Sleep -Seconds 5
        $waited += 5
        if (Test-PortListening $Port) {
            Write-Ok "$Label ready (port $Port, ${waited}s)"
            return $true
        }
        if (($waited % 10) -eq 0) {
            Write-Info "Waiting for $Label ... (${waited}s / ${TimeoutSec}s)"
        }
    }
    Write-Warn "Timeout waiting for $Label (${TimeoutSec}s)"
    return $false
}

function Save-Pid($Name, $ProcessId) {
    if (-not (Test-Path $LogDir)) {
        New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    }
    $path = Join-Path $LogDir "$Name.pid"
    "$ProcessId" | Out-File -FilePath $path -Encoding ASCII
}

# === Banner ===
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   LIS Laboratory System - Start Script" -ForegroundColor Cyan
Write-Host "   Monolith :8080 | TCP :9100 | Vite :3000" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Info "Project root: $ProjectRoot"
Write-Info "Log dir     : $LogDir"

# === 1. Environment check ===
Write-Step "Checking Java..."
try {
    $javaVer = & java -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Java OK"
    } else {
        Write-Fail "Java not available. Please install JDK 17+"
        exit 1
    }
} catch {
    Write-Fail "Java not available"
    exit 1
}

if (-not $SkipFrontend) {
    Write-Step "Checking Node.js..."
    try {
        $nodeVer = & node --version 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Node.js $nodeVer"
        } else {
            Write-Fail "Node.js not available. Please install Node.js 18+"
            exit 1
        }
    } catch {
        Write-Fail "Node.js not available"
        exit 1
    }
}

# === 2. Backend JAR check / build ===
$jarPath = Join-Path $BackendDir $BackendJar
if (-not (Test-Path $jarPath)) {
    if ($SkipBuild) {
        Write-Fail "Backend JAR not found at $jarPath and -SkipBuild specified. Build first."
        exit 1
    }
    Write-Warn "Backend JAR not found, running Maven build (first build may take a while)..."
    Push-Location $BackendSrc
    try {
        & mvn clean package "-Dmaven.test.skip=true" -pl lis-monolith -am
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Maven build failed"
            Pop-Location
            exit 1
        }
        Write-Ok "Maven build succeeded"
    } catch {
        Write-Fail "Maven execution failed: $_"
        Pop-Location
        exit 1
    }
    Pop-Location
}
Write-Ok "Backend JAR ready: $jarPath"

# === 3. Port conflict handling ===
$portsToCheck = @()
if (-not $SkipFrontend) { $portsToCheck += $FrontendPort }
$portsToCheck += $BackendPort
$portsToCheck += $TcpPort

$conflictPorts = @()
foreach ($port in $portsToCheck) {
    if (Test-PortListening $port) {
        $conflictPorts += $port
        Write-Warn "Port $port is already in use"
    }
}

if ($conflictPorts.Count -gt 0) {
    if ($AutoKill) {
        Write-Warn "-AutoKill enabled, releasing occupied ports..."
        foreach ($port in $conflictPorts) { Stop-ProcessByPort $port | Out-Null }
    } else {
        Write-Warn "Port conflicts detected: $($conflictPorts -join ', ')"
        $resp = Read-Host "Type Y to release, N to abort"
        if ($resp -eq "Y" -or $resp -eq "y") {
            foreach ($port in $conflictPorts) { Stop-ProcessByPort $port | Out-Null }
        } else {
            Write-Fail "Port conflicts unresolved. Exiting."
            exit 1
        }
    }
}
Write-Ok "All ports available"

# === 4. Prepare log dir ===
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

# === 5. Start backend (Spring Boot monolith, includes TCP server) ===
Write-Step "Starting backend (Spring Boot Monolith)..."
Write-Info "HTTP port: $BackendPort"
Write-Info "TCP port : $TcpPort (listened by TcpServer inside the same JVM)"

$backendOutLog = Join-Path $LogDir "backend.out.log"
$backendErrLog = Join-Path $LogDir "backend.err.log"

Push-Location $BackendDir
try {
    $backendProc = Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $BackendJar) `
        -RedirectStandardOutput $backendOutLog `
        -RedirectStandardError  $backendErrLog `
        -PassThru `
        -WindowStyle Hidden

    Save-Pid "backend" $backendProc.Id
    Write-Ok "Backend started (PID=$($backendProc.Id))"
} catch {
    Write-Fail "Backend start failed: $_"
    Pop-Location
    exit 1
}
Pop-Location

# === 6. Wait for backend HTTP ===
Write-Step "Waiting for backend HTTP (up to 120s)..."
$backendHttpOk = Wait-Port $BackendPort 120 "Backend HTTP"

if (-not $backendHttpOk) {
    $proc = Get-Process -Id $backendProc.Id -ErrorAction SilentlyContinue
    if (-not $proc -or $proc.HasExited) {
        Write-Fail "Backend process exited. Recent error log:"
        Get-Content $backendErrLog -Tail 30 -ErrorAction SilentlyContinue
        exit 1
    }
    Write-Warn "HTTP port not ready yet, process still running..."
}

# === 7. Wait for TCP port ===
Write-Step "Waiting for TCP instrument port (up to 130s)..."
Wait-Port $TcpPort 130 "TCP instrument" | Out-Null

# === 8. Start frontend (optional) ===
$frontendProc = $null
if (-not $SkipFrontend) {
    Write-Step "Starting frontend (Vite)..."
    Write-Info "Frontend port: $FrontendPort"

    Push-Location $FrontendDir
    try {
        if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
            Write-Warn "node_modules missing, running npm install (first install may take a while)..."
            & npm install
            if ($LASTEXITCODE -ne 0) {
                Write-Fail "npm install failed"
                Pop-Location
                exit 1
            }
        }

        $frontendOutLog = Join-Path $LogDir "frontend.out.log"
        $frontendErrLog = Join-Path $LogDir "frontend.err.log"

        $frontendProc = Start-Process -FilePath "cmd.exe" `
            -ArgumentList @("/c", "npm", "run", "dev") `
            -RedirectStandardOutput $frontendOutLog `
            -RedirectStandardError  $frontendErrLog `
            -PassThru `
            -WindowStyle Hidden

        Save-Pid "frontend" $frontendProc.Id
        Write-Ok "Frontend started (PID=$($frontendProc.Id))"
    } catch {
        Write-Fail "Frontend start failed: $_"
        Pop-Location
        exit 1
    }
    Pop-Location

    Write-Step "Waiting for frontend (up to 60s)..."
    Wait-Port $FrontendPort 60 "Frontend Vite" | Out-Null
}

# === 9. Summary ===
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   System Started!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "   Frontend (Vite)   : http://localhost:$FrontendPort" -ForegroundColor Cyan
Write-Host "   Backend (HTTP)    : http://localhost:$BackendPort" -ForegroundColor Cyan
Write-Host "   API Docs          : http://localhost:$BackendPort/doc.html" -ForegroundColor Cyan
Write-Host "   TCP Instrument    : tcp://localhost:$TcpPort" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Logs     : $LogDir" -ForegroundColor Gray
Write-Host "   Stop     : stop.ps1 or stop.bat" -ForegroundColor Gray
Write-Host ""
Write-Host "   Closing this window will NOT stop the services." -ForegroundColor Yellow
Write-Host ""

# === 10. Save process info ===
$frontendPidVal  = if ($frontendProc) { $frontendProc.Id } else { $null }
$frontendPortVal = if ($SkipFrontend) { $null } else { $FrontendPort }
$info = @{
    startTime    = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    backendPid   = $backendProc.Id
    backendPort  = $BackendPort
    tcpPort      = $TcpPort
    frontendPid  = $frontendPidVal
    frontendPort = $frontendPortVal
    skipFrontend = [bool]$SkipFrontend
}
$info | ConvertTo-Json | Out-File (Join-Path $LogDir "processes.json") -Encoding UTF8
