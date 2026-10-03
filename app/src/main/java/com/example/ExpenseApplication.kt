package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class ExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.i("ExpenseApplication", "FirebaseApp initialized.")
            }
        } catch (e: Exception) {
            Log.w("ExpenseApplication", "Firebase initialization deferred or google-services.json not found: ${e.message}")
        }
    }
}
