package com.example

import com.example.data.model.CategoryEntity
import com.example.data.nlp.VoiceTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class VoiceTransactionParserTest {

    private val testUserId = "user_test_123"
    private val categories = listOf(
        CategoryEntity("cat_food", testUserId, "Food & Dining", "restaurant", "#F59E0B", "EXPENSE", true),
        CategoryEntity("cat_transport", testUserId, "Transport", "directions_bus", "#3B82F6", "EXPENSE", true),
        CategoryEntity("cat_bills", testUserId, "Bills & Utilities", "receipt_long", "#EC4899", "EXPENSE", true),
        CategoryEntity("cat_groceries", testUserId, "Groceries", "shopping_cart", "#14B8A6", "EXPENSE", true),
        CategoryEntity("cat_other_exp", testUserId, "Other Expense", "category", "#64748B", "EXPENSE", true),
        CategoryEntity("cat_salary", testUserId, "Salary", "account_balance_wallet", "#10B981", "INCOME", true),
        CategoryEntity("cat_other_inc", testUserId, "Other Income", "savings", "#0EA5E9", "INCOME", true)
    )

    private val todayMillis = System.currentTimeMillis()

    @Test
    fun testDinnerWithFriendsSentence() {
        val sentence = "Dost ke sath dinner par 1500 rupay lag gaye"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(150000L, parsed.amountMinor) // Rs 1,500 = 150,000 paisa
        assertEquals("Food & Dining", parsed.categoryName)
        assertTrue(parsed.categoryConfidence >= 0.8f)
    }

    @Test
    fun testPetrolSentence() {
        val sentence = "Petrol dalwaya 2000 ka"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(200000L, parsed.amountMinor) // Rs 2,000 = 200,000 paisa
        assertEquals("Transport", parsed.categoryName)
    }

    @Test
    fun testElectricityBillSentence() {
        val sentence = "Bijli ka bill 8500 diya"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(850000L, parsed.amountMinor) // Rs 8,500
        assertEquals("Bills & Utilities", parsed.categoryName)
    }

    @Test
    fun testDedhHazarRashanSentence() {
        val sentence = "Dedh hazar ka rashan liya"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(150000L, parsed.amountMinor) // Rs 1,500
        assertEquals("Groceries", parsed.categoryName)
    }

    @Test
    fun testChaiKharchSentence() {
        val sentence = "Chai pe 150 kharch hue"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(15000L, parsed.amountMinor) // Rs 150
        assertEquals("Food & Dining", parsed.categoryName)
    }

    @Test
    fun testSalaryIncomeSentence() {
        val sentence = "Salary 50000 mili"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("INCOME", parsed.type)
        assertEquals(5000000L, parsed.amountMinor) // Rs 50,000
        assertEquals("Salary", parsed.categoryName)
    }

    @Test
    fun testUrduScriptPetrolSentence() {
        val sentence = "دو ہزار کا پیٹرول ڈلوایا"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(200000L, parsed.amountMinor) // Rs 2,000
        assertEquals("Transport", parsed.categoryName)
    }

    @Test
    fun testYesterdayDateSentence() {
        val sentence = "Kal 500 ka petrol dalwaya"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(50000L, parsed.amountMinor) // Rs 500
        assertEquals("Transport", parsed.categoryName)

        val calToday = Calendar.getInstance().apply { timeInMillis = todayMillis }
        val calParsed = Calendar.getInstance().apply { timeInMillis = parsed.occurredOn }

        assertEquals(calToday.get(Calendar.DAY_OF_YEAR) - 1, calParsed.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun testEnglishRickshawSentence() {
        val sentence = "Spent 300 on rickshaw today"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(30000L, parsed.amountMinor) // Rs 300
        assertEquals("Transport", parsed.categoryName)
    }

    @Test
    fun testUrduDigitsParsing() {
        val sentence = "۱۵۰۰ روپے کا پیٹرول"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(150000L, parsed.amountMinor)
        assertEquals("Transport", parsed.categoryName)
    }

    @Test
    fun testShortForm2kParsing() {
        val sentence = "2k for bike petrol"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(200000L, parsed.amountMinor)
        assertEquals("Transport", parsed.categoryName)
    }

    @Test
    fun testSawaSauNumberPhrase() {
        val sentence = "sawa sau ka juice"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(12500L, parsed.amountMinor) // Rs 125 = 12500 paisa
        assertEquals("Food & Dining", parsed.categoryName)
    }

    @Test
    fun testNoAmountFound() {
        val sentence = "Dost ke sath dinner par gaye"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertNull("Amount must be null when no number is mentioned", parsed.amountMinor)
        assertEquals("Food & Dining", parsed.categoryName)
    }

    @Test
    fun testUnknownCategoryFallsBackToOther() {
        val sentence = "Random item 500 ka kharida"
        val parsed = VoiceTransactionParser.parse(sentence, todayMillis, categories)

        assertEquals("EXPENSE", parsed.type)
        assertEquals(50000L, parsed.amountMinor)
        assertNotNull(parsed.categoryName)
        assertTrue("Confidence should be low for unknown categories", parsed.categoryConfidence < 0.5f)
    }
}
