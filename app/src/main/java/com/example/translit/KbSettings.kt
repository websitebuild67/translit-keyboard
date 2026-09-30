package com.example.translit

import android.content.Context

object KbSettings {
    private const val PREFS = "translit_prefs"

    data class State(
        val translitMode: TranslitMode,
        val themeId: String,
        val accentOverride: Int?,
        val keyHeight: Float,   // 0.8..1.6
        val fontSize: Float,    // 0.8..1.4
        val vibrate: Boolean,
        val sound: Boolean,
        val layoutName: String, // TRANSLIT / CYRILLIC / LATIN
        val autocorrect: Boolean,
        val suggestionsEnabled: Boolean
    )

    data class Palette(
        val bg: Int, val keyTop: Int, val keyBottom: Int, val keyStroke: Int,
        val funcTop: Int, val funcBottom: Int, val funcStroke: Int,
        val text: Int, val accent: Int, val pressedKey: Int, val pressedFunc: Int,
        val dimText: Int, val barBg: Int, val bubbleBg: Int
    )

    data class Theme(val id: String, val name: String, val palette: Palette)

    val THEMES: List<Theme> = listOf(
        Theme("midnight", "Полночь", Palette(
            bg = 0xFF17171E.toInt(), keyTop = 0xFF42424E.toInt(), keyBottom = 0xFF383844.toInt(),
            keyStroke = 0xFF52525E.toInt(), funcTop = 0xFF2C2C36.toInt(), funcBottom = 0xFF262630.toInt(),
            funcStroke = 0xFF3A3A46.toInt(), text = 0xFFF0F0F5.toInt(), accent = 0xFF4A7DFF.toInt(),
            pressedKey = 0xFF5A5A68.toInt(), pressedFunc = 0xFF3E3E4C.toInt(),
            dimText = 0xFF9A9AA4.toInt(), barBg = 0xFF101016.toInt(), bubbleBg = 0xFF5A5A68.toInt()
        )),
        Theme("oled", "OLED-чёрная", Palette(
            bg = 0xFF000000.toInt(), keyTop = 0xFF232329.toInt(), keyBottom = 0xFF1B1B21.toInt(),
            keyStroke = 0xFF33333B.toInt(), funcTop = 0xFF17171C.toInt(), funcBottom = 0xFF121216.toInt(),
            funcStroke = 0xFF26262C.toInt(), text = 0xFFEDEDF2.toInt(), accent = 0xFF5C8DFF.toInt(),
            pressedKey = 0xFF3A3A44.toInt(), pressedFunc = 0xFF2A2A32.toInt(),
            dimText = 0xFF8A8A94.toInt(), barBg = 0xFF000000.toInt(), bubbleBg = 0xFF3A3A44.toInt()
        )),
        Theme("light", "Светлая", Palette(
            bg = 0xFFE4E7EE.toInt(), keyTop = 0xFFFFFFFF.toInt(), keyBottom = 0xFFF2F4F8.toInt(),
            keyStroke = 0xFFD2D6E0.toInt(), funcTop = 0xFFDDE1E9.toInt(), funcBottom = 0xFFD3D8E2.toInt(),
            funcStroke = 0xFFC4C9D5.toInt(), text = 0xFF1C1C22.toInt(), accent = 0xFF2F6BFF.toInt(),
            pressedKey = 0xFFC9CEDA.toInt(), pressedFunc = 0xFFBFC5D2.toInt(),
            dimText = 0xFF666B76.toInt(), barBg = 0xFFD8DCE5.toInt(), bubbleBg = 0xFFC9CEDA.toInt()
        )),
        Theme("ocean", "Океан", Palette(
            bg = 0xFF0B1E2D.toInt(), keyTop = 0xFF17394F.toInt(), keyBottom = 0xFF123043.toInt(),
            keyStroke = 0xFF234B63.toInt(), funcTop = 0xFF0F2839.toInt(), funcBottom = 0xFF0C2231.toInt(),
            funcStroke = 0xFF1A3A4E.toInt(), text = 0xFFE4F2FA.toInt(), accent = 0xFF35C6E8.toInt(),
            pressedKey = 0xFF235270.toInt(), pressedFunc = 0xFF17394F.toInt(),
            dimText = 0xFF7FA3B8.toInt(), barBg = 0xFF081722.toInt(), bubbleBg = 0xFF235270.toInt()
        )),
        Theme("sunset", "Закат", Palette(
            bg = 0xFF241318.toInt(), keyTop = 0xFF47262E.toInt(), keyBottom = 0xFF3D2027.toInt(),
            keyStroke = 0xFF5A333C.toInt(), funcTop = 0xFF33191F.toInt(), funcBottom = 0xFF2C151A.toInt(),
            funcStroke = 0xFF45262E.toInt(), text = 0xFFFBEAEC.toInt(), accent = 0xFFFF8A3D.toInt(),
            pressedKey = 0xFF633943.toInt(), pressedFunc = 0xFF4A262E.toInt(),
            dimText = 0xFFB98F96.toInt(), barBg = 0xFF1C0E12.toInt(), bubbleBg = 0xFF633943.toInt()
        )),
        Theme("candy", "Конфета", Palette(
            bg = 0xFF2A1B33.toInt(), keyTop = 0xFF4A2F57.toInt(), keyBottom = 0xFF40284C.toInt(),
            keyStroke = 0xFF5E3D6E.toInt(), funcTop = 0xFF372241.toInt(), funcBottom = 0xFF301D39.toInt(),
            funcStroke = 0xFF4A2E56.toInt(), text = 0xFFF7ECFB.toInt(), accent = 0xFFFF5DA2.toInt(),
            pressedKey = 0xFF654376.toInt(), pressedFunc = 0xFF4E325C.toInt(),
            dimText = 0xFFB494C2.toInt(), barBg = 0xFF221529.toInt(), bubbleBg = 0xFF654376.toInt()
        ))
    )

