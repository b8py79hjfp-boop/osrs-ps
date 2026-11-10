# Lotus one-command installer and updater for Windows Server 2025 (docs/VPS.md).
#
# Run in PowerShell opened with "Run as administrator":
#
#   irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/install-lotus.ps1 | iex
#
# First run: downloads Java, Git and Shawl into C:\lotus\tools, clones the game into C:\lotus\game, downloads the
# cache, makes the login key, builds the server and the Lotus client, opens the firewall ports, installs the
# "lotus" Windows service and a daily backup. Run it again at any time to update: it pulls the latest code, rebuilds
# and restarts (players are disconnected for a few minutes; warn them with ::update 60 first).
#
# Nothing here needs editing. The server address comes from client/runelite/release.properties (`site`).

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = 'C:\lotus'
$Tools = "$Root\tools"
$Game = "$Root\game"
$Logs = "$Root\logs"
$Backups = "$Root\backups"
$Config = "$Root\server.toml"
$RepoUrl = 'https://github.com/b8py79hjfp-boop/osrs-ps.git'
$Service = 'lotus'

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Done($text) { Write-Host "    $text" -ForegroundColor Green }

function Assert-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $admin = (New-Object Security.Principal.WindowsPrincipal $id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $admin) {
        throw 'Run this in PowerShell opened with "Run as administrator" (right-click PowerShell, Run as administrator).'
    }
}

function Get-File($url, $out) {
    for ($i = 1; $i -le 4; $i++) {
        try {
            Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing -Headers @{ 'User-Agent' = 'lotus-installer' }
            return
        } catch {
            if ($i -eq 4) { throw "Download failed: $url ($($_.Exception.Message))" }
            Start-Sleep -Seconds (2 * $i)
        }
    }
}

# Unzips $zip and moves its single top-level folder (or its contents) to $dest.
function Expand-To($zip, $dest) {
    $tmp = "$dest.tmp"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $items = @(Get-ChildItem $tmp)
    $src = if ($items.Count -eq 1 -and $items[0].PSIsContainer) { $items[0].FullName } else { $tmp }
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Move-Item $src $dest
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Remove-Item $zip -Force
}

function Install-Jdk($version) {
    $dest = "$Tools\jdk$version"
    if (Test-Path "$dest\bin\java.exe") { Done "Java $version already installed"; return $dest }
    $zip = "$Tools\jdk$version.zip"
    Get-File "https://api.adoptium.net/v3/binary/latest/$version/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" $zip
    Expand-To $zip $dest
    Done "Java $version installed (Eclipse Temurin)"
    return $dest
}

function Install-Git {
    $dest = "$Tools\git"
    if (Test-Path "$dest\cmd\git.exe") { Done 'Git already installed'; return }
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/git-for-windows/git/releases/latest' -Headers @{ 'User-Agent' = 'lotus-installer' }
    $asset = $release.assets | Where-Object { $_.name -match '^MinGit-[\d.]+-64-bit\.zip$' } | Select-Object -First 1
    if (-not $asset) { throw 'Could not find MinGit in the latest Git for Windows release.' }
    $zip = "$Tools\git.zip"
    Get-File $asset.browser_download_url $zip
    New-Item -ItemType Directory -Force $dest | Out-Null
    Expand-Archive -Path $zip -DestinationPath $dest -Force
    Remove-Item $zip -Force
    Done "Git installed ($($release.tag_name))"
}

# Shawl (MIT, github.com/mtkennerly/shawl) runs java as a Windows service: Ctrl+C on stop, restart on exit.
$ShawlUrl = 'https://github.com/mtkennerly/shawl/releases/download/v1.7.0/shawl-v1.7.0-win64.zip'
$ShawlSha256 = 'eaa4fed710e844cc7968fdb82e816d406ed89c4486ab34c3e5db2da7e5927923'

function Install-Shawl {
    $exe = "$Tools\shawl.exe"
    if (Test-Path $exe) { Done 'Shawl already installed'; return $exe }
    $zip = "$Tools\shawl.zip"
    Get-File $ShawlUrl $zip
    if ((Get-FileHash $zip -Algorithm SHA256).Hash -ne $ShawlSha256.ToUpper()) { throw 'Shawl download failed its checksum.' }
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    Remove-Item $zip -Force
    Done 'Shawl installed (service wrapper)'
    return $exe
}

