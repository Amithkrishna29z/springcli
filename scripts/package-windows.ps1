# Builds a one-click Windows installer (springcli-<version>-setup.exe) that adds springcli to PATH.
#
# Pipeline:  Maven fat jar  ->  jpackage app-image (springcli.exe + bundled JRE)  ->  Inno Setup .exe
# The bundled runtime means end users need neither Java nor manual PATH edits.
#
# Prerequisites:
#   - JDK 21+ (jpackage on PATH)
#   - Maven
#   - Inno Setup 6 (ISCC.exe on PATH)  https://jrsoftware.org/isdl.php
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1
# Output: dist\springcli-setup.exe

$ErrorActionPreference = "Stop"

$Version = "1.1.0"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> Building fat jar..." -ForegroundColor Cyan
mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

# Stage only the runnable jar so jpackage doesn't bundle the extra shade artifacts.
$Out = "dist"
$Staging = Join-Path $Out "input"
$AppImage = Join-Path $Out "app-image"
Remove-Item -Recurse -Force $Staging, $AppImage -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Staging | Out-Null
Copy-Item "target\springcli.jar" $Staging

Write-Host "==> Building self-contained app-image (springcli.exe + runtime)..." -ForegroundColor Cyan
# --win-console is required for a CLI: without it the launcher runs as a windowless GUI app.
jpackage `
  --type app-image `
  --name springcli `
  --app-version $Version `
  --input $Staging `
  --main-jar springcli.jar `
  --main-class cli.Main `
  --win-console `
  --dest $AppImage
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

Write-Host "==> Compiling Inno Setup installer (adds springcli to PATH)..." -ForegroundColor Cyan
$Iscc = (Get-Command ISCC.exe -ErrorAction SilentlyContinue)
if (-not $Iscc) {
    throw "ISCC.exe (Inno Setup) not found on PATH. Install Inno Setup 6 from https://jrsoftware.org/isdl.php"
}
& $Iscc.Source "scripts\windows\springcli.iss"
if ($LASTEXITCODE -ne 0) { throw "Inno Setup compilation failed." }

Write-Host "==> Done. Installer written to $Out\springcli-setup.exe" -ForegroundColor Green
