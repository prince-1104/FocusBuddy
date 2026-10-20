param([Parameter(Mandatory = $true)][string]$Serial)

# Use a disposable API 33+ emulator/device: this changes this app's permission state.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $root '.tooling\android-sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
function Device([string[]]$Arguments) {
    $output = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    $output
}
function Run-Test([string]$Class, [string]$Phase = '') {
    $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Class)
    if ($Phase) { $arguments += @('-e', 'phase', $Phase) }
    $result = Device ($arguments + 'com.focusbuddy.test/androidx.test.runner.AndroidJUnitRunner')
    $result | Write-Output
    if (($result -join "`n") -notmatch 'OK \(1 test\)' -or ($result -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED|AssumptionViolated|ignored=1') {
        throw "Device test did not pass: $Class $Phase"
    }
}
$api = [int](Device @('shell', 'getprop', 'ro.build.version.sdk') | Select-Object -Last 1)
if ($api -lt 33) { throw 'This permission verification script requires API 33 or newer.' }
& (Join-Path $PSScriptRoot 'verify.ps1') -Tasks @(':app:assembleDebug', ':app:assembleDebugAndroidTest')
Device @('install', '-r', (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'))
Device @('install', '-r', (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$fixture = 'com.focusbuddy.reminders.ReminderProcessDeathTest#processDeathRecovery'
try {
    Device @('shell', 'pm', 'grant', 'com.focusbuddy', 'android.permission.POST_NOTIFICATIONS')
    Device @('shell', 'appops', 'set', 'com.focusbuddy', 'SCHEDULE_EXACT_ALARM', 'deny')
    Run-Test 'com.focusbuddy.reminders.ReminderDeviceTest#unavailableExactAccessUsesRealInexactFallback'
    Device @('shell', 'appops', 'set', 'com.focusbuddy', 'SCHEDULE_EXACT_ALARM', 'allow')
    Run-Test 'com.focusbuddy.reminders.ReminderDeviceTest#notificationIsPostedByRealAlarmWhileNoActivityIsOpen'
    Device @('shell', 'pm', 'revoke', 'com.focusbuddy', 'android.permission.POST_NOTIFICATIONS')
    Run-Test 'com.focusbuddy.reminders.ReminderDeviceTest#deniedPermissionLeavesFutureOccurrenceBlocked'
    Device @('shell', 'pm', 'grant', 'com.focusbuddy', 'android.permission.POST_NOTIFICATIONS')
    Run-Test $fixture 'seed'
    Device @('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    Device @('shell', 'am', 'kill', 'com.focusbuddy')
    # pidof returns exit 1 when absent. Check directly rather than treating absence as an adb error.
    $pidOutput = & $adb -s $Serial shell pidof com.focusbuddy 2>$null
    if ($pidOutput) { throw 'App process remained alive; process-death verification is invalid.' }
    Write-Output 'App process confirmed absent before alarm delivery.'
    Start-Sleep -Seconds 45
    Start-Sleep -Seconds 45
    Run-Test $fixture 'verify'
} finally {
    Run-Test $fixture 'cleanup'
    Device @('shell', 'pm', 'grant', 'com.focusbuddy', 'android.permission.POST_NOTIFICATIONS')
    Device @('shell', 'appops', 'set', 'com.focusbuddy', 'SCHEDULE_EXACT_ALARM', 'default')
}
