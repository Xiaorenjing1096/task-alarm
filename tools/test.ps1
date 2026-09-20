#!/usr/bin/env pwsh
# Run the JVM unit tests.
#
# Why this does not simply call `gradle testDebugUnitTest`: the DSH file sandbox
# forbids the named pipe Gradle opens to feed the forked test executor's stdin,
# so the task aborts with "Could not write standard input to Gradle Test
# Executor 1" even though compilation itself succeeds.
#
# So the work is split: Gradle compiles the test sources (that part works fine),
# then JUnit runs directly in this foreground process, whose stdout pwsh can
# capture without any inter-process pipe.
#
# The `testDebugUnitTest` Gradle task is still configured in app/build.gradle.kts
# and works normally in a real terminal or in Android Studio.
#
# Usage:
#   tools/test.ps1

$ErrorActionPreference = 'Continue'

. (Join-Path $PSScriptRoot 'gradle-env.ps1')

Write-Host '[1/2] Compiling unit tests with Gradle...' -ForegroundColor Cyan
$compileCode = Invoke-AppAlarmGradle 'compileDebugUnitTestKotlin'
if ($compileCode -ne 0) {
    Write-Host "Compilation failed (exit $compileCode)." -ForegroundColor Red
    exit $compileCode
}

$jars = @(
    (Find-ArtifactJar 'junit\junit' 'junit-*.jar' '4.13.2')
    (Find-ArtifactJar 'org.hamcrest\hamcrest-core' 'hamcrest-core-*.jar' '1.3')
    (Find-ArtifactJar 'org.jetbrains.kotlin\kotlin-stdlib' 'kotlin-stdlib-*.jar' '2.2.10')
    (Find-ArtifactJar 'org.jetbrains.kotlinx\kotlinx-serialization-core-jvm' 'kotlinx-serialization-core-jvm-*.jar' '1.9.0')
    (Find-ArtifactJar 'org.jetbrains.kotlinx\kotlinx-serialization-json-jvm' 'kotlinx-serialization-json-jvm-*.jar' '1.9.0')
    (Find-ArtifactJar 'org.jetbrains\annotations' 'annotations-*.jar')
) | Where-Object { $_ } | Select-Object -Unique

if ($jars.Count -lt 4) {
    throw "Could not locate the JUnit runtime jars in the Gradle caches. Found: $($jars -join ', ')"
}

# AGP's intermediate output paths are an implementation detail that has already
# moved once (the directory is "built_in_kotlinc", not "built_in_kotlin"), so
# discover them instead of hard-coding.
function Find-CompiledClasses {
    param([Parameter(Mandatory)][string]$TaskNameFragment)

    $intermediates = Join-Path $AppAlarmRoot 'app\build\intermediates'
    $candidates = Get-ChildItem $intermediates -Recurse -Directory -Filter 'classes' -ErrorAction SilentlyContinue

    foreach ($candidate in $candidates) {
        if ($candidate.FullName -notlike "*$TaskNameFragment*") { continue }
        $hasClasses = Get-ChildItem $candidate.FullName -Recurse -File -Filter '*.class' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hasClasses) { return $candidate.FullName }
    }
    return $null
}

$mainClassesDir = Find-CompiledClasses 'compileDebugKotlin'
$testClassesDir = Find-CompiledClasses 'compileDebugUnitTestKotlin'

if (-not $mainClassesDir) { throw 'Could not locate the compiled main classes under app\build\intermediates.' }
if (-not $testClassesDir) { throw 'Could not locate the compiled test classes under app\build\intermediates.' }

$testClasses = @(
    Get-ChildItem $testClassesDir -Recurse -File -Filter '*Test.class' |
        Where-Object { $_.Name -notlike '*$*' } |
        ForEach-Object {
            ($_.FullName.Substring($testClassesDir.Length + 1) -replace '\\', '.') -replace '\.class$', ''
        } |
        Sort-Object
)

if (-not $testClasses) { throw "No test classes found under $testClassesDir" }

$classpath = (@($jars) + $mainClassesDir + $testClassesDir) -join ';'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

Write-Host "[2/2] Running $($testClasses.Count) test class(es) with JUnit..." -ForegroundColor Cyan
Write-Host ''

# JUnit prints failures to stderr; merge it so the host does not mistake the
# expected failure output for a harness error. -Dfile.encoding=UTF-8 keeps the
# Chinese assertion messages readable instead of turning into mojibake.
$output = & $java '-Dfile.encoding=UTF-8' -cp $classpath org.junit.runner.JUnitCore @testClasses 2>&1
$exitCode = $LASTEXITCODE
$output | ForEach-Object { "$_" }
exit $exitCode
