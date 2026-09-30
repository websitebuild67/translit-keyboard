package com.example.translit

import android.content.Context
import android.provider.UserDictionary
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * Подсказки слов: частотный словарь, контекстные ассоциации (по 1–2 последним
 * словам), предикт по префиксу, UserDictionary, словари-плагины и автоисправление.
 */
object SuggestionEngine {

    private val ru = listOf(
        "я", "ты", "он", "она", "мы", "вы", "они", "это", "что", "как", "не", "да", "нет",
        "и", "а", "но", "в", "на", "с", "по", "к", "у", "из", "за", "от", "до", "для",
        "привет", "пока", "здравствуй", "спасибо", "пожалуйста", "извини", "прости",
        "хорошо", "плохо", "нормально", "дела", "кто", "где", "когда", "почему", "зачем",
        "сколько", "меня", "тебя", "мне", "тебе", "нас", "вас", "их", "мой", "твой",
        "наш", "ваш", "свой", "этот", "тот", "весь", "очень", "много", "мало", "больше",
        "меньше", "лучше", "хуже", "можно", "нельзя", "надо", "нужно", "хочу", "хочешь",
        "люблю", "нравится", "знаю", "думаю", "вижу", "слышу", "понимаю", "помоги",
        "помощь", "дом", "семья", "мама", "папа", "брат", "сестра", "сын", "дочь",
        "друг", "подруга", "девушка", "парень", "человек", "люди", "жизнь", "здоровье",
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
        "вечера", "ночи", "спокойной", "доброй", "добрый", "любимый", "родной",
        "скучаю", "позвони", "напиши", "встретимся", "договорились", "конечно",
        "согласен", "неважно", "круто", "супер", "отлично", "беда", "поживаешь",
        "настроение", "выходные", "приедешь", "созвонимся", "делаешь", "нового",
        "случилось", "видел", "слышал", "знаешь", "помнишь", "забыл", "напомни",
        "позже", "раньше", "утром", "вечером", "ночью", "днем", "дома", "здесь",
        "там", "туда", "сюда", "вместе", "один", "одна", "одни", "сам", "сама",
        "тоже", "еще", "уже", "только", "всего", "всех", "всё", "ничего", "кто-то",
        "что-то", "где-то", "когда-то", "почти", "совсем", "просто", "обычно",
        "особенно", "например", "кстати", "значит", "короче", "ладно", "окей",
        "точно", "верно", "правильно", "ошибся", "опечатка", "исправил"
    )

    private val en = listOf(
        "i", "you", "he", "she", "we", "they", "it", "this", "that", "what", "how",
        "hello", "hi", "goodbye", "bye", "thanks", "thank", "please", "sorry", "yes",
        "no", "good", "bad", "okay", "fine", "great", "nice", "are", "is", "am",
        "where", "when", "why", "who", "which", "these", "those", "there", "here",
        "today", "tomorrow", "yesterday", "now", "later", "always", "never",
        "sometimes", "often", "very", "much", "many", "more", "less", "best",
        "better", "want", "need", "like", "love", "know", "think", "see", "help",
        "can", "cannot", "will", "would", "could", "should", "have", "has", "had",
        "was", "were", "been", "make", "made", "take", "took", "come", "came",
        "going", "went", "work", "working", "home", "house", "family", "friend",
        "friends", "people", "man", "woman", "life", "world", "time", "year",
        "month", "week", "night", "morning", "evening", "money", "name", "word",
        "words", "language", "english", "russian", "question", "answer", "problem",
        "idea", "phone", "computer", "internet", "message", "email", "water",
        "food", "tea", "coffee", "city", "country", "street", "car", "school",
        "book", "movie", "music", "game", "welcome", "sure", "maybe", "really",
        "because", "about", "with", "without", "from", "into", "doing", "up",
        "long", "see", "later", "soon", "talk", "call", "back", "right", "wrong",
        "easy", "hard", "done", "ready", "wait", "waiting"
    )

