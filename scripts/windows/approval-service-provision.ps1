<#
.SYNOPSIS
  ADR-0015 P2: provision the isolated Mammoth approval-service account and credential file.

.DESCRIPTION
  Run in an elevated PowerShell as a local administrator. Creates a dedicated local user, a
  service-only install directory, the apply-token file with a strict ACL, an optional scheduled
  task that runs the approval-service watch loop, and prints the Domain configuration step.

  The apply token is held only by this service account: the agent/Host process (running as the
  interactive user) must not be able to read it. Run with -DryRun to inspect the plan without
  changing anything.

.PREREQUISITES
  - Windows 10/11 or Windows Server, PowerShell 5.1+.
  - A built Domain jar (services/domain/target/mammoth-domain-service.jar).
  - The owner/admin chooses -ServiceAccountPassword and keeps it in a password manager; the
    scheduled task needs it to run whether or not the service user is logged on.

.EXAMPLE
  .\approval-service-provision.ps1 -DryRun -DomainBase http://127.0.0.1:8080 `
      -JarPath C:\Mammoth\mammoth-domain-service.jar -ProjectId 8f3...

.EXAMPLE
  .\approval-service-provision.ps1 -DomainBase http://127.0.0.1:8080 `
      -JarPath C:\Mammoth\mammoth-domain-service.jar -ProjectId 8f3... -RegisterScheduledTask
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$DomainBase,
  [Parameter(Mandatory = $true)][string]$JarPath,
  [Parameter(Mandatory = $true)][string]$ProjectId,
  [string]$ServiceAccountName = 'MammothApproval',
  [string]$InstallDir = 'C:\ProgramData\Mammoth\approval-service',
  [string]$JavaExe = 'java.exe',
  [int]$PollSeconds = 5,
  [SecureString]$ServiceAccountPassword,
  [switch]$RegisterScheduledTask,
  [switch]$Force,
  [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step([string]$text) { Write-Host "[plan] $text" }
function Fail([string]$text) { throw "approval-service-provision: $text" }

if ($DomainBase -notmatch '^https?://') { Fail '-DomainBase must be an http(s) URL' }
$account = "$env:COMPUTERNAME\$ServiceAccountName"
$tokenFile = Join-Path $InstallDir 'apply-token.txt'
$configFile = Join-Path $InstallDir 'approval-service.config.json'
$wrapperFile = Join-Path $InstallDir 'apply-approved.cmd'
$taskName = 'MammothApprovalApply'

Write-Step "local account: $account (create if missing; password supplied by owner)"
Write-Step "install dir:   $InstallDir"
Write-Step "token file:    $tokenFile (ACL: Administrators/SYSTEM full, service account read only)"
Write-Step "config file:   $configFile"
Write-Step "wrapper:       $wrapperFile"
Write-Step "domain base:   $DomainBase"
Write-Step "project id:    $ProjectId"
Write-Step "poll seconds:  $PollSeconds"
Write-Step "scheduled task: $taskName (registered: $($RegisterScheduledTask.IsPresent))"
Write-Step 'acl plan: disable inheritance; remove inherited users; Administrators=S-1-5-32-544 full; SYSTEM full; service account read'
Write-Step 'next steps: set MAMMOTH_AGENT_WRITE_APPLY_TOKEN for the Domain writer to the token file value, restart Domain, then run approval-service-verify.ps1'

if ($DryRun) {
  Write-Host '[dry-run] no changes were made.'
  exit 0
}

# Manual admin check keeps -DryRun usable by a non-admin operator.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Fail 'must run elevated (Run as administrator); use -DryRun to preview the plan'
}
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) { Fail "jar not found: $JarPath" }
if (-not (Get-Command $JavaExe -ErrorAction SilentlyContinue) -and -not (Test-Path $JavaExe)) {
  Fail "java executable not found: $JavaExe"
}

# 1. Dedicated local account (ADSI; avoids the optional LocalAccounts module).
$plainPassword = $null
if ($null -ne $ServiceAccountPassword) {
  $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ServiceAccountPassword))
}
$computer = [ADSI]"WinNT://$env:COMPUTERNAME,computer"
$existing = $computer.Children | Where-Object {
  $_.SchemaClassName -eq 'User' -and $_.Name -eq $ServiceAccountName
}
if (-not $existing) {
  if ($null -eq $plainPassword) {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $plainPassword = [Convert]::ToBase64String($bytes)
    Write-Host "[action] generated a random password for $account; store it in your password manager now:" -ForegroundColor Yellow
    Write-Host "         $plainPassword"
  }
  $user = $computer.Create('User', $ServiceAccountName)
  $user.SetPassword($plainPassword)
  $user.Put('Description', 'ADR-0015 isolated approval service account')
  # UF_SCRIPT (0x1) + UF_DONT_EXPIRE_PASSWD (0x10000)
  $flags = [int]$user.Get('UserFlags')
  $user.Put('UserFlags', ($flags -bor 0x1 -bor 0x10000))
  $user.SetInfo()
  Write-Host "[action] created local user $account"
} else {
  Write-Host "[skip] local user already exists: $account"
  if ($RegisterScheduledTask -and $null -eq $plainPassword) {
    Fail '-RegisterScheduledTask needs -ServiceAccountPassword for an existing account'
  }
}

