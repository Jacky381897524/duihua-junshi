param([string[]]$Tasks = @(':core:test', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskConfig = Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskMirror = Join-Path $taskConfig.active 'project'
New-Item -ItemType Directory -Force -Path $taskMirror | Out-Null
$taskSource = Join-Path $taskRoot 'android'
& robocopy $taskSource $taskMirror /MIR /XD .gradle build .git /XF local.properties /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'Build mirror copy failed' }
$taskSdkEscaped = $taskConfig.sdk.Replace('\','/').Replace(':','\:')
Set-Content -LiteralPath (Join-Path $taskMirror 'local.properties') -Value "sdk.dir=$taskSdkEscaped" -Encoding ascii
$env:JAVA_HOME = $taskConfig.java
$env:ANDROID_HOME = $taskConfig.sdk
$env:ANDROID_USER_HOME = Join-Path $taskConfig.active 'android-user'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$env:GRADLE_USER_HOME = Join-Path $taskConfig.active 'gradle-cache'
$env:LOVEADVISOR_MAVEN_CACHE = Join-Path $taskConfig.active 'maven'
$taskSigningDir = Join-Path $taskRoot 'private-signing'
New-Item -ItemType Directory -Force -Path $taskSigningDir | Out-Null
$taskSigningStore = Join-Path $taskSigningDir 'love-advisor-dev.p12'
if (!(Test-Path -LiteralPath $taskSigningStore)) {
    & (Join-Path $taskConfig.java 'bin/keytool.exe') '-genkeypair' '-keystore' $taskSigningStore '-storetype' 'PKCS12' '-storepass' 'android' '-keypass' 'android' '-alias' 'love-advisor-debug' '-keyalg' 'RSA' '-keysize' '2048' '-validity' '10000' '-dname' 'CN=LoveAdvisor Development,O=Local,C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'Own development signing key generation failed' }
}
$taskSigningMirror = Join-Path $taskConfig.active 'signing'
New-Item -ItemType Directory -Force -Path $taskSigningMirror | Out-Null
$env:LOVEADVISOR_SIGNING_STORE = Join-Path $taskSigningMirror 'love-advisor-dev.p12'
Copy-Item -LiteralPath $taskSigningStore -Destination $env:LOVEADVISOR_SIGNING_STORE -Force
$env:PATH = "$($taskConfig.java)\bin;$env:PATH"
$taskLogDir = Join-Path $taskRoot '.superpowers/sdd/2026-10-06-author-1212'
New-Item -ItemType Directory -Force -Path $taskLogDir | Out-Null
$taskLog = Join-Path $taskLogDir ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
Push-Location $taskMirror
try {
    & (Join-Path $taskConfig.gradle 'bin/gradle.bat') @Tasks '--no-daemon' '--console=plain' "-DANDROID_USER_HOME=$env:ANDROID_USER_HOME" 2>&1 | Tee-Object -FilePath $taskLog
    $taskExitCode = $LASTEXITCODE
} finally { Pop-Location }
$taskOutput = Join-Path $taskRoot 'output'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskApk = Join-Path $taskMirror 'app/build/outputs/apk/debug/app-debug.apk'
if ($taskExitCode -eq 0 -and $Tasks -contains ':app:assembleDebug' -and (Test-Path -LiteralPath $taskApk)) { Copy-Item -LiteralPath $taskApk -Destination (Join-Path $taskOutput 'love-advisor-1.2.12-debug.apk') -Force }
Write-Output "Build log: $taskLog"
exit $taskExitCode
