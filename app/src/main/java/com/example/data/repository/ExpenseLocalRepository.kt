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

    fun getAllExpenses(userId: String): Flow<List<Expense>> = expenseDao.getAllExpenses(userId)

    fun getExpenseById(userId: String, id: Long): Flow<Expense?> = expenseDao.getExpenseById(userId, id)

    suspend fun getExpenseByIdSync(userId: String, id: Long): Expense? = withContext(Dispatchers.IO) {
        expenseDao.getExpenseByIdSync(userId, id)
    }

    fun getExpensesByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<Expense>> =
        expenseDao.getExpensesByDateRange(userId, startDate, endDate)

    fun getExpensesByCategory(userId: String, category: String): Flow<List<Expense>> =
        expenseDao.getExpensesByCategory(userId, category)

    fun getTotalExpensePaisa(userId: String): Flow<Long> = expenseDao.getTotalExpensePaisa(userId)

    fun getTotalExpensePaisaInRange(userId: String, startDate: Long, endDate: Long): Flow<Long> =
        expenseDao.getTotalExpensePaisaInRange(userId, startDate, endDate)

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

    suspend fun deleteExpenseById(userId: String, id: Long) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpenseById(userId, id)
    }

    suspend fun deleteAllForUser(userId: String) = withContext(Dispatchers.IO) {
        expenseDao.deleteAllForUser(userId)
    }

    suspend fun deleteAllExpenses() = withContext(Dispatchers.IO) {
        expenseDao.deleteAllExpenses()
    }
}
