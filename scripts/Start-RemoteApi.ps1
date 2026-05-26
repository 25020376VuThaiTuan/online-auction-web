[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "remote.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$requiredNames = @(
    "AUCTION_DB_URL",
    "AUCTION_DB_USER",
    "AUCTION_DB_PASSWORD"
)
$optionalNames = @(
    "AUCTION_API_PORT",
    "AUCTION_API_WORKER_THREADS",
    "AUCTION_API_TOKEN_TTL_SECONDS"
)

Import-AuctionRemoteEnv -Path $EnvFile -RequiredNames $requiredNames -OptionalNames $optionalNames | Out-Null

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
Write-Host "Loaded API environment from $resolvedEnvFile"
Write-AuctionRemoteEnvSummary -Names ($requiredNames + $optionalNames)

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
Write-Host "Starting standalone auction API server..."

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
