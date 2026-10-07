param([ValidateSet('inspect','fixture','fill','clear')][string]$Mode='inspect')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskAdbHelper=Join-Path $PSScriptRoot 'device-command.ps1'
# This removes only the debug probe result, never credentials or conversation documents.
& $taskAdbHelper -AdbArguments @('shell','run-as','com.loveadvisor.android','rm','-f','files/device-probe.json') -TimeoutSeconds 8
if($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $taskAdbHelper -AdbArguments @('shell','am','broadcast','-n','com.loveadvisor.android/com.loveadvisor.android.debug.DeviceProbeReceiver','--es','mode',$Mode) -TimeoutSeconds 8
if($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$taskUntil=[DateTime]::UtcNow.AddSeconds(28)
while([DateTime]::UtcNow -lt $taskUntil) {
    Start-Sleep -Milliseconds 800
    $taskResult=& $taskAdbHelper -AdbArguments @('shell','run-as','com.loveadvisor.android','cat','files/device-probe.json') -TimeoutSeconds 5
    if($LASTEXITCODE -eq 0 -and ($taskResult -join '').StartsWith('{')) {
        $taskReportDir=Join-Path $taskRoot '.superpowers/sdd/2026-10-06-multi-chat-0210'
        New-Item -ItemType Directory -Force -Path $taskReportDir|Out-Null
        $taskResult | Set-Content -LiteralPath (Join-Path $taskReportDir "device-$Mode.json") -Encoding utf8
        Write-Output $taskResult
        exit 0
    }
    if(($taskResult -join '') -match 'ADB_TIMEOUT|no devices|offline') { Write-Output $taskResult;exit 124 }
}
throw 'Device probe did not return; no verification result is claimed.'