function Invoke-Gradle([string[]] $taskArgs) {
    Push-Location $Game
    try {
        & .\gradlew.bat --no-daemon --console=plain @taskArgs
        if ($LASTEXITCODE -ne 0) { throw "Build step failed: gradlew $($taskArgs -join ' ')" }
    } finally { Pop-Location }
}

function Get-ReleaseHost {
    $line = Get-Content "$Game\client\runelite\release.properties" | Where-Object { $_ -match '^site=' } | Select-Object -First 1
    if (-not $line) { throw 'site= is missing from client/runelite/release.properties' }
    return ([Uri]($line -replace '^site=', '')).Host
}

function Set-Firewall($name, $port) {
    if (-not (Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $name -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow | Out-Null
    }
}

function Stop-Lotus {
    $svc = Get-Service $Service -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -ne 'Stopped') {
        Step 'Stopping the server (everyone online is saved first)'
        Stop-Service $Service
        Done 'Stopped'
    }
}

# ------------------------------------------------------------------------------------------------------------------

Assert-Admin
New-Item -ItemType Directory -Force $Root, $Tools, $Logs, $Backups | Out-Null

Step 'Installing tools into C:\lotus\tools'
$Jdk25 = Install-Jdk 25
$Jdk21 = Install-Jdk 21
Install-Git
$Shawl = Install-Shawl
$env:JAVA_HOME = $Jdk25
$env:Path = "$Jdk25\bin;$Tools\git\cmd;$env:Path"
# Also for new PowerShell windows, so java and git work when typed by hand.
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
foreach ($dir in "$Tools\git\cmd", "$Jdk25\bin") {
    if ($machinePath -notlike "*$dir*") { $machinePath = "$dir;$machinePath" }
}
[Environment]::SetEnvironmentVariable('Path', $machinePath, 'Machine')
[Environment]::SetEnvironmentVariable('JAVA_HOME', $Jdk25, 'Machine')
git config --global core.longpaths true

Step 'Getting the latest game code'
if (Test-Path "$Game\.git") {
    git -C $Game pull --ff-only
    if ($LASTEXITCODE -ne 0) { throw 'git pull failed. Local changes in C:\lotus\game? Remove them, then run the installer again.' }
} else {
    git clone $RepoUrl $Game
    if ($LASTEXITCODE -ne 0) { throw 'git clone failed.' }
}
Done (git -C $Game log -1 --format='%h %s')
$PublicHost = Get-ReleaseHost
Done "Server address: $PublicHost"

Step 'Downloading the game cache (first time only; about 200 MB)'
if (-not (Test-Path "$Game\.data\cache\242\disk")) { Invoke-Gradle @('setup') } else { Done 'Cache already present' }

Step 'Login key'
if (-not (Test-Path "$Game\.data\rsa\private.pem")) { Invoke-Gradle @('generateRsa'); Done 'New key made in C:\lotus\game\.data\rsa (keep it secret)' } else { Done 'Key already present' }

