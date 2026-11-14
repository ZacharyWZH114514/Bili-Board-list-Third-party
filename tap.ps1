# Find a UI node by text (or content-desc) and tap its center, then screenshot.
# Far more reliable than guessing pixel coordinates.
#
# IMPORTANT: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files as
# ANSI/GBK when there is no BOM, so non-ASCII bytes (Chinese comments, symbols like
# the check mark) can be decoded into quotes or backticks and break parsing.
# Chinese UI text is still passed in as data via -Text.
#
# Usage:
#   powershell -File tap.ps1 -Shot 13-issues -Text "<label>" [-Index 2]
#   powershell -File tap.ps1 -Shot x -Back
#   powershell -File tap.ps1 -List
param(
    [string]$Shot = "",
    [string]$Text = "",
    [int]$Index = 1,
    [int]$Wait = 5,
    [switch]$Back,
    [switch]$Retap,
    [switch]$List
)

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = "C:\Users\wuzih\AppData\Local\Android\Sdk"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$Shots = Join-Path $ProjectRoot "shots"
$Tmp = Join-Path $ProjectRoot ".tmp"
New-Item -ItemType Directory -Force -Path $Shots, $Tmp | Out-Null
$env:ANDROID_SDK_ROOT = $Sdk

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $o = & $Adb @AdbArgs 2>&1
    return ($o | Out-String).Trim()
}

function Get-UiNodes {
    Invoke-Adb @("shell", "uiautomator", "dump", "/sdcard/ui.xml") | Out-Null
    $local = Join-Path $Tmp "ui.xml"
    Invoke-Adb @("pull", "/sdcard/ui.xml", $local) | Out-Null
    if (-not (Test-Path $local)) { return @() }
    $xml = [System.IO.File]::ReadAllText($local, [System.Text.Encoding]::UTF8)
    $nodes = @()
    foreach ($m in [regex]::Matches($xml, '<node[^>]*>')) {
        $tag = $m.Value
        $t = ([regex]::Match($tag, 'text="([^"]*)"')).Groups[1].Value
        $d = ([regex]::Match($tag, 'content-desc="([^"]*)"')).Groups[1].Value
        $b = ([regex]::Match($tag, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'))
        if (-not $b.Success) { continue }
        $label = if ($t) { $t } else { $d }
        if (-not $label) { continue }
        $x1 = [int]$b.Groups[1].Value; $y1 = [int]$b.Groups[2].Value
        $x2 = [int]$b.Groups[3].Value; $y2 = [int]$b.Groups[4].Value
        $nodes += [pscustomobject]@{
            Label = $label
            CX = [int](($x1 + $x2) / 2)
            CY = [int](($y1 + $y2) / 2)
            Area = ($x2 - $x1) * ($y2 - $y1)
        }
    }
    return $nodes
}

if ($Back) {
    Invoke-Adb @("shell", "input", "keyevent", "4") | Out-Null
    Start-Sleep -Milliseconds 1400
}

$nodes = Get-UiNodes
if ($List) {
    Write-Host "== nodes =="
    $nodes | ForEach-Object { Write-Host ("   [{0},{1}] {2}" -f $_.CX, $_.CY, $_.Label) }
}

if ($Text) {
    # exact match first, then prefix, then contains; smallest area wins in each tier
    $tiers = @(
        @($nodes | Where-Object { $_.Label -eq $Text }),
        @($nodes | Where-Object { $_.Label.StartsWith($Text) }),
        @($nodes | Where-Object { $_.Label -like "*$Text*" })
    )
    $matches = @()
    foreach ($t in $tiers) { if ($t.Count -gt 0) { $matches = @($t | Sort-Object Area); break } }
    if ($matches.Count -eq 0) {
        Write-Host "!! node not found: $Text"
        $nodes | Select-Object -First 30 | ForEach-Object { Write-Host ("   [{0},{1}] {2}" -f $_.CX, $_.CY, $_.Label) }
        exit 2
    }
    $pickIdx = [Math]::Min($Index - 1, $matches.Count - 1)
    $target = $matches[$pickIdx]
    Write-Host "tap '$($target.Label)' at ($($target.CX),$($target.CY))  [candidates $($matches.Count)]"
    Invoke-Adb @("shell", "input", "tap", "$($target.CX)", "$($target.CY)") | Out-Null
    if ($Retap) {
        Start-Sleep -Milliseconds 700
        Invoke-Adb @("shell", "input", "tap", "$($target.CX)", "$($target.CY)") | Out-Null
    }
}

Start-Sleep -Seconds $Wait

if ($Shot) {
    $remote = "/sdcard/$Shot.png"
    Invoke-Adb @("shell", "screencap", "-p", $remote) | Out-Null
    $local = Join-Path $Shots "$Shot.png"
    Invoke-Adb @("pull", $remote, $local) | Out-Null
    Invoke-Adb @("shell", "rm", "-f", $remote) | Out-Null
    if (Test-Path $local) { Write-Host "shot: $local ($((Get-Item $local).Length) bytes)" }
    else { Write-Host "!! screenshot failed" }
}

$errs = (Invoke-Adb @("logcat", "-d", "-v", "brief")) -split "`r?`n" |
    Where-Object { $_ -match "FATAL EXCEPTION|AndroidRuntime: .*Exception" } | Select-Object -First 8
if ($errs) { Write-Host "== ERRORS =="; $errs | ForEach-Object { Write-Host "   $_" } }
