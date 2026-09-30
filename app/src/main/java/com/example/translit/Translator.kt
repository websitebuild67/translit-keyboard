package com.example.translit

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Translator {

    val LANGS = listOf(
        "en" to "English", "ru" to "Русский", "de" to "Deutsch", "fr" to "Français",
        "es" to "Español", "it" to "Italiano", "pt" to "Português", "tr" to "Türkçe",
        "pl" to "Polski", "uk" to "Українська", "kk" to "Қазақша", "zh" to "中文",
        "ja" to "日本語", "ko" to "한국어", "ar" to "العربية", "hi" to "हिन्दी"
    )

    /** Синхронный вызов, использовать из фонового потока. */
    fun translate(text: String, target: String): String {
        return tryGoogle(text, target) ?: tryMyMemory(text, target)
        ?: throw Exception("оба сервиса недоступны")
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
        // MyMemory требует пару языков вида ru|en; источник определяем по кириллице
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
