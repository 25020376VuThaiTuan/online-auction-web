[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "azure-api.env")
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "Start-RemoteApi.ps1") -EnvFile $EnvFile
exit $LASTEXITCODE
