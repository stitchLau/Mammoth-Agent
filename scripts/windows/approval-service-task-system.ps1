<#
.SYNOPSIS
  ADR-0015 P2 fallback: register the approval-service watch loop as a SYSTEM task via Task XML.

.DESCRIPTION
  The dedicated MammothApproval account path can fail with Task Scheduler
  LogonUserEx error 0x8007052E when the stored task credential does not match the
  account. This script registers the same watch loop with the SYSTEM principal
  and a direct java.exe action (no .cmd, no stored password) so the approval
  service runs automatically. SYSTEM can read the apply-token file because the
  provisioning ACL grants SYSTEM full control; the interactive (agent) user still
  cannot read it, so the P2 boundary holds. The tighter dedicated-account setup
  remains a P2.1 item.

  Run elevated. Values default to this machine's spike environment.

.EXAMPLE
  .\approval-service-task-system.ps1 -DryRun
.EXAMPLE
  .\approval-service-task-system.ps1
.EXAMPLE
  .\approval-service-task-system.ps1 -Unregister
#>
[CmdletBinding()]
param(
  [string]$TaskName = 'MammothApprovalApply',
  [string]$JavaExe = 'D:\Tools\Java\jdk-17.0.15\bin\java.exe',
  [string]$JarPath = 'E:\MyProj\MommothAgent\code\Mammoth-Agent-p1\services\domain\target\mammoth-domain-service.jar',
  [string]$DomainBase = 'http://127.0.0.1:18090',
  [string]$ProjectId = 'a899ed37-7e3a-488c-9c1b-16c97bfe0b11',
  [string]$TokenFile = 'C:\ProgramData\Mammoth\approval-service\apply-token.txt',
  [int]$PollSeconds = 5,
  [switch]$Unregister,
  [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string]$text) { throw "approval-service-task: $text" }
function Write-Step([string]$text) { Write-Host "[plan] $text" }

$arguments = '-jar "{0}" --approval-service --watch --project-id {1} --domain-base {2} --token-file "{3}" --poll-seconds {4}' -f `
  $JarPath, $ProjectId, $DomainBase, $TokenFile, $PollSeconds
Write-Step "task:          $TaskName"
Write-Step "principal:     SYSTEM (S-1-5-18), HighestAvailable"
Write-Step "executable:    $JavaExe"
Write-Step "arguments:     $arguments"
Write-Step "action:        register via Task XML (schtasks /Create /XML), boot trigger, no password stored"

if ($DryRun) {
  Write-Host '[dry-run] no changes were made.'
  exit 0
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Fail 'must run elevated (use -DryRun to preview)'
}
if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { Fail "java not found: $JavaExe" }
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) { Fail "jar not found: $JarPath" }
if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) {
  Fail "token file not found: $TokenFile (run approval-service-provision.ps1 first)"
}

if ($Unregister) {
  & schtasks.exe /Delete /F /TN $TaskName | Out-Null
  if ($LASTEXITCODE -ne 0) { Fail "schtasks /Delete failed (exit $LASTEXITCODE)" }
  Write-Host "[action] unregistered task $TaskName"
  exit 0
}

$xml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Author>$env:USERDOMAIN\$env:USERNAME</Author>
    <Description>ADR-0015 approval service watch loop (SYSTEM, local only)</Description>
  </RegistrationInfo>
  <Principals>
    <Principal id="Author"><UserId>S-1-5-18</UserId><RunLevel>HighestAvailable</RunLevel></Principal>
  </Principals>
  <Settings>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Enabled>true</Enabled>
  </Settings>
  <Triggers><BootTrigger><Enabled>true</Enabled></BootTrigger></Triggers>
  <Actions Context="Author">
    <Exec>
      <Command>$JavaExe</Command>
      <Arguments>$arguments</Arguments>
    </Exec>
  </Actions>
</Task>
"@
$xmlPath = Join-Path $env:TEMP 'MammothApprovalApply-task.xml'
[IO.File]::WriteAllText($xmlPath, $xml, (New-Object Text.UnicodeEncoding($false, $true)))
& schtasks.exe /Create /F /TN $TaskName /XML $xmlPath | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "schtasks /Create /XML failed (exit $LASTEXITCODE)" }
Write-Host "[action] registered task $TaskName (SYSTEM, direct java action)"
Write-Host "Next: schtasks /Run /TN $TaskName   (or reboot)"
