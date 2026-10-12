package com.example.data.nlp

import com.example.data.model.CategoryEntity
import com.example.util.DateUtils
import com.example.util.PaisaHelper
import java.util.Calendar
import java.util.Locale

data class ParsedVoiceEntry(
    val type: String,                   // "EXPENSE" or "INCOME"
    val amountMinor: Long?,             // in paisa (via PaisaHelper), null if not found
    val categoryName: String?,          // matched to one of the user's existing categories
    val categoryConfidence: Float,      // 0.0 to 1.0
    val occurredOn: Long,               // timestamp in millis (Asia/Karachi); default today
    val note: String                     // original sentence
)

object VoiceTransactionParser {

    /**
     * Main parse entry point.
     * Rule-based NLP parser working 100% offline.
     */
    fun parse(
        text: String,
        todayMillis: Long = System.currentTimeMillis(),
        categories: List<CategoryEntity> = emptyList()
    ): ParsedVoiceEntry {
        val originalText = text.trim()
        if (originalText.isEmpty()) {
            val defaultCategory = categories.firstOrNull { it.type == "EXPENSE" }?.name
            return ParsedVoiceEntry(
                type = "EXPENSE",
                amountMinor = null,
                categoryName = defaultCategory,
                categoryConfidence = 0.0f,
                occurredOn = todayMillis,
                note = ""
            )
        }

        val normalized = normalize(originalText)

        // 1. Detect Type
        val type = detectType(normalized)

        // 2. Extract Amount
        val amountMinor = extractAmountMinor(normalized)

        // 3. Match Category
        val (categoryName, confidence) = matchCategory(normalized, type, categories)

        // 4. Detect Date
        val dateMillis = detectDate(normalized, todayMillis)

        return ParsedVoiceEntry(
            type = type,
            amountMinor = amountMinor,
            categoryName = categoryName,
            categoryConfidence = confidence,
            occurredOn = dateMillis,
            note = originalText
        )
    }

    private fun normalize(text: String): String {
        var s = text.trim().lowercase(Locale.ROOT)

        // Convert Urdu & Arabic-Indic digits to ASCII 0-9
        val urduDigits = mapOf(
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
        )
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(urduDigits[ch] ?: ch)
        }
        s = sb.toString()

        // Replace commas inside numbers e.g. "1,500" -> "1500"
        s = s.replace(Regex("(\\d+),(\\d+)"), "$1$2")

