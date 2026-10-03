package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for offline storage and reactive retrieval of expenses.
 */
@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE user_id = :userId ORDER BY date DESC")
    fun getAllExpenses(userId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id AND user_id = :userId LIMIT 1")
    fun getExpenseById(userId: String, id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getExpenseByIdSync(userId: String, id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE user_id = :userId AND category = :category ORDER BY date DESC")
    fun getExpensesByCategory(userId: String, category: String): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = :userId")
    fun getTotalExpensePaisa(userId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpensePaisaInRange(userId: String, startDate: Long, endDate: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = :userId AND category = :category AND date BETWEEN :startDate AND :endDate")
    fun getCategoryExpensePaisaInRange(userId: String, category: String, startDate: Long, endDate: Long): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id AND user_id = :userId")
    suspend fun deleteExpenseById(userId: String, id: Long)

    @Query("DELETE FROM expenses WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
