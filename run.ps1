# Install the debug APK, launch it, capture a screenshot, and dump recent errors.
#
# IMPORTANT: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files as
# ANSI/GBK when there is no BOM, so non-ASCII bytes (e.g. Chinese comments) can be
# decoded into quotes or backticks and break parsing.
#
# Usage: powershell -File run.ps1 [-Shot name] [-Wait 8] [-NoInstall]

param(
    [string]$Shot = "shot",
    [int]$Wait = 8,
    [switch]$NoInstall,
    # MainActivity has no LAUNCHER filter any more: the entry points are the
    # activity-alias components used for icon switching, so launch via one of them.
    [string]$Activity = "za.bilbd.app.icon.IconDefault"
)

# Native adb writes progress to stderr; keep that from aborting the script.
$ErrorActionPreference = "Continue"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk   = "C:\Users\wuzih\AppData\Local\Android\Sdk"
$Adb   = Join-Path $Sdk "platform-tools\adb.exe"
$Apk   = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
$Pkg   = "za.bilbd.app.debug"
$Out   = Join-Path $ProjectRoot "shots"
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$env:ANDROID_SDK_ROOT = $Sdk

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $o = & $Adb @AdbArgs 2>&1
    return ($o | Out-String).Trim()
}

if (-not $NoInstall) {
    # Stop the app BEFORE installing: while it is running, "adb install -r" can
    # report Success without the new code actually taking effect.
    Invoke-Adb @("shell", "am", "force-stop", $Pkg) | Out-Null
    Write-Host "== install"
    $installOut = Invoke-Adb @("install", "-r", "-t", $Apk)
    Write-Host ("   " + ($installOut -replace "`r`n", " | "))
    if ($installOut -notmatch "Success") {
        Write-Host "!! install failed, aborting"
        exit 1
    }
}

Write-Host "== clear logcat + launch"
Invoke-Adb @("logcat", "-c") | Out-Null
Invoke-Adb @("shell", "am", "force-stop", $Pkg) | Out-Null
$launch = Invoke-Adb @("shell", "am", "start", "-W", "-n", "$Pkg/$Activity")
Write-Host ("   " + ($launch -replace "`r`n", " | "))

Start-Sleep -Seconds $Wait

$remote = "/sdcard/$Shot.png"
Invoke-Adb @("shell", "screencap", "-p", $remote) | Out-Null
$local = Join-Path $Out "$Shot.png"
Write-Host ("   pull: " + (Invoke-Adb @("pull", $remote, $local)))
Invoke-Adb @("shell", "rm", "-f", $remote) | Out-Null

if (Test-Path $local) {
    $len = (Get-Item $local).Length
    Write-Host "== screenshot $local ($len bytes)"
} else {
    Write-Host "!! screenshot failed"
}

Write-Host "== process alive?"
Write-Host ("   " + (Invoke-Adb @("shell", "pidof", $Pkg)))

Write-Host "== crashes / app errors"
$log = Invoke-Adb @("logcat", "-d", "-v", "brief")
$log -split "`r?`n" | Where-Object { $_ -match "AndroidRuntime|FATAL|biliboard|uk\.biliboard" } | Select-Object -First 40 | ForEach-Object { Write-Host "   $_" }
