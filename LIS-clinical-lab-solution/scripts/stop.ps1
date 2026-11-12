# ============================================================
# LIS Laboratory System - Stop Script
# Stops: Frontend (:3000) + Backend (:8080 + TCP :9100)
# Usage:
#   powershell -ExecutionPolicy Bypass -File stop.ps1
#   powershell -ExecutionPolicy Bypass -File stop.ps1 -Force
# ============================================================

param(
    [switch]$Force
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$PSScriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$ProjectRoot  = Split-Path -Parent $PSScriptRoot
$LogDir       = Join-Path $ProjectRoot "logs"

$BackendPort  = 8080
$TcpPort      = 9100
$FrontendPort = 3000

function Write-Step($Msg)   { Write-Host "  [*] $Msg" -ForegroundColor Cyan }
function Write-Ok($Msg)     { Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Warn($Msg)   { Write-Host "  [!!] $Msg" -ForegroundColor Yellow }
function Write-Fail($Msg)   { Write-Host "  [XX] $Msg" -ForegroundColor Red }

function Test-PortListening($Port) {
    $c = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $c
}

function Stop-ProcessByPort($Port) {
    $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if (-not $connections) { return 0 }
    $procIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
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

function Stop-ProcessByPidFile($FileName) {
    $path = Join-Path $LogDir $FileName
    if (-not (Test-Path $path)) { return 0 }
    $pidStr = Get-Content $path -ErrorAction SilentlyContinue
    if (-not $pidStr) { return 0 }
    $procId = [int]$pidStr.Trim()
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Warn "Killing PID=$procId ($($proc.ProcessName)) from $FileName"
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        return 1
    }
    return 0
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Red
Write-Host "   LIS System - Stop Script" -ForegroundColor Red
Write-Host "============================================" -ForegroundColor Red
Write-Host ""

$stoppedTotal = 0

# 1. Stop frontend (Vite) - port 3000
Write-Step "[1/3] Frontend (port $FrontendPort)..."
if (Test-PortListening $FrontendPort) {
    $n = Stop-ProcessByPort $FrontendPort
    if ($n -gt 0) {
        Write-Ok "Frontend stopped"
        $stoppedTotal += $n
    }
} else {
    Write-Ok "Frontend not running"
}

# 2. Stop backend (Spring Boot) - port 8080
Write-Step "[2/3] Backend HTTP (port $BackendPort)..."
if (Test-PortListening $BackendPort) {
    $n = Stop-ProcessByPort $BackendPort
    if ($n -gt 0) {
        Write-Ok "Backend stopped"
        $stoppedTotal += $n
    }
} else {
    Write-Ok "Backend HTTP not running"
}

# 3. Stop TCP service (port 9100) - usually same JVM as backend
Write-Step "[3/3] TCP instrument (port $TcpPort)..."
if (Test-PortListening $TcpPort) {
    $n = Stop-ProcessByPort $TcpPort
    if ($n -gt 0) {
        Write-Ok "TCP service stopped"
        $stoppedTotal += $n
    }
} else {
    Write-Ok "TCP port not listening"
}

# 4. Force-clean via PID files
if ($Force -and (Test-Path $LogDir)) {
    Write-Step "Force-clean using PID files..."
    $files = @("backend.pid", "frontend.pid")
    foreach ($f in $files) {
        $n = Stop-ProcessByPidFile $f
        if ($n -gt 0) { $stoppedTotal += $n }
    }
}

# 5. Cleanup PID / process records
Write-Step "Cleaning runtime records..."
if (Test-Path $LogDir) {
    Get-ChildItem $LogDir -Include "*.pid","processes.json" -File -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }
}
Write-Ok "Runtime records cleaned"

Write-Host ""
if ($stoppedTotal -gt 0) {
    Write-Host "Stopped $stoppedTotal process(es)" -ForegroundColor Green
} else {
    Write-Host "No LIS services were running" -ForegroundColor Gray
}
Write-Host "============================================" -ForegroundColor Red
