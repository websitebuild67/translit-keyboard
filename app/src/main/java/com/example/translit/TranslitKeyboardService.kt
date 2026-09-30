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
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

class TranslitKeyboardService : InputMethodService() {

    enum class Layout { TRANSLIT, CYRILLIC, LATIN, CUSTOM }

    private lateinit var st: KbSettings.State
    private lateinit var pal: KbSettings.Palette

    private var layout = Layout.TRANSLIT
    private var page = KeyboardLayoutManager.Page.LETTERS
    private var customLayoutIndex = 0

    private var shiftActive = false
    private var shiftLocked = false

    private lateinit var input: InputHandler

    private var root: LinearLayout? = null
    private var overlay: FrameLayout? = null
    private val letterKeys = mutableListOf<TextView>()
    private var shiftKey: TextView? = null
    private var suggestionsBar: LinearLayout? = null
    private var translatePanel: LinearLayout? = null
    private var translatePreview: TextView? = null
    private var translateThread: Thread? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        input = InputHandler(this)
        st = KbSettings.get(this)
        pal = KbSettings.palette(st)
        layout = layoutFromName(st.layoutName)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        st = KbSettings.get(this)
        pal = KbSettings.palette(st)
        layout = layoutFromName(st.layoutName)
        input.reset()
        translatePanel = null
        translatePreview = null
        rebuild()
    }

    private fun layoutFromName(name: String): Layout =
        try { Layout.valueOf(name) } catch (_: Exception) { Layout.TRANSLIT }

    override fun onCreateInputView(): View {
        val pad = dp(6f).toInt()
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            setPadding(pad / 2, pad, pad / 2, pad + navBarHeight())
        }
        root = r
        val frame = FrameLayout(this).apply {
            setBackgroundColor(pal.bg)
            addView(r)
        }
        overlay = frame
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            r.setPadding(pad / 2, pad, pad / 2, pad + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(frame)
        rebuild()
        return frame
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
        } else if (layout != Layout.TRANSLIT) {
            r.addView(buildSuggestions())
        }

        when (page) {
            KeyboardLayoutManager.Page.LETTERS -> buildLetters(r)
            KeyboardLayoutManager.Page.SYMBOLS1 ->
                buildGrid(r, KeyboardLayoutManager.SYMBOLS1.rows)
            KeyboardLayoutManager.Page.SYMBOLS2 ->
                buildGrid(r, KeyboardLayoutManager.SYMBOLS2.rows)
            KeyboardLayoutManager.Page.EMOJI ->
                buildGrid(r, KeyboardLayoutManager.EMOJI.rows)
        }
        r.addView(buildBottomRow())
        r.requestLayout()
    }

    private fun buildSuggestions(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.barBg)
            val hPad = dp(6f).toInt()
            setPadding(hPad, dp(4f).toInt(), hPad, dp(4f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44f).toInt()
            )
        }
        suggestionsBar = bar

        if (layout != Layout.TRANSLIT) {
            val tr = makeIconKey(R.drawable.ic_translate, 0f)
            tr.layoutParams = LinearLayout.LayoutParams(dp(40f).toInt(), dp(34f).toInt())
                .apply { marginEnd = dp(4f).toInt() }
            tr.setOnClickListener { openTranslate() }
            bar.addView(tr)
        }

        val kind = suggestionKind()
        val translitFn: ((Char) -> String)? =
            if (layout == Layout.TRANSLIT) ({ c -> st.translitMode.translit(c, false) }) else null

        val words = if (st.suggestionsEnabled) try {
            SuggestionEngine.suggestions(
                this, kind, input.composing.toString(), input.lastWords, translitFn
            )
        } catch (_: Exception) {
            emptyList()
        } else emptyList()

        for ((src, ins) in words) {
            val tv = TextView(this).apply {
                text = ins
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * st.fontSize)
                background = GradientDrawable().apply {
                    cornerRadius = dp(17f)
                    setColor(pal.keyTop)
                    setStroke(dp(1f).toInt(), pal.keyStroke)
                }
                val hPad = dp(14f).toInt()
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

    private fun suggestionKind(): String = when (layout) {
        Layout.TRANSLIT -> "translit"
        Layout.CYRILLIC -> "ru"
        else -> "en"
    }

    private fun commitSuggestion(src: String, ins: String) {
        val ic = currentInputConnection ?: return
        if (input.composingCommitLen > 0) {
            ic.deleteSurroundingText(input.composingCommitLen, 0)
        }
        val upper = shiftActive || shiftLocked
        val out = if (upper && ins.isNotEmpty())
            ins[0].uppercaseChar() + ins.substring(1) else ins
        ic.commitText(out, 1)

        val parts = src.trim().split(' ').map { it.lowercase() }
        input.lastWords.addAll(parts)
        while (input.lastWords.size > 2) input.lastWords.removeAt(0)

        input.composing.setLength(0)
        input.composingCommitLen = 0
        input.lastCommitLen = 0
        if (shiftActive && !shiftLocked) shiftActive = false
        refreshLetters()
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val r = root ?: return
        if (translatePanel != null) return
        if (layout == Layout.TRANSLIT) return
        val bar = suggestionsBar ?: return
        val index = r.indexOfChild(bar)
        if (index >= 0) {
            r.removeViewAt(index)
            r.addView(buildSuggestions(), index)
        }
    }

    private fun buildLetters(r: LinearLayout) {
        val def = when (layout) {
            Layout.TRANSLIT, Layout.CYRILLIC -> KeyboardLayoutManager.RU_LETTERS
            Layout.LATIN -> KeyboardLayoutManager.EN_LETTERS
            Layout.CUSTOM -> {
                val customs = KeyboardLayoutManager.customLayouts(this)
                customs.getOrNull(customLayoutIndex) ?: KeyboardLayoutManager.RU_LETTERS
            }
        }
        for ((rowIdx, rowKeys) in def.rows.withIndex()) {
            if (rowIdx == def.rows.size - 1 && def == KeyboardLayoutManager.RU_LETTERS ||
                rowIdx == def.rows.size - 1 && def == KeyboardLayoutManager.EN_LETTERS) {
                r.addView(buildLastLetterRow(rowKeys))
            } else {
                r.addView(buildLetterRow(rowKeys))
            }
        }
    }

    private fun buildLetterRow(keys: List<KeyboardLayoutManager.KeyDef>): LinearLayout {
        val row = newRow()
        for (k in keys) {
            val tv = makeKey(k.label, 1f, func = false)
            tv.tag = k.label
            letterKeys.add(tv)
            tv.setOnClickListener { onLetter(k.label) }
            if (k.longPress.isNotEmpty()) attachLongPress(tv, k.longPress)
            row.addView(tv)
        }
        return row
    }

    private fun buildLastLetterRow(keys: List<KeyboardLayoutManager.KeyDef>): LinearLayout {
        val row = newRow()
        row.addView(makeShift())
        for (k in keys) {
            val tv = makeKey(k.label, 1f, func = false)
            tv.tag = k.label
            letterKeys.add(tv)
            tv.setOnClickListener { onLetter(k.label) }
            if (k.longPress.isNotEmpty()) attachLongPress(tv, k.longPress)
            row.addView(tv)
        }
        row.addView(makeBackspace())
        return row
    }

    private fun buildGrid(r: LinearLayout, rows: List<List<KeyboardLayoutManager.KeyDef>>) {
        for (rowKeys in rows) {
            val row = newRow()
            for (k in rowKeys) {
                val tv = makeKey(k.label, 1f, func = false)
                tv.tag = k.label
                tv.setOnClickListener { onSimpleSymbol(k.label) }
                if (k.longPress.isNotEmpty()) attachLongPress(tv, k.longPress)
                row.addView(tv)
            }
            r.addView(row)
        }
    }

    private fun makeShift(): TextView {
        val shift = makeIconKey(R.drawable.ic_shift, 1.3f)
        if (shiftActive || shiftLocked) setIconTint(shift, pal.accent)
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
        val back = makeIconKey(R.drawable.ic_backspace, 1.3f)
        back.setOnClickListener {
            haptic()
            val ic = currentInputConnection ?: return@setOnClickListener
            input.onBackspace(ic)
            refreshSuggestions()
        }
        back.setOnLongClickListener {
            val ic = currentInputConnection ?: return@setOnLongClickListener true
            input.onLongBackspace(ic)
            refreshSuggestions()
            true
        }
        return back
    }

    private fun buildBottomRow(): LinearLayout {
        val row = newRow()

        val sym = makeKey(
            when (page) {
                KeyboardLayoutManager.Page.LETTERS -> "?123"
                KeyboardLayoutManager.Page.SYMBOLS1 -> "=\\<"
                KeyboardLayoutManager.Page.SYMBOLS2 -> "АБВ"
                KeyboardLayoutManager.Page.EMOJI -> "АБВ"
            }, 1.4f, func = true
        )
        sym.setOnClickListener {
            haptic()
            page = when (page) {
                KeyboardLayoutManager.Page.LETTERS -> KeyboardLayoutManager.Page.SYMBOLS1
                KeyboardLayoutManager.Page.SYMBOLS1 -> KeyboardLayoutManager.Page.SYMBOLS2
                KeyboardLayoutManager.Page.SYMBOLS2 -> KeyboardLayoutManager.Page.LETTERS
                KeyboardLayoutManager.Page.EMOJI -> KeyboardLayoutManager.Page.LETTERS
            }
            rebuild()
        }
        row.addView(sym)

        val emoji = makeIconKey(R.drawable.ic_smile, 1f)
        emoji.setOnClickListener {
            haptic()
            page = if (page == KeyboardLayoutManager.Page.EMOJI)
                KeyboardLayoutManager.Page.LETTERS else KeyboardLayoutManager.Page.EMOJI
            rebuild()
        }
        row.addView(emoji)

        val globe = makeIconKey(R.drawable.ic_globe, 1f)
        globe.setOnClickListener {
            haptic()
            val customs = KeyboardLayoutManager.customLayouts(this)
            layout = when (layout) {
                Layout.TRANSLIT -> Layout.CYRILLIC
                Layout.CYRILLIC -> if (customs.isNotEmpty()) {
                    customLayoutIndex = 0
                    Layout.CUSTOM
                } else Layout.LATIN
                Layout.CUSTOM -> if (customLayoutIndex < customs.size - 1) {
                    customLayoutIndex++
                    Layout.CUSTOM
                } else Layout.LATIN
                Layout.LATIN -> Layout.TRANSLIT
            }
            input.finishWord(false, "")
            input.lastWords.clear()
            rebuild()
        }
        row.addView(globe)

        val settings = makeIconKey(R.drawable.ic_settings, 1f)
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
            finishCurrentWord()
            currentInputConnection?.commitText(" ", 1)
            if (shiftActive && !shiftLocked) {
                shiftActive = false
                refreshLetters()
            }
            refreshSuggestions()
        }
        row.addView(space)

        val enter = makeIconKey(R.drawable.ic_enter, 1.3f)
        enter.setOnClickListener {
            haptic()
            finishCurrentWord()
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
        Layout.CUSTOM -> {
            val customs = KeyboardLayoutManager.customLayouts(this)
            customs.getOrNull(customLayoutIndex)?.name ?: "custom"
        }
    }

    private fun finishCurrentWord() {
        val ic = currentInputConnection ?: return
        val kind = when (layout) {
            Layout.CYRILLIC -> "ru"
            Layout.LATIN -> "en"
            else -> ""
        }
        val fix = input.finishWord(st.autocorrect && layout != Layout.TRANSLIT, kind)
        if (fix != null) input.applyCorrection(ic, fix)
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
            text = "Наберите текст, выберите язык и нажмите на него"
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(110f).toInt()
            )
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (chunk in TranslationManager.LANGS.chunked(4)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((code, name) in chunk) {
                val b = Button(this).apply {
                    text = name
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * st.fontSize)
                    setTextColor(pal.text)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8f)
                        setColor(pal.keyTop)
                    }
                    val m = dp(2f).toInt()
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
            background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(pal.funcTop)
            }
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
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        if (before.isBlank()) {
            pv.text = "Сначала наберите текст"
            return
        }
        // Кэш: мгновенный результат без сети
        TranslationManager.getCached(this, before.trim(), target)?.let { cached ->
            pv.text = cached
            pv.setTextColor(pal.accent)
            showInsertButton(cached, before.length)
            return
        }
        pv.text = "Перевожу на $targetName…"
        translateThread?.interrupt()
        translateThread = thread(start = true) {
            val result = try {
                TranslationManager.translate(this, before.trim(), target)
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

    private fun showInsertButton(result: String, sourceLen: Int) {
        val panel = translatePanel ?: return
        for (i in panel.childCount - 1 downTo 0) {
            if (panel.getChildAt(i).tag == "insert") panel.removeViewAt(i)
        }
        val btn = Button(this).apply {
            tag = "insert"
            text = "⤵ Вставить перевод"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(pal.accent)
            }
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
            input.onCharCommitted(ic, out, k[0], isLetter = true)
        } else {
            input.onCharCommitted(ic, "", k[0], isLetter = true)
        }
        if (shiftActive && !shiftLocked) shiftActive = false
        refreshLetters()
        refreshSuggestions()
    }

    private fun onSimpleSymbol(k: String) {
        haptic()
        val ic = currentInputConnection ?: return
        ic.commitText(k, 1)
        input.onSymbolCommitted(ic, k)
        refreshSuggestions()
    }

    private fun attachLongPress(tv: TextView, variants: List<String>) {
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
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(pal.keyTop)
                setStroke(dp(1f).toInt(), pal.accent)
            }
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
                    input.lastCommitLen = v.length
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

    private fun refreshLetters() {
        val upper = shiftActive || shiftLocked
        for (tv in letterKeys) {
            val base = tv.tag as String
            tv.text = if (upper && base.length == 1 && base[0].isLetter())
                base.uppercase() else base
        }
        shiftKey?.apply {
            setIcon(this, if (shiftLocked) R.drawable.ic_capslock else R.drawable.ic_shift)
            setIconTint(this, if (upper) pal.accent else pal.text)
        }
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
            background = keyBackground(func)
            val vPad = (dp(11f) * st.keyHeight).toInt()
            setPadding(0, vPad, 0, vPad)
            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.background = pressedBackground(func)
                            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60).start()
                            showBubble(v)
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            v.background = keyBackground(func)
                            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                            hideBubble()
                        }
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

    private fun keyBackground(func: Boolean): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (func) intArrayOf(pal.funcTop, pal.funcBottom)
        else intArrayOf(pal.keyTop, pal.keyBottom)
    ).apply {
        cornerRadius = dp(10f)
        setStroke(dp(1f).toInt(), if (func) pal.funcStroke else pal.keyStroke)
    }

    private fun pressedBackground(func: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(10f)
        setColor(if (func) pal.pressedFunc else pal.pressedKey)
        setStroke(dp(1f).toInt(), if (func) pal.funcStroke else pal.keyStroke)
    }

    private fun makeIconKey(@DrawableRes icon: Int, weight: Float, func: Boolean = true): TextView {
        val tv = makeKey("", weight, func)
        setIcon(tv, icon)
        return tv
    }

    private fun setIcon(tv: TextView, @DrawableRes icon: Int) {
        val d = ContextCompat.getDrawable(this, icon)!!
        d.setTint(pal.text)
        val size = dp(22f).toInt()
        d.setBounds(0, 0, size, size)
        tv.setCompoundDrawables(d, null, null, null)
        tv.compoundDrawablePadding = 0
    }

    private fun setIconTint(tv: TextView, color: Int) {
        tv.compoundDrawables.firstOrNull()?.setTint(color)
    }

    private var bubble: TextView? = null

    private fun showBubble(key: View) {
        hideBubble()
        val label = key.tag as? String ?: return
        val show = when {
            label.isEmpty() -> null
            layout == Layout.TRANSLIT && label.length == 1 && label[0].isLetter() ->
                st.translitMode.translit(label[0], shiftActive || shiftLocked)
                    .ifEmpty { null }
            label.length <= 2 -> label
            else -> null
        } ?: return
        val tv = TextView(this).apply {
            text = show
            gravity = Gravity.CENTER
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * st.fontSize)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(pal.bubbleBg)
            }
            val w = dp(46f).toInt()
            val h = dp(42f).toInt()
            layoutParams = android.view.ViewGroup.LayoutParams(w, h)
        }
        bubble = tv
        val frame = overlay ?: return
        frame.addView(tv)
        val loc = IntArray(2)
        key.getLocationInWindow(loc)
        val parentLoc = IntArray(2)
        frame.getLocationInWindow(parentLoc)
        val x = loc[0] + key.width / 2 - tv.layoutParams.width / 2
        val y = loc[1] - parentLoc[1] - tv.layoutParams.height - dp(4f).toInt()
        tv.x = x.toFloat()
        tv.y = y.toFloat()
        tv.alpha = 0f
        tv.animate().alpha(1f).setDuration(80).start()
    }

    private fun hideBubble() {
        bubble?.let { b ->
            (b.parent as? android.view.ViewGroup)?.removeView(b)
        }
        bubble = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        input.reset()
    }
}
