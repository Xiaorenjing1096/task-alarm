#!/usr/bin/env pwsh
# Build wrapper for this project. See tools/gradle-env.ps1 for why the
# environment is set up the way it is.
#
# Usage:
#   tools/build.ps1                       # assembleDebug
#   tools/build.ps1 assembleRelease
#   tools/build.ps1 compileDebugKotlin

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Tasks = @('assembleDebug')
)

. (Join-Path $PSScriptRoot 'gradle-env.ps1')

exit (Invoke-AppAlarmGradle @Tasks)
