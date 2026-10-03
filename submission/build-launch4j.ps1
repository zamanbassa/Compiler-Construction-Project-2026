param(
  [string]$Launch4jHome
)

$ErrorActionPreference = "Stop"
$submission = $PSScriptRoot
$config = Join-Path $submission "launch4j.xml"
$jar = Join-Path $submission "staging\group-27.jar"
$exe = Join-Path $submission "dist\group-27.exe"

if (-not (Test-Path $jar)) {
  throw "Missing $jar. Run build-submission.ps1 first to create the JAR."
}

$launch4jc = $null
if ($Launch4jHome) {
  $launch4jc = Join-Path $Launch4jHome "launch4jc.exe"
} else {
  $command = Get-Command launch4jc.exe -ErrorAction SilentlyContinue
  if ($command) {
    $launch4jc = $command.Source
  }
}

if (-not $launch4jc -or -not (Test-Path $launch4jc)) {
  throw "Launch4j was not found. Install it or pass -Launch4jHome to its folder."
}

New-Item (Join-Path $submission "dist") -ItemType Directory -Force | Out-Null
& $launch4jc $config

if (-not (Test-Path $exe)) {
  throw "Launch4j did not create $exe."
}

Write-Host "Created $exe"