# Lotus one-command installer and updater for Windows Server 2025 (docs/VPS.md, panel/docs/plan.md §1).
#
# Run in PowerShell opened with "Run as administrator":
#
#   irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/install-lotus.ps1 | iex
#
# Sets up, in C:\lotus:
#   - tools: Java 25 (server) and 21 (client build), MinGit, Shawl (service wrapper), PostgreSQL 17, Caddy (HTTPS),
#     and lotus-panel.exe (the control panel, built by CI);
#   - services, each under its own restricted account and started in this order:
#       lotus-postgresql  the database (127.0.0.1:5432 only)
#       lotus-hub         accounts, saves, world list, website and client download (:8080), panel API (127.0.0.1)
#       lotus             the game world (:43594)
#       lotus-panel       the control panel (127.0.0.1:8088)
#       lotus-caddy       HTTPS for the panel on https://lotusps.duckdns.org (:443)
#   - the Lotus client, built here with this server's own login key (served at :8080/update/Lotus.jar);
#   - firewall rules for 43594, 8080 and 443; a daily database backup;
#   - each build as a release in C:\lotus\releases (the services run it through C:\lotus\nodes), so the panel can
#     deploy signed releases and roll back.
# Run it again at any time to update: it pulls the latest code, rebuilds and restarts (players are disconnected for a
# few minutes; warn them with ::update 60 first).

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = 'C:\lotus'
$Tools = "$Root\tools"
$Game = "$Root\game"
$Logs = "$Root\logs"
$Backups = "$Root\backups"
$Releases = "$Root\releases"  # one folder per game release (panel brief section 6)
$Nodes = "$Root\nodes"        # one junction per service, pointing at the release it runs
$Config = "$Root\server.toml"
$OwnerFile = "$Root\owner.txt"
$PgData = "$Root\pgdata"
$PgDir = "$Root\pg"          # the database superuser password (Administrators only)
$Pki = "$Root\pki"           # the panel CA and its key (Administrators only)
$HubDir = "$Root\hub"        # the hub's certificates and database password
$PanelDir = "$Root\panel"
$CaddyDir = "$Root\caddy"
$RepoUrl = 'https://github.com/b8py79hjfp-boop/osrs-ps.git'
$PanelDomain = 'lotusps.duckdns.org'
$PanelExeUrl = 'https://github.com/b8py79hjfp-boop/osrs-ps/releases/download/panel-latest/lotus-panel.exe'

# Pinned downloads (version and SHA-256).
$ShawlUrl = 'https://github.com/mtkennerly/shawl/releases/download/v1.7.0/shawl-v1.7.0-win64.zip'
$ShawlSha256 = 'eaa4fed710e844cc7968fdb82e816d406ed89c4486ab34c3e5db2da7e5927923'
$PgUrl = 'https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip'
$PgSha256 = '6eabdf00d2893713b75db4336a23c3fdf505f056e217ec6e2e95d901750cfea3'
$CaddyUrl = 'https://github.com/caddyserver/caddy/releases/download/v2.11.9/caddy_2.11.9_windows_amd64.zip'
$CaddySha256 = 'ad93112d335ee687100c4c9f11bced16ca590377976b7fe7883882b9e797e472'

# Well-known SIDs, so the ACLs work on any Windows language.
$Admins = '*S-1-5-32-544'
$System = '*S-1-5-18'
$NetworkService = '*S-1-5-20'

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Done($text) { Write-Host "    $text" -ForegroundColor Green }
function Warn($text) { Write-Host "    $text" -ForegroundColor Yellow }

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

