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
     * Direct download URL for the latest release APK on GitHub Releases.
     */
    const val LATEST_APK_DOWNLOAD_URL =
        "https://github.com/TestersNightmare/arara/releases/latest/download/arara.apk"

    /**
     * GitHub API endpoint to query the latest release APK asset dynamically.
     */
    const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/TestersNightmare/arara/releases/latest"

    /**
     * GitHub repository for splash ad configuration and image assets:
     * https://github.com/TestersNightmare/arara.git
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
}
