[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "local-api.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$optionalNames = @(
    "AUCTION_API_BASE_URL",
    "AUCTION_API_PORT",
    "AUCTION_API_CONNECT_TIMEOUT_MILLIS",
    "AUCTION_API_REQUEST_TIMEOUT_MILLIS"
)

$envFileProvided = $PSBoundParameters.ContainsKey("EnvFile")
if (Test-Path -LiteralPath $EnvFile -PathType Leaf) {
    Import-AuctionRemoteEnv -Path $EnvFile -OptionalNames $optionalNames | Out-Null
    $resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    Write-Host "Loaded localhost client environment from $resolvedEnvFile"
} elseif ($envFileProvided) {
    throw "Env file '$EnvFile' was not found."
} else {
    Write-Host "No localhost client env file found at $EnvFile; using http://localhost:8081/api."
}

$port = [Environment]::GetEnvironmentVariable("AUCTION_API_PORT", "Process")
if ([string]::IsNullOrWhiteSpace($port)) {
    $port = "8081"
    [Environment]::SetEnvironmentVariable("AUCTION_API_PORT", $port, "Process")
}

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_API_BASE_URL", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_API_BASE_URL", "http://localhost:$port/api", "Process")
}

foreach ($name in @("AUCTION_DB_URL", "AUCTION_DB_USER", "AUCTION_DB_PASSWORD")) {
    [Environment]::SetEnvironmentVariable($name, $null, "Process")
}

Write-AuctionRemoteEnvSummary -Names $optionalNames

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
Write-Host "Starting JavaFX auction client against the localhost API..."

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
