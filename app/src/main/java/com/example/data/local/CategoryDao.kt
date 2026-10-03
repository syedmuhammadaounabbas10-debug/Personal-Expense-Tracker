package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY is_default DESC, name ASC")
    fun getAllCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId AND type = :type ORDER BY is_default DESC, name ASC")
    fun getCategoriesByType(userId: String, type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId")
    suspend fun getAllCategoriesSync(userId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    fun getCategoryById(categoryId: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryByIdSync(categoryId: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE user_id = :userId")
    suspend fun getCategoryCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    @Query("DELETE FROM categories WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