        return s
    }

    private fun detectType(text: String): String {
        val incomeCues = listOf(
            "salary", "tankhwah", "tankwah", "mili", "mila", "aaya", "aaye",
            "kamaye", "kamai", "bonus", "received", "earned", "income",
            "تنخواہ", "ملی", "آئے", "کمائے", "گیفٹ", "allowance", "freelance"
        )
        val lower = text.lowercase(Locale.ROOT)

        for (cue in incomeCues) {
            if (lower.contains(cue)) {
                return "INCOME"
            }
        }
        return "EXPENSE"
    }

    private fun extractAmountMinor(text: String): Long? {
        val pkrDouble = extractAmountPkr(text) ?: return null
        if (pkrDouble <= 0.0) return null
        return PaisaHelper.pkrDoubleToPaisa(pkrDouble)
    }

    private fun extractAmountPkr(text: String): Double? {
        // A) Urdu / Roman Urdu compound number phrases
        val compoundPhrases = listOf(
            Pair("dedh hazar", 1500.0),
            Pair("دیدھ ہزار", 1500.0),
            Pair("دیڑھ ہزار", 1500.0),
            Pair("ڈیڑھ ہزار", 1500.0),
            Pair("dhai hazar", 2500.0),
            Pair("ڈھائی ہزار", 2500.0),
            Pair("dedh sau", 150.0),
            Pair("dhai sau", 250.0),
            Pair("sawa sau", 125.0),
            Pair("sawa hazar", 1250.0),
            Pair("saadhe teen hazar", 3500.0),
            Pair("ساڑھے تین ہزار", 3500.0),
            Pair("saadhe char hazar", 4500.0),
            Pair("saadhe paanch hazar", 5500.0),
            Pair("pandrah sau", 1500.0),
            Pair("پندرہ سو", 1500.0),
            Pair("fifteen hundred", 1500.0),
            Pair("two thousand", 2000.0),
            Pair("five hundred", 500.0)
        )

        for ((phrase, valPkr) in compoundPhrases) {
            if (text.contains(phrase)) {
                return valPkr
            }
        }

        // B) Short forms like 2k, 1.5k, 1.5 lakh, 1 lac, 2 lakh
        val kMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*k\\b").find(text)
        if (kMatch != null) {
            val num = kMatch.groupValues[1].toDoubleOrNull()
            if (num != null) return num * 1000.0
        }

        val lakhMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:lakh|lac|لاکھ)\\b").find(text)
        if (lakhMatch != null) {
            val num = lakhMatch.groupValues[1].toDoubleOrNull()
            if (num != null) return num * 100000.0
        }

        // C) Roman Urdu multi-word number matcher (e.g., "do hazar", "paanch sau", "pachas hazar")
        val romanWordAmount = parseRomanUrduNumberWord(text)
        if (romanWordAmount != null && romanWordAmount > 0) {
            return romanWordAmount
        }

        // D) Explicit Digits (e.g., "1500", "Rs 1500", "1500 rupay", "50000 mili")
        val digitMatches = Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(text).toList()
        for (m in digitMatches) {
            val rawNum = m.value
            val doubleVal = rawNum.toDoubleOrNull() ?: continue

            val idx = m.range.first
            val preceding = if (idx > 10) text.substring(idx - 10, idx) else text.substring(0, idx)
            val following = if (idx + rawNum.length + 10 < text.length) {
                text.substring(idx + rawNum.length, idx + rawNum.length + 10)
            } else {
                text.substring((idx + rawNum.length).coerceAtMost(text.length))
            }

            // Ignore time indicators like "3 baje" or friend count like "2 dost"
            if (following.contains("baje") || following.contains("dost") || preceding.contains("dost")) {
                continue
            }

            return doubleVal
        }

        return null
    }

    private fun parseRomanUrduNumberWord(text: String): Double? {
        val wordValues = mapOf(
            "ek" to 1.0, "یک" to 1.0, "ایک" to 1.0, "one" to 1.0,
            "do" to 2.0, "دو" to 2.0, "two" to 2.0,
            "teen" to 3.0, "تین" to 3.0, "three" to 3.0,
            "char" to 4.0, "چار" to 4.0, "four" to 4.0,
            "paanch" to 5.0, "پانچ" to 5.0, "five" to 5.0,
            "chhe" to 6.0, "چھ" to 6.0, "six" to 6.0,
            "saat" to 7.0, "سات" to 7.0, "seven" to 7.0,
            "aath" to 8.0, "آٹھ" to 8.0, "eight" to 8.0,
            "nau" to 9.0, "نو" to 9.0, "nine" to 9.0,
            "dus" to 10.0, "دس" to 10.0, "ten" to 10.0,
            "gyarah" to 11.0, "barah" to 12.0, "terah" to 13.0, "chaudah" to 14.0,
            "pandrah" to 15.0, "پندرہ" to 15.0, "soleh" to 16.0, "satrah" to 17.0,
            "atharah" to 18.0, "unnees" to 19.0,
            "bees" to 20.0, "بیست" to 20.0, "tees" to 30.0, "تیس" to 30.0,
            "chalis" to 40.0, "چالیس" to 40.0, "pachas" to 50.0, "پچاس" to 50.0,
            "saath" to 60.0, "ساٹھ" to 60.0, "sattar" to 70.0, "ستر" to 70.0,
            "assi" to 80.0, "اسی" to 80.0, "nabbe" to 90.0, "نوے" to 90.0
        )

        val multipliers = mapOf(
            "sau" to 100.0, "سو" to 100.0, "hundred" to 100.0,
            "hazar" to 1000.0, "ہزار" to 1000.0, "thousand" to 1000.0,
            "lakh" to 100000.0, "لاکھ" to 100000.0, "lac" to 100000.0
        )

        val words = text.split(Regex("\\s+"))
        for (i in words.indices) {
            val w = words[i]
            val valBase = wordValues[w]
            if (valBase != null && i + 1 < words.size) {
                val nextW = words[i + 1]
                val mult = multipliers[nextW]
                if (mult != null) {
                    return valBase * mult
                }
            }
        }

        return null
    }

    private fun matchCategory(
        text: String,
        type: String,
        categories: List<CategoryEntity>
    ): Pair<String?, Float> {
        val keywordCategoryMap = mapOf(
            "Food & Dining" to listOf(
                "dinner", "lunch", "breakfast", "khana", "khaana", "nashta", "chai", "tea",
                "biryani", "pizza", "burger", "restaurant", "hotel", "cafe", "juice",
                "coffee", " खाना", "چائے", "ناشتہ", "کھانا"
            ),
            "Transport" to listOf(
                "petrol", "fuel", "diesel", "gari", "car", "bike", "rickshaw", "rikshaw",
                "uber", "careem", "indrive", "yango", "bus", "taxi", "fare", "parking",
                "tyre", "mechanic", "پیٹرول", "رکشہ", "کرایہ"
            ),
            "Bills & Utilities" to listOf(
                "bijli", "electricity", "gas", "pani", "water bill", "internet", "wifi",
                "ptcl", "mobile load", "recharge", "bill", "بجلی", "گیس", "بل"
            ),
            "Groceries" to listOf(
                "rashan", "raashan", "sabzi", "sabziyan", "doodh", "milk", "fruit", "fruits",
                "grocery", "groceries", "atta", "flour", "chawal", "rice", "mutton",
                "chicken", "gosht", "daal", "supermarket", "mart", "راشن", "سبزی", "دودھ"
            ),
            "Healthcare" to listOf(
                "dawai", "dawa", "medicine", "doctor", "hospital", "clinic", "test",
                "pharmacy", "medical", "fever", "دوائی", "ڈاکٹر", "ہسپتال"
            ),
            "Shopping" to listOf(
                "kapray", "kapre", "clothes", "shopping", "shoes", "joota", "jootay",
                "mall", "dress", "shirt", "pant", "کپڑے"
            ),
            "Education" to listOf(
                "fees", "fee", "kitab", "kitaben", "books", "book", "school", "college",
                "university", "tuition", "academy", "stationery", "فیس"
            ),
            "Entertainment" to listOf(
                "movie", "cinema", "game", "gaming", "netflix", "outing", "picnic"
            ),
            "Salary" to listOf(
                "salary", "tankhwah", "paycheck", "monthly pay", "تنخواہ"
            ),
            "Freelance" to listOf(
                "freelance", "project payment", "client payment", "upwork", "fiverr"
            ),
            "Gift" to listOf(
                "gift", "eidi", "present", "گیفٹ"
            ),
            "Allowance" to listOf(
                "pocket money", "allowance"
            )
        )

        val filteredCategories = categories.filter { it.type == type }

        for ((targetCatName, keywords) in keywordCategoryMap) {
            for (kw in keywords) {
                if (text.contains(kw)) {
                    val matchingCategory = filteredCategories.find {
                        it.name.contains(targetCatName, ignoreCase = true) ||
                                targetCatName.contains(it.name, ignoreCase = true)
                    }
                    if (matchingCategory != null) {
                        return Pair(matchingCategory.name, 0.95f)
                    }
                }
            }
        }

        // Fallback: Pick default/other category for this type
        val fallbackCategory = filteredCategories.find {
            it.name.contains("Other", ignoreCase = true) || it.is_default
        } ?: filteredCategories.firstOrNull()

        return Pair(fallbackCategory?.name, 0.3f)
    }

    private fun detectDate(text: String, todayMillis: Long): Long {
        val cal = Calendar.getInstance(DateUtils.KARACHI_TIMEZONE).apply {
            timeInMillis = todayMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val lower = text.lowercase(Locale.ROOT)

        if (lower.contains("kal") || lower.contains("yesterday") || lower.contains("گذشتہ کل")) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return cal.timeInMillis
        }

        if (lower.contains("parso") || lower.contains("day before yesterday")) {
            cal.add(Calendar.DAY_OF_YEAR, -2)
            return cal.timeInMillis
        }

        return cal.timeInMillis
    }
}
