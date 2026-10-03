package com.example

import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionWithCategory
import com.example.util.CsvExporter
import com.example.util.DateUtils
import com.example.util.PaisaHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPaisaConversionAccuracy() {
        // Test basic integer paisa conversions
        val paisa = 250000L // 2,500 PKR
        val pkrDouble = PaisaHelper.paisaToPkrDouble(paisa)
        assertEquals(2500.0, pkrDouble, 0.001)

        val convertedBack = PaisaHelper.pkrDoubleToPaisa(2500.0)
        assertEquals(paisa, convertedBack)

        // Fractional cents / paisa handling without floating point rounding error
        val fractionalPaisa = PaisaHelper.pkrStringToPaisa("1450.75")
        assertEquals(145075L, fractionalPaisa)
        assertEquals("1450.75", PaisaHelper.paisaToEditableString(fractionalPaisa))

        val integerPaisa = PaisaHelper.pkrStringToPaisa("5000")
        assertEquals(500000L, integerPaisa)
        assertEquals("5000", PaisaHelper.paisaToEditableString(integerPaisa))
    }

    @Test
    fun testPkrFormatting() {
        // Test formatted string representation
        val amount = 6500000L // 65,000 PKR
        val formatted = PaisaHelper.formatPkr(amount, includeCurrencyCode = true, currencyCode = "PKR")
        assertEquals("PKR 65,000", formatted)

        val negativeAmount = -120000L
        val formattedNegative = PaisaHelper.formatPkr(negativeAmount, includeCurrencyCode = true, currencyCode = "PKR")
        assertEquals("-PKR 1,200", formattedNegative)
    }

    @Test
    fun testBudgetThresholdPercentages() {
        val limitMinor = 5000000L // 50,000 PKR limit
        val spent79 = 3950000L // 39,500 PKR = 79%
        val spent80 = 4000000L // 40,000 PKR = 80%
        val spent100 = 5000000L // 50,000 PKR = 100%
        val spent110 = 5500000L // 55,000 PKR = 110%

        val pct79 = PaisaHelper.calculateBudgetPercentage(spent79, limitMinor)
        val pct80 = PaisaHelper.calculateBudgetPercentage(spent80, limitMinor)
        val pct100 = PaisaHelper.calculateBudgetPercentage(spent100, limitMinor)
        val pct110 = PaisaHelper.calculateBudgetPercentage(spent110, limitMinor)

        assertEquals(79f, pct79, 0.1f)
        assertEquals(80f, pct80, 0.1f)
        assertEquals(100f, pct100, 0.1f)
        assertEquals(110f, pct110, 0.1f)

        assertFalse(pct79 >= 80f)
        assertTrue(pct80 >= 80f)
        assertTrue(pct100 >= 100f)
        assertTrue(pct110 >= 100f)
    }

    @Test
    fun testMonthBoundariesInKarachiTimezone() {
        val (start, end) = DateUtils.getMonthRange("2026-10")
        assertTrue("Start timestamp should be less than end timestamp", start < end)
        assertTrue("Month duration should be greater than 27 days", (end - start) > 27L * 24 * 60 * 60 * 1000)

        val monthDisplay = DateUtils.formatMonthString("2026-10")
        assertTrue(monthDisplay.contains("October") && monthDisplay.contains("2026"))
    }

    @Test
    fun testCsvExportFormat() {
        val sampleTx = TransactionWithCategory(
            transaction = TransactionEntity(
                id = "tx-test-1",
                user_id = "user_test",
                category_id = "cat_test",
                type = "EXPENSE",
                amount_minor = 150000L,
                note = "Test Grocery, with comma",
                occurred_on = 1791000000000L,
                client_id = "client-test-1"
            ),
            category = CategoryEntity(
                id = "cat_test",
                user_id = "user_test",
                name = "Groceries",
                icon = "shopping_cart",
                color_hex = "#10B981",
                type = "EXPENSE",
                is_default = true
            )
        )

        val csv = CsvExporter.generateCsv(listOf(sampleTx))
        assertTrue(csv.contains("Transaction ID,Date,Type,Category,Note,Amount (PKR),Amount (Paisa)"))
        assertTrue(csv.contains("tx-test-1"))
        assertTrue(csv.contains("\"Groceries\""))
        assertTrue(csv.contains("\"Test Grocery, with comma\""))
        assertTrue(csv.contains("1500.0"))
        assertTrue(csv.contains("150000"))
    }

    @Test
    fun testExpenseEntityPaisaPrecision() {
        val expense = com.example.data.model.Expense(
            id = 1L,
            amount = 175050L, // 1,750.50 PKR stored in paisa
            category = "Food & Dining",
            date = 1791000000000L,
            description = "Biryani & Raita Lunch"
        )

        assertEquals(175050L, expense.amount)
        assertEquals(1750.50, expense.amountPkr, 0.001)
        assertEquals("Food & Dining", expense.category)
        assertEquals("Biryani & Raita Lunch", expense.description)
        assertEquals("PKR 1,750.50", expense.formattedAmount)
    }
}
