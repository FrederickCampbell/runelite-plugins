param(
    [string]$JarPath,
    [string]$RuneliteDirectory = (Join-Path $HOME ".runelite")
)
$ErrorActionPreference = "Stop"
if (-not $JarPath) {
    $lib = Join-Path (Split-Path $PSScriptRoot -Parent) "build/libs"
    $jars = @(Get-ChildItem -LiteralPath $lib -Filter "*1.13.0-rc1*.jar" -File | Where-Object { $_.Name -notlike "*-all.jar" })
    if ($jars.Count -ne 1) { throw "Pass -JarPath to the single standard 1.13.0-rc1 plugin JAR." }
    $JarPath = $jars[0].FullName
}
$source = (Resolve-Path -LiteralPath $JarPath).Path
if (Get-Process -Name RuneLite -ErrorAction SilentlyContinue) { throw "Close RuneLite before installing." }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($source)
try {
    if (-not $archive.GetEntry("com/farminghelper/FarmingHelperPlugin.class")) { throw "Not a Farming Helper plugin JAR." }
    $entry = $archive.GetEntry("com/farminghelper/runelite-plugin.properties")
    if (-not $entry) { throw "Missing version resource." }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($metadata.Trim() -ne "version=1.13.0-rc1") { throw "Wrong release candidate version." }
} finally { $archive.Dispose() }
$dir = Join-Path $RuneliteDirectory "sideloaded-plugins"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$target = Join-Path $dir "FarmingHelper.jar"
$duplicates = @(Get-ChildItem -LiteralPath $dir -Filter "*.jar" -File | Where-Object { $_.Name -match "farming.?helper" -and $_.FullName -ne $target })
if ($duplicates.Count -gt 0) { throw "Move existing duplicate Farming Helper JARs out of sideloaded-plugins before retrying." }
if (Test-Path -LiteralPath $target) {
    Copy-Item -LiteralPath $target -Destination ($target + ".bak") -Force
}
Copy-Item -LiteralPath $source -Destination $target -Force
Write-Host "Installed 1.13.0-rc1 at $target"
Write-Host "Launch a compatible RuneLite developer client. This does not install a Plugin Hub release."
