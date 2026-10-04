param(
    [Parameter(Mandatory = $true)]
    [string] $GeneratedFrame,
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$source = @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class WaypointerMapArtwork
{
    public static void ExtractLightBackground(string input, string output)
    {
        using (var source = new Bitmap(input))
        using (var result = new Bitmap(source.Width, source.Height,
                                       PixelFormat.Format32bppArgb))
        {
            for (int y = 0; y < source.Height; y++)
            for (int x = 0; x < source.Width; x++)
            {
                Color pixel = source.GetPixel(x, y);
                int low = Math.Min(pixel.R, Math.Min(pixel.G, pixel.B));
                int high = Math.Max(pixel.R, Math.Max(pixel.G, pixel.B));
                bool neutral = high - low <= 24;
                int alpha = neutral && low >= 205 ? 0
                    : neutral && low >= 155 ? (205 - low) * 255 / 50
                    : 255;
                result.SetPixel(x, y, Color.FromArgb(alpha,
                    pixel.R, pixel.G, pixel.B));
            }
            result.Save(output, ImageFormat.Png);
        }
    }

    public static void MakeThumbnail(string input, string output, int size)
    {
        using (var source = Image.FromFile(input))
        using (var result = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(result))
        {
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.CompositingQuality = CompositingQuality.HighQuality;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            graphics.SmoothingMode = SmoothingMode.HighQuality;
            graphics.DrawImage(source, new Rectangle(0, 0, size, size),
                0, 0, source.Width, source.Height, GraphicsUnit.Pixel);
            result.Save(output, ImageFormat.Png);
        }
    }
}
'@

$drawingAssembly = [System.Drawing.Bitmap].Assembly.Location
$drawingPrimitivesAssembly = [System.Drawing.Color].Assembly.Location
$gdiAssembly = [System.Reflection.Assembly]::Load(
    'System.Private.Windows.GdiPlus').Location
$windowsCoreAssembly = [System.Reflection.Assembly]::Load(
    'System.Private.Windows.Core').Location
Add-Type -TypeDefinition $source -ReferencedAssemblies @(
    $drawingAssembly, $drawingPrimitivesAssembly,
    $gdiAssembly, $windowsCoreAssembly)

$resourceRoot = Join-Path $ProjectRoot `
    'client-wurm\src\main\resources\org\waypoints\next\map'
$galleryRoot = Join-Path $resourceRoot 'gallery'
New-Item -ItemType Directory -Force -Path $galleryRoot | Out-Null

[WaypointerMapArtwork]::ExtractLightBackground(
    (Resolve-Path -LiteralPath $GeneratedFrame).Path,
    (Join-Path $resourceRoot 'main-map-frame.png'))

$officialRoot = Join-Path $ProjectRoot 'design\official-sklotopolis-maps'
foreach ($name in @('liberty', 'novus', 'caza', 'infinity-r5', 'old-infinity')) {
    [WaypointerMapArtwork]::MakeThumbnail(
        (Join-Path $officialRoot ($name + '.png')),
        (Join-Path $galleryRoot ($name + '.png')),
        256)
}
