# Push only a theme's icons/ folder to the launcher on the TV: clear the TV's icons, write each one through the
# launcher's ThemeProvider (falling back to plain cp into its files directory), then restart Home once. The full
# tools/theme.ps1 does the same and re-uploads every wallpaper too, about two minutes; this is the quick route after
# re-running tools/make_icons.ps1.
#
# Usage: $env:TV_HOST = '<tv ip>'; powershell -File tools/push_icons.ps1 [-Theme themes\aerial-glass]
param([string]$TvHost = $env:TV_HOST, [string]$Theme = 'themes\aerial-glass')
$ErrorActionPreference = 'Continue'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set TV_HOST' }
$env:TV_HOST = $TvHost; $env:PYTHONWARNINGS = 'ignore'
$root = Split-Path $PSScriptRoot -Parent
$icons = Join-Path $root "$Theme\icons"
if (-not (Test-Path $icons)) { throw "no icons folder at $icons; run tools/make_icons.ps1 first" }
$py = Join-Path $PSScriptRoot 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { $py = Join-Path $PSScriptRoot 'venv\bin\python' }
if (-not (Test-Path $py)) { throw 'run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt' }
$dir = '/sdcard/Android/data/com.gathin.tvhome/files'; $uri = 'content://com.gathin.tvhome.theme'
& $py -I (Join-Path $PSScriptRoot 'tv.py') "mkdir -p $dir/icons 2>/dev/null; rm -f $dir/icons/*.png 2>/dev/null; content delete --uri $uri/icons 2>/dev/null; true" 2>$null | Out-Null
$n = 0
foreach ($f in Get-ChildItem $icons -Filter *.png) {
    if ($f.Name -notmatch '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*\.png$') { "skipped (name): $($f.Name)"; continue }
    $tmp = "/data/local/tmp/cyt-icon-$($f.Name)"
    $ok = (& $py -I (Join-Path $PSScriptRoot 'b64push.py') $f.FullName $tmp 2>$null | Out-String) -match 'MATCH'
    & $py -I (Join-Path $PSScriptRoot 'tv.py') "{ o=`$(content write --uri $uri/icons/$($f.Name) < $tmp 2>&1) && case `"`$o`" in *'Unsupported operation'*) false ;; esac; } || cp $tmp $dir/icons/$($f.Name); rm -f $tmp" 2>$null | Out-Null
    if ($ok) { $n++ } else { "upload failed: $($f.Name)" }
}
$count = (& $py -I (Join-Path $PSScriptRoot 'tv.py') "ls $dir/icons | wc -l" 2>$null | Select-String -Pattern '^\s*\d+\s*$').Line
"pushed $n; on the TV now: $count"
& $py -I (Join-Path $PSScriptRoot 'theme_restart.py') 2>$null | Out-Null
'Home restarted'
