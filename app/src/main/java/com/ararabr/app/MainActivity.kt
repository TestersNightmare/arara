package com.ararabr.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorOverlay: View
    private lateinit var splashOverlay: View
    private lateinit var splashBrandView: View
    private lateinit var splashAdContainer: View
    private lateinit var btnLanguageSwitch: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private var countdownRunnable: Runnable? = null

    private var pendingPermissionRequest: PermissionRequest? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionRequest?.let { req ->
                if (granted) req.grant(req.resources) else req.deny()
            }
            pendingPermissionRequest = null
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorOverlay = findViewById(R.id.errorOverlay)
        splashOverlay = findViewById(R.id.splashOverlay)
        splashBrandView = findViewById(R.id.splashBrandView)
        splashAdContainer = findViewById(R.id.splashAdContainer)
        btnLanguageSwitch = findViewById(R.id.btnLanguageSwitch)

        setupWebView()
        setupLanguageAndQuickMenu()
        setupBackNavigation()

        findViewById<Button>(R.id.btnRetry).setOnClickListener {
            errorOverlay.visibility = View.GONE
            webView.reload()
        }

        if (savedInstanceState == null) {
            // Default open ararabr.com Brazilian Portuguese homepage
            loadUrlWithLocaleHeaders(AppConfig.HOME_URL)
            startSplashAdFlow()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    /**
     * Returns the active language tag (`pt-BR`, `es`, `en`, `zh-CN`), defaulting to `pt-BR`.
     */
    private fun currentLocaleTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale: Locale = if (!appLocales.isEmpty) {
            appLocales[0] ?: Locale.forLanguageTag("pt-BR")
        } else {
            resources.configuration.locales[0] ?: Locale.forLanguageTag("pt-BR")
        }
        return when (locale.language.lowercase(Locale.ROOT)) {
            "pt" -> "pt-BR"
            "es" -> "es"
            "en" -> "en"
            "zh" -> "zh-CN"
            else -> "pt-BR"
        }
    }

    private fun loadUrlWithLocaleHeaders(url: String) {
        val tag = currentLocaleTag()
        val acceptLang = when (tag) {
            "pt-BR" -> "pt-BR,pt;q=0.9,en;q=0.8"
            "es" -> "es-419,es;q=0.9,pt-BR;q=0.8,en;q=0.7"
            "en" -> "en-US,en;q=0.9,pt-BR;q=0.8"
            "zh-CN" -> "zh-CN,zh;q=0.9,pt-BR;q=0.8,en;q=0.7"
            else -> "pt-BR,pt;q=0.9"
        }
        webView.loadUrl(url, mapOf("Accept-Language" to acceptLang))
    }

    /**
     * Splash Screen Ad Flow:
     * 1. If a cached GitHub splash ad exists on disk, display it immediately (0ms delay),
     *    and refresh from GitHub in the background for next startup.
     * 2. If no cached ad exists yet (first install / cleared storage), show the branded
     *    Arara launch screen briefly (up to 2.5s) while fetching `ads/splash.json` + image
     *    from `https://github.com/TestersNightmare/arara.git`, or fallback to embedded default
     *    splash asset if offline.
     */
    private fun startSplashAdFlow(forceShow: Boolean = false) {
        val localeTag = currentLocaleTag()
        val cachedAd = SplashPromo.resolveCachedAd(this, localeTag, ignoreInterval = forceShow)

        if (cachedAd != null) {
            renderSplashAd(cachedAd)
            bgExecutor.execute {
                SplashPromo.refreshFromGitHub(applicationContext, localeTag)
            }
            return
        }

        // First launch or cache empty: show Arara brand splash while pulling from GitHub
        splashOverlay.visibility = View.VISIBLE
        splashBrandView.visibility = View.VISIBLE
        splashAdContainer.visibility = View.GONE

        var handled = false
        val timeoutRunnable = Runnable {
            if (!handled) {
                handled = true
                // Fallback to built-in default promo asset if available, else dismiss
                val fallbackAd = buildBuiltInFallbackAd()
                if (fallbackAd != null) {
                    renderSplashAd(fallbackAd)
                } else {
                    dismissSplashOverlay()
                }
            }
        }
        mainHandler.postDelayed(timeoutRunnable, AppConfig.FIRST_LAUNCH_FETCH_TIMEOUT_MS)

        bgExecutor.execute {
            val ok = SplashPromo.refreshFromGitHub(applicationContext, localeTag)
            val freshAd = if (ok) {
                SplashPromo.resolveCachedAd(applicationContext, localeTag, ignoreInterval = true)
            } else {
                null
            }
            mainHandler.post {
                if (!handled) {
                    handled = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (freshAd != null) {
                        renderSplashAd(freshAd)
                    } else {
                        val fallbackAd = buildBuiltInFallbackAd()
                        if (fallbackAd != null) {
                            renderSplashAd(fallbackAd)
                        } else {
                            dismissSplashOverlay()
                        }
                    }
                }
            }
        }
    }

    private fun buildBuiltInFallbackAd(): SplashPromo.AdDisplayItem? {
        return try {
            assets.open("splash_default.png").use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return null
                SplashPromo.AdDisplayItem(
                    id = "builtin_default",
                    version = "1.0.0",
                    imagePathOrUrl = "assets://splash_default.png",
                    link = "/",
                    ctaText = null,
                    durationSec = 4,
                    openExternal = false,
                    bitmap = bmp
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun renderSplashAd(ad: SplashPromo.AdDisplayItem) {
        SplashPromo.markAdShown(this)
        splashOverlay.visibility = View.VISIBLE
        splashBrandView.visibility = View.GONE
        splashAdContainer.visibility = View.VISIBLE

        val imageView = findViewById<ImageView>(R.id.splashImage)
        val skipView = findViewById<TextView>(R.id.splashSkip)
        val ctaView = findViewById<TextView>(R.id.splashCta)

        imageView.setImageBitmap(ad.bitmap)
        ctaView.text = ad.ctaText?.takeIf { it.isNotBlank() } ?: getString(R.string.splash_cta_default)

        val onAdClick = View.OnClickListener {
            dismissSplashOverlay()
            handleAdClick(ad.link, ad.openExternal)
        }
        imageView.setOnClickListener(onAdClick)
        ctaView.setOnClickListener(onAdClick)

        skipView.setOnClickListener {
            dismissSplashOverlay()
        }

        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        var remaining = ad.durationSec
        val tick = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    dismissSplashOverlay()
                } else {
                    skipView.text = getString(R.string.splash_skip, remaining)
                    remaining--
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = tick
        mainHandler.post(tick)
    }

    private fun dismissSplashOverlay() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        splashOverlay.visibility = View.GONE
    }

    private fun handleAdClick(link: String, openExternal: Boolean) {
        val trimmed = link.trim()
        if (trimmed.isEmpty() || trimmed == "/") {
            return
        }
        val targetUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "${AppConfig.HOME_URL.trimEnd('/')}/${trimmed.trimStart('/')}"
        }
        if (openExternal) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                return
            } catch (_: Exception) {
            }
        }
        loadUrlWithLocaleHeaders(targetUrl)
    }

    private fun setupLanguageAndQuickMenu() {
        btnLanguageSwitch.setOnClickListener {
            val items = arrayOf(
                getString(R.string.lang_opt_pt_br),
                getString(R.string.lang_opt_es),
                getString(R.string.lang_opt_en),
                getString(R.string.lang_opt_zh),
                getString(R.string.lang_opt_home),
                getString(R.string.lang_opt_refresh_ad)
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.lang_dialog_title)
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> switchAppLocale("pt-BR", "${AppConfig.HOME_URL}/")
                        1 -> switchAppLocale("es", null)
                        2 -> switchAppLocale("en", "${AppConfig.HOME_URL}/en")
                        3 -> switchAppLocale("zh-CN", null)
                        4 -> loadUrlWithLocaleHeaders(AppConfig.HOME_URL)
                        5 -> {
                            bgExecutor.execute {
                                SplashPromo.refreshFromGitHub(applicationContext, currentLocaleTag())
                                mainHandler.post {
                                    Toast.makeText(
                                        this,
                                        R.string.ad_refreshed_toast,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    startSplashAdFlow(forceShow = true)
                                }
                            }
                        }
                    }
                }
                .show()
        }
    }

    private fun switchAppLocale(languageTag: String, targetWebUrl: String?) {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
        if (targetWebUrl != null) {
            loadUrlWithLocaleHeaders(targetWebUrl)
        } else {
            // Reload current page with updated Accept-Language header
            val currentUrl = webView.url ?: AppConfig.HOME_URL
            loadUrlWithLocaleHeaders(currentUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val scheme = url.scheme?.lowercase(Locale.ROOT) ?: return false
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    swipeRefresh.isRefreshing = false
                    errorOverlay.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val needsCamera = request.resources.contains(
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE
                )
                if (!needsCamera) {
                    request.deny()
                    return
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (_: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            } catch (_: Exception) {
            }
        }

        swipeRefresh.setOnRefreshListener {
            errorOverlay.visibility = View.GONE
            webView.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (splashOverlay.visibility == View.VISIBLE) {
                    dismissSplashOverlay()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        bgExecutor.shutdownNow()
        super.onDestroy()
    }
}
