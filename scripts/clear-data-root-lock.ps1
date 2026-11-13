param(
  [Parameter(Mandatory = $true)][string]$DataRoot,
  [string]$Jar = "services/domain/target/mammoth-domain-service.jar",
  [string]$Java = $env:MAMMOTH_JAVA
)

# ADR-0008: remove a stale Mammoth instance lock. Refuses while a writer is active.
$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $DataRoot -PathType Container)) {
  Write-Error "data root does not exist: $DataRoot"
  exit 1
}
if ([string]::IsNullOrWhiteSpace($Java)) {
  $Java = "java"
}
$resolvedJar = (Resolve-Path -LiteralPath $Jar).Path
$resolvedRoot = (Resolve-Path -LiteralPath $DataRoot).Path

Write-Host "Checking Mammoth data root: $resolvedRoot"
& $Java -jar $resolvedJar --admin-clear-lock "--data-root=$resolvedRoot"
exit $LASTEXITCODE
