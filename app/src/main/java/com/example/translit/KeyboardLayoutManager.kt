package com.example.translit

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Управляет раскладками: встроенные + кастомные (JSON-файлы).
 *
 * Формат кастомной раскладки (файл в filesDir/layouts/имя.json):
 * {"id":"code","name":"Программист","rows":[[{"k":"q","lp":["Q"]},{"k":"w"}],...]}
 */
object KeyboardLayoutManager {

    data class KeyDef(val label: String, val longPress: List<String> = emptyList())
    data class LayoutDef(val id: String, val name: String, val rows: List<List<KeyDef>>)

    enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, EMOJI }

    private val LONG_PRESS = mapOf(
        "е" to listOf("ё"), "h" to listOf("ë"),
        "c" to listOf("ç"), "n" to listOf("ñ"),
        "-" to listOf("—", "–", "•"), "." to listOf("…", "·"),
        "?" to listOf("¿"), "!" to listOf("¡"),
        "$" to listOf("₽", "₴", "₸", "₺"),
        "\"" to listOf("„", "“", "”", "«", "»"),
        "'" to listOf("‚", "‘", "’"),
        "0" to listOf("°")
    )

    private fun lp(label: String): List<String> = LONG_PRESS[label] ?: emptyList()

    private fun row(vararg labels: String): List<KeyDef> =
        labels.map { KeyDef(it, lp(it)) }

    val RU_LETTERS = LayoutDef("ru", "ЙЦУКЕН", listOf(
        row("й","ц","у","к","е","н","г","ш","щ","з","х","ъ"),
        row("ф","ы","в","а","п","р","о","л","д","ж","э"),
        row("я","ч","с","м","и","т","ь","б","ю")
    ))

    val EN_LETTERS = LayoutDef("en", "QWERTY", listOf(
        row("q","w","e","r","t","y","u","i","o","p"),
        row("a","s","d","f","g","h","j","k","l"),
        row("z","x","c","v","b","n","m")
    ))

    val SYMBOLS1 = LayoutDef("sym1", "Символы 1", listOf(
        row("1","2","3","4","5","6","7","8","9","0"),
        row("-","/",":",";","(",")","$","&","@","\""),
        row(".",",","?","!","'","+","=","*","#","%")
    ))

    val SYMBOLS2 = LayoutDef("sym2", "Символы 2", listOf(
        row("~","`","|","•","√","π","÷","×","{","}"),
        row("£","¢","€","¥","^","°","=","\\","«","»"),
        row("[","]","_","™","®","©","¶","§","<",">")
    ))

    val EMOJI = LayoutDef("emoji", "Эмодзи", listOf(
        listOf("\uD83D\uDE00","\uD83D\uDE02","\uD83E\uDD70","\uD83D\uDE0E","\uD83E\uDD14",
               "\uD83D\uDE22","\uD83D\uDE21","\uD83E\uDD73","\uD83D\uDE34","\uD83E\uDD2F").map { KeyDef(it) },
        listOf("\uD83D\uDC4D","\uD83D\uDC4E","\uD83D\uDE4F","\uD83D\uDC4B","\uD83D\uDCAA",
               "\uD83E\uDD1D","✌️","\uD83D\uDC4C","❤️","\uD83D\uDD25").map { KeyDef(it) },
        listOf("\uD83C\uDF89","✨","\uD83D\uDCAF","✅","⚡",
               "\uD83C\uDF19","☀️","\uD83C\uDF08","\uD83C\uDF55","☕").map { KeyDef(it) }
    ))

    private var customCache: Pair<Long, List<LayoutDef>>? = null

    fun customLayouts(ctx: Context): List<LayoutDef> {
        val now = System.currentTimeMillis()
        customCache?.let { if (now - it.first < 30_000) return it.second }
        val dir = File(ctx.filesDir, "layouts")
        val result = mutableListOf<LayoutDef>()
        if (dir.isDirectory) {
            dir.listFiles { f -> f.extension == "json" }?.forEach { f ->
                try {
                    val obj = JSONObject(f.readText())
                    val id = obj.optString("id", f.nameWithoutExtension)
                    val name = obj.optString("name", id)
                    val rowsArr = obj.getJSONArray("rows")
                    val rows = mutableListOf<List<KeyDef>>()
                    for (i in 0 until rowsArr.length()) {
                        val rowArr = rowsArr.getJSONArray(i)
                        val keys = mutableListOf<KeyDef>()
                        for (j in 0 until rowArr.length()) {
                            val k = rowArr.getJSONObject(j)
                            val label = k.optString("k", "")
                            val lpList = mutableListOf<String>()
                            k.optJSONArray("lp")?.let { arr ->
                                for (m in 0 until arr.length()) lpList.add(arr.getString(m))
                            }
                            if (label.isNotEmpty()) keys.add(KeyDef(label, lpList))
                        }
                        if (keys.isNotEmpty()) rows.add(keys)
                    }
                    if (rows.isNotEmpty()) result.add(LayoutDef(id, name, rows))
                } catch (_: Exception) {
                }
            }
        }
        customCache = now to result
        return result
    }

    fun invalidateCache() {
        customCache = null
    }
}
