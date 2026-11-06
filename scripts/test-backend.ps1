param(
    [string]$SocketTempDirectory = (Join-Path $env:USERPROFILE '.luyu-java-test-temp')
)

$originalTemp = $env:TEMP
$originalTmp = $env:TMP
$backendPom = Join-Path $PSScriptRoot '../blog-backend/pom.xml'
$result = 1

try {
    New-Item -ItemType Directory -Force -Path $SocketTempDirectory -ErrorAction Stop | Out-Null
    $env:TEMP = (Resolve-Path -LiteralPath $SocketTempDirectory).Path
    $env:TMP = $env:TEMP
    & mvn -f $backendPom test
    $result = $LASTEXITCODE
} finally {
    $env:TEMP = $originalTemp
    $env:TMP = $originalTmp
}

exit $result
