$ErrorActionPreference = 'Stop'
$taskPython = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $taskPython (Join-Path $PSScriptRoot 'verify-sources.py')
exit $LASTEXITCODE
