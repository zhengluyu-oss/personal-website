param(
    [ValidateRange(512, 4096)] [int]$BufferPoolMiB = 768,
    [ValidateRange(2048, 16384)] [int]$HeapMiB = 4096,
    [switch]$Force
)

# 只调整本次本地分析进程，退出后还原调用者环境，不修改系统或应用配置。
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $repoRoot '.gitnexus/run.cjs'
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw '缺少项目 GitNexus 入口；请先完成项目索引初始化。'
}
$names = @('GITNEXUS_LBUG_BUFFER_POOL_SIZE', 'GITNEXUS_MEMORY', 'NODE_OPTIONS')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $repoRoot
try {
    $env:GITNEXUS_LBUG_BUFFER_POOL_SIZE = ([long]$BufferPoolMiB * 1MB).ToString()
    # 禁止按物理总内存推算出过大的堆上限；保留原有其他 Node 选项。
    $env:GITNEXUS_MEMORY = 'off'
    $originalOptions = ($previous['NODE_OPTIONS'] -replace '--max-old-space-size(?:=|\s+)\d+', '').Trim()
    $env:NODE_OPTIONS = "$originalOptions --max-old-space-size=$HeapMiB".Trim()
    $arguments = @($runner, 'analyze', '--index-only')
    if ($Force) { $arguments += '--force' }
    & node @arguments
    if ($LASTEXITCODE -ne 0) { throw "图谱重建失败，退出码：$LASTEXITCODE" }
}
finally {
    Pop-Location
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
