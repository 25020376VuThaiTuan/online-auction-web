[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "local-api.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$optionalNames = @(
    "AUCTION_API_PORT",
    "AUCTION_API_WORKER_THREADS",
    "AUCTION_API_TOKEN_TTL_SECONDS",
    "AUCTION_DEMO_ACCOUNTS_ENABLED"
)

$envFileProvided = $PSBoundParameters.ContainsKey("EnvFile")
if (Test-Path -LiteralPath $EnvFile -PathType Leaf) {
    Import-AuctionRemoteEnv -Path $EnvFile -OptionalNames $optionalNames | Out-Null
    $resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    Write-Host "Loaded localhost API environment from $resolvedEnvFile"
} elseif ($envFileProvided) {
    throw "Env file '$EnvFile' was not found."
} else {
    Write-Host "No localhost API env file found at $EnvFile; using local demo defaults."
}

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_API_PORT", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_API_PORT", "8081", "Process")
}
if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_DEMO_ACCOUNTS_ENABLED", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_DEMO_ACCOUNTS_ENABLED", "true", "Process")
}

foreach ($name in @("AUCTION_DB_URL", "AUCTION_DB_USER", "AUCTION_DB_PASSWORD")) {
    [Environment]::SetEnvironmentVariable($name, $null, "Process")
}

Write-AuctionRemoteEnvSummary -Names $optionalNames

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
Write-Host "Starting localhost auction API server with local demo storage..."

Push-Location $projectRoot
try {
    & mvn exec:java@api-server
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($null -eq $exitCode) {
    $exitCode = 0
}
exit $exitCode
