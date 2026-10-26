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
    [string]$RemoteNginxSite = "/etc/nginx/sites-enabled/zhengluyu-blog",
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
    $exe = if ($File -in @("ssh", "scp", "tar", "mvn", "corepack")) {
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
        "-o", "ServerAliveInterval=15",
        "-o", "ServerAliveCountMax=20",
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
        "-o", "ServerAliveInterval=15",
        "-o", "ServerAliveCountMax=20",
        $LocalFile,
        "${User}@${ServerHost}:${RemoteTarget}"
    )
    Invoke-Native "scp" $scpArgs
}

function Invoke-SftpResumable([string]$LocalFile, [string]$RemoteTarget) {
    Invoke-Ssh "touch '$RemoteTarget'"
    $batchFile = Join-Path $env:TEMP ("zhengluyu-sftp-{0}.txt" -f ([Guid]::NewGuid().ToString("N")))
    [System.IO.File]::WriteAllText($batchFile, ("reput `"{0}`" `"{1}`"`n" -f $LocalFile, $RemoteTarget))
    try {
        $sftpArgs = @(
            "-b", $batchFile,
            "-i", $IdentityFile,
            "-P", "$Port",
            "-o", "IdentitiesOnly=yes",
            "-o", "StrictHostKeyChecking=accept-new",
            "-o", "ServerAliveInterval=15",
            "-o", "ServerAliveCountMax=20",
            "${User}@${ServerHost}"
        )
        for ($attempt = 1; $attempt -le 8; $attempt++) {
            Write-Host "  resumable upload attempt $attempt/8"
            try {
                Invoke-Native "sftp" $sftpArgs
                return
            }
            catch {
                if ($attempt -eq 8) { throw }
                Start-Sleep -Seconds 2
            }
        }
    }
    finally {
        Remove-Item $batchFile -Force -ErrorAction SilentlyContinue
    }
}

# --- resolve paths ---
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalRoot = Resolve-Path (Join-Path $ScriptDir "..")
$BackendDir = Join-Path $LocalRoot "blog-backend"
$BlogDir = Join-Path $LocalRoot "blog-frontend\luyu-blog"
$AdminDir = Join-Path $LocalRoot "blog-frontend\luyu-admin"
$NginxSite = Join-Path $LocalRoot "deploy\nginx\zhengluyu-blog.conf"

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
    Assert-Command "sftp"
    Assert-Command "tar"
    Assert-Command "python"
    if (-not (Test-Path $IdentityFile)) {
        throw "SSH private key not found: $IdentityFile"
    }
    if (-not $SkipBuild) {
        if (Test-Should "backend" $Selected) { Assert-Command "mvn" }
        if ((Test-Should "blog" $Selected) -or (Test-Should "admin" $Selected)) {
            Assert-Command "corepack"
        }
    }

    New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null

    # ----- build -----
    if (-not $SkipBuild) {
        if (Test-Should "backend" $Selected) {
            Write-Step "Build backend (Maven)"
            Invoke-Native "mvn" @("-Pprod", "-DskipTests", "package") $BackendDir
        }

        if (Test-Should "blog" $Selected) {
            Write-Step "Build blog frontend"
            $blogEnv = Join-Path $BlogDir ".env.production"
            if (-not (Test-Path $blogEnv)) {
                throw "Missing $blogEnv (required for production build)."
            }
            Invoke-Native "corepack" @("pnpm", "install", "--frozen-lockfile") $BlogDir
            Invoke-Native "corepack" @("pnpm", "build") $BlogDir
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
            # Deployment builds do not install developer Git hooks in this nested package.
            $previousHusky = $env:HUSKY
            try {
                $env:HUSKY = "0"
                Invoke-Native "corepack" @("pnpm", "install", "--frozen-lockfile") $AdminDir
            }
            finally { $env:HUSKY = $previousHusky }
            Invoke-Native "corepack" @("pnpm", "build") $AdminDir
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
        Invoke-Native "python" @((Join-Path $ScriptDir "security/check_release_artifacts.py"), "--jar", $jar.FullName) $LocalRoot
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
        Invoke-Native "python" @((Join-Path $ScriptDir "security/check_release_artifacts.py"), "--dist", $blogDist) $LocalRoot
        $blogStage = Join-Path $stageRoot "blog"
        New-Item -ItemType Directory -Path $blogStage -Force | Out-Null
        Copy-Item -Path (Join-Path $blogDist "*") -Destination $blogStage -Recurse -Force
        Write-Host "  blog dist staged"
        if (-not (Test-Path $NginxSite)) { throw "Nginx site config not found: $NginxSite" }
        $nginxStage = Join-Path $stageRoot "nginx"
        New-Item -ItemType Directory -Path $nginxStage -Force | Out-Null
        Copy-Item $NginxSite (Join-Path $nginxStage "zhengluyu-blog") -Force
        Write-Host "  nginx site config staged"
    }

    if (Test-Should "admin" $Selected) {
        $adminDist = Join-Path $AdminDir "dist"
        if (-not (Test-Path $adminDist)) {
            throw "Admin dist not found: $adminDist"
        }
        Invoke-Native "python" @((Join-Path $ScriptDir "security/check_release_artifacts.py"), "--dist", $adminDist) $LocalRoot
        $adminStage = Join-Path $stageRoot "admin"
        New-Item -ItemType Directory -Path $adminStage -Force | Out-Null
        Copy-Item -Path (Join-Path $adminDist "*") -Destination $adminStage -Recurse -Force
        Write-Host "  admin dist staged"
    }

    Write-Step "Create archive"
    # tar from stageRoot so archive root contains app/ blog/ admin/
    Invoke-Native "tar" @("-czf", $archivePath, "-C", $stageRoot, ".")

    Write-Step "Upload archive"
    Invoke-SftpResumable $archivePath $remoteArchive

    Write-Step "Place, verify and recover the selected release components"
    # Pass values as positional arguments using strict shell-safe characters. Never interpolate shell code.
    $releaseValues = @($remoteArchive, $RemoteApp, $RemoteBlog, $RemoteAdmin, $RemoteNginxSite, $SystemdUnit, ($Selected -join ','), 'www.zhengluyu.com')
    foreach ($value in $releaseValues) {
        if ($value -notmatch '^[a-zA-Z0-9_./,@-]+$') { throw "Unsafe remote release argument" }
    }
    $releaseScript = Join-Path $ScriptDir 'deploy-release.sh'
    $remoteRelease = "/tmp/zhengluyu-release-$stamp.sh"
    Invoke-Scp $releaseScript $remoteRelease
    try {
        $quotedValues = @($releaseValues | ForEach-Object { "'$_'" })
        Invoke-Ssh ("bash '$remoteRelease' " + ($quotedValues -join ' '))
    }
    finally {
        # The remote release retains its own manifest/backups on failure for manual recovery.
        Invoke-Ssh "rm -f '$remoteRelease'"
    }

    Write-Step "Done"
    Write-Host "  Deployed: $($Selected -join ', ')" -ForegroundColor Green
    Write-Host "  App dir  : $RemoteApp"
    Write-Host "  Blog dir : $RemoteBlog"
    Write-Host "  Admin dir: $RemoteAdmin"
    Write-Host "  Release verification completed; exactly one previous frontend is retained."
}
finally {
    if (Test-Path $stageRoot) { Remove-Item $stageRoot -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path $archivePath) { Remove-Item $archivePath -Force -ErrorAction SilentlyContinue }
}
