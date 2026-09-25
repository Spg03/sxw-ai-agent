[CmdletBinding()]
param(
    [string]$DshSource = 'D:\github-work\deepseek-harness',
    [string]$Image = 'agentforge/dsh-eval:99f6f02'
)

$ErrorActionPreference = 'Stop'
$expectedCommit = '99f6f02fecdb7dff40c3fbc9470f5907c29f74ca'
$sourcePath = (Resolve-Path -LiteralPath $DshSource).Path
$actualCommit = (& git -C $sourcePath rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $actualCommit -ne $expectedCommit) {
    throw "DSH source must be pinned to $expectedCommit; found $actualCommit"
}

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$stagePath = Join-Path ([System.IO.Path]::GetTempPath()) ("agentforge-dsh-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $stagePath | Out-Null
try {
    & robocopy $sourcePath $stagePath /MIR /XD .git node_modules .turbo .cache /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) { throw "Unable to stage DSH source (robocopy exit $LASTEXITCODE)" }
    $deployPath = Join-Path $stagePath 'eval-deploy'
    New-Item -ItemType Directory -Path $deployPath -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $scriptPath 'Dockerfile') -Destination $deployPath
    Copy-Item -LiteralPath (Join-Path $scriptPath 'cordis.yml') -Destination $deployPath
    Copy-Item -LiteralPath (Join-Path $scriptPath 'eval-tools.mjs') -Destination $deployPath

    & docker build --build-arg "DSH_COMMIT=$expectedCommit" -f (Join-Path $deployPath 'Dockerfile') -t $Image $stagePath
    if ($LASTEXITCODE -ne 0) { throw "Docker build failed with exit code $LASTEXITCODE" }
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $resolvedStage = [System.IO.Path]::GetFullPath($stagePath)
    if ($resolvedStage.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
}
