package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL ORDER BY occurred_on DESC")
    fun getAllActiveTransactions(userId: String): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND occurred_on BETWEEN :startTime AND :endTime ORDER BY occurred_on DESC")
    fun getTransactionsInRange(userId: String, startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND id = :transactionId LIMIT 1")
    fun getTransactionById(userId: String, transactionId: String): Flow<TransactionWithCategory?>

    @Transaction
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND id = :transactionId LIMIT 1")
    suspend fun getTransactionByIdSync(userId: String, transactionId: String): TransactionWithCategory?

    @Transaction
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL")
    suspend fun getAllTransactionsSync(userId: String): List<TransactionWithCategory>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND type = 'EXPENSE' AND occurred_on BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpenseInRange(userId: String, startTime: Long, endTime: Long): Long

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND type = 'INCOME' AND occurred_on BETWEEN :startTime AND :endTime")
    suspend fun getTotalIncomeInRange(userId: String, startTime: Long, endTime: Long): Long

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM transactions WHERE user_id = :userId AND deleted_at IS NULL AND type = 'EXPENSE' AND category_id = :categoryId AND occurred_on BETWEEN :startTime AND :endTime")
    suspend fun getCategoryExpenseInRange(userId: String, categoryId: String, startTime: Long, endTime: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deleted_at = :deleteTime, updated_at = :deleteTime WHERE id = :transactionId")
    suspend fun softDeleteTransaction(transactionId: String, deleteTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun hardDeleteTransaction(transactionId: String)

    @Query("DELETE FROM transactions WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
