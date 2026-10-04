package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthUser
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ExpenseDatabase.getDatabase(application)
    private val repository = ExpenseRepository(
        userDao = db.userDao(),
        categoryDao = db.categoryDao(),
        transactionDao = db.transactionDao(),
        budgetDao = db.budgetDao(),
        notificationDao = db.notificationDao()
    )

    val authManager = AuthManager(application)

    // Auth States
    private val _authUser = MutableStateFlow<AuthUser?>(authManager.currentUser)
    val authUser: StateFlow<AuthUser?> = _authUser.asStateFlow()

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth.asStateFlow()

    private val _currentUserId = MutableStateFlow(authManager.currentUser?.uid ?: "")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    // Current Month Selection
    private val _selectedMonth = MutableStateFlow(DateUtils.getCurrentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // User Profile from Local Room Database
    val user: StateFlow<UserEntity?> = _currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getUser(uid)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Categories scoped to active user
    val categories: StateFlow<List<CategoryEntity>> = _currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getAllCategories(uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Active Transactions scoped to active user
    val allTransactions: StateFlow<List<TransactionWithCategory>> = _currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getAllTransactions(uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val matchType = when (typeF) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> tx.type == "EXPENSE"
                TypeFilter.INCOME -> tx.type == "INCOME"
            }

            val matchCat = catF == null || tx.category_id == catF

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

            val matchSearch = query.isBlank() ||
                    tx.note.contains(query, ignoreCase = true) ||
                    (item.category?.name?.contains(query, ignoreCase = true) ?: false) ||
                    PaisaHelper.formatPkr(tx.amount_minor).contains(query, ignoreCase = true)

            matchType && matchCat && matchDate && matchSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Budgets for Selected Month
    val budgets: StateFlow<List<BudgetEntity>> = combine(_currentUserId, _selectedMonth) { uid, month ->
        uid to month
    }.flatMapLatest { (uid, month) ->
        if (uid.isNotBlank()) {
            repository.getBudgetsForMonth(uid, month)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overallBudget: StateFlow<BudgetEntity?> = combine(_currentUserId, _selectedMonth) { uid, month ->
        uid to month
    }.flatMapLatest { (uid, month) ->
        if (uid.isNotBlank()) {
            repository.getOverallBudget(uid, month)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notifications
    val notifications: StateFlow<List<NotificationEntity>> = _currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getNotifications(uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = _currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getUnreadNotificationsCount(uid)
        } else {
            flowOf(0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthStats())

    init {
        // Collect Auth State changes reactively
        viewModelScope.launch {
            authManager.authState.collect { authState ->
                _authUser.value = authState
                val uid = authState?.uid ?: ""
                _currentUserId.value = uid

                if (authState != null) {
                    repository.syncUserWithLocalDb(
                        uid = authState.uid,
                        email = authState.email,
                        displayName = authState.displayName,
                        photoUrl = authState.photoUrl,
                        isGuest = authState.isAnonymous,
                        providerId = authState.providerId
                    )
                }
                _isCheckingAuth.value = false
            }
        }
    }

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
    }

    // Authentication Actions
    fun signInWithGoogle(activityContext: Context, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = authManager.signInWithGoogle(activityContext)) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    _currentUserId.value = result.data.uid
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = result.data.displayName,
                        photoUrl = result.data.photoUrl,
                        isGuest = false,
                        providerId = "google.com"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = authManager.signUpWithEmail(name, email, pass)) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    _currentUserId.value = result.data.uid
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = name,
                        photoUrl = null,
                        isGuest = false,
                        providerId = "password"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = authManager.signInWithEmail(email, pass)) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    _currentUserId.value = result.data.uid
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = result.data.displayName,
                        photoUrl = result.data.photoUrl,
                        isGuest = false,
                        providerId = "password"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun signInAsGuest(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = authManager.signInAnonymously()) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    _currentUserId.value = result.data.uid
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = "Guest User",
                        photoUrl = null,
                        isGuest = true,
                        providerId = "anonymous"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = authManager.sendPasswordReset(email)) {
                is AuthResult.Success -> onSuccess()
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun linkGuestWithEmail(name: String, email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = authManager.linkGuestWithEmail(name, email, pass)) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = name,
                        photoUrl = null,
                        isGuest = false,
                        providerId = "password"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun linkGuestWithGoogle(activityContext: Context, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = authManager.linkGuestWithGoogle(activityContext)) {
                is AuthResult.Success -> {
                    _authUser.value = result.data
                    repository.syncUserWithLocalDb(
                        uid = result.data.uid,
                        email = result.data.email,
                        displayName = result.data.displayName,
                        photoUrl = result.data.photoUrl,
                        isGuest = false,
                        providerId = "google.com"
                    )
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        authManager.signOut()
        _authUser.value = null
        _currentUserId.value = ""
        onComplete()
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = _currentUserId.value
        viewModelScope.launch {
            when (val result = authManager.deleteAccount()) {
                is AuthResult.Success -> {
                    if (uid.isNotBlank()) {
                        repository.deleteUserData(uid)
                    }
                    _authUser.value = null
                    _currentUserId.value = ""
                    onSuccess()
                }
                is AuthResult.Error -> onError(result.message)
            }
        }
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
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            val amountMinor = PaisaHelper.pkrDoubleToPaisa(amountPkr)
            val txId = id ?: UUID.randomUUID().toString()
            val tx = TransactionEntity(
                id = txId,
                user_id = uid,
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
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            val limitMinor = PaisaHelper.pkrDoubleToPaisa(limitPkr)
            val existing = budgets.value.find { it.category_id == categoryId && it.month == month }
            val budget = BudgetEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                user_id = uid,
                category_id = categoryId,
                month = month,
                limit_minor = limitMinor,
                alerted_80 = false,
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
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            val cat = CategoryEntity(
                id = "cat_" + UUID.randomUUID().toString().take(8),
                user_id = uid,
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
    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markNotificationRead(id: String) = markNotificationAsRead(id)

    fun markAllNotificationsRead() {
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.markAllNotificationsAsRead(uid)
        }
    }

    fun clearAllNotifications() {
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.clearAllNotifications(uid)
        }
    }

    // Settings & Demo Data
    fun seedSampleData() {
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.seedSampleData(uid)
        }
    }

    fun resetAllData() {
        val uid = _currentUserId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.resetAllData(uid)
        }
    }

    fun updateProfile(name: String, email: String, currency: String) {
        val uid = _currentUserId.value
        if (uid.isBlank()) return
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
