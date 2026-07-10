package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.FriendRequest
import com.example.data.User
import com.example.service.FloatingBubbleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN, SIGNUP, DASHBOARD, CHAT
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    // View States
    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Expose data flows from repository
    val currentUser = ChatRepository.currentUser
    val friends = ChatRepository.friends
    val friendRequests = ChatRepository.friendRequests
    val recentChats = ChatRepository.recentChats
    val activeChatMessages = ChatRepository.activeChatMessages
    val typingState = ChatRepository.typingState

    // Search query for adding friends
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signUp(email: String, username: String) {
        if (email.trim().isEmpty() || username.trim().isEmpty()) {
            _errorMessage.value = "Email and Username cannot be empty."
            return
        }
        _isLoading.value = true
        ChatRepository.signUp(email, username) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    _currentScreen.value = AppScreen.DASHBOARD
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Registration failed."
                }
            )
        }
    }

    fun login(email: String, username: String) {
        if (email.trim().isEmpty() && username.trim().isEmpty()) {
            _errorMessage.value = "Enter your Username or Email to Login."
            return
        }
        _isLoading.value = true
        ChatRepository.login(email, username) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    _currentScreen.value = AppScreen.DASHBOARD
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Authentication failed."
                }
            )
        }
    }

    fun logout() {
        ChatRepository.logout()
        _activeChatId.value = null
        _currentScreen.value = AppScreen.LOGIN
    }

    fun updateUsername(newUsername: String) {
        _isLoading.value = true
        ChatRepository.updateUsername(newUsername) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    _errorMessage.value = "Username changed successfully!"
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Username update failed."
                }
            )
        }
    }

    // Friends functionality
    fun sendFriendRequest() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            _errorMessage.value = "Type a Username or Friend ID to search."
            return
        }
        _isLoading.value = true
        ChatRepository.sendFriendRequest(query) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    _searchQuery.value = ""
                    _errorMessage.value = "Friend request sent successfully!"
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to send friend request."
                }
            )
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        ChatRepository.acceptFriendRequest(request)
    }

    fun rejectFriendRequest(request: FriendRequest) {
        ChatRepository.rejectFriendRequest(request)
    }

    fun removeFriend(friendUid: String) {
        ChatRepository.removeFriend(friendUid)
    }

    // Chat functionality
    fun startChatWith(friendUid: String) {
        val chatId = ChatRepository.startChatWith(friendUid)
        _activeChatId.value = chatId
        ChatRepository.observeChatMessages(chatId)
        _currentScreen.value = AppScreen.CHAT
    }

    fun closeChat() {
        ChatRepository.cleanupMessagesListener()
        _activeChatId.value = null
        _currentScreen.value = AppScreen.DASHBOARD
    }

    fun sendMessage(text: String) {
        val chatId = _activeChatId.value ?: return
        if (text.trim().isEmpty()) return
        ChatRepository.sendMessage(chatId, text.trim())
    }

    fun setTypingStatus(isTyping: Boolean) {
        val chatId = _activeChatId.value ?: return
        ChatRepository.setTypingStatus(chatId, isTyping)
    }

    // Overlay bubble trigger
    fun startOverlayService(context: Context) {
        val activeUser = currentUser.value ?: return
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                _activeChatId.value?.let { putExtra(FloatingBubbleService.EXTRA_CHAT_ID, it) }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopOverlayService(context: Context) {
        val intent = Intent(context, FloatingBubbleService::class.java)
        context.stopService(intent)
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
