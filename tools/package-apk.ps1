$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfig=Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskGradle=Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'android/app/build.gradle.kts')
$taskVersion=[regex]::Match($taskGradle,'versionName\s*=\s*"V([^"\r\n]+)"').Groups[1].Value
if([string]::IsNullOrWhiteSpace($taskVersion)) { throw 'No Android versionName found' }
$taskApk=Join-Path $taskRoot "output/love-advisor-$taskVersion-debug.apk"
$env:JAVA_HOME=$taskConfig.java
& (Join-Path $taskConfig.sdk 'build-tools/35.0.0/apksigner.bat') 'verify' '--verbose' '--print-certs' $taskApk | Tee-Object -FilePath (Join-Path $taskRoot "output/signature-$taskVersion.txt")
if($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$taskHash=(Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLower()
Set-Content -LiteralPath (Join-Path $taskRoot "output/SHA256-$taskVersion.txt") -Value "$taskHash  love-advisor-$taskVersion-debug.apk" -Encoding ascii
& (Join-Path $PSScriptRoot 'verify-native-libs.ps1') -Apk "output/love-advisor-$taskVersion-debug.apk" -Report "output/native-libs-$taskVersion.json"
exit $LASTEXITCODE
