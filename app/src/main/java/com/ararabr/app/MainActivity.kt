package com.ararabr.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PT_BR_ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9"
        private const val PREFS_SESSION = "arara_shopify_session"
        private const val KEY_PT_COOKIE_INIT_V2 = "pt_cookie_init_v2"
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var rootContainer: View
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorOverlay: View
    private lateinit var splashOverlay: View
    private lateinit var splashBrandView: View
    private lateinit var splashAdContainer: View
    private lateinit var splashBadge: TextView
    private lateinit var splashSkip: TextView
    private lateinit var splashCta: TextView

    /** Device's real system language tag (`pt-BR`, `es`, `en`, `zh-CN`) for native UI & splash ads */
    private lateinit var systemLocaleTag: String
    private lateinit var localizedUiContext: Context

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
        // 1. Capture device's system language for native UI & splash ad localization
        val sysLocale = Resources.getSystem().configuration.locales[0] ?: Locale.forLanguageTag("pt-BR")
        systemLocaleTag = resolveSupportedLocaleTag(sysLocale)
        localizedUiContext = createLocaleContext(sysLocale)

        // 2. Force JVM default Locale to pt-BR BEFORE WebView initialization so Chromium WebView
        //    sends `Accept-Language: pt-BR,pt;q=0.9` even across 302 redirects (ararabr.com -> shopee2028.myshopify.com)
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))

        super.onCreate(savedInstanceState)

        // 3. Enable HyperOS / Android 15+ full-screen before & after setContentView
        applyHyperOsFullScreen()
        setContentView(R.layout.activity_main)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        rootContainer = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorOverlay = findViewById(R.id.errorOverlay)
        splashOverlay = findViewById(R.id.splashOverlay)
        splashBrandView = findViewById(R.id.splashBrandView)
        splashAdContainer = findViewById(R.id.splashAdContainer)
        splashBadge = findViewById(R.id.splashBadge)
        splashSkip = findViewById(R.id.splashSkip)
        splashCta = findViewById(R.id.splashCta)

        setupWindowInsets()
        setupWebView()
        setupBackNavigation()

        findViewById<Button>(R.id.btnRetry).setOnClickListener {
            errorOverlay.visibility = View.GONE
            loadPtBrUrl(webView.url ?: AppConfig.HOME_URL)
        }

        if (savedInstanceState == null) {
            prepareShopifyPtBrSession()
            loadPtBrUrl(AppConfig.HOME_URL)
            // Automatically sync and display splash ad on app launch
            startSplashAdFlow()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        applyHyperOsFullScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyHyperOsFullScreen()
        }
    }

    /**
     * Forces true full-screen mode on Xiaomi HyperOS / MIUI and Android 9–16+,
     * hiding top notification/status bar and bottom navigation bar while extending into display cutouts.
     */
    @Suppress("DEPRECATION")
    private fun applyHyperOsFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Also set legacy systemUiVisibility flags which HyperOS window manager still inspects
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    /**
     * Prevents top camera cutout / notification bar and bottom navigation bar / soft keyboard
     * from occluding WebView buttons or splash ad controls on HyperOS.
     */
    private fun setupWindowInsets() {
        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusVis = insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navVis = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val topSafe = maxOf(cutout.top, if (statusVis) statusBars.top else 0)
            val bottomSafe = maxOf(ime.bottom, cutout.bottom, if (navVis) navBars.bottom else 0)
            val leftSafe = maxOf(cutout.left, if (navVis) navBars.left else 0)
            val rightSafe = maxOf(cutout.right, if (navVis) navBars.right else 0)

            // Pad WebView container and error overlay so top/bottom webpage buttons are never covered
            swipeRefresh.setPadding(leftSafe, topSafe, rightSafe, bottomSafe)
            errorOverlay.setPadding(
                leftSafe + (24 * density).toInt(),
                topSafe + (24 * density).toInt(),
                rightSafe + (24 * density).toInt(),
                bottomSafe + (24 * density).toInt()
            )

            // Keep progress bar just below the top cutout/safe area
            (progressBar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.topMargin = topSafe
                progressBar.layoutParams = lp
            }

            // Offset splash ad controls away from camera cutout & bottom edge while keeping image full-bleed
            (splashBadge.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.topMargin = topSafe + (16 * density).toInt()
                lp.marginStart = leftSafe + (16 * density).toInt()
                splashBadge.layoutParams = lp
            }
            (splashSkip.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.topMargin = topSafe + (14 * density).toInt()
                lp.marginEnd = rightSafe + (16 * density).toInt()
                splashSkip.layoutParams = lp
            }
            (splashCta.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = bottomSafe + (40 * density).toInt()
                splashCta.layoutParams = lp
            }

            insets
        }
    }

    private fun resolveSupportedLocaleTag(locale: Locale): String {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "pt" -> "pt-BR"
            "es" -> "es"
            "en" -> "en"
            "zh" -> "zh-CN"
            else -> "pt-BR"
        }
    }

    private fun createLocaleContext(locale: Locale): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    /**
     * Ensures Shopify (`ararabr.com` / `shopee2028.myshopify.com`) locks to Brazil (`BR`)
     * and Brazilian Portuguese (`pt-BR`), clearing any stale `/en` session cookies from prior runs.
     */
    private fun prepareShopifyPtBrSession() {
        val prefs = getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        if (!prefs.getBoolean(KEY_PT_COOKIE_INIT_V2, false)) {
            // Clear any previously cached /en session cookie (_shopify_essential) from earlier installs
            cm.removeAllCookies(null)
            prefs.edit().putBoolean(KEY_PT_COOKIE_INIT_V2, true).apply()
        }

        val domains = listOf("https://ararabr.com", "https://shopee2028.myshopify.com")
        for (domain in domains) {
            cm.setCookie(domain, "localization=BR; path=/; Secure; SameSite=Lax")
            cm.setCookie(domain, "cart_currency=BRL; path=/; Secure; SameSite=Lax")
        }
        cm.flush()
    }

    /**
     * Rewrites any Shopify `/en` path on `ararabr.com` or `shopee2028.myshopify.com`
     * to the Portuguese root path (`/`) and loads with `Accept-Language: pt-BR,pt;q=0.9`.
     */
    private fun rewriteShopifyUrlToPtBr(rawUrl: String): String {
        return try {
            val uri = Uri.parse(rawUrl)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return rawUrl
            if (host == "ararabr.com" || host.endsWith(".ararabr.com") || host == "shopee2028.myshopify.com") {
                val path = uri.path ?: "/"
                if (path == "/en" || path.startsWith("/en/")) {
                    val newPath = path.removePrefix("/en").ifEmpty { "/" }
                    return uri.buildUpon().path(newPath).build().toString()
                }
            }
            rawUrl
        } catch (_: Exception) {
            rawUrl
        }
    }

    private fun loadPtBrUrl(url: String) {
        val targetUrl = rewriteShopifyUrlToPtBr(url)
        webView.loadUrl(targetUrl, mapOf("Accept-Language" to PT_BR_ACCEPT_LANGUAGE))
    }

    /**
     * Automatic Splash Screen Ad Flow (syncs from GitHub on startup):
     * 1. If a cached GitHub splash ad exists on disk, display it immediately (0ms delay),
     *    and refresh from GitHub in the background for next startup.
     * 2. If no cached ad exists yet (first install), show the branded Arara launch screen
     *    briefly (up to 2.5s) while fetching `ads/splash.json` + image from GitHub,
     *    or fallback to built-in default splash poster if offline.
     */
    private fun startSplashAdFlow() {
        val cachedAd = SplashPromo.resolveCachedAd(this, systemLocaleTag)

        if (cachedAd != null) {
            renderSplashAd(cachedAd)
            bgExecutor.execute {
                SplashPromo.refreshFromGitHub(applicationContext, systemLocaleTag)
            }
            return
        }

        splashOverlay.visibility = View.VISIBLE
        splashBrandView.visibility = View.VISIBLE
        splashAdContainer.visibility = View.GONE

        var handled = false
        val timeoutRunnable = Runnable {
            if (!handled) {
                handled = true
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
            val ok = SplashPromo.refreshFromGitHub(applicationContext, systemLocaleTag)
            val freshAd = if (ok) {
                SplashPromo.resolveCachedAd(applicationContext, systemLocaleTag, ignoreInterval = true)
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
        imageView.setImageBitmap(ad.bitmap)

        firebaseAnalytics.logEvent("splash_ad_impression", Bundle().apply {
            putString("ad_id", ad.id)
            putString("ad_version", ad.version)
            putString("locale", systemLocaleTag)
        })

        splashBadge.text = localizedUiContext.getString(R.string.splash_ad_badge)
        splashCta.text = ad.ctaText?.takeIf { it.isNotBlank() }
            ?: localizedUiContext.getString(R.string.splash_cta_default)

        val onAdClick = View.OnClickListener {
            firebaseAnalytics.logEvent("splash_ad_click", Bundle().apply {
                putString("ad_id", ad.id)
                putString("link", ad.link)
            })
            dismissSplashOverlay()
            handleAdClick(ad.link, ad.openExternal)
        }
        imageView.setOnClickListener(onAdClick)
        splashCta.setOnClickListener(onAdClick)

        splashSkip.setOnClickListener {
            firebaseAnalytics.logEvent("splash_ad_skip", Bundle().apply {
                putString("ad_id", ad.id)
            })
            dismissSplashOverlay()
        }

        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        var remaining = ad.durationSec
        val tick = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    dismissSplashOverlay()
                } else {
                    splashSkip.text = localizedUiContext.getString(R.string.splash_skip, remaining)
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
        loadPtBrUrl(targetUrl)
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
                    val rawUrl = url.toString()
                    val ptUrl = rewriteShopifyUrlToPtBr(rawUrl)
                    if (ptUrl != rawUrl) {
                        // Intercept Shopify's /en redirect and force pt-BR root path
                        loadPtBrUrl(ptUrl)
                        return true
                    }
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val ptUrl = rewriteShopifyUrlToPtBr(url)
                if (ptUrl != url) {
                    view.stopLoading()
                    loadPtBrUrl(ptUrl)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipeRefresh.isRefreshing = false
                // Ensure that if Shopify rendered an /en locale page, it switches to pt-BR
                val ptUrl = rewriteShopifyUrlToPtBr(url)
                if (ptUrl != url) {
                    loadPtBrUrl(ptUrl)
                }
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
                Toast.makeText(
                    this,
                    localizedUiContext.getString(R.string.download_started),
                    Toast.LENGTH_SHORT
                ).show()
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
