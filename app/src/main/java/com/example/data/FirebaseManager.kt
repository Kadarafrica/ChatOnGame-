package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    var isFirebaseAvailable: Boolean = false
        private set

    fun initialize(context: Context) {
        try {
            val resId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
            val compiledAppId = if (resId != 0) context.getString(resId) else null
            
            if (!compiledAppId.isNullOrEmpty()) {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                isFirebaseAvailable = true
                Log.d(TAG, "Firebase initialized successfully with google-services.json.")
            } else {
                isFirebaseAvailable = false
                Log.w(TAG, "Firebase google_app_id not found. App running in robust local offline mode.")
            }
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.e(TAG, "Failed to initialize Firebase: ${e.message}. Falling back to offline mode.")
        }
        // Initialize local persistence database backup
        ChatRepository.initialize(context)
    }

    val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) FirebaseAuth.getInstance() else null

    val database: FirebaseDatabase?
        get() = if (isFirebaseAvailable) {
            FirebaseDatabase.getInstance().apply {
                try {
                    setPersistenceEnabled(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Database persistence enable failed: ${e.message}")
                }
            }
        } else null

    val storage: FirebaseStorage?
        get() = if (isFirebaseAvailable) FirebaseStorage.getInstance() else null

    val messaging: FirebaseMessaging?
        get() = if (isFirebaseAvailable) FirebaseMessaging.getInstance() else null
}
