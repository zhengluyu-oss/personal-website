<#
.SYNOPSIS
  Local build + SCP upload for backend / blog / admin (Plan A).

.EXAMPLE
  .\scripts\deploy-to-server.ps1
  .\scripts\deploy-to-server.ps1 -Only backend
  .\scripts\deploy-to-server.ps1 -Only blog,admin
  .\scripts\deploy-to-server.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$ServerHost = "82.156.90.186",
    [string]$User = "root",
    [int]$Port = 22,
    [string]$IdentityFile = "C:\Users\Administrator\.ssh\id_ed25519_personal",
    [string]$RemoteApp = "/home/wwwroot/zhengluyu-website/app",
    [string]$RemoteBlog = "/home/wwwroot/zhengluyu-website/blog",
    [string]$RemoteAdmin = "/home/wwwroot/zhengluyu-website/admin",
    [string]$SystemdUnit = "zhengluyu-blog",
    [string[]]$Only = @("backend", "blog", "admin"),
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name. Install it and ensure it is on PATH."
    }
}

function Normalize-Only([string[]]$Values) {
    $set = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($v in $Values) {
        foreach ($part in ($v -split ",")) {
            $t = $part.Trim()
            if (-not $t) { continue }
            if ($t -notin @("backend", "blog", "admin")) {
                throw "Invalid -Only value '$t'. Use backend, blog, admin."
            }
            [void]$set.Add($t.ToLowerInvariant())
        }
    }
    if ($set.Count -eq 0) {
        throw "-Only cannot be empty."
    }
    return @($set)
}

function Test-Should([string]$Name, [string[]]$Selected) {
    return $Selected -contains $Name
}

function Resolve-NativeExe([string]$Name) {
    # Prefer .cmd/.exe over .ps1 — Start-Process cannot run PowerShell shim scripts.
    $all = @(Get-Command $Name -All -ErrorAction SilentlyContinue)
    $app = $all | Where-Object { $_.CommandType -eq "Application" } | Select-Object -First 1
    if ($app) { return $app.Source }
    $cmdPath = Join-Path (Split-Path -Parent ((Get-Command $Name -ErrorAction SilentlyContinue).Source)) "$Name.cmd"
    if ((Test-Path $cmdPath)) { return $cmdPath }
    throw "Cannot resolve executable for '$Name' (need .cmd/.exe, not only .ps1)."
}

function Invoke-Native([string]$File, [string[]]$CmdArgs, [string]$WorkDir = $null) {
    Write-Host ("  > {0} {1}" -f $File, ($CmdArgs -join " "))
    $exe = if ($File -in @("ssh", "scp", "tar", "mvn", "pnpm", "npm")) {
        Resolve-NativeExe $File
    }
    else {
        $File
    }
    # Quote args that contain spaces for ArgumentList safety
    $argList = @($CmdArgs | ForEach-Object {
        $a = [string]$_
        if ($a -match '\s') { '"{0}"' -f ($a -replace '"', '\"') } else { $a }
    })
    $startParams = @{
        FilePath     = $exe
        ArgumentList = $argList
        NoNewWindow  = $true
        Wait         = $true
        PassThru     = $true
    }
    if ($WorkDir) { $startParams.WorkingDirectory = $WorkDir }
    $p = Start-Process @startParams
    if ($null -eq $p.ExitCode -or $p.ExitCode -ne 0) {
        throw ("Command failed (exit {0}): {1} {2}" -f $p.ExitCode, $File, ($CmdArgs -join " "))
    }
}

function Invoke-Ssh([string]$RemoteCommand) {
    $sshArgs = @(
        "-i", $IdentityFile,
        "-p", "$Port",
        "-o", "IdentitiesOnly=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "${User}@${ServerHost}",
        $RemoteCommand
    )
    Invoke-Native "ssh" $sshArgs
}

function Invoke-Scp([string]$LocalFile, [string]$RemoteTarget) {
    $scpArgs = @(
        "-i", $IdentityFile,
        "-P", "$Port",
        "-o", "IdentitiesOnly=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        $LocalFile,
        "${User}@${ServerHost}:${RemoteTarget}"
    )
    Invoke-Native "scp" $scpArgs
}

# --- resolve paths ---
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalRoot = Resolve-Path (Join-Path $ScriptDir "..")
$BackendDir = Join-Path $LocalRoot "blog-backend"
$BlogDir = Join-Path $LocalRoot "blog-frontend\kuailemao-blog"
$AdminDir = Join-Path $LocalRoot "blog-frontend\kuailemao-admin"

