# Arara — Android WebView App (`ararabr.com`)

[![GitHub Repo](https://img.shields.io/badge/GitHub-TestersNightmare%2Farara-181717?logo=github)](https://github.com/TestersNightmare/arara.git)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-009C3B?logo=android)](#)
[![Languages](https://img.shields.io/badge/I18n-PT--BR%20%7C%20ES%20%7C%20EN%20%7C%20ZH-002776)](#)

**Arara** 是一款专为巴西市场打造的轻量级 Android WebView 商城应用，默认加载 **[https://ararabr.com](https://ararabr.com)** 葡语（巴西）官方主页，采用 `logo2.png`（巴西金刚鹦鹉标识）作为全分辨率自适应应用图标，原生支持 **葡萄牙语（巴西）、西班牙语、英语、中文** 四种语言，并内置基于 GitHub 仓库的 **远程开屏图片广告动态推送引擎**。

---

## 1. 核心功能一览

| 模块 | 功能说明 |
| :--- | :--- |
| **默认主页** | 默认打开 `https://ararabr.com` 葡语（巴西）主页，支持下拉刷新、顶部进度条、断网重连、登录态 Cookie 持久化、文件下载、相机扫码授权及外部唤起（`pix:`、`whatsapp:`、`tel:`、`mailto:`） |
| **应用图标** | 基于 `logo2.png` 生成 `mdpi` ~ `xxxhdpi` 全套圆形/方形图标及 Android 8.0+ Adaptive Icon (`mipmap-anydpi-v26`) |
| **四语言支持** | 原生内置 **Português (Brasil)** (`pt-BR`)、**Español** (`es`)、**English** (`en`)、**中文** (`zh-CN` / `zh-TW`) 四套完整语言包，支持系统语言自动匹配与 App 内悬浮按钮一键切换语言 |
| **GitHub 开屏广告推送** | 从 [`https://github.com/TestersNightmare/arara.git`](https://github.com/TestersNightmare/arara.git) 的 [`ads/splash.json`](ads/splash.json) 提取开屏广告图片、倒计时、跳转链接与多语言按钮文案，支持多源 CDN 容灾、定时排期与本地缓存秒开 |

---

## 2. 目录结构

```text
arara/
├── .github/workflows/build-apk.yml       # GitHub Actions 自动构建 APK 工作流
├── ads/                                  # 【远程开屏广告中心】（直接由客户端从 GitHub 拉取）
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
        ├── AndroidManifest.xml           # 权限、启动 Activity 与多语言 localeConfig
        ├── assets/
        │   └── splash_default.png        # 离线/首启兜底开屏图
        ├── java/com/ararabr/app/
        │   ├── AppConfig.kt              # 主页地址与 GitHub 多源 CDN 地址配置
        │   ├── SplashPromo.kt            # GitHub 开屏广告拉取、多策略轮播与磁盘缓存引擎
        │   └── MainActivity.kt           # WebView 容器、四语切换菜单与开屏广告展示控制
        └── res/
            ├── layout/activity_main.xml  # 主界面 + 断网重连层 + 开屏广告层
            ├── xml/locales_config.xml    # Android 13+ 应用内多语言声明
            ├── values/strings.xml        # 默认文案：葡语巴西 (pt-BR)
            ├── values-pt-rBR/strings.xml # 葡语巴西 (Português - Brasil)
            ├── values-es/strings.xml     # 西班牙语 (Español)
            ├── values-en/strings.xml     # 英语 (English)
            └── values-zh/strings.xml     # 中文 (简体/繁体)
```

---

## 3. 多语言支持说明 (`pt-BR` / `es` / `en` / `zh`)

1. **默认主页固定为葡语巴西主页**：
   - 应用冷启动默认加载 `https://ararabr.com`（葡语巴西主页）。
2. **原生界面与系统级四语言适配**：
   - 支持 **Português (Brasil)**、**Español**、**English**、**中文**。
   - 点击主界面右下角的 **`🌐 PT-BR`** 悬浮胶囊按钮，可随时在四种语言间实时切换：
     - 自动更新开屏广告角标（`Publicidade` / `Publicidad` / `Ad` / `广告`）、跳过倒计时（`Pular 4s` / `Saltar 4s` / `Skip 4s` / `跳过 4s`）、行动按钮文案及断网提示。
     - 自动向 WebView 注入对应语言的 `Accept-Language` 请求头。
     - 菜单内还提供 **返回葡语巴西主页 (`ararabr.com`)** 与 **立即同步 GitHub 开屏广告** 快捷入口。

---

## 4. 开屏广告推送快速上手

详细架构与字段参考请查阅：**[《Arara 开屏广告图片远程推送方案》(docs/AD_PUSH_GUIDE.md)](docs/AD_PUSH_GUIDE.md)**。

### 一键推送新广告图（PowerShell）

```powershell
.\push-ad.ps1 -ImagePath ".\你的新海报.jpg" -Link "/collections/all" -Duration 4
```

或者直接在 GitHub 网页端上传图片到 `ads/images/` 并修改 [`ads/splash.json`](ads/splash.json) 中的 `version` 与 `image` 字段，无需重新打包发布 APK，客户端即可自动拉取更新。

---

## 5. 本地构建与 GitHub Actions 自动构建

### 本地构建 APK

```powershell
# 构建 Debug 测试包
.\gradlew.bat assembleDebug

# 构建 Release 正式包
.\gradlew.bat assembleRelease
```

构建产物路径：
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`（配置 `keystore.properties` 后自动签名）

### GitHub Actions 自动构建
每次推送代码到 `main` 分支或推送 `v*` 标签时，[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) 会自动编译 APK 并上传到 Actions Artifacts / GitHub Releases。
