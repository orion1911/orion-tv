# custom_icons for a theme: one shaded white mark per app, cut from SicMundus86's Projectivy Icon Pack
# (github.com/SicMundus86/ProjectivyIconPack). The pack is free but carries no licence file, so its icons are made
# here for your own TV and stay out of git (themes/*/icons/ is in .gitignore). Every launchable app on the TV that the
# pack maps gets the pack's icon as a shaded white mark in that theme's icons/<package>.png, replacing any icons there.
# The icons live only inside the pack's APK, with obfuscated resource names: the app-to-icon mapping is the compiled
# res/hA.xml and the icon files are found through the resource table, both read with aapt2 from the Android SDK.
# Each icon is cut to its own outline (transparent background), shaded white (brightest part full white, darker parts
# clearer) and centred in a 192 px square: at most 62 % of it tall, and up to 78 % wide for the pack's wordmarks.
#
# Usage: $env:TV_HOST = '<tv ip>'; powershell -File tools/make_icons.ps1 [-Theme themeserial-glass]
# Downloads the pack APK and Bebas Neue (SIL OFL) once into tools/iconpack/; run tools/push_icons.ps1 to send the
# icons, or tools/theme.ps1 for the whole theme.
param([string]$TvHost = $env:TV_HOST, [string]$Theme = 'themes\aerial-glass')
$ErrorActionPreference = 'Continue'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set TV_HOST' }
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $PSScriptRoot 'iconpack'   # the pack APK, the font and the cut-outs: local only (.gitignore)
$dest = Join-Path $root "$Theme\icons"
$apk = Join-Path $work 'pack.apk'
New-Item -ItemType Directory -Force $work, $dest | Out-Null
if (-not (Test-Path $apk)) { Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/SicMundus86/ProjectivyIconPack/main/Latestrelease/projectivyiconpack-latest.apk' -OutFile $apk -UseBasicParsing }
$aapt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $aapt) { throw 'aapt2 not found in the Android SDK build-tools' }

# The pack's mapping (package -> icon name) and resource table (icon name -> file in the APK).
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
$filterEntry = $null
foreach ($e in $zip.Entries | Where-Object { $_.FullName -match '^res/[^/]+\.xml$' } | Sort-Object Length -Descending | Select-Object -First 3) {
    $dump = & $aapt dump xmltree --file $e.FullName $apk 2>$null
    if (($dump -join "`n") -match 'ComponentInfo\{') { $filterEntry = $e.FullName; break }
}
if (-not $filterEntry) { throw 'no appfilter found in the pack' }
$map = @{}; $comp = $null
foreach ($l in $dump) { if ($l -match 'A: component="ComponentInfo\{([^/}]*)/') { $comp = $Matches[1] } elseif ($l -match 'A: drawable="([^"]+)"' -and $comp) { if (-not $map.ContainsKey($comp)) { $map[$comp] = $Matches[1] }; $comp = $null } }
$file = @{}; $cur = $null
foreach ($l in (& $aapt dump resources $apk 2>$null)) { if ($l -match 'resource 0x[0-9a-f]+ drawable/(\S+)') { $cur = $Matches[1] } elseif ($l -match '\(file\) (res/\S+) type=PNG' -and $cur -and -not $file.ContainsKey($cur)) { $file[$cur] = $Matches[1] } }
"pack: $($map.Count) packages mapped ($filterEntry)"

