package com.example.translit

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

class TranslitKeyboardService : InputMethodService() {

    enum class Layout { TRANSLIT, CYRILLIC, LATIN }
    enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, EMOJI }

    private lateinit var st: KbSettings.State
    private lateinit var pal: KbSettings.Palette

    private var layout = Layout.TRANSLIT
    private var page = Page.LETTERS

    private var shiftActive = false
    private var shiftLocked = false

    // Сколько символов вставлено последним нажатием (щ -> "sch" = 3)
    private var lastCommitLen = 0
    // Текущее недособранное слово на исходном языке (для подсказок)
    private var composing = StringBuilder()
    // Сколько символов текущего слова уже стоит в поле (в транслите "щ" = 3 символа)
    private var composingCommitLen = 0
    // Последнее завершённое слово (для ассоциативных подсказок)
    private var lastWord: String? = null

    private var root: LinearLayout? = null
    private val letterKeys = mutableListOf<TextView>()
    private var shiftKey: TextView? = null
    private var globeKey: TextView? = null
    private var suggestionsBar: LinearLayout? = null
    private var translatePanel: LinearLayout? = null
    private var translatePreview: TextView? = null
    private var translateThread: Thread? = null

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // ЙЦУКЕН
        val RU1 = "й ц у к е н г ш щ з х ъ".split(' ')
        val RU2 = "ф ы в а п р о л д ж э".split(' ')
        val RU3 = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
        // QWERTY
        val EN1 = "q w e r t y u i o p".split(' ')
        val EN2 = "a s d f g h j k l".split(' ')
        val EN3 = listOf("z", "x", "c", "v", "b", "n", "m")

        val SYM1 = listOf(
            "1 2 3 4 5 6 7 8 9 0".split(' '),
            "- / : ; ( ) $ & @ \"".split(' '),
            listOf(".", ",", "?", "!", "'", "+", "=", "*", "#", "%")
        )
        val SYM2 = listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
            listOf("£", "¢", "€", "¥", "^", "°", "=", "\\", "«", "»"),
            listOf("[", "]", "_", "™", "®", "©", "¶", "§", "<", ">")
        )
        val EMOJI = listOf(
            listOf("😀", "😂", "🥰", "😎", "🤔", "😢", "😡", "🥳", "😴", "🤯"),
            listOf("👍", "👎", "🙏", "👋", "💪", "🤝", "✌️", "👌", "❤️", "🔥"),
            listOf("🎉", "✨", "💯", "✅", "⚡", "🌙", "☀️", "🌈", "🍕", "☕")
        )
        // Долгое нажатие: базовый символ -> всплывающие варианты
        val LONG_PRESS = mapOf(
            "е" to listOf("ё"), "h" to listOf("ë"),
            "c" to listOf("ç"), "n" to listOf("ñ"),
            "-" to listOf("—", "–", "•"), "." to listOf("…", "·"),
            "?" to listOf("¿"), "!" to listOf("¡"),
            "$" to listOf("₽", "₴", "₸", "₺"),
            "\"" to listOf("„", "“", "”", "«", "»"),
            "'" to listOf("‚", "‘", "’"),
            "0" to listOf("°")
        )
    }

    override fun onCreate() {
        super.onCreate()
        st = KbSettings.get(this)
        pal = KbSettings.palette(st)
        layout = try {
            Layout.valueOf(st.layoutName)
        } catch (_: Exception) {
            Layout.TRANSLIT
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        st = KbSettings.get(this)
        pal = KbSettings.palette(st)
        layout = try {
            Layout.valueOf(st.layoutName)
        } catch (_: Exception) {
            layout
        }
        lastCommitLen = 0
        composing.setLength(0)
        composingCommitLen = 0
        lastWord = null
        translatePanel = null
        translatePreview = null
        rebuild()
    }

    override fun onCreateInputView(): View {
        val pad = dp(6f).toInt()
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            setPadding(pad / 2, pad, pad / 2, pad + navBarHeight())
        }
        root = r
        ViewCompat.setOnApplyWindowInsetsListener(r) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(pad / 2, pad, pad / 2, pad + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(r)
        rebuild()
        return r
    }

    private fun navBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ---------------------------------------------------------------- сборка UI

    private fun rebuild() {
        val r = root ?: return
        r.removeAllViews()
        letterKeys.clear()
        shiftKey = null

        if (translatePanel != null) {
            r.addView(buildTranslatePanel())
        } else {
            r.addView(buildSuggestions())
        }

        when (page) {
            Page.LETTERS -> buildLetters(r)
            Page.SYMBOLS1 -> buildGrid(r, SYM1)
            Page.SYMBOLS2 -> buildGrid(r, SYM2)
            Page.EMOJI -> buildGrid(r, EMOJI)
        }
        r.addView(buildBottomRow())
        r.requestLayout()
    }

    private fun buildSuggestions(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40f).toInt()
            )
        }
        suggestionsBar = bar

        // Кнопка перевода — только для ЙЦУКЕН и QWERTY
        if (layout != Layout.TRANSLIT) {
            val tr = TextView(this).apply {
                text = "🌐"
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * st.fontSize)
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(8f)
                    setColor(pal.func)
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(dp(44f).toInt(), dp(34f).toInt())
                    .apply { marginEnd = dp(4f).toInt() }
                setOnClickListener { openTranslate() }
            }
            bar.addView(tr)
        }

        val kind = when (layout) {
            Layout.TRANSLIT -> "translit"
            Layout.CYRILLIC -> "ru"
            Layout.LATIN -> "en"
        }
        val translitFn: ((Char) -> String)? =
            if (layout == Layout.TRANSLIT) ({ c -> st.translitMode.translit(c, false) }) else null

        val words = try {
            Dictionary.suggestions(
                this, kind, composing.toString(), lastWord, translitFn
            )
        } catch (_: Exception) {
            emptyList()
        }

        for ((src, ins) in words) {
            val tv = TextView(this).apply {
                text = ins
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * st.fontSize)
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(8f)
                    setColor(pal.key)
                }
                background = bg
                val hPad = dp(12f).toInt()
                setPadding(hPad, 0, hPad, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(34f).toInt()
                ).apply { marginEnd = dp(4f).toInt() }
                setOnClickListener { commitSuggestion(src, ins) }
            }
            bar.addView(tv)
        }
        return bar
    }

    /**
     * src — фраза на исходном языке (например «как дела»), ins — строка для вставки
     * (в транслите «kak dela»). Заменяет недособранное слово и добавляет фразу.
     */
    private fun commitSuggestion(src: String, ins: String) {
        val ic = currentInputConnection ?: return
        // стираем уже набранную часть текущего слова
        if (composingCommitLen > 0) {
            ic.deleteSurroundingText(composingCommitLen, 0)
        }
        val upper = shiftActive || shiftLocked
        val out = if (upper && ins.isNotEmpty())
            ins[0].uppercaseChar() + ins.substring(1) else ins
        ic.commitText(out, 1)

        // последнее слово фразы становится ассоциацией для следующих подсказок
        lastWord = src.trim().split(' ').lastOrNull()?.lowercase()
        composing.setLength(0)
        composingCommitLen = 0
        lastCommitLen = 0
        if (shiftActive && !shiftLocked) shiftActive = false
        refreshLetters()
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val r = root ?: return
        if (translatePanel != null) return
        val bar = suggestionsBar ?: return
        val index = r.indexOfChild(bar)
        if (index >= 0) {
            r.removeViewAt(index)
            r.addView(buildSuggestions(), index)
        }
    }

    private fun buildLetters(r: LinearLayout) {
        when (layout) {
            Layout.TRANSLIT, Layout.CYRILLIC -> {
                r.addView(buildRow(RU1, letters = true))
                r.addView(buildRow(RU2, letters = true))
                r.addView(buildCyrRow3())
            }
            Layout.LATIN -> {
                r.addView(buildRow(EN1, letters = true))
                r.addView(buildRow(EN2, letters = true))
                r.addView(buildLatRow3())
            }
        }
    }

    private fun buildRow(keys: List<String>, letters: Boolean): LinearLayout {
        val row = newRow()
        for (k in keys) {
            val tv = makeKey(display(k), 1f, func = false)
            tv.tag = k
            if (letters) letterKeys.add(tv)
            tv.setOnClickListener { onLetter(k) }
            attachLongPress(tv, k)
            row.addView(tv)
        }
        return row
    }

    private fun buildGrid(r: LinearLayout, rows: List<List<String>>) {
        for (rowKeys in rows) {
            val row = newRow()
            for (k in rowKeys) {
                val tv = makeKey(k, 1f, func = false)
                tv.tag = k
                tv.setOnClickListener { onSimpleSymbol(k) }
                attachLongPress(tv, k)
                row.addView(tv)
            }
            r.addView(row)
        }
    }

    private fun buildCyrRow3(): LinearLayout {
        val row = newRow()
        row.addView(makeShift())
        for (k in RU3) {
            val tv = makeKey(display(k), 1f, func = false)
            tv.tag = k
            letterKeys.add(tv)
            tv.setOnClickListener { onLetter(k) }
            attachLongPress(tv, k)
            row.addView(tv)
        }
        row.addView(makeBackspace())
        return row
    }

    private fun buildLatRow3(): LinearLayout {
        val row = newRow()
        row.addView(makeShift())
        for (k in EN3) {
            val tv = makeKey(display(k), 1f, func = false)
            tv.tag = k
            letterKeys.add(tv)
            tv.setOnClickListener { onLetter(k) }
            attachLongPress(tv, k)
            row.addView(tv)
        }
        row.addView(makeBackspace())
        return row
    }

    private fun makeShift(): TextView {
        val shift = makeKey("⇧", 1.3f, func = true)
        shift.setTypeface(null, Typeface.BOLD)
        if (shiftActive || shiftLocked) shift.setTextColor(pal.accent)
        shift.setOnClickListener {
            haptic()
            if (shiftLocked) {
                shiftLocked = false
                shiftActive = false
            } else if (shiftActive) {
                shiftLocked = true
            } else {
                shiftActive = true
            }
            refreshLetters()
        }
        shiftKey = shift
        return shift
    }

    private fun makeBackspace(): TextView {
        val back = makeKey("⌫", 1.3f, func = true)
        back.setOnClickListener {
            haptic()
            onBackspace()
        }
        back.setOnLongClickListener {
            val ic = currentInputConnection ?: return@setOnLongClickListener true
            for (i in 0 until 20) ic.deleteSurroundingText(1, 0)
            lastCommitLen = 0
            composing.setLength(0)
            composingCommitLen = 0
            lastWord = null
            refreshSuggestions()
            true
        }
        return back
    }

    private fun buildBottomRow(): LinearLayout {
        val row = newRow()

        val sym = makeKey(
            when (page) {
                Page.LETTERS -> "?123"
                Page.SYMBOLS1 -> "=\\<"
                Page.SYMBOLS2 -> "АБВ"
                Page.EMOJI -> "АБВ"
            }, 1.4f, func = true
        )
        sym.text = sym.text.toString()
        sym.setOnClickListener {
            haptic()
            page = when (page) {
                Page.LETTERS -> Page.SYMBOLS1
                Page.SYMBOLS1 -> Page.SYMBOLS2
                Page.SYMBOLS2 -> Page.LETTERS
                Page.EMOJI -> Page.LETTERS
            }
            rebuild()
        }
        row.addView(sym)

        val emoji = makeKey("☺", 1f, func = true)
        emoji.setOnClickListener {
            haptic()
            page = if (page == Page.EMOJI) Page.LETTERS else Page.EMOJI
            rebuild()
        }
        row.addView(emoji)

        val globe = makeKey("🌍", 1f, func = true)
        globe.setOnClickListener {
            haptic()
            layout = when (layout) {
                Layout.TRANSLIT -> Layout.CYRILLIC
                Layout.CYRILLIC -> Layout.LATIN
                Layout.LATIN -> Layout.TRANSLIT
            }
            finishWord()
            rebuild()
        }
        globeKey = globe
        row.addView(globe)

        val settings = makeKey("⚙", 1f, func = true)
        settings.setOnClickListener {
            haptic()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            requestHideSelf(0)
        }
        row.addView(settings)

        val space = makeKey(spaceLabel(), 3.2f, func = true)
        space.setOnClickListener {
            haptic()
            finishWord()
            currentInputConnection?.commitText(" ", 1)
            if (shiftActive && !shiftLocked) {
                shiftActive = false
                refreshLetters()
            }
            refreshSuggestions()
        }
        row.addView(space)

        val enter = makeKey("↵", 1.3f, func = true)
        enter.setOnClickListener {
            haptic()
            finishWord()
            val ic = currentInputConnection ?: return@setOnClickListener
            val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                ic.performEditorAction(action)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
            refreshSuggestions()
        }
        row.addView(enter)
        return row
    }

    private fun spaceLabel(): String = when (layout) {
        Layout.TRANSLIT -> if (st.translitMode == TranslitMode.PASSPORT)
            "транслит ICAO" else "транслит"
        Layout.CYRILLIC -> "русский"
        Layout.LATIN -> "english"
    }

    // ---------------------------------------------------------- панель перевода

    private fun openTranslate() {
        haptic()
        translatePanel = LinearLayout(this)
        rebuild()
    }

    private fun buildTranslatePanel(): View {
        val panel = translatePanel!!
        panel.removeAllViews()
        panel.orientation = LinearLayout.VERTICAL

        val title = TextView(this).apply {
            text = "Перевод (Google)"
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * st.fontSize)
            setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(8f).toInt(), dp(4f).toInt(), 0, dp(4f).toInt())
            layoutParams = lp
        }
        panel.addView(title)

        val preview = TextView(this).apply {
            text = "Наберите текст, выберите язык и нажмите «Перевести»"
            setTextColor(pal.dimText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * st.fontSize)
            maxLines = 3
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(8f).toInt(), 0, dp(8f).toInt(), dp(4f).toInt())
            layoutParams = lp
        }
        panel.addView(preview)
        translatePreview = preview

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(90f).toInt()
            )
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (chunk in Translator.LANGS.chunked(4)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((code, name) in chunk) {
                val b = Button(this).apply {
                    text = name
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * st.fontSize)
                    setTextColor(pal.text)
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(8f)
                        setColor(pal.key)
                    }
                    background = bg
                    val m = dp(3f).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(m, m, m, m) }
                    setOnClickListener { doTranslate(code, name) }
                }
                row.addView(b)
            }
            grid.addView(row)
        }
        scroll.addView(grid)
        panel.addView(scroll)

        val close = Button(this).apply {
            text = "✕ Закрыть"
            isAllCaps = false
            setTextColor(pal.text)
            val bg = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(pal.func)
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(2f).toInt()) }
            setOnClickListener {
                translatePanel = null
                translatePreview = null
                rebuild()
            }
        }
        panel.addView(close)
        return panel
    }

    private fun doTranslate(target: String, targetName: String) {
        val pv = translatePreview ?: return
        val ic = currentInputConnection ?: return
        // Берём текст до курсора (максимум 200 символов)
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        if (before.isBlank()) {
            pv.text = "Сначала наберите текст"
            return
        }
        pv.text = "Перевожу на $targetName…"
        translateThread?.interrupt()
        translateThread = thread(start = true) {
            val result = try {
                val out = Translator.translate(before.trim(), target)
                if (out.isEmpty()) "Ошибка: пустой ответ" else out
            } catch (e: Exception) {
                "Ошибка сети: ${e.message}"
            }
            handler.post {
                val p = translatePreview ?: return@post
                p.text = result
                if (result.startsWith("Ошибка")) {
                    p.setTextColor(Color.RED)
                } else {
                    p.setTextColor(pal.accent)
                    showInsertButton(result, before.length)
                }
            }
        }
    }

    /** Кнопка вставки результата: заменяет исходный текст переводом. */
    private fun showInsertButton(result: String, sourceLen: Int) {
        val panel = translatePanel ?: return
        // удаляем старую кнопку вставки, если была (tag = "insert")
        for (i in panel.childCount - 1 downTo 0) {
            if (panel.getChildAt(i).tag == "insert") panel.removeViewAt(i)
        }
        val btn = Button(this).apply {
            tag = "insert"
            text = "⤵ Вставить перевод"
            isAllCaps = false
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(pal.accent)
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(6f).toInt(), dp(2f).toInt(), dp(6f).toInt(), dp(2f).toInt())
            }
            setOnClickListener {
                val ic = currentInputConnection ?: return@setOnClickListener
                val before = ic.getTextBeforeCursor(sourceLen, 0)?.toString() ?: ""
                if (before.length == sourceLen && sourceLen > 0) {
                    ic.deleteSurroundingText(sourceLen, 0)
                }
                ic.commitText(result, 1)
                translatePanel = null
                translatePreview = null
                rebuild()
            }
        }
        // вставляем перед кнопкой "Закрыть" (последний ребёнок)
        panel.addView(btn, panel.childCount - 1)
    }

    // ------------------------------------------------------------ ввод символов

    private fun display(k: String): String {
        val upper = shiftActive || shiftLocked
        return if (upper && k.length == 1 && k[0].isLetter()) k.uppercase() else k
    }

    private fun onLetter(k: String) {
        haptic()
        val ic = currentInputConnection ?: return
        val upper = shiftActive || shiftLocked
        val out: String
        when (layout) {
            Layout.TRANSLIT -> out = st.translitMode.translit(k[0], upper)
            else -> out = if (upper) k.uppercase() else k
        }
        if (out.isNotEmpty()) {
            ic.commitText(out, 1)
            lastCommitLen = out.length
            if (k[0].isLetter()) {
                // для подсказок собираем слово на исходном языке
                composing.append(if (layout == Layout.TRANSLIT) k[0] else out.lowercase())
                composingCommitLen += out.length
            }
        } else if (layout == Layout.TRANSLIT && k[0].isLetter()) {
            // ъ/ь в транслите не дают символов, но входят в слово
            composing.append(k[0])
        }
        if (shiftActive && !shiftLocked) {
            shiftActive = false
        }
        refreshLetters()
        refreshSuggestions()
    }

    private fun onSimpleSymbol(k: String) {
        haptic()
        val ic = currentInputConnection ?: return
        ic.commitText(k, 1)
        finishWord()
        refreshSuggestions()
    }

    /** Слово завершено: запоминаем его для ассоциативных подсказок. */
    private fun finishWord() {
        if (composing.isNotEmpty()) lastWord = composing.toString().lowercase()
        composing.setLength(0)
        composingCommitLen = 0
        lastCommitLen = 0
    }

    private fun attachLongPress(tv: TextView, base: String) {
        val variants = LONG_PRESS[base] ?: return
        tv.setOnLongClickListener {
            showVariants(tv, variants)
            true
        }
    }

    private fun showVariants(anchor: View, variants: List<String>) {
        val ic = currentInputConnection ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(pal.key)
                setStroke(dp(1f).toInt(), pal.accent)
            }
            background = bg
        }
        val popup = android.widget.PopupWindow(
            row,
            (dp(48f) * variants.size).toInt() + dp(12f).toInt(),
            dp(44f).toInt(),
            true
        )
        for (v in variants) {
            val tv = TextView(this).apply {
                text = v
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * st.fontSize)
                layoutParams = LinearLayout.LayoutParams(
                    dp(44f).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    ic.commitText(v, 1)
                    lastCommitLen = v.length
                    popup.dismiss()
                }
            }
            row.addView(tv)
        }
        popup.contentView = row
        popup.isOutsideTouchable = true
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        popup.showAtLocation(
            anchor, Gravity.NO_GRAVITY,
            loc[0] + anchor.width / 2 - popup.width / 2,
            loc[1] - popup.height - dp(8f).toInt()
        )
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        val del = if (lastCommitLen > 1) lastCommitLen else 1
        ic.deleteSurroundingText(del, 0)
        if (composingCommitLen > 0) {
            composingCommitLen = (composingCommitLen - del).coerceAtLeast(0)
            if (composing.isNotEmpty()) composing.deleteCharAt(composing.length - 1)
        }
        lastCommitLen = 0
        refreshSuggestions()
    }

    private fun refreshLetters() {
        val upper = shiftActive || shiftLocked
        for (tv in letterKeys) {
            val base = tv.tag as String
            tv.text = if (upper && base.length == 1 && base[0].isLetter())
                base.uppercase() else base
        }
        shiftKey?.apply {
            setTextColor(if (upper) pal.accent else pal.text)
            text = if (shiftLocked) "⇪" else "⇧"
        }
        globeKey?.text = "🌍"
    }

    private fun haptic() {
        if (st.vibrate) {
            try {
                val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(12)
                }
            } catch (_: Exception) {
            }
        }
        if (st.sound) {
            try {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.4f)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------- рисование

    private fun newRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun makeKey(label: String, weight: Float, func: Boolean): TextView {
        val tv = TextView(this).apply {
            text = label
            tag = label
            gravity = Gravity.CENTER
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (func) 15f else 19f) * st.fontSize)
            isClickable = true
            isFocusable = true
            val bg = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(if (func) pal.func else pal.key)
            }
            background = bg
            val vPad = (dp(11f) * st.keyHeight).toInt()
            setPadding(0, vPad, 0, vPad)
            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            (v.background as GradientDrawable).setColor(
                                if (func) pal.pressedFunc else pal.pressedKey
                            )
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL ->
                            (v.background as GradientDrawable).setColor(
                                if (func) pal.func else pal.key
                            )
                    }
                    return false
                }
            })
        }
        val margin = dp(3f).toInt()
        tv.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, weight
        ).apply { setMargins(margin, margin, margin, margin) }
        return tv
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lastCommitLen = 0
        composing.setLength(0)
        composingCommitLen = 0
        lastWord = null
    }
}
