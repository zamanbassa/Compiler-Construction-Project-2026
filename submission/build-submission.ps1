$ErrorActionPreference = "Stop"

$group = "27"
$root = Split-Path -Parent $PSScriptRoot
$submission = $PSScriptRoot
$classes = Join-Path $submission "classes"
$staging = Join-Path $submission "staging"
$dist = Join-Path $submission "dist"
$jar = "group-$group.jar"

if (-not (Test-Path (Join-Path $submission "group-$group.pdf"))) {
  throw "Missing group-$group.pdf. Place the completed manual beside this script before packaging."
}

Remove-Item $classes, $staging, $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item $classes, $staging, $dist -ItemType Directory | Out-Null

$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object FullName
javac -d $classes $sources
jar --create --file (Join-Path $staging $jar) --main-class spl.Main -C $classes .

$tests = @(
  "spl.lexer.LexerTest",
  "spl.parser.ParserTest",
  "spl.tree.TreeBuilderTest",
  "spl.semantic.SemanticDataStructureTest",
  "spl.MainTest",
  "spl.ComprehensiveSystemTest"
)
$testSources = Get-ChildItem (Join-Path $root "test\java") -Recurse -Filter *.java | ForEach-Object FullName
javac -d $classes $sources $testSources
foreach ($test in $tests) {
  java -ea -cp $classes $test
}

$generatedExe = Join-Path $dist "group-$group-1.0.exe"
$exe = Join-Path $dist "group-$group.exe"

jpackage --type exe --name "group-$group" --app-version 1.0 --input $staging `
  --main-jar $jar --main-class spl.Main --dest $dist --win-console `
  --description "SPL Phase 1 lexical and syntax analyser"

if (-not (Test-Path $generatedExe)) {
  throw "jpackage did not create $generatedExe."
}
Move-Item $generatedExe $exe

Copy-Item (Join-Path $submission "group-$group.pdf") $dist
Compress-Archive -Path $exe, (Join-Path $dist "group-$group.pdf") `
  -DestinationPath (Join-Path $submission "group-$group.zip") -Force

Write-Host "Created $dist\group-$group.exe"
Write-Host "Created $dist\group-$group.pdf"
Write-Host "Created $submission\group-$group.zip"