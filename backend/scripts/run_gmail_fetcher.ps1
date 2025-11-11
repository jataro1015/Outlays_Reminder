param(
    [string]$Config = "config.yml",
    [string]$Profile = "default",
    [string[]]$ExtraArgs
)

$scriptDir = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
Set-Location -LiteralPath $scriptDir

$arguments = @("run", "python", "-m", "gmail_fetcher")

if ($Config -and $Config.Trim().Length -gt 0) {
    $arguments += @("--config", $Config)
}

if ($Profile -and $Profile.Trim().Length -gt 0) {
    $arguments += @("--profile", $Profile)
}

if ($ExtraArgs) {
    $arguments += $ExtraArgs
}

& poetry @arguments
exit $LASTEXITCODE
