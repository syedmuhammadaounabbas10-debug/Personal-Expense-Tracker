package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month")
    fun getBudgetsForMonth(userId: String, month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month")
    suspend fun getBudgetsForMonthSync(userId: String, month: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND category_id IS NULL LIMIT 1")
    fun getOverallBudget(userId: String, month: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND category_id IS NULL LIMIT 1")
    suspend fun getOverallBudgetSync(userId: String, month: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND category_id = :categoryId LIMIT 1")
    suspend fun getCategoryBudgetSync(userId: String, categoryId: String, month: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET alerted_80 = :alerted80, alerted_100 = :alerted100 WHERE id = :id")
    suspend fun updateAlertFlags(id: String, alerted80: Boolean, alerted100: Boolean)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)

    @Query("DELETE FROM budgets WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
