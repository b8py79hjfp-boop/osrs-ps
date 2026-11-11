# Builds a signed Lotus release on the VPS itself, for the control panel's Deploys page (panel brief section 6),
# without GitHub Actions or its secrets: it pulls the latest code into C:\lotus\game, builds the server and the hub,
# signs the release with this server's own key (C:\lotus\pki\release.key, made by the installer), and puts it in
# C:\lotus\releases\incoming. The running game is not touched. Then, in the panel: Deploys > Check the incoming folder
# > open the release > Deploy (in-game countdown, health checks, rollback).
#
# Run in PowerShell opened with "Run as administrator":
#
#   irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/build-release.ps1 | iex

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Root = 'C:\lotus'
$Tools = "$Root\tools"
$Game = "$Root\game"
$Pki = "$Root\pki"
$Incoming = "$Root\releases\incoming"

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Done($text) { Write-Host "    $text" -ForegroundColor Green }

$id = [Security.Principal.WindowsIdentity]::GetCurrent()
if (-not (New-Object Security.Principal.WindowsPrincipal $id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this in PowerShell opened with "Run as administrator".'
}
foreach ($need in "$Game\.git", "$Tools\lotus-panel.exe", "$Pki\release.key", "$Pki\release-key-password", "$Tools\jdk25\bin\java.exe") {
    if (-not (Test-Path $need)) { throw "Missing $need. Run the installer first (docs/VPS.md)." }
}
$env:JAVA_HOME = "$Tools\jdk25"
$env:Path = "$Tools\jdk25\bin;$Tools\git\cmd;$env:Path"

Step 'Getting the latest game code'
git -C $Game pull --ff-only
if ($LASTEXITCODE -ne 0) { throw 'git pull failed. Local changes in C:\lotus\game? Remove them, then run this again.' }
Done (git -C $Game log -1 --format='%h %s')

Step 'Building the server and the hub (the game keeps running)'
Push-Location $Game
try {
    & .\gradlew.bat --no-daemon --console=plain :server:installDist :hub:installDist
    if ($LASTEXITCODE -ne 0) { throw 'The build failed (see above).' }
} finally { Pop-Location }
Done 'Built'

Step 'Making the release'
$short = (git -C $Game rev-parse --short=7 HEAD).Trim()
$version = '{0}-{1}' -f (Get-Date).ToUniversalTime().ToString('yyyy.MM.dd-HHmm'), $short
# The commits since the release the hub runs now (its src-<commit> or <date>-<commit> name ends in the commit).
$running = ''
$link = Get-Item "$Root\nodes\lotus-hub" -ErrorAction SilentlyContinue
if ($link -and $link.Target) { $running = (Split-Path ($link.Target | Select-Object -First 1) -Leaf) -replace '^.*-', '' }
$range = 'HEAD'
if ($running -match '^[0-9a-f]{7,40}$') {
    # Windows PowerShell stops on a native program's redirected stderr under 'Stop'; this check may write some.
    $ErrorActionPreference = 'Continue'
    git -C $Game cat-file -e "$running^{commit}" 2>$null
    if ($LASTEXITCODE -eq 0) { $range = "$running..HEAD" }
    $ErrorActionPreference = 'Stop'
}
$commits = @(git -C $Game log --format=%s -n 50 $range)
$work = Join-Path ([IO.Path]::GetTempPath()) "lotus-release-$version"
New-Item -ItemType Directory -Force $Incoming | Out-Null
$zip = "$Incoming\lotus-$version.zip"
& "$Game\deploy\windows\make-release.ps1" -Repo $Game -Version $version -Out $work -Zip $zip -Commits $commits
Remove-Item $work -Recurse -Force

Step 'Signing it with this server''s release key'
$env:LOTUS_RELEASE_KEY_PASSWORD = (Get-Content "$Pki\release-key-password" -Raw).Trim()
try {
    & "$Tools\lotus-panel.exe" release sign $zip --key "$Pki\release.key"
    if ($LASTEXITCODE -ne 0) { throw 'Signing failed.' }
} finally { Remove-Item Env:\LOTUS_RELEASE_KEY_PASSWORD }
Done "Release $version is in $Incoming"

Write-Host ''
Write-Host ' Next, in the control panel (https://lotusps.duckdns.org):' -ForegroundColor Green
Write-Host '   Deploys > Check the incoming folder, then open the release and press Deploy.'
Write-Host ' Players get the in-game countdown; if anything fails its health check, it goes back to the old version.'
Write-Host ''
