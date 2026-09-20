#!/usr/bin/env pwsh
# Start the AppAlarm AVD. This is a long-running process: run it as a
# background job, not in the foreground.
#
# Usage:
#   tools/emulator.ps1                 # headless
#   tools/emulator.ps1 -WithWindow     # show the emulator window

param(
    [switch]$WithWindow,
    [string]$AvdName = 'AppAlarm'
)

. (Join-Path $PSScriptRoot 'emulator-env.ps1')

$emulatorArgs = @(
    '-avd', $AvdName
    '-no-audio'
    '-no-boot-anim'
    '-no-snapshot'
    # SwiftShader renders in software, which is what makes -no-window work and
    # keeps screenshots deterministic.
    '-gpu', 'swiftshader_indirect'
    '-no-metrics'
)
if (-not $WithWindow) { $emulatorArgs += '-no-window' }

& $Emulator @emulatorArgs
exit $LASTEXITCODE
