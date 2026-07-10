package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.example.data.FirebaseManager
import com.example.ui.AppNavigationContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase config dynamic loader
        FirebaseManager.initialize(applicationContext)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        // Add observer to manage floating bubble overlays when entering or leaving background
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.startOverlayService(this)
                }
                Lifecycle.Event.ON_START -> {
                    viewModel.stopOverlayService(this)
                }
                else -> {}
            }
        })

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigationContainer(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::viewModel.isInitialized) {
            viewModel.setTypingStatus(false)
            com.example.data.ChatRepository.updatePresence(true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::viewModel.isInitialized) {
            com.example.data.ChatRepository.updatePresence(false)
        }
    }
}
