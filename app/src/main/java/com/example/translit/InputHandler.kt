package com.example.translit

import android.content.Context
import android.view.inputmethod.InputConnection

/**
 * Логика ввода: сборка слова, автоисправление, контекст для подсказок,
 * стирание по блокам (щ -> "sch" удаляется целиком).
 */
class InputHandler(private val ctx: Context) {

    var lastCommitLen = 0
    val composing = StringBuilder()
    var composingCommitLen = 0
    val lastWords = mutableListOf<String>()

    fun reset() {
        lastCommitLen = 0
        composing.setLength(0)
        composingCommitLen = 0
        lastWords.clear()
    }

    /** Буква вставлена: out — что реально попало в поле, srcChar — исходная клавиша. */
    fun onCharCommitted(ic: InputConnection, out: String, srcChar: Char, isLetter: Boolean) {
        if (out.isNotEmpty()) {
            lastCommitLen = out.length
            if (isLetter) {
                composing.append(srcChar.lowercaseChar())
                composingCommitLen += out.length
            }
        } else if (isLetter) {
            // ъ/ь в транслите не дают символов, но входят в слово
            composing.append(srcChar.lowercaseChar())
        }
    }

    fun onSymbolCommitted(ic: InputConnection, symbol: String) {
        lastCommitLen = symbol.length
        finishWord(false, "")
    }

    /** Слово завершено (пробел/Enter/символ): автоисправление + контекст. */
    fun finishWord(autocorrectEnabled: Boolean, layoutKind: String): String? {
        var corrected: String? = null
        if (composing.isNotEmpty()) {
            val typed = composing.toString()
            lastWords.add(typed.lowercase())
            while (lastWords.size > 2) lastWords.removeAt(0)
            if (autocorrectEnabled) {
                corrected = try {
                    SuggestionEngine.autocorrect(ctx, layoutKind, typed, null)
                } catch (_: Exception) { null }
            }
        }
        composing.setLength(0)
        composingCommitLen = 0
        lastCommitLen = 0
        return corrected
    }

    /** Применить автоисправление: заменить набранное слово на исправленное. */
    fun applyCorrection(ic: InputConnection, fix: String) {
        if (composingCommitLen > 0) {
            ic.deleteSurroundingText(composingCommitLen, 0)
            ic.commitText(fix, 1)
            if (lastWords.isNotEmpty()) lastWords[lastWords.size - 1] = fix.lowercase()
        }
    }

    fun onBackspace(ic: InputConnection) {
        val del = if (lastCommitLen > 1) lastCommitLen else 1
        ic.deleteSurroundingText(del, 0)
        if (composingCommitLen > 0) {
            composingCommitLen = (composingCommitLen - del).coerceAtLeast(0)
            if (composing.isNotEmpty()) composing.deleteCharAt(composing.length - 1)
        }
        lastCommitLen = 0
    }

    fun onLongBackspace(ic: InputConnection) {
        for (i in 0 until 20) ic.deleteSurroundingText(1, 0)
        lastCommitLen = 0
        composing.setLength(0)
        composingCommitLen = 0
        lastWords.clear()
    }
}
