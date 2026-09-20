# Android SDK / emulator environment for this project. Dot-source it, do not run it.
#
# WHY THIS FILE EXISTS
#
# The DSH file sandbox only permits writes inside the project directory, but the
# emulator and adb write to the user profile by default:
#
#   ~/.android/avd                      -> AVD definitions and disk images
#   ~/.android/emu-last-feature-flags.* -> emulator preferences
#   ~/.android/adbkey                   -> adb keys
#
# All of it is redirected into .android-home/ below. Note that these are four
# *different* variables and the emulator reads different ones for different
# things: getting only some of them right produces a confusing mix of "no
# writable prefs" and "unknown AVD name" errors.
#
# WARNING: never store any of these paths in a variable called $home or $HOME.
# PowerShell's $HOME is read-only, so `$home = 'D:\...'` fails with a non-fatal
# error and *leaves the old value in place*, silently sending ANDROID_AVD_HOME
# to C:\Users\<user>\avd and making the AVD look like it does not exist.

# --------------------------------------------------------------------------
# Locate the Android SDK. Override with $env:APPALARM_SDK if it lives elsewhere.
# --------------------------------------------------------------------------
function Find-AppAlarmSdk {
    $candidates = @(
        $env:APPALARM_SDK,
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) {
            return $candidate
        }
    }
    return $null
}

$AndroidSdk = Find-AppAlarmSdk
if (-not $AndroidSdk) {
    throw 'Android SDK not found. Set $env:APPALARM_SDK (or ANDROID_HOME) to your SDK directory.'
}

$AppAlarmPrefsRoot = Join-Path (Split-Path -Parent $PSScriptRoot) '.android-home'

$env:ANDROID_HOME          = $AndroidSdk
$env:ANDROID_SDK_ROOT      = $AndroidSdk
$env:ANDROID_USER_HOME     = $AppAlarmPrefsRoot
$env:ANDROID_PREFS_ROOT    = $AppAlarmPrefsRoot
$env:ANDROID_EMULATOR_HOME = $AppAlarmPrefsRoot
$env:ANDROID_AVD_HOME      = Join-Path $AppAlarmPrefsRoot 'avd'

$Adb      = Join-Path $AndroidSdk 'platform-tools\adb.exe'
$Emulator = Join-Path $AndroidSdk 'emulator\emulator.exe'

foreach ($tool in @($Adb, $Emulator)) {
    if (-not (Test-Path $tool)) { throw "Android tool not found: $tool" }
}

# adb prints progress on stderr, which upsets the host; flatten it to text.
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $result = & $Adb @AdbArgs 2>&1
    $code = $LASTEXITCODE
    $result | ForEach-Object { "$_" }
    return $code
}
