Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$releaseRoot = Join-Path $projectRoot "dist"
$releaseDir = Join-Path $releaseRoot ("build-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$appName = "J-AP"
Write-Host "[1/5] Building Spring Boot jar..."
Push-Location $projectRoot
try {
    mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$targetDir = Join-Path $projectRoot "target"
$jar = Get-ChildItem -Path $targetDir -Filter "*-exec.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "No executable Spring Boot jar (*-exec.jar) found under target/."
}

Write-Host "[2/5] Preparing release directory..."
if (-not (Test-Path $releaseRoot)) {
    New-Item -ItemType Directory -Path $releaseRoot | Out-Null
}
New-Item -ItemType Directory -Path $releaseDir | Out-Null

Write-Host "[3/5] Creating app-image with jpackage..."
$jpackageInputDir = Join-Path $releaseDir "jpackage-input"
New-Item -ItemType Directory -Path $jpackageInputDir | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $jpackageInputDir $jar.Name) -Force

jpackage `
  --type app-image `
  --name $appName `
  --dest $releaseDir `
  --input $jpackageInputDir `
  --main-jar $jar.Name `
  --java-options "-Dfile.encoding=UTF-8" `
  --java-options "-Dsun.jnu.encoding=UTF-8" `
  --win-console

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

$appRoot = Join-Path $releaseDir $appName
$appDir = Join-Path $appRoot "app"
$configDir = Join-Path $appDir "config"

Write-Host "[4/5] Preparing interview config template..."
if (-not (Test-Path $configDir)) {
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
}

$settingsTemplatePath = Join-Path $projectRoot "config\settings.template.json"
if (-not (Test-Path $settingsTemplatePath)) {
    throw "Missing config/settings.template.json."
}
Copy-Item -LiteralPath $settingsTemplatePath -Destination (Join-Path $configDir "settings.json") -Force

$readmePath = Join-Path $appRoot "README-INTERVIEW.md"
@'
# J-AP Interview Build

## Quick Start
1. Double-click `J-AP.exe`.
2. Open `http://localhost:8080`.
3. Enter your LLM API key in settings and save.

## Notes
- Do not share your API key.
- Generated files are created under the app's working directory.
'@ | Set-Content -LiteralPath $readmePath -Encoding UTF8

Write-Host "[5/5] Done."
Write-Host "App image path: $appRoot"
Write-Host "Executable path: $(Join-Path $appRoot 'J-AP.exe')"

if (-not (Test-Path (Join-Path $appRoot "J-AP.exe"))) {
    throw "Executable was not created."
}
