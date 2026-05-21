[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "remote.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$requiredNames = @(
    "AUCTION_API_BASE_URL"
)
$optionalNames = @(
    "AUCTION_API_CONNECT_TIMEOUT_MILLIS",
    "AUCTION_API_REQUEST_TIMEOUT_MILLIS"
)

Import-AuctionRemoteEnv -Path $EnvFile -RequiredNames $requiredNames -OptionalNames $optionalNames | Out-Null

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
Write-Host "Loaded JavaFX client environment from $resolvedEnvFile"
Write-AuctionRemoteEnvSummary -Names ($requiredNames + $optionalNames)

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
Write-Host "Starting JavaFX auction client..."

Push-Location $projectRoot
try {
    & mvn exec:java
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($null -eq $exitCode) {
    $exitCode = 0
}
exit $exitCode
