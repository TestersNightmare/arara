<#
.SYNOPSIS
    Arara 开屏广告图片一键推送脚本 (GitHub Remote Ad Push Tool)
.DESCRIPTION
    将本地广告图片复制到 ads/images/ 目录，自动更新 ads/splash.json 的版本号、图片路径、
    跳转链接、展示时长及四语按钮文案，并提交推送到 https://github.com/TestersNightmare/arara.git。
.EXAMPLE
    .\push-ad.ps1 -ImagePath ".\my_promo.jpg" -Link "/collections/all" -Duration 5 -CtaPt "Comprar Agora →"
.EXAMPLE
    .\push-ad.ps1 -Disable
#>
param(
    [Parameter(Mandatory = $false)]
    [string]$ImagePath = "",

    [Parameter(Mandatory = $false)]
    [string]$Link = "/",

    [Parameter(Mandatory = $false)]
    [int]$Duration = 4,

    [Parameter(Mandatory = $false)]
    [int]$IntervalMinutes = 0,

    [Parameter(Mandatory = $false)]
    [string]$CtaPt = "Ver Oferta →",

    [Parameter(Mandatory = $false)]
    [string]$CtaEs = "Ver Oferta →",

    [Parameter(Mandatory = $false)]
    [string]$CtaEn = "Shop Now →",

    [Parameter(Mandatory = $false)]
    [string]$CtaZh = "立即查看 →",

    [Parameter(Mandatory = $false)]
    [switch]$OpenExternal = $false,

    [Parameter(Mandatory = $false)]
    [switch]$Disable = $false
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$AdsDir = Join-Path $RepoRoot "ads"
$ImagesDir = Join-Path $AdsDir "images"
$SplashJsonPath = Join-Path $AdsDir "splash.json"

if (-not (Test-Path $ImagesDir)) {
    New-Item -ItemType Directory -Force -Path $ImagesDir | Out-Null
}

$Version = (Get-Date).ToString("yyyy.MM.dd.HHmmss")
$RelativeImagePath = "ads/images/splash_default.png"

if ($Disable) {
    Write-Host ">>> 正在关闭远程开屏广告 (enabled = false)..." -ForegroundColor Yellow
    if (Test-Path $SplashJsonPath) {
        $existing = Get-Content $SplashJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($existing.image) { $RelativeImagePath = $existing.image }
    }
} elseif ($ImagePath -ne "") {
    if (-not (Test-Path $ImagePath)) {
        throw "找不到指定的广告图片文件: $ImagePath"
    }
    $ext = [System.IO.Path]::GetExtension($ImagePath).ToLower()
    $targetFileName = "promo_$((Get-Date).ToString('yyyyMMdd_HHmmss'))$ext"
    $targetFullPath = Join-Path $ImagesDir $targetFileName
    Copy-Item -Path $ImagePath -Destination $targetFullPath -Force
    $RelativeImagePath = "ads/images/$targetFileName"
    Write-Host ">>> 已复制广告图片到: $RelativeImagePath" -ForegroundColor Green
} else {
    if (Test-Path $SplashJsonPath) {
        $existing = Get-Content $SplashJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($existing.image) { $RelativeImagePath = $existing.image }
    }
}

$EnabledVal = -not $Disable.IsPresent

$ConfigObj = [ordered]@{
    enabled               = $EnabledVal
    version               = $Version
    duration_seconds      = $Duration
    show_interval_minutes = $IntervalMinutes
    strategy              = "priority"
    image                 = $RelativeImagePath
    link                  = $Link
    cta_texts             = [ordered]@{
        "pt-BR" = $CtaPt
        "es"    = $CtaEs
        "en"    = $CtaEn
        "zh"    = $CtaZh
    }
    campaigns             = @(
        [ordered]@{
            id               = "promo_$Version"
            enabled          = $EnabledVal
            priority         = 100
            weight           = 10
            version          = $Version
            duration_seconds = $Duration
            image            = $RelativeImagePath
            images           = [ordered]@{
                "pt-BR" = $RelativeImagePath
                "es"    = $RelativeImagePath
                "en"    = $RelativeImagePath
                "zh"    = $RelativeImagePath
            }
            link             = $Link
            cta_texts        = [ordered]@{
                "pt-BR" = $CtaPt
                "es"    = $CtaEs
                "en"    = $CtaEn
                "zh"    = $CtaZh
            }
            open_external    = [bool]$OpenExternal.IsPresent
            start_time       = "2026-01-01T00:00:00Z"
            end_time         = "2030-12-31T23:59:59Z"
        }
    )
}

$JsonContent = $ConfigObj | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($SplashJsonPath, $JsonContent, [System.Text.Encoding]::UTF8)
Write-Host ">>> 已更新配置文件: ads/splash.json (version=$Version)" -ForegroundColor Green

git add ads/
git commit -m "chore(ads): update splash ad config ($Version)"
git -c http.proxy= -c https.proxy= push origin main
Write-Host ">>> 推送成功！客户端将在下次启动或刷新时自动拉取最新广告图片。" -ForegroundColor Cyan
