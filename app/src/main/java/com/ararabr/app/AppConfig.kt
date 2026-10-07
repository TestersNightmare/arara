package com.ararabr.app

/**
 * Global configuration for Arara WebView App & GitHub Splash Ad Push Strategy.
 */
object AppConfig {
    /**
     * Default homepage: ararabr.com (Brazilian Portuguese homepage).
     */
    const val HOME_URL = "https://ararabr.com"

    /**
     * GitHub repository for splash ad configuration and image assets:
     * https://github.com/TestersNightmare/arara.git
     *
     * Multi-CDN fallback base URLs (ordered by priority):
     * 1. GitHub Raw (Direct real-time source)
     * 2. jsDelivr Global CDN (Fast edge caching in Brazil, Americas, Europe, Asia)
     * 3. Fastly jsDelivr Mirror (Backup edge network)
     */
    val GITHUB_ASSET_BASES = listOf(
        "https://raw.githubusercontent.com/TestersNightmare/arara/main",
        "https://cdn.jsdelivr.net/gh/TestersNightmare/arara@main",
        "https://fastly.jsdelivr.net/gh/TestersNightmare/arara@main"
    )

    /**
     * Relative path to the splash ad configuration JSON in the GitHub repository.
     */
    const val SPLASH_CONFIG_PATH = "ads/splash.json"

    /**
     * Maximum wait time (in ms) on first cold start when local cache is empty.
     */
    const val FIRST_LAUNCH_FETCH_TIMEOUT_MS = 2500L

    /**
     * Supported application languages:
     * - pt-BR: Português (Brasil) [Default]
     * - es:    Español
     * - en:    English
     * - zh-CN: 中文 (简体)
     */
    val SUPPORTED_LOCALES = listOf("pt-BR", "es", "en", "zh-CN")
}
