package com.ararabr.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * GitHub-driven Splash Screen Image Ad Engine (`https://github.com/TestersNightmare/arara.git`).
 */
object SplashPromo {

    private const val TAG = "SplashPromo"
    private const val PREFS = "arara_splash_promo"
    private const val KEY_RAW_JSON = "raw_config_json"
    private const val KEY_LAST_SHOWN_MS = "last_shown_ms"
    private const val KEY_ROUND_ROBIN_IDX = "round_robin_idx"
    private const val KEY_LATEST_APK_URL = "latest_apk_download_url"

    data class AdDisplayItem(
        val id: String,
        val version: String,
        val imagePathOrUrl: String,
        val link: String,
        val ctaText: String?,
        val durationSec: Int,
        val openExternal: Boolean,
        val bitmap: Bitmap
    )

    /**
     * Returns the latest release APK download URL to share with other Android phones.
     * Priority:
     * 1. `apk_download_url` in `ads/splash.json` (if non-empty)
     * 2. Dynamically resolved `.apk` asset URL from GitHub Releases API (`/releases/latest`)
     * 3. Default permanent GitHub latest release download URL (`.../releases/latest/download/arara.apk`)
     */
    fun latestApkDownloadUrl(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RAW_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = JSONObject(rawJson)
                val customUrl = root.optString("apk_download_url", "").trim()
                if (customUrl.startsWith("http://") || customUrl.startsWith("https://")) {
                    return customUrl
                }
            } catch (_: Exception) {
            }
        }
        val cachedReleaseUrl = prefs.getString(KEY_LATEST_APK_URL, null)
        if (!cachedReleaseUrl.isNullOrBlank()) {
            return cachedReleaseUrl
        }
        return AppConfig.LATEST_APK_DOWNLOAD_URL
    }

    /**
     * Resolves an active ad campaign from cached configuration & local image cache for the given locale.
     */
    fun resolveCachedAd(ctx: Context, localeTag: String, ignoreInterval: Boolean = false): AdDisplayItem? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RAW_JSON, null) ?: return null
        return try {
            val root = JSONObject(rawJson)
            if (!root.optBoolean("enabled", true)) return null

            val intervalMin = root.optInt("show_interval_minutes", 0)
            if (!ignoreInterval && intervalMin > 0) {
                val lastShown = prefs.getLong(KEY_LAST_SHOWN_MS, 0L)
                val elapsedMin = (System.currentTimeMillis() - lastShown) / 60000L
                if (lastShown > 0L && elapsedMin < intervalMin) {
                    Log.d(TAG, "Skipped due to show_interval_minutes ($elapsedMin < $intervalMin)")
                    return null
                }
            }

            val selected = selectCampaign(ctx, root, localeTag) ?: return null
            val cacheFile = imageCacheFile(ctx, selected.imageKey)
            if (!cacheFile.exists()) return null

            val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return null
            AdDisplayItem(
                id = selected.id,
                version = selected.version,
                imagePathOrUrl = selected.imagePathOrUrl,
                link = selected.link,
                ctaText = selected.ctaText,
                durationSec = selected.durationSec,
                openExternal = selected.openExternal,
                bitmap = bmp
            )
        } catch (e: Exception) {
            Log.w(TAG, "resolveCachedAd error: $e")
            null
        }
    }

    fun markAdShown(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SHOWN_MS, System.currentTimeMillis())
            .apply()
    }

    /**
     * Fetches the latest `ads/splash.json` and latest release APK link from GitHub
     * and downloads any new or updated ad images into local storage.
     * Must be called on a background thread.
     */
    fun refreshFromGitHub(ctx: Context, localeTag: String): Boolean {
        // Also refresh the latest GitHub Release APK download link in background
        refreshLatestReleaseApkUrl(ctx)

        val configText = fetchTextWithFallback(AppConfig.SPLASH_CONFIG_PATH) ?: return false
        return try {
            val root = JSONObject(configText)
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_RAW_JSON, configText).apply()

            if (!root.optBoolean("enabled", true)) {
                return true
            }

            val candidates = extractAllActiveCandidates(root, localeTag)
            val activeKeys = mutableSetOf<String>()
            for (cand in candidates) {
                activeKeys.add(cand.imageKey)
                val targetFile = imageCacheFile(ctx, cand.imageKey)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    downloadImageWithFallback(ctx, cand.imagePathOrUrl, targetFile)
                }
            }

            cleanOldCacheFiles(ctx, activeKeys)
            true
        } catch (e: Exception) {
            Log.w(TAG, "refreshFromGitHub failed: $e")
            false
        }
    }

    private fun refreshLatestReleaseApkUrl(ctx: Context) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(AppConfig.GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val dlUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && dlUrl.startsWith("https://")) {
                            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit()
                                .putString(KEY_LATEST_APK_URL, dlUrl)
                                .apply()
                            Log.d(TAG, "Resolved latest GitHub Release APK URL: $dlUrl")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "refreshLatestReleaseApkUrl fallback to default: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    private data class CandidateCampaign(
        val id: String,
        val version: String,
        val imagePathOrUrl: String,
        val imageKey: String,
        val link: String,
        val ctaText: String?,
        val durationSec: Int,
        val openExternal: Boolean,
        val priority: Int,
        val weight: Int
    )

    private fun selectCampaign(ctx: Context, root: JSONObject, localeTag: String): CandidateCampaign? {
        val candidates = extractAllActiveCandidates(root, localeTag)
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        return when (root.optString("strategy", "priority").lowercase(Locale.ROOT)) {
            "random" -> {
                val totalWeight = candidates.sumOf { it.weight.coerceAtLeast(1) }
                var roll = Random.nextInt(totalWeight)
                var chosen = candidates.first()
                for (c in candidates) {
                    roll -= c.weight.coerceAtLeast(1)
                    if (roll < 0) {
                        chosen = c
                        break
                    }
                }
                chosen
            }
            "round_robin" -> {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val idx = prefs.getInt(KEY_ROUND_ROBIN_IDX, 0)
                val chosen = candidates[idx.mod(candidates.size)]
                prefs.edit().putInt(KEY_ROUND_ROBIN_IDX, (idx + 1) % candidates.size).apply()
                chosen
            }
            else -> {
                candidates.maxByOrNull { it.priority }
            }
        }
    }

    private fun extractAllActiveCandidates(root: JSONObject, localeTag: String): List<CandidateCampaign> {
        val globalVersion = root.optString("version", "1.0")
        val globalDuration = root.optInt("duration_seconds", 4).coerceIn(2, 15)
        val nowMs = System.currentTimeMillis()
        val result = mutableListOf<CandidateCampaign>()

        val campaignsArr = root.optJSONArray("campaigns")
        if (campaignsArr != null && campaignsArr.length() > 0) {
            for (i in 0 until campaignsArr.length()) {
                val item = campaignsArr.optJSONObject(i) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                if (!isWithinSchedule(item, nowMs)) continue

                val img = resolveLocalizedField(item, "images", "image", localeTag)
                if (img.isBlank()) continue
                val link = resolveLocalizedField(item, "links", "link", localeTag).ifBlank { "/" }
                val cta = resolveLocalizedField(item, "cta_texts", "cta_text", localeTag).ifBlank { null }
                val itemVer = item.optString("version", globalVersion)
                val id = item.optString("id", "campaign_$i")

                result.add(
                    CandidateCampaign(
                        id = id,
                        version = itemVer,
                        imagePathOrUrl = img,
                        imageKey = hashKey("$itemVer|$img"),
                        link = link,
                        ctaText = cta,
                        durationSec = item.optInt("duration_seconds", globalDuration).coerceIn(2, 15),
                        openExternal = item.optBoolean("open_external", false),
                        priority = item.optInt("priority", 10),
                        weight = item.optInt("weight", 1)
                    )
                )
            }
        }

        if (result.isEmpty()) {
            val topImg = resolveLocalizedField(root, "images", "image", localeTag)
            if (topImg.isNotBlank() && isWithinSchedule(root, nowMs)) {
                val topLink = resolveLocalizedField(root, "links", "link", localeTag).ifBlank { "/" }
                val topCta = resolveLocalizedField(root, "cta_texts", "cta_text", localeTag).ifBlank { null }
                result.add(
                    CandidateCampaign(
                        id = "default",
                        version = globalVersion,
                        imagePathOrUrl = topImg,
                        imageKey = hashKey("$globalVersion|$topImg"),
                        link = topLink,
                        ctaText = topCta,
                        durationSec = globalDuration,
                        openExternal = root.optBoolean("open_external", false),
                        priority = 10,
                        weight = 1
                    )
                )
            }
        }

        return result
    }

    private fun resolveLocalizedField(
        obj: JSONObject,
        mapFieldName: String,
        defaultFieldName: String,
        localeTag: String
    ): String {
        val langShort = localeTag.substringBefore("-").lowercase(Locale.ROOT)
        val mapObj = obj.optJSONObject(mapFieldName)
        if (mapObj != null) {
            val exact = mapObj.optString(localeTag, "")
            if (exact.isNotBlank()) return exact
            val byLang = mapObj.optString(langShort, "")
            if (byLang.isNotBlank()) return byLang
            val ptBr = mapObj.optString("pt-BR", "")
            if (ptBr.isNotBlank() && langShort == "pt") return ptBr
            val zhCn = mapObj.optString("zh-CN", "")
            if (zhCn.isNotBlank() && langShort == "zh") return zhCn
            val def = mapObj.optString("default", "")
            if (def.isNotBlank()) return def
        }
        return obj.optString(defaultFieldName, "")
    }

    private fun isWithinSchedule(obj: JSONObject, nowMs: Long): Boolean {
        val startStr = obj.optString("start_time", "")
        val endStr = obj.optString("end_time", "")
        if (startStr.isNotBlank()) {
            val startMs = parseIsoTime(startStr)
            if (startMs != null && nowMs < startMs) return false
        }
        if (endStr.isNotBlank()) {
            val endMs = parseIsoTime(endStr)
            if (endMs != null && nowMs > endMs) return false
        }
        return true
    }

    private fun parseIsoTime(iso: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                if (p.endsWith("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(iso)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun fetchTextWithFallback(relativePathOrUrl: String): String? {
        val urls = buildCandidateUrls(relativePathOrUrl, bustCache = true)
        for (url in urls) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().readText()
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Fetched config from: $url")
                            return text
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Config endpoint failed ($url): ${e.message}")
            }
        }
        return null
    }

    private fun downloadImageWithFallback(ctx: Context, pathOrUrl: String, destFile: File): Boolean {
        val urls = buildCandidateUrls(pathOrUrl, bustCache = false)
        for (url in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 12000
                if (conn.responseCode in 200..299) {
                    val tmpFile = File(ctx.filesDir, "${destFile.name}.tmp")
                    conn.inputStream.use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (BitmapFactory.decodeFile(tmpFile.absolutePath) != null) {
                        if (destFile.exists()) destFile.delete()
                        tmpFile.renameTo(destFile)
                        Log.d(TAG, "Downloaded splash ad image from: $url")
                        return true
                    } else {
                        tmpFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Image download failed ($url): ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }

    private fun buildCandidateUrls(pathOrUrl: String, bustCache: Boolean): List<String> {
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return listOf(trimmed)
        }
        val cleanPath = trimmed.trimStart('/')
        val suffix = if (bustCache) "?t=${System.currentTimeMillis() / 60000}" else ""
        return AppConfig.GITHUB_ASSET_BASES.map { base ->
            "${base.trimEnd('/')}/$cleanPath$suffix"
        }
    }

    private fun imageCacheFile(ctx: Context, key: String): File {
        val dir = File(ctx.filesDir, "splash_ads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "ad_$key.img")
    }

    private fun cleanOldCacheFiles(ctx: Context, keepKeys: Set<String>) {
        val dir = File(ctx.filesDir, "splash_ads")
        val files = dir.listFiles() ?: return
        val keepNames = keepKeys.map { "ad_$it.img" }.toSet()
        for (f in files) {
            if (f.name.endsWith(".img") && f.name !in keepNames) {
                f.delete()
            }
        }
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