# 2. Install dir + token + config + wrapper.
if (-not (Test-Path -LiteralPath $InstallDir)) {
  New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
  Write-Host "[action] created $InstallDir"
}
if ((Test-Path -LiteralPath $tokenFile) -and -not $Force) {
  Fail "token file already exists; re-run with -Force to rotate it: $tokenFile"
}
$tokenBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($tokenBytes)
$applyToken = [Convert]::ToHexString($tokenBytes).ToLowerInvariant()
[IO.File]::WriteAllText($tokenFile, $applyToken, (New-Object Text.UTF8Encoding($false)))
$config = [ordered]@{
  serviceAccount = $account
  domainBase = $DomainBase
  javaExe = $JavaExe
  jarPath = $JarPath
  projectId = $ProjectId
  tokenFile = $tokenFile
  pollSeconds = $PollSeconds
}
[IO.File]::WriteAllText($configFile, ($config | ConvertTo-Json -Depth 4),
  (New-Object Text.UTF8Encoding($false)))
$wrapper = "@echo off`r`n`"$JavaExe`" -jar `"$JarPath`" --approval-service --watch --project-id `"$ProjectId`" --domain-base `"$DomainBase`" --token-file `"$tokenFile`" --poll-seconds $PollSeconds >> `"$InstallDir\approval-service.log`" 2>&1`r`n"
[IO.File]::WriteAllText($wrapperFile, $wrapper, (New-Object Text.UTF8Encoding($false)))
Write-Host "[action] wrote token/config/wrapper (token value is only in $tokenFile)"

# 3. Strict ACLs: inherited users are removed; only admins/SYSTEM/service account remain.
& icacls.exe $InstallDir /inheritance:r | Out-Null
& icacls.exe $InstallDir /grant:r '*S-1-5-32-544:(OI)(CI)F' 'NT AUTHORITY\SYSTEM:(OI)(CI)F' "${account}:(OI)(CI)RX" | Out-Null
& icacls.exe $tokenFile /inheritance:r | Out-Null
& icacls.exe $tokenFile /grant:r '*S-1-5-32-544:F' 'NT AUTHORITY\SYSTEM:F' "${account}:R" | Out-Null
& icacls.exe $configFile /inheritance:r | Out-Null
& icacls.exe $configFile /grant:r '*S-1-5-32-544:F' 'NT AUTHORITY\SYSTEM:F' "${account}:R" | Out-Null
& icacls.exe $wrapperFile /inheritance:r | Out-Null
& icacls.exe $wrapperFile /grant:r '*S-1-5-32-544:RX' 'NT AUTHORITY\SYSTEM:RX' "${account}:RX" | Out-Null
Write-Host '[action] applied strict ACLs (admins/SYSTEM full; service account read)'

# 4. Optional scheduled task running the watch loop as the service account.
if ($RegisterScheduledTask) {
  if ($null -eq $plainPassword) { Fail '-RegisterScheduledTask needs -ServiceAccountPassword' }
  $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$wrapperFile`""
  $trigger = New-ScheduledTaskTrigger -AtStartup
  $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Hours 0)
  Register-ScheduledTask -TaskName $taskName -TaskPath '\Mammoth\' -Action $action `
    -Trigger $trigger -Settings $settings -User $account -Password $plainPassword `
    -RunLevel Highest `
    -Description 'ADR-0015 approval service: apply human-approved proposals' -Force | Out-Null
  Write-Host "[action] registered scheduled task \Mammoth\$taskName"
}

Write-Host ''
Write-Host 'Provisioning complete. OWNER-ONLY next steps:' -ForegroundColor Green
Write-Host '  1. As the Domain writer owner, set env MAMMOTH_AGENT_WRITE_APPLY_TOKEN to this value'
Write-Host '     (read it as administrator: Get-Content -Raw ''' + $tokenFile + ''')'
Write-Host '  2. Restart the Domain writer so it picks up the apply token.'
Write-Host '  3. Run scripts\windows\approval-service-verify.ps1 as the interactive (agent) user.'
Write-Host '  4. If the scheduled task is registered: Start-ScheduledTask -TaskName ''' + $taskName + ''''
