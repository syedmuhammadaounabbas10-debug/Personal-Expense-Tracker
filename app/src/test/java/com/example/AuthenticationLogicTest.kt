package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ExpenseDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserEntity
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthenticationLogicTest {

    private lateinit var database: ExpenseDatabase
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = ExpenseRepository(
            userDao = database.userDao(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            budgetDao = database.budgetDao(),
            notificationDao = database.notificationDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testUserSyncAndGuestAccountCreation() = runBlocking {
        // Create Guest User
        val guest = repository.syncUserWithLocalDb(
            uid = "guest_12345",
            email = "guest@expensetracker.local",
            displayName = "Guest User",
            photoUrl = null,
            isGuest = true,
            providerId = "anonymous"
        )
        assertTrue(guest.is_guest)
        assertEquals("guest_12345", guest.id)
        assertEquals("PKR", guest.currency)
        assertEquals("Asia/Karachi", guest.timezone)

        // Seed some guest transactions
        val txId = UUID.randomUUID().toString()
        repository.addTransaction(
            TransactionEntity(
                id = txId,
                user_id = guest.id,
                category_id = "cat_food",
                type = "EXPENSE",
                amount_minor = 350000L, // 3,500 PKR
                note = "Guest Mess Lunch",
                occurred_on = System.currentTimeMillis(),
                client_id = txId
            )
        )

        // Link Guest to Permanent Account (UID is preserved!)
        val linkedUser = repository.syncUserWithLocalDb(
            uid = "guest_12345",
            email = "aun.abbas@gmail.com",
            displayName = "Aun Abbas",
            photoUrl = "https://example.com/avatar.jpg",
            isGuest = false,
            providerId = "google.com"
        )

        assertFalse(linkedUser.is_guest)
        assertEquals("aun.abbas@gmail.com", linkedUser.email)
        assertEquals("Aun Abbas", linkedUser.name)

        // Guest transaction is retained intact
        val transactions = repository.getAllTransactions(guest.id).first()
        assertEquals(1, transactions.size)
        assertEquals(350000L, transactions[0].transaction.amount_minor)
    }

    @Test
    fun testStrictCrossUserDataIsolation() = runBlocking {
        val userA = "user_A_id"
        val userB = "user_B_id"

        repository.syncUserWithLocalDb(userA, "a@example.com", "User A", null, false, "password")
        repository.syncUserWithLocalDb(userB, "b@example.com", "User B", null, false, "password")

        // User A logs a transaction
        val txA = TransactionEntity(
            id = "tx_a_1",
            user_id = userA,
            category_id = "cat_transport",
            type = "EXPENSE",
            amount_minor = 120000L, // 1,200 PKR
            note = "User A Fuel",
            occurred_on = System.currentTimeMillis(),
            client_id = "tx_a_1"
        )
        repository.addTransaction(txA)

        // User B logs a transaction
        val txB = TransactionEntity(
            id = "tx_b_1",
            user_id = userB,
            category_id = "cat_groceries",
            type = "EXPENSE",
            amount_minor = 450000L, // 4,500 PKR
            note = "User B Groceries",
            occurred_on = System.currentTimeMillis(),
            client_id = "tx_b_1"
        )
        repository.addTransaction(txB)

        // Verify User A only sees User A's transaction
        val userATxs = repository.getAllTransactions(userA).first()
        assertEquals(1, userATxs.size)
        assertEquals("tx_a_1", userATxs[0].transaction.id)

        // Verify User B only sees User B's transaction
        val userBTxs = repository.getAllTransactions(userB).first()
        assertEquals(1, userBTxs.size)
        assertEquals("tx_b_1", userBTxs[0].transaction.id)

        // Cross-user query by ID returns null for unauthorized user
        val crossQueryResult = database.transactionDao().getTransactionById(userB, "tx_a_1").first()
        assertNull("User B must NOT be able to read User A's transaction", crossQueryResult)
    }

    @Test
    fun testUserAccountDeletionPurgesData() = runBlocking {
        val userToDelete = "user_to_delete"
        repository.syncUserWithLocalDb(userToDelete, "delete.me@example.com", "Delete Me", null, false, "password")

        // Add a transaction, budget, and notification
        repository.addTransaction(
            TransactionEntity(
                id = "tx_delete_1",
                user_id = userToDelete,
                category_id = "cat_bills",
                type = "EXPENSE",
                amount_minor = 90000L,
                note = "Water bill",
                occurred_on = System.currentTimeMillis(),
                client_id = "tx_delete_1"
            )
        )
        repository.setBudget(
            BudgetEntity(
                id = "b_delete_1",
                user_id = userToDelete,
                category_id = null,
                month = "2026-10",
                limit_minor = 5000000L
            )
        )
        database.notificationDao().insertNotification(
            NotificationEntity(
                id = "n_delete_1",
                user_id = userToDelete,
                title = "Alert",
                body = "Budget warning",
                type = "BUDGET_WARNING"
            )
        )

        // Ensure records exist
        assertEquals(1, repository.getAllTransactions(userToDelete).first().size)
        assertEquals(1, repository.getBudgetsForMonth(userToDelete, "2026-10").first().size)
        assertEquals(1, repository.getNotifications(userToDelete).first().size)

        // Delete user's data
        repository.deleteUserData(userToDelete)

        // Ensure all data is permanently purged
        assertEquals(0, repository.getAllTransactions(userToDelete).first().size)
        assertEquals(0, repository.getBudgetsForMonth(userToDelete, "2026-10").first().size)
        assertEquals(0, repository.getNotifications(userToDelete).first().size)
        assertNull(database.userDao().getUserSync(userToDelete))
    }
}
