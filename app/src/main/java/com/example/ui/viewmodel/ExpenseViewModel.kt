package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExpenseDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionWithCategory
import com.example.data.model.UserEntity
import com.example.data.repository.ExpenseRepository
import com.example.util.CsvExporter
import com.example.util.DateUtils
import com.example.util.PaisaHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

enum class DateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class TypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expense"),
    INCOME("Income")
}

data class MonthStats(
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val categorySpend: Map<String, Long> = emptyMap()
)

data class CategorySpendItem(
    val category: CategoryEntity?,
    val spentMinor: Long,
    val percentage: Float
)

data class DailyTrendItem(
    val dateLabel: String,
    val dayOfWeek: String,
    val timestamp: Long,
    val expenseMinor: Long,
    val incomeMinor: Long
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ExpenseDatabase.getDatabase(application)
    private val repository = ExpenseRepository(
        userDao = db.userDao(),
        categoryDao = db.categoryDao(),
        transactionDao = db.transactionDao(),
        budgetDao = db.budgetDao(),
        notificationDao = db.notificationDao()
    )

    val currentUserId = ExpenseRepository.DEFAULT_USER_ID

    // User State
    val user: StateFlow<UserEntity?> = repository.getUser(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current Month Selection
    private val _selectedMonth = MutableStateFlow(DateUtils.getCurrentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Categories
    val categories: StateFlow<List<CategoryEntity>> = repository.getAllCategories(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Active Transactions
    val allTransactions: StateFlow<List<TransactionWithCategory>> = repository.getAllTransactions(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filters for Transactions screen
    val dateFilter = MutableStateFlow(DateFilter.ALL)
    val typeFilter = MutableStateFlow(TypeFilter.ALL)
    val categoryFilter = MutableStateFlow<String?>(null)
    val searchQuery = MutableStateFlow("")

    // Filtered Transactions
    val filteredTransactions: StateFlow<List<TransactionWithCategory>> = combine(
        allTransactions,
        dateFilter,
        typeFilter,
        categoryFilter,
        searchQuery
    ) { txList, dateF, typeF, catF, query ->
        txList.filter { item ->
            val tx = item.transaction
            // Type filter
            val matchType = when (typeF) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> tx.type == "EXPENSE"
                TypeFilter.INCOME -> tx.type == "INCOME"
            }

            // Category filter
            val matchCat = catF == null || tx.category_id == catF

            // Date filter
            val matchDate = when (dateF) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> {
                    val (start, end) = DateUtils.getTodayRange()
                    tx.occurred_on in start..end
                }
                DateFilter.THIS_WEEK -> {
                    val (start, end) = DateUtils.getWeekRange()
                    tx.occurred_on in start..end
                }
                DateFilter.THIS_MONTH -> {
                    val (start, end) = DateUtils.getMonthRange(DateUtils.getCurrentMonth())
                    tx.occurred_on in start..end
                }
            }

            // Search query
            val matchSearch = query.isBlank() ||
                    tx.note.contains(query, ignoreCase = true) ||
                    (item.category?.name?.contains(query, ignoreCase = true) ?: false) ||
                    PaisaHelper.formatPkr(tx.amount_minor).contains(query, ignoreCase = true)

            matchType && matchCat && matchDate && matchSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Budgets for Selected Month
    val budgets: StateFlow<List<BudgetEntity>> = repository.getBudgetsForMonth(currentUserId, DateUtils.getCurrentMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overallBudget: StateFlow<BudgetEntity?> = repository.getOverallBudget(currentUserId, DateUtils.getCurrentMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notifications
    val notifications: StateFlow<List<NotificationEntity>> = repository.getNotifications(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.getUnreadNotificationsCount(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Current Month Totals
    val thisMonthStats: StateFlow<MonthStats> = combine(allTransactions, _selectedMonth) { txList, month ->
        val (start, end) = DateUtils.getMonthRange(month)
        var income = 0L
        var expense = 0L
        val catMap = mutableMapOf<String, Long>()

        for (item in txList) {
            val tx = item.transaction
            if (tx.occurred_on in start..end) {
                if (tx.type == "INCOME") {
                    income += tx.amount_minor
                } else if (tx.type == "EXPENSE") {
                    expense += tx.amount_minor
                    val current = catMap[tx.category_id] ?: 0L
                    catMap[tx.category_id] = current + tx.amount_minor
                }
            }
        }
        MonthStats(income, expense, catMap)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonthStats()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultUserAndCategories()
        }
    }

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
    }

    // CRUD Transactions
    fun saveTransaction(
        id: String? = null,
        type: String,
        amountPkr: Double,
        categoryId: String,
        note: String,
        dateMillis: Long
    ) {
        viewModelScope.launch {
            val amountMinor = PaisaHelper.pkrDoubleToPaisa(amountPkr)
            val txId = id ?: UUID.randomUUID().toString()
            val tx = TransactionEntity(
                id = txId,
                user_id = currentUserId,
                category_id = categoryId,
                type = type,
                amount_minor = amountMinor,
                note = note.trim(),
                occurred_on = dateMillis,
                deleted_at = null,
                updated_at = System.currentTimeMillis(),
                client_id = if (id != null) txId else UUID.randomUUID().toString()
            )
            if (id == null) {
                repository.addTransaction(tx)
            } else {
                repository.updateTransaction(tx)
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
        }
    }

    // Budgets
    fun saveBudget(categoryId: String?, limitPkr: Double, month: String = _selectedMonth.value) {
        viewModelScope.launch {
            val limitMinor = PaisaHelper.pkrDoubleToPaisa(limitPkr)
            val existing = budgets.value.find { it.category_id == categoryId && it.month == month }
            val budget = BudgetEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                user_id = currentUserId,
                category_id = categoryId,
                month = month,
                limit_minor = limitMinor,
                alerted_80 = false, // Reset alert flags on limit change
                alerted_100 = false
            )
            repository.setBudget(budget)
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            repository.deleteBudget(budgetId)
        }
    }

    // Categories
    fun addCategory(name: String, icon: String, colorHex: String, type: String) {
        viewModelScope.launch {
            val cat = CategoryEntity(
                id = "cat_" + UUID.randomUUID().toString().take(8),
                user_id = currentUserId,
                name = name.trim(),
                icon = icon,
                color_hex = colorHex,
                type = type,
                is_default = false
            )
            repository.addCategory(cat)
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
        }
    }

    // Notifications
    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead(currentUserId)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications(currentUserId)
        }
    }

    // Settings & Demo Data
    fun seedSampleData() {
        viewModelScope.launch {
            repository.seedSampleData(currentUserId)
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.resetAllData(currentUserId)
        }
    }

    fun updateProfile(name: String, email: String, currency: String) {
        viewModelScope.launch {
            val current = user.value ?: return@launch
            val updated = current.copy(name = name, email = email, currency = currency)
            repository.updateUser(updated)
        }
    }

    fun exportTransactionsCsv(): String {
        return CsvExporter.generateCsv(allTransactions.value)
    }
}
