param([Parameter(Mandatory=$true)][string[]]$AdbArguments,[int]$TimeoutSeconds=25)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfig=Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskAdb=Join-Path $taskConfig.sdk 'platform-tools/adb.exe'
$taskUnique=[Guid]::NewGuid().ToString('N')
$taskOut=Join-Path $env:TEMP "jev-adb-$taskUnique.out"
$taskErr=Join-Path $env:TEMP "jev-adb-$taskUnique.err"
$taskProcess=Start-Process -FilePath $taskAdb -ArgumentList $AdbArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskOut -RedirectStandardError $taskErr
if(!$taskProcess.WaitForExit($TimeoutSeconds*1000)) { Stop-Process -Id $taskProcess.Id -Force; Write-Output 'ADB_TIMEOUT'; exit 124 }
Get-Content -LiteralPath $taskOut -Encoding utf8
Get-Content -LiteralPath $taskErr -Encoding utf8
exit $taskProcess.ExitCode