    private val assocRu: Map<String, List<String>> = mapOf(
        "привет" to listOf("как дела", "что нового", "давно не виделись", "здорово", "приветик"),
        "здравствуй" to listOf("как дела", "что нового", "рад тебя видеть"),
        "как" to listOf("дела", "ты", "поживаешь", "настроение", "прошли выходные", "сам"),
        "дела" to listOf("хорошо", "нормально", "плохо", "а у тебя", "потихоньку", "отлично"),
        "ты" to listOf("как", "где", "когда", "что делаешь", "здесь", "свободен"),
        "пока" to listOf("до встречи", "увидимся", "до завтра", "спокойной ночи", "береги себя"),
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
        "да" to listOf("конечно", "точно", "без проблем", "договорились", "давай"),
        "нет" to listOf("не хочу", "не могу", "в другой раз", "извини", "спасибо"),
        "хорошо" to listOf("договорились", "отлично", "ладно", "по рукам", "что дальше"),
        "люблю" to listOf("тебя", "тоже тебя люблю", "очень скучаю"),
        "скучаю" to listOf("по тебе", "тоже скучаю", "приезжай скорее"),
        "позвони" to listOf("мне", "как доберешься", "позже", "вечером"),
        "напиши" to listOf("мне", "как приедешь", "в телеграм", "когда будешь"),
        "встретимся" to listOf("завтра", "позже", "где и когда", "у метро"),
        "где" to listOf("ты", "ты сейчас", "встретимся", "находишься"),
        "когда" to listOf("ты приедешь", "встретимся", "созвонимся", "будешь"),
        "что" to listOf("делаешь", "нового", "случилось", "будешь делать"),
        "пойдем" to listOf("гулять", "в кино", "кофе выпьем", "погуляем", "покушаем"),
        "кофе" to listOf("будешь", "выпьем", "с молоком", "попьем"),
        "жду" to listOf("тебя", "звонка", "ответа", "сообщения"),
        "до" to listOf("встречи", "завтра", "свидания", "скорого"),
        "как дела" to listOf("хорошо", "нормально", "а у тебя", "все отлично"),
        "как ты" to listOf("поживаешь", "себя чувствуешь", "добираешься"),
        "что нового" to listOf("ничего", "расскажу при встрече", "много всего"),
        "рад тебя" to listOf("видеть", "слышать"),
        "давно не" to listOf("виделись", "созванивались", "писали"),
        "а у" to listOf("тебя", "вас", "нас"),
        "не за" to listOf("что", "что, обращайся"),
        "все нормально" to listOf("не переживай", "забудь", "проехали"),
        "ничего страшного" to listOf("бывает", "забудь", "все ок"),
        "сладких снов" to listOf("до утра", "спокойной ночи")
    )

