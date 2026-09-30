param(
  [Parameter(Position = 0)]
  [string]$InputFile = "input\print.spl",

  [Parameter(Position = 1)]
  [string]$OutputFile,

  [switch]$All
)

$root = $PSScriptRoot
$jar = Join-Path $root "submission\staging\group-27.jar"

if (-not (Test-Path $jar)) {
  throw "Missing $jar. Run submission\build-submission.ps1 first."
}

function Invoke-SplFile($source) {
  $sourcePath = (Resolve-Path $source -ErrorAction Stop).Path
  $name = [IO.Path]::GetFileNameWithoutExtension($sourcePath)
  $target = if ($OutputFile -and -not $All) {
    $OutputFile
  } else {
    Join-Path $root "output\runs\$name.xml"
  }

  $targetPath = if ([IO.Path]::IsPathRooted($target)) {
    $target
  } else {
    Join-Path $root $target
  }
  New-Item (Split-Path $targetPath) -ItemType Directory -Force | Out-Null

  Write-Host "Running $sourcePath"
  & java -jar $jar $sourcePath $targetPath
  if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed for $sourcePath."
  }
  Write-Host "XML written to $targetPath" -ForegroundColor Green
}

if ($All) {
  $failed = 0
  Get-ChildItem (Join-Path $root "input") -Filter *.spl |
    ForEach-Object {
      $file = $_
      try {
        Invoke-SplFile $file.FullName
      } catch {
        $failed++
        Write-Host "FAILED: $($file.Name) - $($_.Exception.Message)" -ForegroundColor Red
      }
    }
  if ($failed -gt 0) {
    exit 1
  }
} else {
  $source = if ([IO.Path]::IsPathRooted($InputFile)) {
    $InputFile
  } else {
    Join-Path $root $InputFile
  }
  Invoke-SplFile $source
}