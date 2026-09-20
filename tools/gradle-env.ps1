# Shared build environment for this project. Dot-source it, do not run it.
#
# WHY THIS FILE EXISTS
#
# The DSH file sandbox only allows writes inside the project directory, while
# Gradle, AGP and the Android tools all want to write into the user profile:
#
#   ~/.gradle        -> Gradle user home (caches, daemon, wrapper locks). The
#                       wrapper also insists on writing a .lck file next to the
#                       distribution, which the sandbox refuses.
#   ~/.android       -> debug keystore validation, analytics.
#   ~/.gradle/jdks   -> auto-provisioned Java toolchains.
#
# Everything is redirected into the project directory below. On a normal
# machine you do NOT need this file — just use ./gradlew. See README.md.

# NOTE: deliberately NOT $ErrorActionPreference = 'Stop'. Gradle's launcher
# writes a harmless native warning to stderr ("Couldn't open current thread,
# error = 5" — the sandbox restricts thread handles). With 'Stop' PowerShell
# turns that stderr line into a terminating error and kills the script mid-build,
# which looks exactly like a mysterious build failure. Callers check exit codes
# explicitly instead, and each native call below funnels stderr into the success
# stream so nothing leaks to the host as an error.

$AppAlarmRoot = Split-Path -Parent $PSScriptRoot

# --------------------------------------------------------------------------
# Locate a JDK to run Gradle with.
#
# AGP 9.4 needs a Java 25 toolchain. Android Studio ships one as its bundled
# JetBrains Runtime. No gradle.properties entry is needed for this: Gradle always
# treats the JVM running the build as an installed toolchain, so merely running
# Gradle on that JDK satisfies the requirement. (Getting this wrong is what
# causes Gradle to try downloading a JDK from foojay, which is extremely slow.)
#
# Override with $env:APPALARM_JDK if your JDK lives somewhere unusual.
# --------------------------------------------------------------------------
function Find-AppAlarmJavaHome {
    $override = $env:APPALARM_JDK
    if ($override -and (Test-Path (Join-Path $override 'bin\java.exe'))) { return $override }

    $candidates = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path ${env:ProgramFiles(x86)} 'Android\Android Studio\jbr'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
    )
    # Android Studio is not always installed on the system drive.
    foreach ($drive in (Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue)) {
        $candidates += (Join-Path $drive.Root 'Program Files\Android\Android Studio\jbr')
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe'))) { return $candidate }
    }
    return $null
}

$appAlarmJavaHome = Find-AppAlarmJavaHome
if (-not $appAlarmJavaHome) {
    throw 'No JDK found. Install Android Studio, or set $env:APPALARM_JDK to a JDK 25 home directory.'
}
$env:JAVA_HOME = $appAlarmJavaHome

$env:GRADLE_USER_HOME   = Join-Path $AppAlarmRoot '.gradle-home'
$env:ANDROID_USER_HOME  = Join-Path $AppAlarmRoot '.android-home'
# Reuse the dependency cache already present in the real user profile as a
# read-only cache; newly downloaded artifacts still land in GRADLE_USER_HOME.
$env:GRADLE_RO_DEP_CACHE = Join-Path $env:USERPROFILE '.gradle\caches'

# --------------------------------------------------------------------------
# Locate the Gradle launcher.
#
# The extracted distribution is invoked directly rather than through gradlew,
# because the wrapper cannot create its lock file under the sandbox. The hash
# directory name in the wrapper path differs per machine, so look it up instead
# of hard-coding it. Falls back to gradlew for normal (non-sandboxed) machines.
# --------------------------------------------------------------------------
function Find-AppAlarmGradle {
    $base = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.6.0-bin'
    if (Test-Path $base) {
        $found = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'gradle-9.6.0\bin\gradle.bat' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($found) { return $found }
    }
    $wrapper = Join-Path $AppAlarmRoot 'gradlew.bat'
    if (Test-Path $wrapper) { return $wrapper }
    return $null
}

$GradleBat = Find-AppAlarmGradle
if (-not $GradleBat) { throw 'Gradle not found: neither a wrapper distribution nor gradlew.bat.' }

function Invoke-AppAlarmGradle {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks)

    if (-not $Tasks) { $Tasks = @('assembleDebug') }
    Push-Location $AppAlarmRoot
    try {
        # Gradle's output goes to the information stream via Write-Host so that
        # ONLY the exit code travels down the pipeline — otherwise a caller doing
        # `$code = Invoke-AppAlarmGradle ...` would capture the whole log instead
        # of the exit code. 2>&1 keeps Gradle's stderr out of the host's error
        # stream (see the note at the top of this file).
        & $GradleBat @Tasks --console=plain 2>&1 | ForEach-Object { Write-Host "$_" }
        # $LASTEXITCODE survives the pipeline: cmdlets do not reset it.
        return $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}

# Candidate module caches, in priority order: the writable one first.
function Get-GradleCacheRoots {
    @(
        (Join-Path $env:GRADLE_USER_HOME 'caches\modules-2\files-2.1'),
        (Join-Path $env:GRADLE_RO_DEP_CACHE 'modules-2\files-2.1')
    ) | Where-Object { Test-Path $_ }
}

# Locate one artifact jar inside the Gradle module caches.
function Find-ArtifactJar {
    param(
        [Parameter(Mandatory)][string]$GroupPath,
        [Parameter(Mandatory)][string]$NamePattern,
        [string]$PreferVersion
    )

    foreach ($root in Get-GradleCacheRoots) {
        $dir = Join-Path $root $GroupPath
        if (-not (Test-Path $dir)) { continue }

        $jars = @(Get-ChildItem $dir -Recurse -File -Filter $NamePattern -ErrorAction SilentlyContinue)
        if (-not $jars) { continue }

        if ($PreferVersion) {
            $preferred = $jars | Where-Object { $_.Name -like "*$PreferVersion*" } | Select-Object -First 1
            if ($preferred) { return $preferred.FullName }
        }
        return ($jars | Sort-Object Name -Descending | Select-Object -First 1).FullName
    }
    return $null
}
