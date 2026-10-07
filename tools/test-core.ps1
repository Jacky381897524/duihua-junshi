param([string[]]$Tests = @())
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskConfig = Get-Content -Raw -LiteralPath (Join-Path $taskRoot '.toolchain/config.json') | ConvertFrom-Json
$taskLib = Join-Path $taskConfig.gradle 'lib'
$taskCompilerCp = "$taskLib/*"
$taskRuntimeJars = @('kotlin-stdlib-1.9.23.jar','junit-4.13.2.jar','hamcrest-core-1.3.jar','annotations-24.0.1.jar') | ForEach-Object { Join-Path $taskLib $_ }
$taskGson = Join-Path $taskRoot '.toolchain/gson-2.11.0.jar'
if (Test-Path -LiteralPath $taskGson) { $taskRuntimeJars += $taskGson }
$taskCp = $taskRuntimeJars -join ';'
$taskOut = Join-Path $taskConfig.active ('core-tests-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
$taskFiles = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'android/core/src') -Recurse -Filter '*.kt' -File
$taskArgs = Join-Path $taskOut 'sources.txt'
$taskFiles | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' } | Set-Content -LiteralPath $taskArgs -Encoding utf8
$taskJava = Join-Path $taskConfig.java 'bin/java.exe'
& $taskJava '-Dfile.encoding=UTF-8' '-cp' $taskCompilerCp 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' '-no-stdlib' '-no-reflect' '-jvm-target' '17' '-classpath' $taskCp '-d' $taskOut "@$taskArgs"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Tests.Count -eq 0) {
    $Tests = $taskFiles | Where-Object { $_.Name -like '*Test.kt' } | ForEach-Object {
        $taskPackage = ((Get-Content -LiteralPath $_.FullName | Where-Object { $_ -match '^package ' } | Select-Object -First 1) -replace '^package ','').Trim()
        "$taskPackage.$($_.BaseName)"
    }
}
$taskLog = Join-Path $taskRoot ('.superpowers/sdd/2026-10-04-automatic-wechat/core-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '.log')
& $taskJava '-Dfile.encoding=UTF-8' '--add-modules' 'jdk.httpserver' '-cp' "$taskOut;$taskCp" 'org.junit.runner.JUnitCore' @Tests 2>&1 | Tee-Object -FilePath $taskLog
exit $LASTEXITCODE
