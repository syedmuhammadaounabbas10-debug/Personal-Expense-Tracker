package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ExpenseDao
import com.example.data.local.ExpenseDatabase
import com.example.data.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpenseDaoTest {

    private lateinit var database: ExpenseDatabase
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expenseDao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveExpenseWithPaisaPrecision() = runBlocking {
        val expense = Expense(
            amount = 450000L, // 4,500 PKR
            category = "Transport",
            date = 1000L,
            description = "Fuel refill"
        )
        val insertedId = expenseDao.insertExpense(expense)

        val retrieved = expenseDao.getExpenseById(insertedId).first()
        assertNotNull(retrieved)
        assertEquals(450000L, retrieved?.amount)
        assertEquals("Transport", retrieved?.category)
        assertEquals("Fuel refill", retrieved?.description)
        assertEquals(4500.0, retrieved?.amountPkr ?: 0.0, 0.001)
    }

    @Test
    fun queryExpensesByCategoryAndTotalPaisa() = runBlocking {
        expenseDao.insertExpense(Expense(amount = 120000L, category = "Food", date = 1000L, description = "Lunch"))
        expenseDao.insertExpense(Expense(amount = 180000L, category = "Food", date = 2000L, description = "Dinner"))
        expenseDao.insertExpense(Expense(amount = 50000L, category = "Bills", date = 3000L, description = "Internet"))

        val foodExpenses = expenseDao.getExpensesByCategory("Food").first()
        assertEquals(2, foodExpenses.size)

        val totalPaisa = expenseDao.getTotalExpensePaisa().first()
        assertEquals(350000L, totalPaisa) // 1,200 + 1,800 + 500 = 3,500 PKR

        val foodPaisaInRange = expenseDao.getCategoryExpensePaisaInRange("Food", 500L, 2500L).first()
        assertEquals(300000L, foodPaisaInRange)
    }

    @Test
    fun deleteExpenseById() = runBlocking {
        val id = expenseDao.insertExpense(Expense(amount = 20000L, category = "Other", date = 1000L, description = "Snack"))
        assertNotNull(expenseDao.getExpenseById(id).first())

        expenseDao.deleteExpenseById(id)
        assertNull(expenseDao.getExpenseById(id).first())
    }
}