function Assert-Sha256($file, $expected) {
    if ((Get-FileHash $file -Algorithm SHA256).Hash -ne $expected.ToUpper()) {
        Remove-Item $file -Force
        throw "$file failed its checksum; run the installer again."
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

function Install-Zip($name, $url, $sha, $dest, $marker) {
    if (Test-Path $marker) { Done "$name already installed"; return }
    $zip = "$Tools\$name.zip"
    Get-File $url $zip
    Assert-Sha256 $zip $sha
    Expand-To $zip $dest
    Done "$name installed"
}

function Install-Shawl {
    $exe = "$Tools\shawl.exe"
    if (Test-Path $exe) { Done 'Shawl already installed'; return $exe }
    $zip = "$Tools\shawl.zip"
    Get-File $ShawlUrl $zip
    Assert-Sha256 $zip $ShawlSha256
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    Remove-Item $zip -Force
    Done 'Shawl installed (service wrapper)'
    return $exe
}

# The control panel binary from CI (release "panel-latest"). Returns $false when it isn't available yet: the game is
# installed anyway and the panel comes with a later run.
function Install-PanelExe {
    $exe = "$Tools\lotus-panel.exe"
    $new = "$Tools\lotus-panel.new.exe"
    try {
        Get-File $PanelExeUrl $new
        Get-File "$PanelExeUrl.sha256" "$new.sha256"
        $expected = (Get-Content "$new.sha256" -Raw).Trim()
        Remove-Item "$new.sha256" -Force
        Assert-Sha256 $new $expected
    } catch {
        Warn "The control panel isn't available yet ($($_.Exception.Message)); skipping it this time."
        return $false
    }
    Move-Item $new $exe -Force
    Done 'Control panel downloaded'
    return $true
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

function Write-Text($path, $text) {
    # UTF-8 without a byte-order mark (Windows PowerShell's -Encoding UTF8 writes one, which TOML and PEM readers reject).
    if (-not $text.EndsWith("`n")) { $text += "`n" }
    [IO.File]::WriteAllText($path, $text, (New-Object Text.UTF8Encoding $false))
}

function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return ([Convert]::ToBase64String($bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

# A directory only Administrators, SYSTEM and the given accounts (icacls grants) can open; nothing inherited.
function Protect-Dir($dir, [string[]] $grants) {
    New-Item -ItemType Directory -Force $dir | Out-Null
    $acl = @("${Admins}:(OI)(CI)F", "${System}:(OI)(CI)F") + $grants
    icacls $dir /inheritance:r /grant:r @acl /Q | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not set permissions on $dir" }
}

function Set-Firewall($name, $port) {
    if (-not (Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $name -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow | Out-Null
    }
}

# A Shawl-wrapped service under its own virtual account (NT SERVICE\<name>). Stop sends Ctrl+C and waits 60 s (the
# game saves everyone); any other exit restarts it after 10 s. Output goes to C:\lotus\logs\<name>\<name>_rCURRENT.log.
function Set-ShawlService($name, $display, $cwd, $command, $depends) {
    $bin = "$Shawl run --name $name --restart --restart-delay 10000 --stop-timeout 60000 --cwd $cwd " +
        "--log-dir $Logs\$name --log-cmd-as $name --log-rotate bytes=10485760 --log-retain 5 -- $command"
    if (Get-Service $name -ErrorAction SilentlyContinue) {
        sc.exe config $name binPath= $bin start= auto | Out-Null
    } else {
        sc.exe create $name binPath= $bin start= auto DisplayName= $display | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { throw "Could not create the $name service." }
    sc.exe config $name obj= "NT SERVICE\$name" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not set the $name service account." }
    $dep = if ($depends) { $depends } else { '/' }
    sc.exe config $name depend= $dep | Out-Null
}

function Stop-IfRunning($name) {
    $svc = Get-Service $name -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -ne 'Stopped') { Stop-Service $name -Force; Done "Stopped $name" }
}

function Wait-Http($url, $what, $match) {
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200 -and (-not $match -or $r.Content -match $match)) { Done "$what is up"; return }
        } catch { }
    }
    throw "$what did not come up within 3 minutes. Look at its log in $Logs."
}

function Invoke-Psql($sql) {
    $out = & "$Tools\pgsql\bin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -tAc $sql
    if ($LASTEXITCODE -ne 0) { throw "Database command failed: $sql" }
    return $out
}

# ------------------------------------------------------------------------------------------------------------------

Assert-Admin
New-Item -ItemType Directory -Force $Root, $Tools, $Logs, $Backups | Out-Null

Step 'Installing tools into C:\lotus\tools'
$Jdk25 = Install-Jdk 25
$Jdk21 = Install-Jdk 21
Install-Git
$Shawl = Install-Shawl
Install-Zip 'PostgreSQL 17' $PgUrl $PgSha256 "$Tools\pgsql" "$Tools\pgsql\bin\pg_ctl.exe"
Install-Zip 'Caddy' $CaddyUrl $CaddySha256 "$Tools\caddy" "$Tools\caddy\caddy.exe"
$HavePanel = Install-PanelExe
$env:JAVA_HOME = $Jdk25
$env:Path = "$Jdk25\bin;$Tools\git\cmd;$Tools\pgsql\bin;$env:Path"
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

Step 'Server settings'
if (-not (Test-Path $Config)) {
    $owner = ''
    while ($owner -notmatch '^[A-Za-z0-9 _-]{1,12}$') {
        $owner = (Read-Host 'Your in-game account name (it becomes the developer rank; 1-12 letters, digits, spaces, - or _)').Trim()
    }
    $toml = Get-Content "$Game\config\server.toml" -Raw
    $toml = $toml -replace 'developers = \["bot\*"\]', "developers = [`"$owner`"]"
    Write-Text $Config $toml
}
if (-not (Test-Path $OwnerFile)) {
    # Installs from before the hub split: the name is in server.toml's developers list.
    $m = Select-String -Path $Config -Pattern '^developers = \["([^"]+)"' | Select-Object -First 1
    if (-not $m) { throw "Put your account name in $OwnerFile (one line), then run the installer again." }
    Write-Text $OwnerFile $m.Matches[0].Groups[1].Value
}
$Owner = (Get-Content $OwnerFile -Raw).Trim()
Done "Settings: $Config (developer: $Owner)"

Step 'Building the Lotus client (takes a few minutes)'
Invoke-Gradle @('setupClient', 'releaseClient', "-PforkJavaHome=$Jdk21")
$jar = Get-ChildItem "$Game\.data\client\fork\client-*-shaded.jar" | Select-Object -First 1
New-Item -ItemType Directory -Force "$Game\.data\update" | Out-Null
Copy-Item $jar.FullName "$Game\.data\update\Lotus.jar" -Force
Copy-Item $jar.FullName "$Root\Lotus.jar" -Force
Done 'Client built: C:\lotus\Lotus.jar'

Step 'Database (PostgreSQL 17, local only)'
Protect-Dir $PgDir @()
if (-not (Test-Path "$PgDir\superuser")) { Write-Text "$PgDir\superuser" (New-Secret) }
$superPw = (Get-Content "$PgDir\superuser" -Raw).Trim()
# pgpass for the backup task and this script: host:port:db:user:password
Write-Text "$PgDir\pgpass.conf" "127.0.0.1:5432:*:postgres:$superPw`n"
$env:PGPASSFILE = "$PgDir\pgpass.conf"
if (-not (Test-Path "$PgData\PG_VERSION")) {
    $pwFile = "$PgDir\initdb.pw"
    Write-Text $pwFile $superPw
    & "$Tools\pgsql\bin\initdb.exe" -D $PgData -U postgres --pwfile=$pwFile -A scram-sha-256 -E UTF8 --locale=C | Out-Host
    Remove-Item $pwFile -Force
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed.' }
    Add-Content -Path "$PgData\postgresql.conf" -Value "`nlisten_addresses = '127.0.0.1'`nport = 5432`n"
    Done 'Database created'
}
Protect-Dir $PgData @("${NetworkService}:(OI)(CI)M")
if (-not (Get-Service 'lotus-postgresql' -ErrorAction SilentlyContinue)) {
    & "$Tools\pgsql\bin\pg_ctl.exe" register -N lotus-postgresql -D $PgData -S auto | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Could not register the database service.' }
}
Start-Service lotus-postgresql
for ($i = 0; $i -lt 30; $i++) {
    & "$Tools\pgsql\bin\pg_isready.exe" -h 127.0.0.1 -p 5432 | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 1
}
Protect-Dir $HubDir @()
New-Item -ItemType Directory -Force "$HubDir\certs" | Out-Null
if (-not (Test-Path "$HubDir\db-password")) { Write-Text "$HubDir\db-password" (New-Secret) }
$hubPw = (Get-Content "$HubDir\db-password" -Raw).Trim()
$panelDbPw = if (Test-Path "$PgDir\panel-db-password") { (Get-Content "$PgDir\panel-db-password" -Raw).Trim() } else { New-Secret }
Write-Text "$PgDir\panel-db-password" $panelDbPw
foreach ($role in @(@('lotus_hub', $hubPw, 'lotus'), @('lotus_panel', $panelDbPw, 'lotus_panel'))) {
    $name = $role[0]; $pw = $role[1]; $db = $role[2]
    if (-not (Invoke-Psql "SELECT 1 FROM pg_roles WHERE rolname = '$name'")) { Invoke-Psql "CREATE ROLE $name LOGIN PASSWORD '$pw'" | Out-Null }
    else { Invoke-Psql "ALTER ROLE $name PASSWORD '$pw'" | Out-Null }
    if (-not (Invoke-Psql "SELECT 1 FROM pg_database WHERE datname = '$db'")) { Invoke-Psql "CREATE DATABASE $db OWNER $name" | Out-Null }
}
# Backups (panel brief section 7): a read-only role the panel runs pg_dump as.
$backupPw = if (Test-Path "$PgDir\backup-db-password") { (Get-Content "$PgDir\backup-db-password" -Raw).Trim() } else { New-Secret }
Write-Text "$PgDir\backup-db-password" $backupPw
if (-not (Invoke-Psql "SELECT 1 FROM pg_roles WHERE rolname = 'lotus_backup'")) { Invoke-Psql "CREATE ROLE lotus_backup LOGIN PASSWORD '$backupPw'" | Out-Null }
else { Invoke-Psql "ALTER ROLE lotus_backup PASSWORD '$backupPw'" | Out-Null }
Invoke-Psql 'GRANT pg_read_all_data TO lotus_backup' | Out-Null
Done 'Databases lotus (game) and lotus_panel ready' 

Step 'Panel certificates and settings'
if ($HavePanel) {
    Protect-Dir $Pki @()
    if (-not (Test-Path "$Pki\ca.pem")) { & "$Tools\lotus-panel.exe" certs --out $Pki | Out-Host; if ($LASTEXITCODE -ne 0) { throw 'Could not make the panel certificates.' } }
    Copy-Item "$Pki\ca.pem", "$Pki\hub.pem", "$Pki\hub.key.pem" "$HubDir\certs\" -Force
    New-Item -ItemType Directory -Force "$PanelDir\certs" | Out-Null
    Copy-Item "$Pki\ca.pem", "$Pki\panel.pem" "$PanelDir\certs\" -Force
    $caPath = "$PanelDir\certs\ca.pem" -replace '\\', '/'
    $certPath = "$PanelDir\certs\panel.pem" -replace '\\', '/'
    $secretsPath = "$PanelDir\secrets" -replace '\\', '/'
    # The release signing key (panel brief section 6): made here once; its secret half and password stay in the
    # Administrators-only PKI folder until the owner copies them into GitHub (see the message at the end).
    if (-not (Test-Path "$Pki\release.key")) {
        Write-Text "$Pki\release-key-password" (New-Secret)
        $env:LOTUS_RELEASE_KEY_PASSWORD = (Get-Content "$Pki\release-key-password" -Raw).Trim()
        & "$Tools\lotus-panel.exe" release keygen --out "$Pki\release.key" | Out-Null
        Remove-Item Env:\LOTUS_RELEASE_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Could not make the release signing key.' }
        $NewReleaseKey = $true
    }
    $releaseKey = ((Get-Content "$Pki\release.key.pub") | Where-Object { $_ -match '^RW' } | Select-Object -First 1).Trim()
    Write-Text "$PanelDir\panel.toml" @"
# Lotus control panel (panel/docs/brief.md section 9). Secrets live in [paths] secrets, never here.
[server]
bind = "127.0.0.1:8088"
public_url = "https://$PanelDomain"
allowed_networks = []

[hub]
endpoint = "https://127.0.0.1:50051"
ca = "$caPath"
cert = "$certPath"
key_credential = "panel-hub-key"

[database]
url_credential = "panel-db-url"

[paths]
secrets = "$secretsPath"

[deploy]
# The release signing key's public half (deploy/release-key.pub in the repository); empty = deploys are off.
public_key = "$releaseKey"
"@
    Protect-Dir "$PanelDir\secrets" @()
    & "$Tools\lotus-panel.exe" --config "$PanelDir\panel.toml" secret set panel-hub-key --from-file "$Pki\panel.key.pem" | Out-Host
    $dbUrl = "postgres://lotus_panel:$panelDbPw@127.0.0.1:5432/lotus_panel"
    $dbUrl | & "$Tools\lotus-panel.exe" --config "$PanelDir\panel.toml" secret set panel-db-url | Out-Host
    $backupPw | & "$Tools\lotus-panel.exe" --config "$PanelDir\panel.toml" secret set backup-db-password | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Could not store the panel secrets.' }
    # The `lotus-panel ctl` token: only Administrators and the panel can read it (panel brief section 3, CLI twin).
    if (-not (Test-Path "$PanelDir\secrets\ctl-token")) {
        (New-Secret) | & "$Tools\lotus-panel.exe" --config "$PanelDir\panel.toml" secret set ctl-token | Out-Host
    }
    New-Item -ItemType Directory -Force $CaddyDir, "$CaddyDir\data" | Out-Null
    Write-Text "$CaddyDir\Caddyfile" @"
# HTTPS for the Lotus panel (panel brief section 8): only port 443. The certificate comes from Let's Encrypt over
# TLS-ALPN on 443, so port 80 stays closed.
{
	admin off
}

$PanelDomain {
	tls {
		issuer acme {
			disable_http_challenge
		}
	}
	encode zstd gzip
	reverse_proxy 127.0.0.1:8088
}
"@
    Done "Panel settings in $PanelDir"
} else {
    Done 'Skipped (panel not downloaded)'
}

Step 'Stopping the game (everyone online is saved first)'
foreach ($s in 'lotus-caddy', 'lotus-panel', 'lotus', 'lotus-hub') { Stop-IfRunning $s }

Step 'Building the server and the hub'
Invoke-Gradle @(':server:installDist', ':hub:installDist')
Done 'Built'

Step 'Installing the build as a release'
# This build becomes release src-<commit> (the installer trusts what it built itself); the services run it through
# their junctions, and the panel's Deploys page can roll back to it later.
$short = (git -C $Game rev-parse --short=7 HEAD).Trim()
$version = "src-$short"
New-Item -ItemType Directory -Force $Releases, $Nodes, "$Releases\incoming" | Out-Null
& "$Game\deploy\windows\make-release.ps1" -Repo $Game -Version $version -Out "$Releases\$version" -Commits @(git -C $Game log --format=%s -n 20)
Write-Text "$Releases\$version\.verified" 'built by the installer'
foreach ($svc in 'lotus-hub', 'lotus') {
    $link = "$Nodes\$svc"
    if (Test-Path $link) { cmd /c rmdir "$link" | Out-Null }
    New-Item -ItemType Junction -Path $link -Target "$Releases\$version" | Out-Null
}
$historyFile = "$Releases\history.json"
$history = @(if (Test-Path $historyFile) { Get-Content $historyFile -Raw | ConvertFrom-Json } else { @() })
$entry = [pscustomobject]@{ version = $version; at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); actor = 'installer' }
$history = @($entry) + @($history | Where-Object { $_.version -ne $version }) | Select-Object -First 50
[IO.File]::WriteAllText($historyFile, (ConvertTo-Json @($history) -Depth 3), (New-Object Text.UTF8Encoding $false))
Done "Release $version is live"

Step 'Opening ports in Windows Firewall (43594 game, 8080 website and downloads, 443 panel)'
Set-Firewall 'Lotus game' 43594
Set-Firewall 'Lotus http' 8080
if ($HavePanel) { Set-Firewall 'Lotus panel https' 443 }
Done 'Ports open'

Step 'Installing the services'
$ramGb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
$worldHeap = [math]::Max(1, [math]::Min(4, [math]::Floor($ramGb * 0.4)))
$hubHeap = 1
$java = "$Jdk25\bin\java.exe"
$hubCerts = "$HubDir\certs"
$hubArgs = "--db jdbc:postgresql://127.0.0.1:5432/lotus --db-user lotus_hub --db-password-file $HubDir\db-password " +
    "--http-port 8080 --grpc-port 43600 --auto-register --bootstrap developer=$($Owner.Replace(' ', '_')) --public-url http://${PublicHost}:8080"
if ($HavePanel) { $hubArgs += " --panel-cert $hubCerts\hub.pem --panel-key $hubCerts\hub.key.pem --panel-ca $hubCerts\ca.pem" }
Set-ShawlService 'lotus-hub' 'Lotus hub' $Game "$java -Xms${hubHeap}g -Xmx${hubHeap}g -cp $Nodes\lotus-hub\hub\lib\* tp.hub.HubMainKt $hubArgs" 'lotus-postgresql'
Set-ShawlService 'lotus' 'Lotus game world' $Game ("$java -Xms${worldHeap}g -Xmx${worldHeap}g -XX:+UseZGC -cp $Nodes\lotus\world\lib\* " +
    "tp.server.MainKt --hub 127.0.0.1:43600 --public-host $PublicHost --config $Config") 'lotus-hub'
if ($HavePanel) {
    Set-ShawlService 'lotus-panel' 'Lotus control panel' $PanelDir "$Tools\lotus-panel.exe --config $PanelDir\panel.toml serve" 'lotus-hub'
    Set-ShawlService 'lotus-caddy' 'Lotus panel HTTPS (Caddy)' $CaddyDir "$Tools\caddy\caddy.exe run --config $CaddyDir\Caddyfile --adapter caddyfile" 'lotus-panel'
    # Caddy keeps its certificates here instead of the service account's profile.
    $caddyKey = 'HKLM:\SYSTEM\CurrentControlSet\Services\lotus-caddy'
    Set-ItemProperty -Path $caddyKey -Name Environment -Type MultiString -Value @("XDG_DATA_HOME=$CaddyDir\data", "XDG_CONFIG_HOME=$CaddyDir\data")
}

Step 'Permissions (each service reads and writes only what it needs)'
$svcAccounts = @('lotus', 'lotus-hub') + $(if ($HavePanel) { @('lotus-panel', 'lotus-caddy') } else { @() })
foreach ($a in $svcAccounts) { icacls $Root /grant "NT SERVICE\${a}:(OI)(CI)RX" /Q | Out-Null }
icacls $Tools /grant "${NetworkService}:(OI)(CI)RX" /Q | Out-Null
foreach ($a in $svcAccounts) {
    New-Item -ItemType Directory -Force "$Logs\$a" | Out-Null
    icacls "$Logs\$a" /grant "NT SERVICE\${a}:(OI)(CI)M" /Q | Out-Null
}
# The game folder holds the login key: only the world (writes .data) and the hub (reads) may open it.
Protect-Dir $Game @('NT SERVICE\lotus:(OI)(CI)M', 'NT SERVICE\lotus-hub:(OI)(CI)RX')
Protect-Dir $HubDir @('NT SERVICE\lotus-hub:(OI)(CI)R')
if ($HavePanel) {
    Protect-Dir "$PanelDir\secrets" @('NT SERVICE\lotus-panel:(OI)(CI)R')
    icacls $CaddyDir /grant 'NT SERVICE\lotus-caddy:(OI)(CI)M' /Q | Out-Null
    # The panel reads every service's log, the diagnostics dumps and Caddy's certificate (expiry check).
    icacls $Logs /grant 'NT SERVICE\lotus-panel:(OI)(CI)RX' /Q | Out-Null
    New-Item -ItemType Directory -Force "$Game\.data\dumps" | Out-Null
    icacls "$Game\.data\dumps" /grant 'NT SERVICE\lotus-panel:(OI)(CI)M' /Q | Out-Null
    icacls $Game /grant 'NT SERVICE\lotus-panel:RX' /Q | Out-Null
    icacls "$Game\.data" /grant 'NT SERVICE\lotus-panel:RX' /Q | Out-Null
    icacls "$CaddyDir\data" /grant 'NT SERVICE\lotus-panel:(OI)(CI)RX' /Q | Out-Null
    # Deploys (panel brief section 6): the panel unpacks verified releases and switches the service junctions; it
    # writes pre-deploy and nightly database backups.
    icacls $Releases /grant 'NT SERVICE\lotus-panel:(OI)(CI)M' /Q | Out-Null
    icacls $Nodes /grant 'NT SERVICE\lotus-panel:(OI)(CI)M' /Q | Out-Null
    icacls $Backups /grant 'NT SERVICE\lotus-panel:(OI)(CI)M' /Q | Out-Null
    # Service control (panel brief section 3): the panel may query, start and stop exactly these services, nothing else.
    $panelSid = (New-Object Security.Principal.NTAccount 'NT SERVICE\lotus-panel').Translate([Security.Principal.SecurityIdentifier]).Value
    foreach ($svc in 'lotus-postgresql', 'lotus-hub', 'lotus') {
        $sddl = ((sc.exe sdshow $svc) | Where-Object { $_ -match '^D:' }) -join ''
        if (-not $sddl) { throw "Could not read the permissions of $svc." }
        if ($sddl -notlike "*$panelSid*") {
            # CC query config, LC query status, SW list dependents, RP start, WP stop, LO interrogate, RC read.
            $new = $sddl -replace '^D:', "D:(A;;CCLCSWRPWPLORC;;;$panelSid)"
            sc.exe sdset $svc $new | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not let the panel control $svc." }
        }
    }
}
Done 'Permissions set'

Step 'Daily database backup (04:00, kept 30 days, in C:\lotus\backups)'
$backup = "`$env:PGPASSFILE = '$PgDir\pgpass.conf'; `$d = Get-Date -Format yyyy-MM-dd; " +
    "& '$Tools\pgsql\bin\pg_dump.exe' -h 127.0.0.1 -U postgres -Fc -f ('$Backups\lotus-' + `$d + '.dump') lotus; " +
    "& '$Tools\pgsql\bin\pg_dump.exe' -h 127.0.0.1 -U postgres -Fc -f ('$Backups\lotus_panel-' + `$d + '.dump') lotus_panel; " +
    "Get-ChildItem '$Backups' -Filter '*.dump' | Where-Object { `$_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Item"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -Command `"$backup`""
Register-ScheduledTask -TaskName 'Lotus backup' -Action $action -Trigger (New-ScheduledTaskTrigger -Daily -At 4am) -User SYSTEM -Force | Out-Null
Done 'Backup scheduled'

Step 'Starting everything'
Start-Service lotus-hub
Wait-Http 'http://127.0.0.1:8080/health' 'The hub'
Start-Service lotus
Wait-Http 'http://127.0.0.1:8080/runelite/worlds.js' 'The game world' '"id":1'
if ($HavePanel) {
    Start-Service lotus-panel
    Wait-Http 'http://127.0.0.1:8088/healthz' 'The control panel'
    Start-Service lotus-caddy
    Done "Caddy started; the HTTPS certificate for $PanelDomain arrives within a minute or two"
}

Write-Host ''
Write-Host '============================================================' -ForegroundColor Green
Write-Host ' Lotus is installed and running.' -ForegroundColor Green
Write-Host '============================================================' -ForegroundColor Green
Write-Host " Players download the client:  http://${PublicHost}:8080/update/Lotus.jar"
Write-Host '   (they need Java 21 or newer; adoptium.net)'
if ($HavePanel) { Write-Host " Control panel:   https://$PanelDomain" }
Write-Host " Logs:            $Logs\<service>\<service>_rCURRENT.log"
Write-Host ' Restart the game: Restart-Service lotus   (the hub and database keep running)'
Write-Host " Settings:        $Config  (then: Restart-Service lotus)"
Write-Host ' Update later:    run the same install command again'
Write-Host ''
Write-Host ' If your VPS provider has its own firewall in its website, open TCP 43594, 8080 and 443 there too.' -ForegroundColor Yellow
if ($HavePanel -and $NewReleaseKey) {
    Write-Host ''
    Write-Host ' One-time step for panel deploys: let GitHub sign releases with this server''s key.' -ForegroundColor Yellow
    Write-Host '   On github.com open the repository > Settings > Secrets and variables > Actions > New repository secret:'
    Write-Host "     LOTUS_RELEASE_KEY           = the text of $Pki\release.key   (notepad $Pki\release.key)"
    Write-Host "     LOTUS_RELEASE_KEY_PASSWORD  = the text of $Pki\release-key-password"
    Write-Host '   Then Actions > game-release > Run workflow builds a release the panel can deploy.'
}
