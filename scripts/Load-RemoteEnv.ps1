Set-StrictMode -Version Latest

function Import-AuctionRemoteEnv {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [string[]]$RequiredNames = @(),

        [string[]]$OptionalNames = @()
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Env file '$Path' was not found. Copy scripts\remote.env.example to scripts\remote.env and fill in your local values."
    }

    $allowedNames = @{}
    foreach ($name in @($RequiredNames + $OptionalNames)) {
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            $allowedNames[$name] = $true
        }
    }

    $loadedNames = New-Object System.Collections.Generic.List[string]
    $lineNumber = 0
    foreach ($rawLine in Get-Content -LiteralPath $Path) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            continue
        }
        if ($line.StartsWith("export ")) {
            $line = $line.Substring(7).TrimStart()
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            throw "Invalid env assignment in '$Path' on line $lineNumber. Use KEY=value."
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()

        if ($name -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
            throw "Invalid env variable name '$name' in '$Path' on line $lineNumber."
        }

        if ($allowedNames.Count -gt 0 -and -not $allowedNames.ContainsKey($name)) {
            continue
        }

        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        $loadedNames.Add($name)
    }

    foreach ($requiredName in $RequiredNames) {
        $value = [Environment]::GetEnvironmentVariable($requiredName, "Process")
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Required environment variable '$requiredName' is missing. Add it to '$Path'."
        }
    }

    return @($loadedNames | Sort-Object -Unique)
}

function Write-AuctionRemoteEnvSummary {
    [CmdletBinding()]
    param(
        [string[]]$Names
    )

    $uniqueNames = @($Names | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
    if ($uniqueNames.Count -eq 0) {
        return
    }

    Write-Host "Effective environment:"
    foreach ($name in $uniqueNames) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }

        $displayValue = $value
        if ($name -match "(PASSWORD|SECRET|TOKEN)") {
            $displayValue = "<set>"
        }

        Write-Host ("  {0}={1}" -f $name, $displayValue)
    }
}