$Selected = Normalize-Only $Only
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stageRoot = Join-Path $env:TEMP "zhengluyu-deploy-$stamp"
$archivePath = Join-Path $env:TEMP "zhengluyu-deploy-$stamp.tar.gz"
$remoteArchive = "/tmp/zhengluyu-deploy-$stamp.tar.gz"

Write-Step "Deploy plan"
Write-Host "  LocalRoot : $LocalRoot"
Write-Host "  Server    : ${User}@${ServerHost}:$Port"
Write-Host "  Targets   : $($Selected -join ', ')"
Write-Host "  SkipBuild : $SkipBuild"

try {
    Write-Step "Check local tools"
    Assert-Command "ssh"
    Assert-Command "scp"
    Assert-Command "tar"
    if (-not (Test-Path $IdentityFile)) {
        throw "SSH private key not found: $IdentityFile"
    }
    if (-not $SkipBuild) {
        if (Test-Should "backend" $Selected) { Assert-Command "mvn" }
        if ((Test-Should "blog" $Selected) -or (Test-Should "admin" $Selected)) {
            Assert-Command "pnpm"
        }
    }

    New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null

    # ----- build -----
    if (-not $SkipBuild) {
        if (Test-Should "backend" $Selected) {
            Write-Step "Build backend (Maven)"
            Invoke-Native "mvn" @("-DskipTests", "package") $BackendDir
        }

        if (Test-Should "blog" $Selected) {
            Write-Step "Build blog frontend"
            $blogEnv = Join-Path $BlogDir ".env.production"
            if (-not (Test-Path $blogEnv)) {
                throw "Missing $blogEnv (required for production build)."
            }
            if (-not (Test-Path (Join-Path $BlogDir "node_modules"))) {
                Invoke-Native "pnpm" @("install") $BlogDir
            }
            Invoke-Native "pnpm" @("build") $BlogDir
        }

        if (Test-Should "admin" $Selected) {
            Write-Step "Build admin frontend"
            $adminEnv = Join-Path $AdminDir ".env.production"
            if (-not (Test-Path $adminEnv)) {
                throw "Missing $adminEnv (required for production build)."
            }
            $adminEnvText = Get-Content $adminEnv -Raw
            if ($adminEnvText -notmatch '(?m)^VITE_APP_BASE_API\s*=\s*\S') {
                Write-Host "  WARNING: VITE_APP_BASE_API looks empty in admin .env.production" -ForegroundColor Yellow
            }
            if (-not (Test-Path (Join-Path $AdminDir "node_modules"))) {
                Invoke-Native "pnpm" @("install") $AdminDir
            }
            Invoke-Native "pnpm" @("build") $AdminDir
        }
    }
    else {
        Write-Step "SkipBuild enabled — packing existing artifacts only"
    }

    # ----- stage artifacts -----
    Write-Step "Stage artifacts"
    if (Test-Should "backend" $Selected) {
        $jar = Get-ChildItem -Path (Join-Path $BackendDir "target") -Filter "*.jar" |
            Where-Object { $_.Name -notmatch "\.original$" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $jar) {
            throw "Backend jar not found under blog-backend/target. Build first or remove -SkipBuild."
        }
        $appStage = Join-Path $stageRoot "app"
        New-Item -ItemType Directory -Path $appStage -Force | Out-Null
        Copy-Item $jar.FullName (Join-Path $appStage "blog-backend.jar") -Force
        Write-Host "  jar: $($jar.Name) -> app/blog-backend.jar"
    }

    if (Test-Should "blog" $Selected) {
        $blogDist = Join-Path $BlogDir "dist"
        if (-not (Test-Path $blogDist)) {
            throw "Blog dist not found: $blogDist"
        }
        $blogStage = Join-Path $stageRoot "blog"
        New-Item -ItemType Directory -Path $blogStage -Force | Out-Null
        Copy-Item -Path (Join-Path $blogDist "*") -Destination $blogStage -Recurse -Force
        Write-Host "  blog dist staged"
    }

    if (Test-Should "admin" $Selected) {
        $adminDist = Join-Path $AdminDir "dist"
        if (-not (Test-Path $adminDist)) {
            throw "Admin dist not found: $adminDist"
        }
        $adminStage = Join-Path $stageRoot "admin"
        New-Item -ItemType Directory -Path $adminStage -Force | Out-Null
        Copy-Item -Path (Join-Path $adminDist "*") -Destination $adminStage -Recurse -Force
        Write-Host "  admin dist staged"
    }

    Write-Step "Create archive"
    # tar from stageRoot so archive root contains app/ blog/ admin/
    Invoke-Native "tar" @("-czf", $archivePath, "-C", $stageRoot, ".")

    Write-Step "Upload archive"
    Invoke-Scp $archivePath $remoteArchive

    Write-Step "Remote extract & place files"
    # Upload a real bash script — joining "then/else" with "; " breaks bash ("then;").
    $remotePlaceSh = @"
#!/bin/bash
set -euo pipefail
REMOTE_APP='$RemoteApp'
REMOTE_BLOG='$RemoteBlog'
REMOTE_ADMIN='$RemoteAdmin'
REMOTE_ARCHIVE='$remoteArchive'
mkdir -p "`$REMOTE_APP" "`$REMOTE_BLOG" "`$REMOTE_ADMIN"
TMPDIR=`$(mktemp -d /tmp/zhengluyu-deploy-XXXXXX)
tar -xzf "`$REMOTE_ARCHIVE" -C "`$TMPDIR"
if [ -d "`$TMPDIR/app" ]; then
  cp -f "`$TMPDIR/app/"*.jar "`$REMOTE_APP"/
fi
if [ -d "`$TMPDIR/blog" ]; then
  find "`$REMOTE_BLOG" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -a "`$TMPDIR/blog/." "`$REMOTE_BLOG"/
fi
if [ -d "`$TMPDIR/admin" ]; then
  find "`$REMOTE_ADMIN" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -a "`$TMPDIR/admin/." "`$REMOTE_ADMIN"/
fi
rm -rf "`$TMPDIR" "`$REMOTE_ARCHIVE"
echo REMOTE_PLACE_OK
"@
    $remotePlaceSh = $remotePlaceSh -replace "`r`n", "`n"
    $localPlaceSh = Join-Path $env:TEMP "zhengluyu-place-$stamp.sh"
    [System.IO.File]::WriteAllText($localPlaceSh, $remotePlaceSh)
    try {
        Invoke-Scp $localPlaceSh "/tmp/zhengluyu-place-$stamp.sh"
        Invoke-Ssh "bash /tmp/zhengluyu-place-$stamp.sh; rm -f /tmp/zhengluyu-place-$stamp.sh"
    }
    finally {
        Remove-Item $localPlaceSh -Force -ErrorAction SilentlyContinue
    }

    Write-Step "Remote restart (best-effort)"
    $remoteRestartSh = @"
#!/bin/bash
set +e
UNIT='$SystemdUnit'
if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files | grep -q "^`${UNIT}\.service"; then
  systemctl restart "`$UNIT" && echo SYSTEMD_RESTARTED || echo SYSTEMD_RESTART_FAILED
else
  echo SYSTEMD_SKIPPED
fi
if command -v nginx >/dev/null 2>&1; then
  nginx -t && nginx -s reload && echo NGINX_RELOADED || echo NGINX_RELOAD_FAILED
else
  echo NGINX_SKIPPED
fi
"@
    $remoteRestartSh = $remoteRestartSh -replace "`r`n", "`n"
    $localRestartSh = Join-Path $env:TEMP "zhengluyu-restart-$stamp.sh"
    [System.IO.File]::WriteAllText($localRestartSh, $remoteRestartSh)
    try {
        Invoke-Scp $localRestartSh "/tmp/zhengluyu-restart-$stamp.sh"
        Invoke-Ssh "bash /tmp/zhengluyu-restart-$stamp.sh; rm -f /tmp/zhengluyu-restart-$stamp.sh"
    }
    finally {
        Remove-Item $localRestartSh -Force -ErrorAction SilentlyContinue
    }

    Write-Step "Done"
    Write-Host "  Deployed: $($Selected -join ', ')" -ForegroundColor Green
    Write-Host "  App dir  : $RemoteApp"
    Write-Host "  Blog dir : $RemoteBlog"
    Write-Host "  Admin dir: $RemoteAdmin"
    Write-Host "  Note: if SYSTEMD_SKIPPED / NGINX_SKIPPED, install services then re-run or restart manually."
}
finally {
    if (Test-Path $stageRoot) { Remove-Item $stageRoot -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path $archivePath) { Remove-Item $archivePath -Force -ErrorAction SilentlyContinue }
}
