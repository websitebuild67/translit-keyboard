package com.example.translit

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.view.inputmethod.InputMethodManager

class MainActivity : Activity() {

    private lateinit var accentRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20f).toInt()
            setPadding(p, p, p, p)
        }
        scroll.addView(content)

        content.addView(header("Translit Keyboard"))
        content.addView(note("Русская раскладка, печатающая латиницей: «привет» → «privet». " +
            "Переключение раскладок — клавиша 🌍 на клавиатуре."))

        // --- Активация
        content.addView(section("Активация"))
        content.addView(button("1. Включить клавиатуру в настройках") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(button("2. Выбрать Translit Keyboard") {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        // --- Раскладка по умолчанию
        content.addView(section("Раскладка по умолчанию"))
        val layoutGroup = RadioGroup(this)
        for ((name, label) in listOf(
            "TRANSLIT" to "Транслит (привет → privet)",
            "CYRILLIC" to "ЙЦУКЕН (обычная русская)",
            "LATIN" to "QWERTY (английская)"
        )) {
            layoutGroup.addView(RadioButton(this).apply {
                text = label
                id = View.generateViewId()
                tag = name
                setTextColor(Color.WHITE)
            })
        }
        val curLayout = KbSettings.get(this).layoutName
        for (i in 0 until layoutGroup.childCount) {
            val rb = layoutGroup.getChildAt(i) as RadioButton
            if (rb.tag == curLayout) layoutGroup.check(rb.id)
        }
        layoutGroup.setOnCheckedChangeListener { _, checkedId ->
            val rb = layoutGroup.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            KbSettings.update(this) { it.copy(layoutName = rb.tag as String) }
        }
        content.addView(layoutGroup)

        // --- Транслит
        content.addView(section("Способ транслитерации"))
        val modeGroup = RadioGroup(this)
        val colloquial = RadioButton(this).apply {
            text = "Разговорная (й→y, ц→c, х→h, щ→sch)"
            id = View.generateViewId(); setTextColor(Color.WHITE)
        }
        val passport = RadioButton(this).apply {
            text = "Паспортная ICAO (й→i, ц→ts, х→kh, щ→shch)"
            id = View.generateViewId(); setTextColor(Color.WHITE)
        }
        modeGroup.addView(colloquial)
        modeGroup.addView(passport)
        modeGroup.check(if (KbSettings.get(this).translitMode == TranslitMode.COLLOQUIAL)
            colloquial.id else passport.id)
        val preview = note("")
        fun updatePreview() {
            val mode = KbSettings.get(this).translitMode
            val sample = "привет щука юлия"
            val out = sample.map { if (it == ' ') ' ' else mode.translit(it, false) }.joinToString("")
            preview.text = "$sample → $out"
        }
        updatePreview()
        colloquial.setOnClickListener {
            KbSettings.update(this) { it.copy(translitMode = TranslitMode.COLLOQUIAL) }
            updatePreview()
        }
        passport.setOnClickListener {
            KbSettings.update(this) { it.copy(translitMode = TranslitMode.PASSPORT) }
            updatePreview()
        }
        content.addView(modeGroup)
        content.addView(preview)

        // --- Тема
        content.addView(section("Тема"))
        val darkSwitch = switchRow("Тёмная тема", KbSettings.get(this).dark) { checked ->
            KbSettings.update(this) { it.copy(dark = checked) }
            recreate()
        }
        content.addView(darkSwitch)

        content.addView(note("Цвет акцента"))
        accentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(8f).toInt(), 0, dp(8f).toInt())
            layoutParams = lp
        }
        content.addView(accentRow)
        renderAccents()

        // --- Размеры
        content.addView(section("Размеры"))
        content.addView(slider("Высота клавиш", 0.8f, 1.3f, KbSettings.get(this).keyHeight) { v ->
            KbSettings.update(this) { it.copy(keyHeight = v) }
        })
        content.addView(slider("Размер шрифта", 0.8f, 1.4f, KbSettings.get(this).fontSize) { v ->
            KbSettings.update(this) { it.copy(fontSize = v) }
        })

        // --- Отклик
        content.addView(section("Отклик клавиш"))
        content.addView(switchRow("Вибрация", KbSettings.get(this).vibrate) { checked ->
            KbSettings.update(this) { it.copy(vibrate = checked) }
        })
        content.addView(switchRow("Звук нажатия", KbSettings.get(this).sound) { checked ->
            KbSettings.update(this) { it.copy(sound = checked) }
        })

        scroll.setBackgroundColor(if (KbSettings.get(this).dark) 0xFF14141A.toInt() else 0xFFF2F3F7.toInt())
        setContentView(scroll)
    }

    private fun renderAccents() {
        accentRow.removeAllViews()
        val current = KbSettings.get(this).accent
        for (c in KbSettings.ACCENTS) {
            val v = View(this).apply {
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    if (c == current) setStroke(dp(3f).toInt(), Color.WHITE)
                    else setStroke(dp(1f).toInt(), 0x44FFFFFF)
                }
                background = bg
                val m = dp(6f).toInt()
                layoutParams = LinearLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt())
                    .apply { setMargins(m, m, m, m) }
                setOnClickListener {
                    KbSettings.update(this@MainActivity) { it.copy(accent = c) }
                    renderAccents()
                }
            }
            accentRow.addView(v)
        }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setTextColor(if (KbSettings.get(context).dark) Color.WHITE else Color.BLACK)
        setTypeface(null, android.graphics.Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(4f).toInt()
        layoutParams = lp
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(KbSettings.get(context).accent)
        setTypeface(null, android.graphics.Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(22f).toInt()
        lp.bottomMargin = dp(6f).toInt()
        layoutParams = lp
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(if (KbSettings.get(context).dark) 0xFF9A9AA4.toInt() else 0xFF55555F.toInt())
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(6f).toInt()
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun switchRow(text: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(4f).toInt()
            layoutParams = lp
        }
        val label = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(if (KbSettings.get(context).dark) Color.WHITE else Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(label)
        row.addView(sw)
        return row
    }

    private fun slider(
        text: String, min: Float, max: Float, initial: Float, onChange: (Float) -> Unit
    ): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10f).toInt()
            layoutParams = lp
        }
        val label = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(if (KbSettings.get(context).dark) Color.WHITE else Color.BLACK)
        }
        fun fmt(v: Float) = String.format("%.0f%%", v * 100)
        label.text = "$text — ${fmt(initial)}"
        val bar = SeekBar(this).apply {
            this.max = 100
            progress = (((initial - min) / (max - min)) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = min + (max - min) * p / 100f
                    label.text = "$text — ${fmt(v)}"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        wrap.addView(label)
        wrap.addView(bar)
        return wrap
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
