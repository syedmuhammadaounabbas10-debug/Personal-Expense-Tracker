package com.example.data.repository

import com.example.data.local.ExpenseDao
import com.example.data.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository pattern implementation for the Expense entity and ExpenseDao,
 * abstracting local offline data storage and retrieval.
 */
class ExpenseLocalRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    fun getExpenseById(id: Long): Flow<Expense?> = expenseDao.getExpenseById(id)

    suspend fun getExpenseByIdSync(id: Long): Expense? = withContext(Dispatchers.IO) {
        expenseDao.getExpenseByIdSync(id)
    }

    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<Expense>> =
        expenseDao.getExpensesByDateRange(startDate, endDate)

    fun getExpensesByCategory(category: String): Flow<List<Expense>> =
        expenseDao.getExpensesByCategory(category)

    fun getTotalExpensePaisa(): Flow<Long> = expenseDao.getTotalExpensePaisa()

    fun getTotalExpensePaisaInRange(startDate: Long, endDate: Long): Flow<Long> =
        expenseDao.getTotalExpensePaisaInRange(startDate, endDate)

    suspend fun insertExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        expenseDao.insertExpense(expense)
    }

    suspend fun insertExpenses(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        expenseDao.insertExpenses(expenses)
    }

    suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteExpenseById(id: Long) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpenseById(id)
    }

    suspend fun deleteAllExpenses() = withContext(Dispatchers.IO) {
        expenseDao.deleteAllExpenses()
    }
}
