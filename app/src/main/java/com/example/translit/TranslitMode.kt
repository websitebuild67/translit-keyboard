package com.example.translit

enum class TranslitMode {
    COLLOQUIAL, PASSPORT;

    fun translit(ch: Char, upper: Boolean): String {
        val lower = ch.lowercaseChar()
        val out = when (this) {
            COLLOQUIAL -> colloquial[lower]
            PASSPORT -> passport[lower]
        }
        if (out == null) return if (upper) ch.uppercaseChar().toString() else ch.toString()
        return if (upper && out.isNotEmpty()) out[0].uppercaseChar() + out.substring(1) else out
    }

    companion object {
        // Разговорная: привет -> privet, йогурт -> yogurt, щука -> schuka
        val colloquial: Map<Char, String> = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )

        // Паспортная (ICAO): ю->iu, я->ia, х->kh, ц->ts, щ->shch, ъ->ie
        val passport: Map<Char, String> = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "ie", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "iu", 'я' to "ia"
        )
    }
}
