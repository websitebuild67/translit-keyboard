package com.example.translit

import android.content.Context
import android.provider.UserDictionary

object Dictionary {

    private val ru = listOf(
        "привет", "пока", "здравствуй", "спасибо", "пожалуйста", "извини", "прости",
        "да", "нет", "хорошо", "плохо", "нормально", "как", "дела", "что", "кто", "где",
        "когда", "почему", "зачем", "сколько", "я", "ты", "он", "она", "мы", "вы", "они",
        "меня", "тебя", "мне", "тебе", "нас", "вас", "их", "мой", "твой", "наш", "ваш",
        "свой", "этот", "тот", "весь", "очень", "много", "мало", "больше", "меньше",
        "лучше", "хуже", "можно", "нельзя", "надо", "нужно", "хочу", "хочешь", "люблю",
        "нравится", "знаю", "думаю", "вижу", "слышу", "понимаю", "помоги", "помощь",
        "дом", "семья", "мама", "папа", "брат", "сестра", "сын", "дочь", "друг", "подруга",
        "девушка", "парень", "человек", "люди", "жизнь", "смерть", "здоровье", "болезнь",
        "врач", "больница", "аптека", "магазин", "рынок", "деньги", "цена", "рубль",
        "доллар", "евро", "карта", "банк", "счет", "работа", "школа", "университет",
        "книга", "фильм", "музыка", "песня", "игра", "телефон", "компьютер", "интернет",
        "сообщение", "письмо", "почта", "новость", "сайт", "страница", "пароль", "имя",
        "фамилия", "номер", "адрес", "город", "страна", "улица", "машина", "автобус",
        "метро", "поезд", "самолет", "дорога", "время", "год", "месяц", "неделя", "день",
        "ночь", "утро", "вечер", "сегодня", "завтра", "вчера", "сейчас", "потом",
        "всегда", "никогда", "часто", "редко", "иногда", "еда", "хлеб", "вода", "чай",
        "кофе", "молоко", "сок", "суп", "мясо", "рыба", "сыр", "яйцо", "сахар", "соль",
        "вкусно", "готов", "идти", "ехать", "прийти", "уйти", "делать", "сделать",
        "писать", "написать", "читать", "говорить", "сказать", "спросить", "ответить",
        "жить", "работать", "учиться", "играть", "смотреть", "слушать", "спать", "есть",
        "пить", "гулять", "встретиться", "позвонить", "ждал", "ждем", "встреча", "дело",
        "слово", "язык", "русский", "английский", "перевод", "вопрос", "ответ", "правда",
        "смысл", "идея", "проблема", "решение", "сразу", "быстро", "медленно", "тихо",
        "громко", "весело", "грустно", "смешно", "красиво", "интересно", "важно",
        "главное", "первое", "второе", "последнее", "следующее", "утра", "дня",
        "вечера", "ночи", "спокойной", "доброй", "добрый", "любимый", "родной", "новое",
        "скучаю", "позвони", "напиши", "встретимся", "договорились", "конечно",
        "согласен", "неважно", "пофиг", "круто", "супер", "отлично", "ужас", "беда"
    )

    private val en = listOf(
        "hello", "hi", "goodbye", "bye", "thanks", "thank", "please", "sorry", "yes",
        "no", "good", "bad", "okay", "fine", "great", "nice", "how", "are", "you",
        "what", "where", "when", "why", "who", "which", "this", "that", "these",
        "those", "there", "here", "today", "tomorrow", "yesterday", "now", "later",
        "always", "never", "sometimes", "often", "very", "much", "many", "more",
        "less", "best", "better", "want", "need", "like", "love", "know", "think",
        "see", "help", "can", "cannot", "will", "would", "could", "should", "have",
        "has", "had", "was", "were", "been", "make", "made", "take", "took", "come",
        "came", "going", "went", "work", "working", "home", "house", "family",
        "friend", "friends", "people", "man", "woman", "life", "world", "time",
        "year", "month", "week", "night", "morning", "evening", "money", "name",
        "word", "words", "language", "english", "russian", "question", "answer",
        "problem", "idea", "phone", "computer", "internet", "message", "email",
        "water", "food", "tea", "coffee", "city", "country", "street", "car",
        "school", "book", "movie", "music", "game", "welcome", "sure",
        "maybe", "really", "because", "about", "with", "without", "from", "into"
    )

