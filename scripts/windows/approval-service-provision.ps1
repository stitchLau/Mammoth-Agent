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

# Native calls run through Start-Process so a non-zero exit code (or stderr text) never turns into
# a terminating NativeCommandError under $ErrorActionPreference = 'Stop'. Returns the exit code.
function Invoke-Native([string]$FilePath, [string[]]$ArgumentList) {
  $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList -Wait -PassThru `
    -WindowStyle Hidden
  return $process.ExitCode
}
function Quote([string]$value) { return '"' + $value + '"' }

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

# 1. Dedicated local account (net.exe; works in Windows PowerShell 5.1 and PowerShell 7,
#    and does not depend on the optional LocalAccounts/ADSI providers).
$plainPassword = $null
if ($null -ne $ServiceAccountPassword) {
  $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ServiceAccountPassword))
}
$accountExists = (Invoke-Native 'net.exe' @('user', $ServiceAccountName)) -eq 0
if (-not $accountExists) {
  if ($null -eq $plainPassword) {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $plainPassword = [Convert]::ToBase64String($bytes)
    Write-Host "[action] generated a random password for $account; store it in your password manager now:" -ForegroundColor Yellow
    Write-Host "         $plainPassword"
  }
  if ($plainPassword -match '[\s"]') {
    Fail 'password must not contain spaces or double quotes (provisioning uses net.exe)'
  }
  $createExit = Invoke-Native 'net.exe' @('user', $ServiceAccountName, $plainPassword, '/add',
    '/expires:never', '/passwordchg:no',
    '/comment:"ADR-0015 isolated approval service account"')
  if ($createExit -ne 0) {
    Fail "net user /add failed (exit $createExit); check the local password policy and permissions"
  }
  Invoke-Native 'net.exe' @('user', $ServiceAccountName,
    '/fullname:"Mammoth Approval Service"') | Out-Null
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
# Convert.ToHexString is .NET 5+ only; build the hex manually so Windows PowerShell 5.1 works too.
$applyToken = ($tokenBytes | ForEach-Object { $_.ToString('x2') }) -join ''
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
$aclExit = Invoke-Native 'icacls.exe' @((Quote $InstallDir), '/inheritance:r')
if ($aclExit -ne 0) { Fail "icacls inheritance failed for $InstallDir (exit $aclExit)" }
$aclExit = Invoke-Native 'icacls.exe' @((Quote $InstallDir), '/grant:r',
  '*S-1-5-32-544:(OI)(CI)F', 'NT AUTHORITY\SYSTEM:(OI)(CI)F', "${account}:(OI)(CI)RX")
if ($aclExit -ne 0) { Fail "icacls grant failed for $InstallDir (exit $aclExit)" }
foreach ($file in @($tokenFile, $configFile)) {
  $aclExit = Invoke-Native 'icacls.exe' @((Quote $file), '/inheritance:r')
  if ($aclExit -ne 0) { Fail "icacls inheritance failed for $file (exit $aclExit)" }
  $aclExit = Invoke-Native 'icacls.exe' @((Quote $file), '/grant:r',
    '*S-1-5-32-544:F', 'NT AUTHORITY\SYSTEM:F', "${account}:R")
  if ($aclExit -ne 0) { Fail "icacls grant failed for $file (exit $aclExit)" }
}
$aclExit = Invoke-Native 'icacls.exe' @((Quote $wrapperFile), '/inheritance:r')
if ($aclExit -ne 0) { Fail "icacls inheritance failed for $wrapperFile (exit $aclExit)" }
$aclExit = Invoke-Native 'icacls.exe' @((Quote $wrapperFile), '/grant:r',
  '*S-1-5-32-544:RX', 'NT AUTHORITY\SYSTEM:RX', "${account}:RX")
if ($aclExit -ne 0) { Fail "icacls grant failed for $wrapperFile (exit $aclExit)" }
Write-Host '[action] applied strict ACLs (admins/SYSTEM full; service account read)'

# 4. Optional scheduled task running the watch loop as the service account.
#    schtasks.exe is used because the ScheduledTasks PowerShell module is not available in
#    PowerShell 7 (and its parameter sets differ across versions).
if ($RegisterScheduledTask) {
  if ($null -eq $plainPassword) { Fail '-RegisterScheduledTask needs -ServiceAccountPassword' }
  $taskExit = Invoke-Native 'schtasks.exe' @('/Create', '/F', '/TN', $taskName, '/TR',
    (Quote $wrapperFile), '/SC', 'ONSTART', '/RU', $account, '/RP', $plainPassword,
    '/RL', 'HIGHEST')
  if ($taskExit -ne 0) {
    Fail "schtasks /Create failed (exit $taskExit)"
  }
  Write-Host "[action] registered scheduled task $taskName (schtasks, runs at startup as $account)"
}

Write-Host ''
Write-Host 'Provisioning complete. OWNER-ONLY next steps:' -ForegroundColor Green
Write-Host '  1. As the Domain writer owner, set env MAMMOTH_AGENT_WRITE_APPLY_TOKEN to this value'
Write-Host '     (read it as administrator: Get-Content -Raw ''' + $tokenFile + ''')'
Write-Host '  2. Restart the Domain writer so it picks up the apply token.'
Write-Host '  3. Run scripts\windows\approval-service-verify.ps1 as the interactive (agent) user.'
Write-Host '  4. If the scheduled task is registered: Start-ScheduledTask -TaskName ''' + $taskName + ''''
