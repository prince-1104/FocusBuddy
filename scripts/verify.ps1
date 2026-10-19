param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolingRoot = Join-Path $projectRoot '.tooling'

# Prefer the user's configured tools; otherwise use this workspace's ignored tools.
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $toolingRoot 'jdk'))) {
    $portableJdk = Get-ChildItem -LiteralPath (Join-Path $toolingRoot 'jdk') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
}
if (-not $env:ANDROID_HOME -and (Test-Path (Join-Path $toolingRoot 'android-sdk'))) {
    $env:ANDROID_HOME = Join-Path $toolingRoot 'android-sdk'
}
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $toolingRoot 'android-user' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $toolingRoot 'gradle-home' }

Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @Tasks --console=plain --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}
