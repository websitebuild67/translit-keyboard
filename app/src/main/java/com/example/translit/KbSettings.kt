package com.example.translit

import android.content.Context

object KbSettings {
    private const val PREFS = "translit_prefs"

    data class State(
        val translitMode: TranslitMode,
        val dark: Boolean,
        val accent: Int,
        val keyHeight: Float,   // 0.8..1.3
        val fontSize: Float,    // 0.8..1.4
        val vibrate: Boolean,
        val sound: Boolean,
        val layoutName: String  // TRANSLIT / CYRILLIC / LATIN
    )

    data class Palette(
        val bg: Int, val key: Int, val func: Int, val text: Int,
        val accent: Int, val pressedKey: Int, val pressedFunc: Int, val dimText: Int
    )

    val ACCENTS = listOf(
        0xFF4A7DFF.toInt(), 0xFF2FBF71.toInt(), 0xFFFF8A3D.toInt(),
        0xFFFF5DA2.toInt(), 0xFF9C6BFF.toInt(), 0xFFFF5252.toInt()
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context): State {
        val p = prefs(ctx)
        val modeName = p.getString("mode", TranslitMode.COLLOQUIAL.name)
        return State(
            translitMode = TranslitMode.values().firstOrNull { it.name == modeName }
                ?: TranslitMode.COLLOQUIAL,
            dark = p.getBoolean("dark", true),
            accent = p.getInt("accent", ACCENTS[0]),
            keyHeight = p.getFloat("keyHeight", 1f),
            fontSize = p.getFloat("fontSize", 1f),
            vibrate = p.getBoolean("vibrate", true),
            sound = p.getBoolean("sound", false),
            layoutName = p.getString("layout", "TRANSLIT") ?: "TRANSLIT"
        )
    }

    fun update(ctx: Context, f: (State) -> State) {
        val st = f(get(ctx))
        prefs(ctx).edit()
            .putString("mode", st.translitMode.name)
            .putBoolean("dark", st.dark)
            .putInt("accent", st.accent)
            .putFloat("keyHeight", st.keyHeight)
            .putFloat("fontSize", st.fontSize)
            .putBoolean("vibrate", st.vibrate)
            .putBoolean("sound", st.sound)
            .putString("layout", st.layoutName)
            .apply()
    }

    fun palette(st: State): Palette = if (st.dark) Palette(
        bg = 0xFF1E1E24.toInt(),
        key = 0xFF3A3A44.toInt(),
        func = 0xFF44444E.toInt(),
        text = 0xFFE8E8EC.toInt(),
        accent = st.accent,
        pressedKey = 0xFF55555F.toInt(),
        pressedFunc = st.accent,
        dimText = 0xFF9A9AA4.toInt()
    ) else Palette(
        bg = 0xFFE8EAF0.toInt(),
        key = 0xFFFFFFFF.toInt(),
        func = 0xFFD6DAE2.toInt(),
        text = 0xFF1A1A20.toInt(),
        accent = st.accent,
        pressedKey = 0xFFC5CAD4.toInt(),
        pressedFunc = st.accent,
        dimText = 0xFF6A6E78.toInt()
    )
}
