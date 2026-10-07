# Arara — Android WebView App (`ararabr.com`)

[![GitHub Repo](https://img.shields.io/badge/GitHub-TestersNightmare%2Farara-181717?logo=github)](https://github.com/TestersNightmare/arara.git)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-009C3B?logo=android)](#)
[![Languages](https://img.shields.io/badge/I18n-PT--BR%20%7C%20ES%20%7C%20EN%20%7C%20ZH-002776)](#)

**Arara** 是一款专为巴西市场打造的轻量级 Android WebView 商城应用，默认打开并锁定 **[https://ararabr.com](https://ararabr.com)**（`shopee2028.myshopify.com`）葡语（巴西 `pt-BR`）官方主页，采用 `logo2.png`（巴西金刚鹦鹉标识）作为全分辨率自适应应用图标，原生支持系统 **葡萄牙语（巴西）、西班牙语、英语、中文** 四种语言，内置基于 GitHub 仓库的 **启动自动同步开屏图片广告引擎**，并针对小米 **HyperOS（澎湃 OS）** 及 Android 15/16 全面屏进行了沉浸式全屏与防遮挡适配。

---

## 1. 核心特性

| 模块 | 功能说明 |
| :--- | :--- |
| **强制锁定葡语巴西主页 (`pt-BR`)** | 针对 Shopify Markets 的浏览器语言自动重定向机制进行四层修复：初始化 WebView 前锁定 `Locale("pt-BR")`（确保 `ararabr.com` 302 跳转到 `shopee2028.myshopify.com` 时携带 `Accept-Language: pt-BR,pt;q=0.9`）、预置 `localization=BR` 与 `cart_currency=BRL` Cookie、自动拦截并重写 `/en` 重定向路径，确保在中文/英文系统手机上也 100% 自动进入葡语（`pt-BR`）页面 |
| **HyperOS 沉浸式全屏与防遮挡** | 声明 HyperOS 全面屏 `notch.config` 与 `android.max_aspect`，启用 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` / `SHORT_EDGES` 与 `WindowInsetsControllerCompat` 强制隐藏系统状态栏与导航栏，并动态计算挖孔屏/虚拟按键/输入法安全边距 (`WindowInsets`)，彻底解决顶部通知栏与底部按钮遮挡问题 |
| **四语言自适应 (`pt-BR`/`es`/`en`/`zh`)** | 原生内置葡语巴西、西班牙语、英语、中文四套完整语言包，无冗余悬浮切换按钮，自动跟随手机系统语言渲染原生提示与开屏广告角标/倒计时文案 |
| **GitHub 开屏广告启动自动同步** | 每次启动应用时自动从 [`https://github.com/TestersNightmare/arara.git`](https://github.com/TestersNightmare/arara.git) 的 [`ads/splash.json`](ads/splash.json) 同步最新广告配置与海报图片（支持 GitHub Raw + jsDelivr + Fastly 三源容灾） |

---

## 2. 目录结构

```text
arara/
├── .github/workflows/build-apk.yml       # GitHub Actions 自动构建 APK 工作流
├── ads/                                  # 【远程开屏广告中心】（应用启动时自动从 GitHub 同步）
│   ├── splash.json                       # 开屏广告远程配置文件（开关/版本/图片/链接/多语言/排期）
│   └── images/
│       └── splash_default.png            # 默认开屏推广海报图 (1080x1920)
├── docs/
│   └── AD_PUSH_GUIDE.md                  # 开屏广告图片推送方案与运营手册
├── push-ad.ps1                           # 一键更新并推送广告图片到 GitHub 的 PowerShell 脚本
├── logo2.png                             # 原始高清应用图标源文件
├── build.gradle.kts / settings.gradle.kts
└── app/
    ├── build.gradle.kts                  # Android 应用模块配置 (com.ararabr.app)
    └── src/main/
        ├── AndroidManifest.xml           # 权限、HyperOS 刘海全屏声明与多语言 localeConfig
        ├── assets/
        │   └── splash_default.png        # 离线/首启兜底开屏图
        ├── java/com/ararabr/app/
        │   ├── AppConfig.kt              # 主页地址与 GitHub 多源 CDN 地址配置
        │   ├── SplashPromo.kt            # GitHub 开屏广告拉取、多策略轮播与磁盘缓存引擎
        │   └── MainActivity.kt           # HyperOS 全屏控制、Shopify pt-BR 语言锁定与开屏广告展示
        └── res/
            ├── layout/activity_main.xml  # 无冗余按钮的纯享 WebView 主界面 + 开屏广告层
            ├── values-v28/themes.xml     # Android 9+ 刘海屏 shortEdges 全屏主题
            ├── values-v30/themes.xml     # Android 11+ / HyperOS always 全屏主题
            ├── values/strings.xml        # 默认文案：葡语巴西 (pt-BR)
            ├── values-pt-rBR/strings.xml # 葡语巴西 (Português - Brasil)
            ├── values-es/strings.xml     # 西班牙语 (Español)
            ├── values-en/strings.xml     # 英语 (English)
            └── values-zh/strings.xml     # 中文 (简体/繁体)
```

---

## 3. 开屏广告推送快速上手

详细架构与字段参考请查阅：**[《Arara 开屏广告图片远程推送方案》(docs/AD_PUSH_GUIDE.md)](docs/AD_PUSH_GUIDE.md)**。

### 一键推送新广告图（PowerShell）

```powershell
.\push-ad.ps1 -ImagePath ".\你的新海报.jpg" -Link "/collections/all" -Duration 4
```

或者直接在 GitHub 网页端上传图片到 `ads/images/` 并修改 [`ads/splash.json`](ads/splash.json) 中的 `version` 与 `image` 字段，应用开启后会自动同步最新广告。

---

## 4. 本地构建与 GitHub Actions 自动构建

```powershell
# 构建 Debug 测试包
.\gradlew.bat assembleDebug

# 构建 Release 正式包
.\gradlew.bat assembleRelease
```

构建产物路径：
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`
