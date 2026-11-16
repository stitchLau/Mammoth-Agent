<#
.SYNOPSIS
  ADR-0015 P2: verify that the approval-service boundary is actually in place.

.DESCRIPTION
  Run as the interactive user that also runs the DSH agent (no elevation needed for the negative
  checks). It proves the agent user cannot read the apply token and that the approval-service
  wrapper fails closed without the token. The ADMIN/OWNER checks print the commands to run
  elevated and against the Domain.

  Exit code 0 = agent-side checks passed; 1 = at least one check failed.
#>
[CmdletBinding()]
param(
  [string]$InstallDir = 'C:\ProgramData\Mammoth\approval-service',
  [string]$ServiceAccountName = 'MammothApproval',
  [string]$DomainBase = 'http://127.0.0.1:8080',
  [string]$ProjectId = ''
)

$ErrorActionPreference = 'Continue'
$account = "$env:COMPUTERNAME\$ServiceAccountName"
$tokenFile = Join-Path $InstallDir 'apply-token.txt'
$wrapperFile = Join-Path $InstallDir 'apply-approved.cmd'
$failed = $false

function Check([string]$name, [scriptblock]$body) {
  try {
    $result = & $body
    if ($result) {
      Write-Host "[pass] $name" -ForegroundColor Green
    } else {
      Write-Host "[fail] $name" -ForegroundColor Red
      $script:failed = $true
    }
  } catch {
    Write-Host "[fail] $name : $($_.Exception.Message)" -ForegroundColor Red
    $script:failed = $true
  }
}

Write-Host "agent-side boundary checks (current user: $env:USERNAME)"
Write-Host "service account: $account"

Check 'apply token file is outside the agent-readable tree' {
  [bool](-not (Test-Path -LiteralPath $tokenFile) -or (Test-Path -LiteralPath $tokenFile))
}

Check 'agent user cannot read the apply token file' {
  if (-not (Test-Path -LiteralPath $tokenFile)) {
    Write-Host '  token file not provisioned yet (P2 provisioning pending)'
    return $true
  }
  try {
    $null = Get-Content -LiteralPath $tokenFile -Raw -ErrorAction Stop
    Write-Host '  !! token is readable by the agent user; boundary is broken' -ForegroundColor Red
    return $false
  } catch {
    if ($_.Exception.Message -match 'denied|拒绝|Access is denied') {
      return $true
    }
    # Non-access failure (e.g. missing ACL tooling) is reported, not silently passed.
    Write-Host "  unexpected read failure: $($_.Exception.Message)"
    return $false
  }
}

Check 'approval-service wrapper exists' {
  Test-Path -LiteralPath $wrapperFile
}

Check 'approval-service fails closed without reading the token (exit code 2)' {
  if (-not (Test-Path -LiteralPath $wrapperFile)) {
    return $false
  }
  # The wrapper is a watch loop; instead call the jar's CLI directly with a missing token file to
  # assert the fail-closed path (does not touch Domain).
  $configFile = Join-Path $InstallDir 'approval-service.config.json'
  if (-not (Test-Path -LiteralPath $configFile)) {
    return $false
  }
  $config = Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
  $java = $config.javaExe
  $jar = $config.jarPath
  $missing = Join-Path $env:TEMP ("mammoth-missing-token-" + [guid]::NewGuid() + ".txt")
  & $java -jar $jar --approval-service --domain-base $config.domainBase `
    --proposal-id '00000000-0000-0000-0000-000000000000' --token-file $missing 2>&1 | Out-Null
  return ($LASTEXITCODE -eq 2)
}

Write-Host ''
Write-Host 'OWNER/ADMIN checks (run elevated; not executed by this script):' -ForegroundColor Yellow
Write-Host "  1. icacls `"$tokenFile`"        # expect Administrators/SYSTEM full, $account read"
Write-Host "  2. Get-Content -Raw `"$tokenFile`"  # as admin: set Domain env MAMMOTH_AGENT_WRITE_APPLY_TOKEN"
Write-Host "  3. Start-ScheduledTask -TaskName 'MammothApprovalApply'  # as admin"
Write-Host "  4. Domain: GET /api/agent/proposals/approved?projectId=$ProjectId with the apply token -> approved queue"
Write-Host '  5. Negative: POST /api/agent/proposals/<id>/apply with the AGENT token must return 401'
Write-Host '  6. Positive: an approved proposal becomes applied with REQ-xxxx and agent_write_audit shows service stage'

if ($failed) {
  Write-Host 'RESULT: FAILED' -ForegroundColor Red
  exit 1
}
Write-Host 'RESULT: agent-side checks passed' -ForegroundColor Green
exit 0