    // Ассоциации: слово -> подходящие по смыслу продолжения
    private val assocRu: Map<String, List<String>> = mapOf(
        "привет" to listOf("как дела", "что нового", "давно не виделись", "здорово", "приветик"),
        "здорово" to listOf("как сам", "что нового", "давно не виделись"),
        "как" to listOf("дела", "ты", "поживаешь", "настроение", "прошли выходные"),
        "дела" to listOf("хорошо", "нормально", "плохо", "а у тебя", "потихоньку"),
        "ты" to listOf("как", "где", "когда", "что делаешь"),
        "пока" to listOf("до встречи", "увидимся", "до завтра", "спокойной ночи"),
        "спокойной" to listOf("ночи", "ночи, сладких снов"),
        "ночи" to listOf("сладких снов", "до утра"),
        "доброе" to listOf("утро", "утро, как спалось"),
        "утро" to listOf("доброе", "как спалось", "кофе будешь"),
        "добрый" to listOf("день", "вечер"),
        "день" to listOf("добрый", "как прошел", "гиблый"),
        "вечер" to listOf("добрый", "чем займемся", "в кино"),
        "спасибо" to listOf("пожалуйста", "не за что", "обращайся", "всегда пожалуйста"),
        "пожалуйста" to listOf("не за что", "обращайся"),
        "извини" to listOf("все нормально", "ничего страшного", "проехали", "не парься"),
        "прости" to listOf("все нормально", "ничего страшного", "я не обижаюсь"),
        "да" to listOf("конечно", "точно", "без проблем", "договорились"),
        "нет" to listOf("не хочу", "не могу", "в другой раз", "извини"),
        "хорошо" to listOf("договорились", "отлично", "ладно", "по рукам"),
        "люблю" to listOf("тебя", "тоже тебя люблю", "очень скучаю"),
        "скучаю" to listOf("по тебе", "тоже скучаю", "приезжай скорее"),
        "позвони" to listOf("мне", "как доберешься", "позже", "вечером"),
        "напиши" to listOf("мне", "как приедешь", "в телеграм"),
        "встретимся" to listOf("завтра", "позже", "где и когда", "у метро"),
        "где" to listOf("ты", "ты сейчас", "встретимся"),
        "когда" to listOf("ты приедешь", "встретимся", "созвонимся"),
        "что" to listOf("делаешь", "нового", "случилось"),
        "кушать" to listOf("хочешь", "будешь", "пойдем поедим"),
        "пойдем" to listOf("гулять", "в кино", "кофе выпьем", "погуляем"),
        "кофе" to listOf("будешь", "выпьем", "с молоком"),
        "спокойно" to listOf("не переживай", "все будет хорошо"),
        "помоги" to listOf("мне", "пожалуйста", "с делом"),
        "жду" to listOf("тебя", "звонка", "ответа"),
        "до" to listOf("встречи", "завтра", "свидания")
    )

