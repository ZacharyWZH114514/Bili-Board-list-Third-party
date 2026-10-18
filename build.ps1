# Build helper for the BiliBoard Android app.
# Ensures JDK 21 + Gradle 9.8.1 are used (the java on PATH is 1.8, unusable for AGP),
# and redirects GRADLE_USER_HOME / TEMP into the project because this machine's
# default locations are rejected for build subprocesses.
#
# Usage: powershell -File build.ps1 -Task ":app:assembleDebug"

param(
    [string]$Task = "assembleDebug",
    [switch]$Clean,
    [switch]$Info,
    [switch]$Stacktrace
)

$ErrorActionPreference = "Stop"

$ProjectRoot   = Split-Path -Parent $MyInvocation.MyCommand.Path
$WorkspaceRoot = Split-Path -Parent $ProjectRoot

$JdkHome   = Join-Path $WorkspaceRoot ".tools\jdk21\jdk-21.0.12.1+1"
$GradleBin = Join-Path $WorkspaceRoot ".tools\gradle-9.8.1\gradle-9.8.1\bin\gradle.bat"

if (-not (Test-Path $JdkHome))   { throw "JDK not found: $JdkHome" }
if (-not (Test-Path $GradleBin)) { throw "Gradle not found: $GradleBin" }

$env:JAVA_HOME = $JdkHome
$env:PATH      = "$JdkHome\bin;$env:PATH"

$env:GRADLE_USER_HOME = Join-Path $ProjectRoot ".gradle-home"
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null

$env:TMP = Join-Path $ProjectRoot ".tmp"
$env:TEMP = $env:TMP
New-Item -ItemType Directory -Force -Path $env:TMP | Out-Null

# AGP writes debug.keystore / analytics into ~/.android by default, which is also
# rejected for build subprocesses; ANDROID_USER_HOME moves it inside the project
# (AGP generates debug.keystore automatically when it is missing).
$env:ANDROID_USER_HOME = Join-Path $ProjectRoot ".android-home"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null

$gradleArgs = @($Task, "--console=plain")
if ($Clean)      { $gradleArgs = @("clean") + $gradleArgs }
if ($Info)       { $gradleArgs += "--info" }
if ($Stacktrace) { $gradleArgs += "--stacktrace" }

Write-Host "JAVA_HOME        = $env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "TEMP             = $env:TEMP"
Write-Host "ANDROID_USER_HOME= $env:ANDROID_USER_HOME"
Write-Host "gradle $($gradleArgs -join ' ')"
Write-Host ""

& $GradleBin @gradleArgs
exit $LASTEXITCODE
