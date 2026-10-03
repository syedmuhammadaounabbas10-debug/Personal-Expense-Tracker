package com.example.data.repository

import com.example.data.local.BudgetDao
import com.example.data.local.CategoryDao
import com.example.data.local.NotificationDao
import com.example.data.local.TransactionDao
import com.example.data.local.UserDao
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionWithCategory
import com.example.data.model.UserEntity
import com.example.util.DateUtils
import com.example.util.PaisaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ExpenseRepository(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val notificationDao: NotificationDao
) {
    companion object {
        const val DEFAULT_USER_ID = "user_aun_abbas"
    }

    // User Operations
    fun getUser(userId: String = DEFAULT_USER_ID): Flow<UserEntity?> = userDao.getUser(userId)

    suspend fun ensureDefaultUserAndCategories(): UserEntity = withContext(Dispatchers.IO) {
        var user = userDao.getFirstUser()
        if (user == null) {
            user = UserEntity(
                id = DEFAULT_USER_ID,
                name = "Aun Abbas",
                email = "aun.abbas@example.com",
                currency = "PKR",
                timezone = "Asia/Karachi"
            )
            userDao.insertUser(user)
        }

        val count = categoryDao.getCategoryCount(user.id)
        if (count == 0) {
            seedDefaultCategories(user.id)
        }
        user
    }

    suspend fun updateUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.updateUser(user)
    }

    // Categories
    fun getAllCategories(userId: String = DEFAULT_USER_ID): Flow<List<CategoryEntity>> =
        categoryDao.getAllCategories(userId)

    fun getCategoriesByType(userId: String = DEFAULT_USER_ID, type: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(userId, type)

    suspend fun addCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(categoryId: String) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(categoryId)
    }

    private suspend fun seedDefaultCategories(userId: String) {
        val defaultCategories = listOf(
            // Expense categories
            CategoryEntity("cat_food", userId, "Food & Dining", "restaurant", "#F59E0B", "EXPENSE", true),
            CategoryEntity("cat_transport", userId, "Transport", "directions_bus", "#3B82F6", "EXPENSE", true),
            CategoryEntity("cat_bills", userId, "Bills & Utilities", "receipt_long", "#EC4899", "EXPENSE", true),
            CategoryEntity("cat_education", userId, "Education", "school", "#8B5CF6", "EXPENSE", true),
            CategoryEntity("cat_health", userId, "Healthcare", "medical_services", "#EF4444", "EXPENSE", true),
            CategoryEntity("cat_shopping", userId, "Shopping", "shopping_bag", "#10B981", "EXPENSE", true),
            CategoryEntity("cat_entertainment", userId, "Entertainment", "movie", "#F97316", "EXPENSE", true),
            CategoryEntity("cat_groceries", userId, "Groceries", "shopping_cart", "#14B8A6", "EXPENSE", true),
            CategoryEntity("cat_other_exp", userId, "Other Expense", "category", "#64748B", "EXPENSE", true),
            // Income categories
            CategoryEntity("cat_salary", userId, "Salary", "account_balance_wallet", "#10B981", "INCOME", true),
            CategoryEntity("cat_allowance", userId, "Allowance", "payments", "#06B6D4", "INCOME", true),
            CategoryEntity("cat_freelance", userId, "Freelance", "laptop_mac", "#6366F1", "INCOME", true),
            CategoryEntity("cat_business", userId, "Business", "store", "#84CC16", "INCOME", true),
            CategoryEntity("cat_gift", userId, "Gift", "card_giftcard", "#EC4899", "INCOME", true),
            CategoryEntity("cat_other_inc", userId, "Other Income", "savings", "#0EA5E9", "INCOME", true)
        )
        categoryDao.insertCategories(defaultCategories)
    }

    // Transactions
    fun getAllTransactions(userId: String = DEFAULT_USER_ID): Flow<List<TransactionWithCategory>> =
        transactionDao.getAllActiveTransactions(userId)

    fun getTransactionsInRange(
        userId: String = DEFAULT_USER_ID,
        startTime: Long,
        endTime: Long
    ): Flow<List<TransactionWithCategory>> =
        transactionDao.getTransactionsInRange(userId, startTime, endTime)

    suspend fun getTransactionsSync(userId: String = DEFAULT_USER_ID): List<TransactionWithCategory> =
        withContext(Dispatchers.IO) {
            transactionDao.getAllTransactionsSync(userId)
        }

    suspend fun addTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.insertTransaction(transaction)
        if (transaction.type == "EXPENSE") {
            val month = DateUtils.getCurrentMonth()
            checkAndTriggerBudgetAlerts(transaction.user_id, month, transaction.category_id)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
        if (transaction.type == "EXPENSE") {
            val month = DateUtils.getCurrentMonth()
            checkAndTriggerBudgetAlerts(transaction.user_id, month, transaction.category_id)
        }
    }

    suspend fun deleteTransaction(transactionId: String) = withContext(Dispatchers.IO) {
        transactionDao.softDeleteTransaction(transactionId)
    }

    // Budgets
    fun getBudgetsForMonth(userId: String = DEFAULT_USER_ID, month: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(userId, month)

    fun getOverallBudget(userId: String = DEFAULT_USER_ID, month: String): Flow<BudgetEntity?> =
        budgetDao.getOverallBudget(userId, month)

    suspend fun setBudget(budget: BudgetEntity) = withContext(Dispatchers.IO) {
        budgetDao.insertBudget(budget)
        // Check alerts with newly updated limit
        checkAndTriggerBudgetAlerts(budget.user_id, budget.month, budget.category_id)
    }

    suspend fun deleteBudget(budgetId: String) = withContext(Dispatchers.IO) {
        budgetDao.deleteBudget(budgetId)
    }

    // Budget Threshold Alert Logic (Rule: trigger at 80% and 100% threshold once per month)
    private suspend fun checkAndTriggerBudgetAlerts(
        userId: String,
        month: String,
        categoryId: String?
    ) {
        val (monthStart, monthEnd) = DateUtils.getMonthRange(month)
        val monthDisplay = DateUtils.formatMonthString(month)

        // 1. Check Overall Budget
        val overallBudget = budgetDao.getOverallBudgetSync(userId, month)
        if (overallBudget != null && overallBudget.limit_minor > 0L) {
            val totalSpent = transactionDao.getTotalExpenseInRange(userId, monthStart, monthEnd)
            val limit = overallBudget.limit_minor
            var updated80 = overallBudget.alerted_80
            var updated100 = overallBudget.alerted_100

            if (totalSpent >= limit && !overallBudget.alerted_100) {
                // 100% Threshold Exceeded
                updated100 = true
                updated80 = true
                notificationDao.insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        user_id = userId,
                        title = "⚠️ Budget Limit Exceeded ($monthDisplay)",
                        body = "You have spent ${PaisaHelper.formatPkr(totalSpent)} exceeding your monthly overall budget of ${PaisaHelper.formatPkr(limit)}.",
                        type = "BUDGET_EXCEEDED"
                    )
                )
            } else if (totalSpent >= (limit * 80 / 100) && !overallBudget.alerted_80) {
                // 80% Threshold Warning
                updated80 = true
                val percent = PaisaHelper.calculateBudgetPercentage(totalSpent, limit).toInt()
                notificationDao.insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        user_id = userId,
                        title = "🔔 Budget Warning ($monthDisplay)",
                        body = "You have reached $percent% of your overall monthly budget (${PaisaHelper.formatPkr(totalSpent)} of ${PaisaHelper.formatPkr(limit)}).",
                        type = "BUDGET_WARNING"
                    )
                )
            }

            if (updated80 != overallBudget.alerted_80 || updated100 != overallBudget.alerted_100) {
                budgetDao.updateAlertFlags(overallBudget.id, updated80, updated100)
            }
        }

        // 2. Check Category Budget if specific category
        if (categoryId != null) {
            val catBudget = budgetDao.getCategoryBudgetSync(userId, categoryId, month)
            if (catBudget != null && catBudget.limit_minor > 0L) {
                val catSpent = transactionDao.getCategoryExpenseInRange(userId, categoryId, monthStart, monthEnd)
                val catLimit = catBudget.limit_minor
                val categoryName = categoryDao.getCategoryByIdSync(categoryId)?.name ?: "Category"
                var catUpdated80 = catBudget.alerted_80
                var catUpdated100 = catBudget.alerted_100

                if (catSpent >= catLimit && !catBudget.alerted_100) {
                    catUpdated100 = true
                    catUpdated80 = true
                    notificationDao.insertNotification(
                        NotificationEntity(
                            id = UUID.randomUUID().toString(),
                            user_id = userId,
                            title = "⚠️ $categoryName Budget Exceeded",
                            body = "You have spent ${PaisaHelper.formatPkr(catSpent)} on $categoryName, exceeding your $monthDisplay budget limit of ${PaisaHelper.formatPkr(catLimit)}.",
                            type = "BUDGET_EXCEEDED"
                        )
                    )
                } else if (catSpent >= (catLimit * 80 / 100) && !catBudget.alerted_80) {
                    catUpdated80 = true
                    val pct = PaisaHelper.calculateBudgetPercentage(catSpent, catLimit).toInt()
                    notificationDao.insertNotification(
                        NotificationEntity(
                            id = UUID.randomUUID().toString(),
                            user_id = userId,
                            title = "🔔 $categoryName at $pct% of Budget",
                            body = "You've spent ${PaisaHelper.formatPkr(catSpent)} of your ${PaisaHelper.formatPkr(catLimit)} limit for $categoryName in $monthDisplay.",
                            type = "BUDGET_WARNING"
                        )
                    )
                }

                if (catUpdated80 != catBudget.alerted_80 || catUpdated100 != catBudget.alerted_100) {
                    budgetDao.updateAlertFlags(catBudget.id, catUpdated80, catUpdated100)
                }
            }
        }
    }

    // Totals
    suspend fun getTotalExpenseForRange(userId: String = DEFAULT_USER_ID, start: Long, end: Long): Long =
        withContext(Dispatchers.IO) {
            transactionDao.getTotalExpenseInRange(userId, start, end)
        }

    suspend fun getTotalIncomeForRange(userId: String = DEFAULT_USER_ID, start: Long, end: Long): Long =
        withContext(Dispatchers.IO) {
            transactionDao.getTotalIncomeInRange(userId, start, end)
        }

    suspend fun getCategoryExpenseForRange(
        userId: String = DEFAULT_USER_ID,
        categoryId: String,
        start: Long,
        end: Long
    ): Long = withContext(Dispatchers.IO) {
        transactionDao.getCategoryExpenseInRange(userId, categoryId, start, end)
    }

    // Notifications
    fun getNotifications(userId: String = DEFAULT_USER_ID): Flow<List<NotificationEntity>> =
        notificationDao.getNotifications(userId)

    fun getUnreadNotificationsCount(userId: String = DEFAULT_USER_ID): Flow<Int> =
        notificationDao.getUnreadCount(userId)

    suspend fun markNotificationAsRead(id: String) = withContext(Dispatchers.IO) {
        notificationDao.markAsRead(id)
    }

    suspend fun markAllNotificationsAsRead(userId: String = DEFAULT_USER_ID) = withContext(Dispatchers.IO) {
        notificationDao.markAllAsRead(userId)
    }

    suspend fun clearAllNotifications(userId: String = DEFAULT_USER_ID) = withContext(Dispatchers.IO) {
        notificationDao.clearAll(userId)
    }

    // Reset Data
    suspend fun resetAllData(userId: String = DEFAULT_USER_ID) = withContext(Dispatchers.IO) {
        transactionDao.deleteAllForUser(userId)
        budgetDao.deleteAllForUser(userId)
        notificationDao.clearAll(userId)
    }

    // Sample Data Seeder for Evaluation and Demos
    suspend fun seedSampleData(userId: String = DEFAULT_USER_ID) = withContext(Dispatchers.IO) {
        // Reset current transactions & budgets
        resetAllData(userId)

        val currentMonth = DateUtils.getCurrentMonth()
        val now = System.currentTimeMillis()
        val oneDay = 86_400_000L

        // Set Overall Monthly Budget: ₨ 65,000 = 6,500,000 paisa
        val overallBudget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            category_id = null,
            month = currentMonth,
            limit_minor = 65_000_00L, // 65,000 PKR
            alerted_80 = false,
            alerted_100 = false
        )
        budgetDao.insertBudget(overallBudget)

        // Set Category Budgets
        val catFoodBudget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            category_id = "cat_food",
            month = currentMonth,
            limit_minor = 15_000_00L, // 15,000 PKR
            alerted_80 = false,
            alerted_100 = false
        )
        val catTransportBudget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            category_id = "cat_transport",
            month = currentMonth,
            limit_minor = 8_000_00L, // 8,000 PKR
            alerted_80 = false,
            alerted_100 = false
        )
        val catGroceriesBudget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            category_id = "cat_groceries",
            month = currentMonth,
            limit_minor = 14_000_00L, // 14,000 PKR
            alerted_80 = false,
            alerted_100 = false
        )
        val catBillsBudget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            category_id = "cat_bills",
            month = currentMonth,
            limit_minor = 18_000_00L, // 18,000 PKR
            alerted_80 = false,
            alerted_100 = false
        )
        budgetDao.insertBudgets(listOf(catFoodBudget, catTransportBudget, catGroceriesBudget, catBillsBudget))

        // Populate realistic Pakistani expenses & incomes
        val sampleTxs = listOf(
            // Income
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_salary",
                type = "INCOME",
                amount_minor = 85_000_00L, // 85,000 PKR
                note = "Monthly Software Engineering Salary",
                occurred_on = now - (oneDay * 3),
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_freelance",
                type = "INCOME",
                amount_minor = 18_500_00L, // 18,500 PKR
                note = "Upwork Web Design Milestone",
                occurred_on = now - (oneDay * 1),
                client_id = UUID.randomUUID().toString()
            ),
            // Expenses
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_bills",
                type = "EXPENSE",
                amount_minor = 16_500_00L, // 16,500 PKR
                note = "Apartment Rent & Society Maintenance",
                occurred_on = now - (oneDay * 2),
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_groceries",
                type = "EXPENSE",
                amount_minor = 11_800_00L, // 11,800 PKR
                note = "Monthly Supermarket Ration (Al-Fatah)",
                occurred_on = now - (oneDay * 2),
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_food",
                type = "EXPENSE",
                amount_minor = 4_500_00L, // 4,500 PKR
                note = "Dinner with Friends at Monal / Cafe",
                occurred_on = now - (oneDay * 1),
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_transport",
                type = "EXPENSE",
                amount_minor = 6_800_00L, // 6,800 PKR
                note = "PSO Petrol Station Bike & Car Refuel",
                occurred_on = now - (oneDay * 1),
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_food",
                type = "EXPENSE",
                amount_minor = 8_200_00L, // 8,200 PKR
                note = "Hostel Mess & University Canteen Meals",
                occurred_on = now,
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_education",
                type = "EXPENSE",
                amount_minor = 5_000_00L, // 5,000 PKR
                note = "Online Course & Semester Textbooks",
                occurred_on = now,
                client_id = UUID.randomUUID().toString()
            ),
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                category_id = "cat_bills",
                type = "EXPENSE",
                amount_minor = 2_200_00L, // 2,200 PKR
                note = "PTCL Flash Fiber Internet Bill",
                occurred_on = now,
                client_id = UUID.randomUUID().toString()
            )
        )

        for (tx in sampleTxs) {
            transactionDao.insertTransaction(tx)
        }

        // Trigger budget calculations and alerts
        checkAndTriggerBudgetAlerts(userId, currentMonth, "cat_food")
        checkAndTriggerBudgetAlerts(userId, currentMonth, "cat_transport")
    }
}