if (-not (Test-Path $Config)) {
    Step 'Server settings'
    $owner = ''
    while ($owner -notmatch '^[A-Za-z0-9 _-]{1,12}$') {
        $owner = (Read-Host 'Your in-game account name (it becomes the developer rank; 1-12 letters, digits, spaces, - or _)').Trim()
    }
    $toml = Get-Content "$Game\config\server.toml" -Raw
    $toml = $toml -replace 'developers = \["bot\*"\]', "developers = [`"$owner`"]"
    # UTF-8 without a byte-order mark (Windows PowerShell's -Encoding UTF8 writes one, which TOML rejects).
    [IO.File]::WriteAllText($Config, $toml, (New-Object Text.UTF8Encoding $false))
    Done "Saved $Config ($owner is developer; edit this file to change settings)"
}

Step 'Building the Lotus client (takes a few minutes)'
Invoke-Gradle @('setupClient', 'releaseClient', "-PforkJavaHome=$Jdk21")
$jar = Get-ChildItem "$Game\.data\client\fork\client-*-shaded.jar" | Select-Object -First 1
New-Item -ItemType Directory -Force "$Game\.data\update" | Out-Null
Copy-Item $jar.FullName "$Game\.data\update\Lotus.jar" -Force
Copy-Item $jar.FullName "$Root\Lotus.jar" -Force
Done 'Client built: C:\lotus\Lotus.jar'

Stop-Lotus

Step 'Building the server'
Invoke-Gradle @(':server:installDist')
Done 'Server built'

Step 'Opening the game ports in Windows Firewall (43594 game, 8080 website and downloads)'
Set-Firewall 'Lotus game' 43594
Set-Firewall 'Lotus http' 8080
Done 'Ports open'

Step 'Installing the "lotus" Windows service'
$ramGb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
$heapGb = [math]::Max(1, [math]::Min(4, [math]::Floor($ramGb / 2)))
$lib = "$Game\server\build\install\world-node\lib\*"
$java = "$Jdk25\bin\java.exe -Xms${heapGb}g -Xmx${heapGb}g -XX:+UseZGC -cp $lib tp.server.MainKt --public-host $PublicHost --config $Config"
# Stop: Ctrl+C first, so the server saves every player, waiting up to 60 s. Any other exit (a crash, or ::update
# reaching zero) restarts it after 10 s. Server output goes to C:\lotus\logs\server_rCURRENT.log.
$bin = "$Shawl run --name $Service --restart --restart-delay 10000 --stop-timeout 60000 --cwd $Game " +
    "--log-dir $Logs --log-cmd-as server --log-rotate bytes=10485760 --log-retain 5 -- $java"
if (Get-Service $Service -ErrorAction SilentlyContinue) {
    sc.exe config $Service binPath= $bin start= auto | Out-Null
} else {
    sc.exe create $Service binPath= $bin start= auto DisplayName= "Lotus game server" | Out-Null
}
if ($LASTEXITCODE -ne 0) { throw 'Could not create the lotus service.' }
sc.exe description $Service "Lotus world and hub. Stop it with Stop-Service lotus so everyone is saved." | Out-Null
# Run as its own virtual account, not SYSTEM: it may write only the game folder and the logs.
sc.exe config $Service obj= "NT SERVICE\$Service" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not set the service account.' }
icacls $Root /grant "NT SERVICE\${Service}:(OI)(CI)RX" /Q | Out-Null
icacls $Game /grant "NT SERVICE\${Service}:(OI)(CI)M" /Q | Out-Null
icacls $Logs /grant "NT SERVICE\${Service}:(OI)(CI)M" /Q | Out-Null
Done "Service set up (Java heap ${heapGb} GB of ${ramGb} GB RAM)"

Step 'Daily backup of accounts and saves (04:00, kept 30 days, in C:\lotus\backups)'
$backup = "`$d = Get-Date -Format yyyy-MM-dd; Copy-Item '$Game\.data\accounts\hub-store.json' ('$Backups\hub-store-' + `$d + '.json') -ErrorAction SilentlyContinue; " +
    "Get-ChildItem '$Backups' -Filter 'hub-store-*.json' | Where-Object { `$_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Item"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -Command `"$backup`""
Register-ScheduledTask -TaskName 'Lotus backup' -Action $action -Trigger (New-ScheduledTaskTrigger -Daily -At 4am) -User SYSTEM -Force | Out-Null
Done 'Backup scheduled'

Step 'Starting the server'
Start-Service $Service
$ok = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/jav_config.ws' -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
if (-not $ok) { throw "The server did not come up within 3 minutes. Look at the log: Get-Content $Logs\server_rCURRENT.log -Tail 50" }
Done 'Server is running'

Write-Host ''
Write-Host '============================================================' -ForegroundColor Green
Write-Host ' Lotus is installed and running.' -ForegroundColor Green
Write-Host '============================================================' -ForegroundColor Green
Write-Host " Players download the client:  http://${PublicHost}:8080/update/Lotus.jar"
Write-Host '   (they need Java 21 or newer; adoptium.net)'
Write-Host " Server log:      Get-Content $Logs\server_rCURRENT.log -Wait -Tail 50"
Write-Host ' Stop / start:    Stop-Service lotus   /   Start-Service lotus'
Write-Host " Settings:        $Config  (then: Restart-Service lotus)"
Write-Host ' Update later:    run the same install command again'
Write-Host ''
Write-Host ' If your VPS provider has its own firewall in its website, open TCP 43594 and 8080 there too.' -ForegroundColor Yellow
