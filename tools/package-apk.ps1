$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfig=Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskApk=Join-Path $taskRoot 'output/love-advisor-1.2.12-debug.apk'
$env:JAVA_HOME=$taskConfig.java
& (Join-Path $taskConfig.sdk 'build-tools/35.0.0/apksigner.bat') 'verify' '--verbose' '--print-certs' $taskApk | Tee-Object -FilePath (Join-Path $taskRoot 'output/signature-1.2.12.txt')
if($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$taskHash=(Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLower()
Set-Content -LiteralPath (Join-Path $taskRoot 'output/SHA256-1.2.12.txt') -Value "$taskHash  love-advisor-1.2.12-debug.apk" -Encoding ascii
& (Join-Path $PSScriptRoot 'verify-native-libs.ps1') -Apk 'output/love-advisor-1.2.12-debug.apk' -Report 'output/native-libs-1.2.12.json'
exit $LASTEXITCODE