    fun theme(id: String): Theme = THEMES.firstOrNull { it.id == id } ?: THEMES[0]

    val ACCENTS = listOf(
        0xFF4A7DFF.toInt(), 0xFF2FBF71.toInt(), 0xFFFF8A3D.toInt(),
        0xFFFF5DA2.toInt(), 0xFF9C6BFF.toInt(), 0xFFFF5252.toInt(),
        0xFF35C6E8.toInt()
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context): State {
        val p = prefs(ctx)
        val modeName = p.getString("mode", TranslitMode.COLLOQUIAL.name)
        val legacyDark = p.getBoolean("dark", true)
        val themeId = p.getString("theme", null)
            ?: if (legacyDark) "midnight" else "light"
        val hasAccent = p.contains("accentOverride")
        return State(
            translitMode = TranslitMode.values().firstOrNull { it.name == modeName }
                ?: TranslitMode.COLLOQUIAL,
            themeId = themeId,
            accentOverride = if (hasAccent) p.getInt("accentOverride", 0) else null,
            keyHeight = p.getFloat("keyHeight", 1f).coerceIn(0.8f, 1.6f),
            fontSize = p.getFloat("fontSize", 1f),
            vibrate = p.getBoolean("vibrate", true),
            sound = p.getBoolean("sound", false),
            layoutName = p.getString("layout", "TRANSLIT") ?: "TRANSLIT",
            autocorrect = p.getBoolean("autocorrect", true),
            suggestionsEnabled = p.getBoolean("suggestions", true)
        )
    }

    fun update(ctx: Context, f: (State) -> State) {
        val st = f(get(ctx))
        val e = prefs(ctx).edit()
            .putString("mode", st.translitMode.name)
            .putString("theme", st.themeId)
            .putFloat("keyHeight", st.keyHeight)
            .putFloat("fontSize", st.fontSize)
            .putBoolean("vibrate", st.vibrate)
            .putBoolean("sound", st.sound)
            .putString("layout", st.layoutName)
            .putBoolean("autocorrect", st.autocorrect)
            .putBoolean("suggestions", st.suggestionsEnabled)
        if (st.accentOverride != null) e.putInt("accentOverride", st.accentOverride)
        else e.remove("accentOverride")
        e.apply()
    }

    fun palette(st: State): Palette {
        val base = theme(st.themeId).palette
        val accent = st.accentOverride ?: base.accent
        return base.copy(
            accent = accent,
            pressedFunc = if (st.accentOverride != null) accent else base.pressedFunc
        )
    }
}
