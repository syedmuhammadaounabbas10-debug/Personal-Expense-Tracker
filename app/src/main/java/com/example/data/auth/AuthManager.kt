package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val isNetworkError: Boolean = false) : AuthResult<Nothing>()
}

data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val isAnonymous: Boolean,
    val providerId: String
)

class AuthManager(private val context: Context) {

    companion object {
        const val FIREBASE_NOT_CONFIGURED_MSG =
            "Firebase is not configured. Please download google-services.json from your Firebase Console for package 'com.aistudio.expensetracker.pkrx' and place it in 'app/google-services.json'."
    }

    private fun resolveAuth(): FirebaseAuth? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext)
            }
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "Firebase initialization check failed: ${e.message}")
            null
        }
    }

    private val auth: FirebaseAuth?
        get() = resolveAuth()

    private val credentialManager by lazy { CredentialManager.create(context) }

    val currentUser: AuthUser?
        get() {
            val user = auth?.currentUser ?: return null
            return mapFirebaseUser(user)
        }

    val authState: Flow<AuthUser?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(null)
            awaitClose { }
        } else {
            val listener = FirebaseAuth.AuthStateListener { fa ->
                val fbUser = fa.currentUser
                trySend(fbUser?.let { mapFirebaseUser(it) })
            }
            firebaseAuth.addAuthStateListener(listener)
            awaitClose { firebaseAuth.removeAuthStateListener(listener) }
        }
    }

    private fun mapFirebaseUser(user: FirebaseUser): AuthUser {
        val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: if (user.isAnonymous) "anonymous" else "password"
        return AuthUser(
            uid = user.uid,
            email = user.email ?: if (user.isAnonymous) "guest@expensetracker.local" else "",
            displayName = user.displayName ?: if (user.isAnonymous) "Guest User" else (user.email?.substringBefore("@") ?: "User"),
            photoUrl = user.photoUrl?.toString(),
            isAnonymous = user.isAnonymous,
            providerId = provider
        )
    }

    /**
     * Sign up with Email and Password
     */
    suspend fun signUpWithEmail(name: String, email: String, pass: String): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        return try {
            val result = fa.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: return AuthResult.Error("Failed to create user account.")

            // Update display name
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w("AuthManager", "Failed to set display name: ${e.message}")
            }

            AuthResult.Success(mapFirebaseUser(user))
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Sign in with Email and Password
     */
    suspend fun signInWithEmail(email: String, pass: String): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        return try {
            val result = fa.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: return AuthResult.Error("Failed to sign in.")
            AuthResult.Success(mapFirebaseUser(user))
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Sign in Anonymously as a Guest
     */
    suspend fun signInAnonymously(): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        return try {
            val result = fa.signInAnonymously().await()
            val user = result.user ?: return AuthResult.Error("Failed to create guest session.")
            AuthResult.Success(mapFirebaseUser(user))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if ((e is FirebaseAuthException && (e.errorCode == "ERROR_ADMIN_RESTRICTED_OPERATION" || e.errorCode == "ERROR_OPERATION_NOT_ALLOWED")) ||
                msg.contains("restricted", ignoreCase = true) || msg.contains("administrator", ignoreCase = true) || msg.contains("admin", ignoreCase = true)
            ) {
                AuthResult.Error("Guest mode is currently unavailable. Please enable Anonymous Authentication in Firebase Console.")
            } else {
                AuthResult.Error(friendlyErrorMessage(e))
            }
        }
    }

    /**
     * Send Password Reset Email
     */
    suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        return try {
            fa.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Continue with Google using Credential Manager (Two-Stage Authorized -> Unfiltered Fallback)
     */
    suspend fun signInWithGoogle(webClientId: String? = null): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        return try {
            // Generate a random nonce for security
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // If a web client ID isn't explicitly provided, use default server client ID from string resources if available
            val clientId = webClientId ?: getWebClientIdFromResources()

            if (clientId.isNullOrBlank()) {
                return AuthResult.Error(
                    "Google Sign-In Web Client ID is not configured. Please add your OAuth Web Client ID in Firebase Console and google-services.json."
                )
            }

            var credentialResponse: GetCredentialResponse? = null

            // Stage 1: Attempt filterByAuthorizedAccounts = true
            try {
                val googleIdOptionAuthorized = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .setNonce(hashedNonce)
                    .build()

                val requestAuthorized = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionAuthorized)
                    .build()

                credentialResponse = credentialManager.getCredential(context, requestAuthorized)
            } catch (e: GetCredentialCancellationException) {
                return AuthResult.Error("Google sign-in was cancelled.")
            } catch (e: Exception) {
                Log.d("AuthManager", "Authorized accounts sign-in failed (${e.message}), retrying with all Google accounts...")
            }

            // Stage 2: Fallback to filterByAuthorizedAccounts = false
            if (credentialResponse == null) {
                val googleIdOptionAll = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .setNonce(hashedNonce)
                    .build()

                val requestAll = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionAll)
                    .build()

                credentialResponse = credentialManager.getCredential(context, requestAll)
            }

            val credential = credentialResponse?.credential ?: return AuthResult.Error("No credential returned from Google.")

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = fa.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return AuthResult.Error("Google authentication completed but no user returned.")
                AuthResult.Success(mapFirebaseUser(user))
            } else {
                AuthResult.Error("Unexpected credential type received from Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Error("Google sign-in was cancelled.")
        } catch (e: GetCredentialException) {
            AuthResult.Error("Google sign-in failed: ${e.message ?: "No account selected"}")
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Link Guest Account with Email & Password to retain data
     */
    suspend fun linkGuestWithEmail(name: String, email: String, pass: String): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val currentUser = fa.currentUser ?: return AuthResult.Error("No active guest account found to link.")

        return try {
            val credential = EmailAuthProvider.getCredential(email.trim(), pass)
            val result = currentUser.linkWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Error("Failed to link account.")

            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w("AuthManager", "Could not set name on linked account: ${e.message}")
            }

            AuthResult.Success(mapFirebaseUser(user))
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("This email address is already registered to another account. Please log in with that email.")
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Link Guest Account with Google Credential
     */
    suspend fun linkGuestWithGoogle(webClientId: String? = null): AuthResult<AuthUser> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val currentUser = fa.currentUser ?: return AuthResult.Error("No active guest account found to link.")

        return try {
            val clientId = webClientId ?: getWebClientIdFromResources()
            if (clientId.isNullOrBlank()) {
                return AuthResult.Error("Google Web Client ID is missing.")
            }

            var credentialResponse: GetCredentialResponse? = null

            // Stage 1: Attempt authorized accounts
            try {
                val googleIdOptionAuth = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val requestAuth = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionAuth)
                    .build()

                credentialResponse = credentialManager.getCredential(context, requestAuth)
            } catch (e: GetCredentialCancellationException) {
                return AuthResult.Error("Google linking cancelled.")
            } catch (e: Exception) {
                Log.d("AuthManager", "Authorized account linking lookup failed (${e.message}), retrying with all accounts...")
            }

            // Stage 2: Fallback to all Google accounts
            if (credentialResponse == null) {
                val googleIdOptionAll = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val requestAll = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionAll)
                    .build()

                credentialResponse = credentialManager.getCredential(context, requestAll)
            }

            val credential = credentialResponse?.credential ?: return AuthResult.Error("No credential returned from Google.")

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val linkResult = currentUser.linkWithCredential(authCredential).await()
                val user = linkResult.user ?: return AuthResult.Error("Failed to link Google account.")
                AuthResult.Success(mapFirebaseUser(user))
            } else {
                AuthResult.Error("Invalid Google credential received.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("This Google account is already linked to another user. Please log in with that account.")
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Error("Google linking cancelled.")
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    /**
     * Sign out
     */
    fun signOut() {
        auth?.signOut()
    }

    /**
     * Delete active Firebase account
     */
    suspend fun deleteAccount(): AuthResult<Unit> {
        val fa = auth ?: return AuthResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val user = fa.currentUser ?: return AuthResult.Error("No user logged in.")
        return try {
            user.delete().await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    private fun getWebClientIdFromResources(): String? {
        return try {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (id != 0) context.getString(id) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun friendlyErrorMessage(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No account found with this email. Please check your email or sign up."
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password. Please verify your credentials."
            is FirebaseAuthUserCollisionException -> "An account with this email already exists. Please log in instead."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 6 characters with letters and numbers."
            is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already in use by another account."
                "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again or reset your password."
                "ERROR_USER_NOT_FOUND" -> "No user found with this email."
                "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again after a few minutes."
                "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is not enabled in Firebase Console. Please enable Anonymous / Email / Google in Authentication > Sign-in method."
                else -> e.localizedMessage ?: "Authentication failed. Please try again."
            }
            else -> {
                val msg = e.localizedMessage ?: ""
                if (msg.contains("network", ignoreCase = true) || msg.contains("connection", ignoreCase = true)) {
                    "Network error. Please check your internet connection and try again."
                } else {
                    msg.ifBlank { "An unexpected error occurred. Please try again." }
                }
            }
        }
    }
}
