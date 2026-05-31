[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "remote.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$apiNames = @(
    "AUCTION_API_BASE_URL"
)
$optionalNames = @(
    "AUCTION_API_CONNECT_TIMEOUT_MILLIS",
    "AUCTION_API_REQUEST_TIMEOUT_MILLIS"
)

if (Test-Path -LiteralPath $EnvFile) {
    Import-AuctionRemoteEnv -Path $EnvFile -RequiredNames @() -OptionalNames ($apiNames + $optionalNames) | Out-Null
    $resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    Write-Host "Loaded JavaFX client environment from $resolvedEnvFile"
} elseif ($PSBoundParameters.ContainsKey("EnvFile")) {
    throw "Env file '$EnvFile' was not found."
} else {
    Write-Host "No remote client env file found at $EnvFile; using http://100.89.207.4:8081/api."
}

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_API_BASE_URL", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_API_BASE_URL", "http://100.89.207.4:8081/api", "Process")
}

Write-AuctionRemoteEnvSummary -Names ($apiNames + $optionalNames)

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
