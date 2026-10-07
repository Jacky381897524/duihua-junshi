$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfig=Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskCp=@(
    (Join-Path $taskConfig.active 'project/app/build/tmp/kotlin-classes/debug'),
    (Join-Path $taskConfig.active 'project/app/build/tmp/kotlin-classes/debugAndroidTest'),
    (Join-Path $taskConfig.active 'project/core/build/classes/kotlin/main'),
    (Join-Path $taskConfig.gradle 'lib/kotlin-stdlib-1.9.23.jar'),
    (Join-Path $taskConfig.gradle 'lib/junit-4.13.2.jar'),
    (Join-Path $taskConfig.gradle 'lib/hamcrest-core-1.3.jar'),
    (Join-Path $taskRoot '.toolchain/gson-2.11.0.jar'),
    (Join-Path $taskConfig.sdk 'platforms/android-35/android.jar')
) -join ';'
# Compile the current migration tests against the latest built app, avoiding stale test classes.
$taskTestOut=Join-Path $taskConfig.active ('settings-tests-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $taskTestOut | Out-Null
$taskSource=Join-Path $taskRoot 'android/app/src/androidTest/java/com/loveadvisor/android/storage/AutomaticSettingsTest.kt'
& (Join-Path $taskConfig.java 'bin/java.exe') '-Dfile.encoding=UTF-8' '-cp' ((Join-Path $taskConfig.gradle 'lib')+'/*') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler '-no-stdlib' '-no-reflect' '-jvm-target' '17' '-classpath' $taskCp '-d' $taskTestOut $taskSource
if($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# This class exercises pure Gson configuration migration; it calls no Android APIs.
$taskLog=Join-Path $taskRoot '.superpowers/sdd/2026-10-04-automatic-wechat/settings-tests.log'
& (Join-Path $taskConfig.java 'bin/java.exe') '-Dfile.encoding=UTF-8' '-cp' "$taskTestOut;$taskCp" org.junit.runner.JUnitCore com.loveadvisor.android.storage.AutomaticSettingsTest 2>&1 | Tee-Object -FilePath $taskLog
exit $LASTEXITCODE