# The TV's launchable apps.
$env:TV_HOST = $TvHost; $env:PYTHONWARNINGS = 'ignore'
$q = Join-Path $work 'q-icons.txt'
Set-Content -Path $q -Value 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER | grep / | cut -d/ -f1 | sort -u' -Encoding ascii
$py = Join-Path $PSScriptRoot 'venv\Scripts\python.exe'; $tv = Join-Path $PSScriptRoot 'tv.py'
if (-not (Test-Path $py)) { $py = Join-Path $PSScriptRoot 'venv\bin\python' }
$pkgs = @((cmd /c "`"$py`" -W ignore `"$tv`" --batch < `"$q`"" 2>$null) | Where-Object { $_ -match '^\s*[a-z][\w\.]+\s*$' } | ForEach-Object { $_.Trim() })
"TV: $($pkgs.Count) launchable apps"

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class IconMaker {
    const int SIZE = 192; const float TALL = 0.62f, WIDE = 0.78f;
    static int[] Read(Bitmap b) {
        var d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var a = new int[b.Width * b.Height]; Marshal.Copy(d.Scan0, a, 0, a.Length); b.UnlockBits(d); return a;
    }
    static Bitmap Write(int[] a, int w, int h) {
        var b = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        var d = b.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(a, 0, d.Scan0, a.Length); b.UnlockBits(d); return b;
    }
    static bool Backdrop(int p) {
        int a = (p >> 24) & 255, r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
        return a < 8 || (r + g + bl < 40 && a < 210);
    }
    static float Lum(int p) { return (0.2126f * ((p >> 16) & 255) + 0.7152f * ((p >> 8) & 255) + 0.0722f * (p & 255)) / 255f; }
    // A logo drawn into a box of its own: wFrac x hFrac of the square, centred, stretched if the box's shape differs.
    // A wordmark alone, styled like the pack's Netflix: the part left of the first gap (a symbol) cut off, the word
    // stretched into wFrac x hFrac of the square, bent like Netflix's (outer letters full height, the middle ones `arc`
    // shorter, mostly from the bottom), strokes thickened by `bold` px at 4x. Drawn at 4x, then scaled down.
    public static string Wordmark(string inPath, string outPath, float wFrac, float hFrac, float arc, int bold, bool cutSymbol) {
        var src = new Bitmap(inPath); int w = src.Width, h = src.Height; var px = Read(src); src.Dispose();
        float maxLum = 0.05f; var al = new float[w * h];
        for (int i = 0; i < px.Length; i++) if (!Backdrop(px[i]) && ((px[i] >> 24) & 255) > 128) maxLum = Math.Max(maxLum, Lum(px[i]));
        for (int i = 0; i < px.Length; i++) if (!Backdrop(px[i])) al[i] = ((px[i] >> 24) & 255) / 255f * (0.55f + 0.45f * Math.Min(1f, Lum(px[i]) / maxLum));
        Func<int, bool> colEmpty = cx => { for (int cy = 0; cy < h; cy++) if (al[cy * w + cx] > 0.05f) return false; return true; };
        int first = 0; while (first < w && colEmpty(first)) first++;
        int x = first, gapAt = -1;
        while (x < w) { if (colEmpty(x)) { int g = x; while (x < w && colEmpty(x)) x++; if (x - g >= 3 && x < w) { gapAt = x; break; } } else x++; }
        int x0 = cutSymbol && gapAt >= 0 ? gapAt : first, x1 = w - 1; while (x1 > x0 && colEmpty(x1)) x1--;
        int y0 = h, y1 = -1;
        for (int yy = 0; yy < h; yy++) for (int xx = x0; xx <= x1; xx++) if (al[yy * w + xx] > 0.05f) { if (yy < y0) y0 = yy; if (yy > y1) y1 = yy; }
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
        int big = SIZE * 4, W = (int)(wFrac * big), H = (int)(hFrac * big);
        // A margin round the word so thickening the strokes does not run into the canvas edge (it sliced the O's right
        // side, 23:07); the word keeps its size.
        int pad = bold + 2, PW = W + 2 * pad, PH = H + 2 * pad;
        var a = new float[PW * PH];
        for (int u = 0; u < W; u++) {
            float t = (u + 0.5f) / W, c = 2f * t - 1f, sc = 1f - arc * (1f - c * c);
            float colH = H * sc, top = (H - colH) * 0.42f, sx = x0 + t * cw - 0.5f;   // 42 % of the bend off the top, so both edges curve (23:11)
            for (int v = 0; v < H; v++) {
                float fy = (v + 0.5f - top) / colH; if (fy < 0f || fy >= 1f) continue;
                float sy = y0 + fy * ch - 0.5f;
                int ix = (int)Math.Floor(sx), iy = (int)Math.Floor(sy); float fx = sx - ix, fyy = sy - iy;
                Func<int, int, float> at = (xa, ya) => (xa < 0 || ya < 0 || xa >= w || ya >= h) ? 0f : al[ya * w + xa];
                a[(v + pad) * PW + u + pad] = (at(ix, iy) * (1 - fx) + at(ix + 1, iy) * fx) * (1 - fyy) + (at(ix, iy + 1) * (1 - fx) + at(ix + 1, iy + 1) * fx) * fyy;
            }
        }
        if (bold > 0) {
            var b = new float[PW * PH];
            for (int v = 0; v < PH; v++) for (int u = 0; u < PW; u++) {
                float m = 0f;
                for (int dy = -bold; dy <= bold; dy++) for (int dx = -bold; dx <= bold; dx++) {
                    if (dx * dx + dy * dy > bold * bold) continue; int uu = u + dx, vv = v + dy;
                    if (uu >= 0 && vv >= 0 && uu < PW && vv < PH && a[vv * PW + uu] > m) m = a[vv * PW + uu];
                }
                b[v * PW + u] = m;
            }
            a = b;
        }
        var o = new int[PW * PH];
        for (int i = 0; i < o.Length; i++) o[i] = ((int)Math.Round(Math.Min(1f, a[i]) * 255) << 24) | 0xFFFFFF;
        var word = Write(o, PW, PH);
        var outB = new Bitmap(SIZE, SIZE, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outB)) {
            g.Clear(Color.Transparent); g.InterpolationMode = InterpolationMode.HighQualityBicubic; g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(word, (SIZE - PW / 4f) / 2f, (SIZE - PH / 4f) / 2f, PW / 4f, PH / 4f);
        }
        word.Dispose(); outB.Save(outPath, ImageFormat.Png); outB.Dispose();
        return string.Format("wordmark {0}x{1} (symbol cut at x{2})", cw, ch, gapAt);
    }
    public static float BoxW = 0f, BoxH = 0f;
    public static string Make(string inPath, string outPath, float boost) {
        var src = new Bitmap(inPath); int w = src.Width, h = src.Height; var px = Read(src); src.Dispose();
        int x0 = w, y0 = h, x1 = -1, y1 = -1; float maxLum = 0.05f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = px[y * w + x]; if (Backdrop(p)) continue;
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
            if (((p >> 24) & 255) > 128) maxLum = Math.Max(maxLum, Lum(p));
        }
        if (x1 < 0) return "no logo found";
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1; var o = new int[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int p = px[(y + y0) * w + x + x0]; if (Backdrop(p)) continue;
            float a = ((p >> 24) & 255) * (0.55f + 0.45f * Math.Min(1f, Lum(p) / maxLum));
            o[y * cw + x] = ((int)Math.Round(a) << 24) | 0xFFFFFF;
        }
        var crop = Write(o, cw, ch);
        var outB = new Bitmap(SIZE, SIZE, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outB)) {
            g.Clear(Color.Transparent); g.InterpolationMode = InterpolationMode.HighQualityBicubic; g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            float s = Math.Min(WIDE * SIZE / cw, TALL * SIZE / ch) * boost;
            s = Math.Min(s, Math.Min(0.98f * SIZE / cw, 0.98f * SIZE / ch));   // a boost never reaches the edge
            float dw = cw * s, dh = ch * s;
            if (BoxW > 0f && BoxH > 0f) { dw = BoxW * SIZE; dh = BoxH * SIZE; }
            g.DrawImage(crop, (SIZE - dw) / 2f, (SIZE - dh) / 2f, dw, dh);
        }
        crop.Dispose(); outB.Save(outPath, ImageFormat.Png); outB.Dispose();
        return string.Format("{0}x{1} logo", cw, ch);
    }
}
'@
# Per-app size, set by eye on the TV (21:41): the wordmarks that read small get bigger; 1 is the base sizing.
$boost = @{ 'com.stremio.one' = 1.25; 'org.smarttube.stable' = 1.12; 'org.xbmc.kodi' = 1.12; 'com.disney.disneyplus' = 1.07;
    'com.alphainventor.filemanager' = 1.12; 'com.android.vending' = 1.12 }
