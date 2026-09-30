package com.example.translit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Перевод: Google Translate (gtx) с резервным MyMemory.
 * Результаты кэшируются в памяти (LRU) и на диске (filesDir/translate_cache.json),
 * чтобы не дёргать API повторно.
 */
object TranslationManager {

    val LANGS = listOf(
        "en" to "English", "ru" to "Русский", "uk" to "Українська", "be" to "Беларуская",
        "bg" to "Български", "de" to "Deutsch", "fr" to "Français", "es" to "Español",
        "it" to "Italiano", "pt" to "Português", "tr" to "Türkçe", "pl" to "Polski",
        "kk" to "Қазақша", "zh" to "中文", "ja" to "日本語", "ko" to "한국어",
        "ar" to "العربية", "hi" to "हिन्दी", "cs" to "Čeština", "el" to "Ελληνικά",
        "nl" to "Nederlands", "sv" to "Svenska", "fi" to "Suomi", "ro" to "Română",
        "hu" to "Magyar", "he" to "עברית", "th" to "ไทย", "vi" to "Tiếng Việt",
        "id" to "Bahasa", "ka" to "ქართული", "az" to "Azərbaycan", "uz" to "Oʻzbekcha"
    )

    private const val CACHE_FILE = "translate_cache.json"
    private const val MAX_CACHE = 200

    private val memCache = LinkedHashMap<String, String>(MAX_CACHE, 0.75f, true)
    private var diskLoaded = false

    private fun cacheKey(text: String, target: String) = "$target|$text"

    private fun loadDisk(ctx: Context) {
        if (diskLoaded) return
        diskLoaded = true
        try {
            val f = File(ctx.filesDir, CACHE_FILE)
            if (!f.exists()) return
            val obj = JSONObject(f.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                memCache[k] = obj.getString(k)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveDisk(ctx: Context) {
        try {
            val obj = JSONObject()
            memCache.forEach { (k, v) -> obj.put(k, v) }
            File(ctx.filesDir, CACHE_FILE).writeText(obj.toString())
        } catch (_: Exception) {
        }
    }

    fun getCached(ctx: Context, text: String, target: String): String? {
        loadDisk(ctx)
        return memCache[cacheKey(text, target)]
    }

    private fun putCache(ctx: Context, text: String, target: String, result: String) {
        loadDisk(ctx)
        memCache[cacheKey(text, target)] = result
        while (memCache.size > MAX_CACHE) {
            val eldest = memCache.keys.firstOrNull() ?: break
            memCache.remove(eldest)
        }
        saveDisk(ctx)
    }

    /** Синхронный вызов, использовать из фонового потока. */
    fun translate(ctx: Context, text: String, target: String): String {
        getCached(ctx, text, target)?.let { return it }
        val result = tryGoogle(text, target) ?: tryMyMemory(text, target)
        ?: throw Exception("оба сервиса недоступны")
        putCache(ctx, text, target, result)
        return result
    }

    private fun http(url: URL, timeoutMs: Int = 8000): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun tryGoogle(text: String, target: String): String? = try {
        val body = http(
            URL(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$target&dt=t&q=" +
                    URLEncoder.encode(text, "UTF-8")
            )
        )
        val arr = JSONArray(body)
        val segs = arr.optJSONArray(0) ?: return null
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val seg = segs.optJSONArray(i) ?: continue
            sb.append(seg.optString(0))
        }
        sb.toString().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    private fun tryMyMemory(text: String, target: String): String? = try {
        val src = if (text.any { it.code in 'а'.code..'я'.code || it == 'ё' }) "ru" else "en"
        val body = http(
            URL(
                "https://api.mymemory.translated.net/get?q=" +
                    URLEncoder.encode(text, "UTF-8") + "&langpair=$src|$target"
            )
        )
        JSONObject(body).getJSONObject("responseData").optString("translatedText").ifEmpty { null }
    } catch (_: Exception) {
        null
    }
}