    private val assocEn: Map<String, List<String>> = mapOf(
        "hello" to listOf("how are you", "hi there", "good to see you", "what's up"),
        "hi" to listOf("how are you", "what's up", "there", "long time no see"),
        "how" to listOf("are you", "about", "much", "was your day", "are you doing"),
        "are" to listOf("you okay", "you coming", "you sure", "we there yet", "you"),
        "you" to listOf("too", "are welcome", "look great", "tell me", "doing"),
        "thanks" to listOf("you're welcome", "no problem", "anytime", "glad to help"),
        "thank" to listOf("you", "you so much", "you very much"),
        "sorry" to listOf("it's okay", "no worries", "my bad", "don't worry"),
        "good" to listOf("morning", "night", "job", "to see you", "evening"),
        "morning" to listOf("good morning", "how did you sleep", "coffee?"),
        "night" to listOf("good night", "sweet dreams", "sleep well"),
        "bye" to listOf("see you", "take care", "talk later", "good night"),
        "see" to listOf("you later", "you soon", "you tomorrow", "what you mean"),
        "yes" to listOf("of course", "sure", "absolutely", "no problem", "please"),
        "no" to listOf("thanks", "way", "I can't", "not today", "problem"),
        "love" to listOf("you", "you too", "this so much", "it"),
        "miss" to listOf("you", "you too", "so much"),
        "call" to listOf("me", "me later", "you back", "me tonight"),
        "meet" to listOf("you later", "at the usual place", "tomorrow", "up"),
        "what" to listOf("are you doing", "happened", "is up", "do you think", "time"),
        "where" to listOf("are you", "are we going", "is it", "to meet"),
        "when" to listOf("are you coming", "do we meet", "will you call", "is it"),
        "okay" to listOf("then", "got it", "no problem", "see you", "cool"),
        "please" to listOf("help me", "let me know", "call me back", "wait"),
        "welcome" to listOf("to our team", "back", "home", "anytime"),
        "how are you" to listOf("fine thanks", "good, and you", "not bad"),
        "long time" to listOf("no see", "we should catch up"),
        "no problem" to listOf("glad to help", "anytime", "you're welcome")
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

    private fun pluginWords(ctx: Context): List<String> {
        val dir = File(ctx.filesDir, "dictionaries")
        if (!dir.isDirectory) return emptyList()
        val out = mutableListOf<String>()
        dir.listFiles { f -> f.extension == "txt" }?.forEach { f ->
            try {
                f.readLines().forEach { line ->
                    val w = line.trim().lowercase()
                    if (w.isNotEmpty()) out.add(w)
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            for (j in 0..b.length) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    /**
     * kind: "translit" (русские слова, отдаём транслитом), "ru", "en".
     * sourcePrefix — набираемое слово; lastWords — до 2 последних слов для контекста.
     */
    fun suggestions(
        ctx: Context,
        kind: String,
        sourcePrefix: String,
        lastWords: List<String>,
        translitFn: ((Char) -> String)?
    ): List<Pair<String, String>> {
        fun conv(s: String): String =
            if (kind == "translit")
                s.map { c -> if (c == ' ') " " else translitFn?.invoke(c) ?: c.toString() }
                    .joinToString("")
            else s

        val p = sourcePrefix.lowercase().trim()
        val extra = pluginWords(ctx)
        val base: List<Pair<String, String>> = when (kind) {
            "en" -> (en + extra + userWords(ctx, "en")).map { it to it }
            "ru" -> (ru + extra + userWords(ctx, "ru")).map { it to it }
            else -> (ru + extra + userWords(ctx, "ru")).map { it to conv(it) }
        }
        val assoc = if (kind == "en") assocEn else assocRu

        val out = LinkedHashSet<Pair<String, String>>()

        if (lastWords.size >= 2) {
            val two = lastWords.takeLast(2).joinToString(" ")
            assoc[two]?.forEach { out.add(it to conv(it)) }
        }
        if (out.size < 3) {
            lastWords.lastOrNull()?.lowercase()?.let { one ->
                assoc[one]?.forEach { out.add(it to conv(it)) }
            }
        }

        if (p.isNotEmpty()) {
            base.asSequence()
                .filter { it.first.startsWith(p) && it.first != p }
                .forEach { out.add(it) }
            base.firstOrNull { it.first == p }?.let { out.add(it) }
        } else if (out.isEmpty()) {
            base.take(3).forEach { out.add(it) }
        }

        return out.filter { it.second.isNotEmpty() }.take(4)
    }

    /** Автоисправление: близкий кандидат по Левенштейну, либо null. */
    fun autocorrect(
        ctx: Context,
        kind: String,
        typed: String,
        translitFn: ((Char) -> String)?
    ): String? {
        if (typed.length < 3) return null
        val t = typed.lowercase()
        val words: List<String> = when (kind) {
            "en" -> en + pluginWords(ctx) + userWords(ctx, "en")
            else -> ru + pluginWords(ctx) + userWords(ctx, "ru")
        }
        if (words.contains(t)) return null

        val limit = if (t.length <= 5) 1 else 2
        var best: String? = null
        var bestDist = limit + 1
        for (w in words) {
            if (abs(w.length - t.length) > limit) continue
            val d = editDistance(t, w, bestDist - 1)
            if (d < bestDist) {
                bestDist = d
                best = w
                if (d == 1 && w.length == t.length) break
            }
        }
        if (best == null) return null

        fun conv(s: String): String =
            if (kind == "translit")
                s.map { c -> translitFn?.invoke(c) ?: c.toString() }.joinToString("")
            else s

        val res = conv(best)
        return if (typed[0].isUpperCase() && res.isNotEmpty())
            res[0].uppercaseChar() + res.substring(1) else res
    }
}