    private val assocEn: Map<String, List<String>> = mapOf(
        "hello" to listOf("how are you", "hi there", "good to see you", "what's up"),
        "hi" to listOf("how are you", "what's up", "there", "long time no see"),
        "how" to listOf("are you", "about", "much", "was your day"),
        "are" to listOf("you okay", "you coming", "you sure", "we there yet"),
        "you" to listOf("too", "are welcome", "look great", "tell me"),
        "thanks" to listOf("you're welcome", "no problem", "anytime", "glad to help"),
        "thank" to listOf("you", "you so much", "you very much"),
        "sorry" to listOf("it's okay", "no worries", "my bad", "don't worry"),
        "good" to listOf("morning", "night", "job", "to see you"),
        "morning" to listOf("good morning", "how did you sleep", "coffee?"),
        "night" to listOf("good night", "sweet dreams", "sleep well"),
        "bye" to listOf("see you", "take care", "talk later", "good night"),
        "see" to listOf("you later", "you soon", "you tomorrow"),
        "yes" to listOf("of course", "sure", "absolutely", "no problem"),
        "no" to listOf("thanks", "way", "I can't", "not today"),
        "love" to listOf("you", "you too", "this so much"),
        "miss" to listOf("you", "you too", "so much"),
        "call" to listOf("me", "me later", "you back"),
        "meet" to listOf("you later", "at the usual place", "tomorrow"),
        "what" to listOf("are you doing", "happened", "is up", "do you think"),
        "where" to listOf("are you", "are we going", "is it"),
        "when" to listOf("are you coming", "do we meet", "will you call"),
        "okay" to listOf("then", "got it", "no problem", "see you"),
        "please" to listOf("help me", "let me know", "call me back"),
        "welcome" to listOf("to our team", "back", "home")
    )

    private var userCache: Pair<Long, List<Pair<String, String>>>? = null

    private fun userWords(ctx: Context, lang: String): List<String> {
        val now = System.currentTimeMillis()
        val cached = userCache
        val all = if (cached != null && now - cached.first < 30_000) cached.second else {
            val list = mutableListOf<Pair<String, String>>()
            try {
                ctx.contentResolver.query(
                    UserDictionary.Words.CONTENT_URI,
                    arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.LOCALE),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val w = c.getString(0) ?: continue
                        list.add(w to (c.getString(1) ?: ""))
                    }
                }
            } catch (_: Exception) {
            }
            userCache = now to list
            list
        }
        return all.filter { it.second.isEmpty() || it.second.startsWith(lang) }
            .map { it.first.lowercase() }
    }

    /**
     * kind: "translit" (русские слова, отдаём транслитом), "ru", "en".
     * sourcePrefix — набираемое слово на исходном языке (в транслите — кириллицей).
     * lastWord — последнее завершённое слово (для ассоциаций).
     * Возвращает пары: исходная фраза -> строка для вставки.
     */
    fun suggestions(
        ctx: Context,
        kind: String,
        sourcePrefix: String,
        lastWord: String?,
        translitFn: ((Char) -> String)?
    ): List<Pair<String, String>> {
        fun conv(s: String): String =
            if (kind == "translit")
                s.map { c -> if (c == ' ') " " else translitFn?.invoke(c) ?: c.toString() }
                    .joinToString("")
            else s

        val p = sourcePrefix.lowercase().trim()

        val base: List<Pair<String, String>> = when (kind) {
            "en" -> (en + userWords(ctx, "en")).map { it to it }
            "ru" -> (ru + userWords(ctx, "ru")).map { it to it }
            else -> (ru + userWords(ctx, "ru")).map { it to conv(it) }
        }

        val assoc = when (kind) {
            "en" -> assocEn
            else -> assocRu
        }

        val out = LinkedHashSet<Pair<String, String>>()

        // 1) ассоциации: для набранного целиком слова или для предыдущего слова
        val assocKey = if (p.isNotEmpty()) p else lastWord?.lowercase()
        if (assocKey != null) {
            assoc[assocKey]?.forEach { phrase -> out.add(phrase to conv(phrase)) }
        }

        // 2) обычные продолжения по префиксу
        if (p.isNotEmpty()) {
            base.asSequence()
                .filter { it.first.startsWith(p) && it.first != p }
                .sortedWith(compareBy({ it.second.length }, { it.first.length }))
                .forEach { out.add(it) }
            // точное совпадение тоже полезно (можно вставить слово как есть)
            base.firstOrNull { it.first == p }?.let { out.add(it) }
        } else if (out.isEmpty()) {
            // 3) ничего не набрано — самые частые слова
            base.take(3).forEach { out.add(it) }
        }

        return out.filter { it.second.isNotEmpty() }.take(4)
    }
}
