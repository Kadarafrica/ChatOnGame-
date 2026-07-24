package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.FirebaseManager
import com.example.ui.AppNavigationContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.NotificationHelper
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Notification permission granted or denied
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this)

        // Prompt user for POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize Firebase config dynamic loader
        FirebaseManager.initialize(applicationContext)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        handleNotificationIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigationContainer(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val chatId = intent?.getStringExtra("CHAT_ID") ?: intent?.getStringExtra("chatId")
        if (!chatId.isNullOrEmpty() && ::viewModel.isInitialized) {
            viewModel.openChat(chatId)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::viewModel.isInitialized) {
            viewModel.stopOverlayService(this)
            viewModel.setTypingStatus(false)
            com.example.data.ChatRepository.updatePresence(true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::viewModel.isInitialized) {
            val isOverlayStarted = viewModel.startOverlayService(this)
            if (!isOverlayStarted) {
                com.example.data.ChatRepository.updatePresence(false)
            }
        }
    }
}