# Wordmarks that read short on the square tiles (21:53): a box of their own, width x height of the square. The width is
# Stremio's (the narrowest side margin the person accepts); the height near TV Bro's and Settings' coverage, so the
# logo is stretched upward. Disney+ only widens: at that width it is already as tall.
$box = @{ 'com.stremio.one' = @(0.975, 0.48); 'org.smarttube.stable' = @(0.975, 0.43); 'org.xbmc.kodi' = @(0.975, 0.43);
    'com.disney.disneyplus' = @(0.975, 0.55) }
if (-not ('IconMaker' -as [type])) { throw 'IconMaker did not compile; the theme''s icons are left as they are' }
Get-ChildItem $dest -Filter *.png -ErrorAction SilentlyContinue | Remove-Item -Force
$raw = Join-Path $work 'raw'; New-Item -ItemType Directory -Force $raw | Out-Null
# Stremio (21:58): the word alone, Netflix-style (see IconMaker.Wordmark): width, height, bend, boldness at 4x.
$wordmarks = @{ 'com.stremio.one' = @(0.90, 0.46, 0.22, 2, 'STREMIO', 0.08) }
# Bebas Neue (SIL OFL) for the Stremio wordmark; downloaded once, kept in tools/iconpack.
$fontFile = Join-Path $work 'BebasNeue-Regular.ttf'
if (-not (Test-Path $fontFile)) { try { Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/google/fonts/main/ofl/bebasneue/BebasNeue-Regular.ttf' -OutFile $fontFile -UseBasicParsing } catch { "font download failed: $($_.Exception.Message)" } }
if (-not (Test-Path $fontFile)) { $wordmarks['com.stremio.one'] = @(0.90, 0.46, 0.22, 2); 'no Bebas Neue: Stremio keeps the pack''s letters' }
# A wordmark with a 5th value is set in that text in Bebas Neue (SIL OFL, fetched into tools/iconpack), all capitals and condensed like
# Netflix's mark (the person, 22:15), instead of the pack's thin letters; no symbol to cut then.
function TextImage([string]$text, [string]$path, [double]$track = 0) {
    Add-Type -AssemblyName System.Drawing
    $pfc = New-Object System.Drawing.Text.PrivateFontCollection; $pfc.AddFontFile($fontFile)
    $font = New-Object System.Drawing.Font($pfc.Families[0], 240, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $bmp = New-Object System.Drawing.Bitmap 1800, 360, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::Transparent)
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    # Letter by letter with `$track` of the font size between them (DrawString has no letter spacing), like
    # Netflix's open spacing (the person, 23:05).
    $fmt = [System.Drawing.StringFormat]::GenericTypographic; $x = 40.0
    foreach ($ch in $text.ToCharArray()) {
        $g.DrawString([string]$ch, $font, [System.Drawing.Brushes]::White, [single]$x, [single]40, $fmt)
        $x += $g.MeasureString([string]$ch, $font, 1000, $fmt).Width + $track * $font.Size
    }
    $g.Dispose(); $font.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
$made = 0
foreach ($p in $pkgs) {
    if (-not $map.ContainsKey($p)) { '{0,-36} not in the pack' -f $p; continue }
    $name = $map[$p]; $f = $file[$name]
    if (-not $f) { '{0,-36} {1}: no file' -f $p, $name; continue }
    $rawFile = Join-Path $raw "$p.png"
    [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry($f), $rawFile, $true)
    if ($wordmarks.ContainsKey($p)) { $wm = $wordmarks[$p]; $srcFile = $rawFile; $cut = $true
        if ($wm.Count -ge 5) { $srcFile = Join-Path $raw "$p-text.png"; TextImage $wm[4] $srcFile $(if ($wm.Count -ge 6) { [double]$wm[5] } else { 0.0 }); $cut = $false }
        '{0,-36} {1,-20} {2}' -f $p, $name, [IconMaker]::Wordmark($srcFile, (Join-Path $dest "$p.png"), [single]$wm[0], [single]$wm[1], [single]$wm[2], [int]$wm[3], $cut); $made++; continue }
    '{0,-36} {1,-20} {2}' -f $p, $name, $(if ($box.ContainsKey($p)) { [IconMaker]::BoxW = [single]$box[$p][0]; [IconMaker]::BoxH = [single]$box[$p][1] } else { [IconMaker]::BoxW = 0; [IconMaker]::BoxH = 0 }; [IconMaker]::Make($rawFile, (Join-Path $dest "$p.png"), $(if ($boost.ContainsKey($p)) { [single]$boost[$p] } else { [single]1 }))); $made++
}
$zip.Dispose()
"icons made: $made in $dest"
