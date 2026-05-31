[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "remote.env")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Load-RemoteEnv.ps1")

$dbNames = @(
    "AUCTION_DB_URL",
    "AUCTION_DB_USER",
    "AUCTION_DB_PASSWORD"
)
$optionalNames = @(
    "AUCTION_API_PORT",
    "AUCTION_API_WORKER_THREADS",
    "AUCTION_API_TOKEN_TTL_SECONDS",
    "AUCTION_API_ALLOWED_ORIGIN",
    "AUCTION_API_VIRTUAL_THREADS",
    "AUCTION_DB_MAX_POOL_SIZE",
    "AUCTION_DB_BORROW_TIMEOUT_MILLIS",
    "PORT",
    "WEBSITES_PORT",
    "CONTAINER_APP_PORT"
)

if (Test-Path -LiteralPath $EnvFile) {
    Import-AuctionRemoteEnv -Path $EnvFile -RequiredNames @() -OptionalNames ($dbNames + $optionalNames) | Out-Null
    $resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    Write-Host "Loaded API environment from $resolvedEnvFile"
} elseif ($PSBoundParameters.ContainsKey("EnvFile")) {
    throw "Env file '$EnvFile' was not found."
} else {
    Write-Host "No remote API env file found at $EnvFile; using default remote API database settings."
}

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_DB_URL", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_DB_URL", "jdbc:mysql://localhost:3306/auctiondb?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC", "Process")
}
if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_DB_USER", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_DB_USER", "auction_user", "Process")
}
if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_DB_PASSWORD", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_DB_PASSWORD", "Auction123", "Process")
}
if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AUCTION_API_PORT", "Process"))) {
    [Environment]::SetEnvironmentVariable("AUCTION_API_PORT", "8081", "Process")
}

Write-AuctionRemoteEnvSummary -Names ($dbNames + $optionalNames)

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
