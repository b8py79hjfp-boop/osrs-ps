# Assembles a Lotus release (panel brief section 6; panel/src/deploy.rs): the hub and world jars, release.json and the
# hub's schema.sql, into a folder and optionally a zip. Used by CI (.github/workflows/game-release.yml, which then signs
# the zip) and by the installer (which unpacks nothing: it assembles straight into C:\lotus\releases\<version>).
#
#   .\make-release.ps1 -Repo C:\lotus\game -Version 2026.11.10-1530-abc1234 -Out C:\lotus\releases\<version> [-Zip lotus.zip]
#
# Needs ':server:installDist' and ':hub:installDist' built first, and Java on PATH.

param(
    [Parameter(Mandatory)] [string] $Repo,
    [Parameter(Mandatory)] [string] $Version,
    [Parameter(Mandatory)] [string] $Out,
    [string] $Zip = '',
    # One-line commit subjects to list on the deploy page (newest first).
    [string[]] $Commits = @()
)

$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}$') { throw "Bad version name: $Version" }
$hubLib = Join-Path $Repo 'hub\build\install\hub\lib'
$worldLib = Join-Path $Repo 'server\build\install\world-node\lib'
foreach ($dir in $hubLib, $worldLib) { if (-not (Test-Path $dir)) { throw "Missing $dir (run installDist first)." } }

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force "$Out\hub\lib", "$Out\world\lib" | Out-Null
Copy-Item "$hubLib\*" "$Out\hub\lib\"
Copy-Item "$worldLib\*" "$Out\world\lib\"

# The hub's schema statements, one per line: the panel shows which ones a deploy adds.
& java -cp "$Out\hub\lib\*" tp.hub.HubMainKt --print-schema "$Out\schema.sql" 2>$null | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$Out\schema.sql")) { throw 'Could not write schema.sql.' }

$commit = (git -C $Repo rev-parse HEAD).Trim()
$manifest = [ordered]@{
    version  = $Version
    commit   = $commit
    built_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    commits  = @($Commits | Select-Object -First 50)
}
# UTF-8 without a byte-order mark: the panel reads it as JSON.
[IO.File]::WriteAllText("$Out\release.json", ($manifest | ConvertTo-Json -Depth 3), (New-Object Text.UTF8Encoding $false))

if ($Zip) {
    if (Test-Path $Zip) { Remove-Item -Force $Zip }
    # .NET's ZipFile writes '/' separators (Compress-Archive on Windows PowerShell 5.1 writes backslashes).
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($Out, $Zip, [IO.Compression.CompressionLevel]::Optimal, $false)
    Write-Host "Wrote $Zip"
}
Write-Host "Release $Version assembled in $Out"
