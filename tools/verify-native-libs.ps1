param([string]$Apk = 'output/love-advisor-0.1.0-debug.apk',[string]$Report = 'output/native-libs.json')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfig=Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskApk=Join-Path $taskRoot $Apk
& (Join-Path $taskConfig.sdk 'build-tools/35.0.0/zipalign.exe') '-c' '-P' '16' '-v' '4' $taskApk | Select-Object -Last 3
if($LASTEXITCODE -ne 0) { throw 'APK 16KB zipalign verification failed' }
& 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' (Join-Path $PSScriptRoot 'verify-native-libs.py') $taskApk | Tee-Object -FilePath (Join-Path $taskRoot $Report)
exit $LASTEXITCODE
