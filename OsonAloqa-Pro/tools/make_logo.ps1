Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root 'app\src\main\res'

$green = [System.Drawing.Color]::FromArgb(255, 0, 105, 92)
$dark  = [System.Drawing.Color]::FromArgb(255, 0, 62, 54)
$teal  = [System.Drawing.Color]::FromArgb(255, 0, 151, 167)
$white = [System.Drawing.Color]::White
$font  = 'Segoe UI'

function RoundedRect([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc($x, $y, 2 * $r, 2 * $r, 180, 90)
    $p.AddArc($x + $w - 2 * $r, $y, 2 * $r, 2 * $r, 270, 90)
    $p.AddArc($x + $w - 2 * $r, $y + $h - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $p.AddArc($x, $y + $h - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $p.CloseFigure()
    return $p
}

function Draw-Icon([System.Drawing.Graphics]$g, [single]$size, [bool]$round) {
    if ($round) {
        $clip = New-Object System.Drawing.Drawing2D.GraphicsPath
        $clip.AddEllipse(0, 0, $size, $size)
        $g.SetClip($clip)
    }
    $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $dark, $teal, [single]55)
    $g.FillRectangle($bg, $rect)

    $bw = $size * 0.78; $bh = $size * 0.50
    $bx = ($size - $bw) / 2; $by = $size * 0.17
    $bubble = RoundedRect $bx $by $bw $bh ($size * 0.11)
    $tail = New-Object System.Drawing.Drawing2D.GraphicsPath
    $tx = $bx + $bw * 0.16
    $pts = @(
        [System.Drawing.PointF]::new($tx, $by + $bh - 2),
        [System.Drawing.PointF]::new($tx + $size * 0.115, $by + $bh - 2),
        [System.Drawing.PointF]::new($tx, $by + $bh + $size * 0.14)
    )
    $tail.AddPolygon([System.Drawing.PointF[]]$pts)
    $wb = New-Object System.Drawing.SolidBrush($white)
    $g.FillPath($wb, $bubble)
    $g.FillPath($wb, $tail)

    $t1 = 'ОСОН'; $t2 = 'АЛОҚА'
    $maxW = $bw * 0.86
    $fs = [single]($size * 0.30)
    $f = New-Object System.Drawing.Font($font, $fs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $s2 = $g.MeasureString($t2, $f)
    if ($s2.Width -gt $maxW -and $s2.Width -gt 0) {
        $fs = [single]($fs * $maxW / $s2.Width)
        $f.Dispose()
        $f = New-Object System.Drawing.Font($font, $fs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    }
    $greenBrush = New-Object System.Drawing.SolidBrush($green)
    $s1 = $g.MeasureString($t1, $f)
    $s2 = $g.MeasureString($t2, $f)
    $totalH = $s1.Height * 0.95 + $s2.Height * 0.95
    $y0 = $by + ($bh - $totalH) / 2
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'
    $fmt.LineAlignment = 'Center'
    $r1 = [System.Drawing.RectangleF]::new($bx, $y0, $bw, $s1.Height)
    $r2 = [System.Drawing.RectangleF]::new($bx, $y0 + $s1.Height * 0.95, $bw, $s2.Height)
    $g.DrawString($t1, $f, $greenBrush, $r1, $fmt)
    $g.DrawString($t2, $f, $greenBrush, $r2, $fmt)
}

function Save-Icon([int]$sz, [bool]$round, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($sz, $sz)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.InterpolationMode = 'HighQualityBicubic'
    Draw-Icon $g $sz $round
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

$dens = [ordered]@{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($d in $dens.Keys) {
    $dir = Join-Path $res $d
    New-Item -ItemType Directory -Force $dir | Out-Null
    Save-Icon $dens[$d] $false (Join-Path $dir 'ic_launcher.png')
    Save-Icon $dens[$d] $true (Join-Path $dir 'ic_launcher_round.png')
}

$logoPath = Join-Path $root 'logo.png'
Save-Icon 1024 $false $logoPath

$nodpi = Join-Path $res 'drawable-nodpi'
New-Item -ItemType Directory -Force $nodpi | Out-Null
$fgSz = 432
$fg = New-Object System.Drawing.Bitmap($fgSz, $fgSz)
$gfg = [System.Drawing.Graphics]::FromImage($fg)
$gfg.SmoothingMode = 'AntiAlias'
$gfg.TextRenderingHint = 'AntiAliasGridFit'
$whiteBrush = New-Object System.Drawing.SolidBrush($white)
$t1 = 'ОСОН'; $t2 = 'АЛОҚА'
$fs = [single]76
$f = New-Object System.Drawing.Font($font, $fs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$s2 = $gfg.MeasureString($t2, $f)
if ($s2.Width -gt 240 -and $s2.Width -gt 0) {
    $fs = [single]($fs * 240 / $s2.Width)
    $f.Dispose()
    $f = New-Object System.Drawing.Font($font, $fs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
}
$s1 = $gfg.MeasureString($t1, $f)
$s2 = $gfg.MeasureString($t2, $f)
$totalH = $s1.Height * 0.95 + $s2.Height * 0.95
$y0 = ($fgSz - $totalH) / 2
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = 'Center'
$fmt.LineAlignment = 'Center'
$r1 = [System.Drawing.RectangleF]::new(0, $y0, $fgSz, $s1.Height)
$r2 = [System.Drawing.RectangleF]::new(0, $y0 + $s1.Height * 0.95, $fgSz, $s2.Height)
$gfg.DrawString($t1, $f, $whiteBrush, $r1, $fmt)
$gfg.DrawString($t2, $f, $whiteBrush, $r2, $fmt)
$fg.Save((Join-Path $nodpi 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$gfg.Dispose(); $fg.Dispose()

Write-Host "OK: logo + icons generated"
