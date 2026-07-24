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
import com.example.data.FirebaseManager
import com.example.service.FloatingBubbleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN, SIGNUP, DASHBOARD, CHAT, VERIFY_EMAIL
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    // View States
    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Automatically route to DASHBOARD if there is already a restored user session!
        if (ChatRepository.currentUser.value != null) {
            val isVerified = if (FirebaseManager.isFirebaseAvailable) {
                FirebaseManager.auth?.currentUser?.isEmailVerified == true
            } else {
                true
            }
            if (isVerified) {
                _currentScreen.value = AppScreen.DASHBOARD
            } else {
                _currentScreen.value = AppScreen.VERIFY_EMAIL
            }
        }
    }

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

    fun getCustomDatabaseUrl(): String {
        return FirebaseManager.customDatabaseUrl ?: ""
    }

    fun updateDatabaseUrl(url: String) {
        val context = getApplication<Application>()
        FirebaseManager.updateDatabaseUrl(context, url.ifBlank { null })
    }

    fun signUp(email: String, username: String, passwordInput: String) {
        if (email.trim().isEmpty() || username.trim().isEmpty()) {
            _errorMessage.value = "Email and Username cannot be empty."
            return
        }
        if (passwordInput.trim().isEmpty()) {
            _errorMessage.value = "Password cannot be empty."
            return
        }
        _isLoading.value = true
        ChatRepository.signUp(email, username, passwordInput) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    val isVerified = if (FirebaseManager.isFirebaseAvailable) {
                        FirebaseManager.auth?.currentUser?.isEmailVerified == true
                    } else {
                        true
                    }
                    if (isVerified) {
                        _currentScreen.value = AppScreen.DASHBOARD
                    } else {
                        _currentScreen.value = AppScreen.VERIFY_EMAIL
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Registration failed."
                }
            )
        }
    }

    fun login(email: String, username: String, passwordInput: String) {
        if (email.trim().isEmpty() && username.trim().isEmpty()) {
            _errorMessage.value = "Enter your Username or Email to Login."
            return
        }
        if (passwordInput.trim().isEmpty()) {
            _errorMessage.value = "Enter your Password to Login."
            return
        }
        _isLoading.value = true
        ChatRepository.login(email, username, passwordInput) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    val isVerified = if (FirebaseManager.isFirebaseAvailable) {
                        FirebaseManager.auth?.currentUser?.isEmailVerified == true
                    } else {
                        true
                    }
                    if (isVerified) {
                        _currentScreen.value = AppScreen.DASHBOARD
                    } else {
                        _currentScreen.value = AppScreen.VERIFY_EMAIL
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Authentication failed."
                }
            )
        }
    }

    fun checkEmailVerificationStatus(onComplete: (Boolean) -> Unit) {
        if (FirebaseManager.isFirebaseAvailable) {
            _isLoading.value = true
            val currentUser = FirebaseManager.auth?.currentUser
            if (currentUser != null) {
                currentUser.reload()
                    ?.addOnSuccessListener {
                        _isLoading.value = false
                        val verified = currentUser.isEmailVerified
                        if (verified) {
                            _currentScreen.value = AppScreen.DASHBOARD
                        }
                        onComplete(verified)
                    }
                    ?.addOnFailureListener {
                        _isLoading.value = false
                        onComplete(currentUser.isEmailVerified)
                    } ?: run {
                        _isLoading.value = false
                        onComplete(false)
                    }
            } else {
                _isLoading.value = false
                onComplete(true)
            }
        } else {
            _currentScreen.value = AppScreen.DASHBOARD
            onComplete(true)
        }
    }

    fun resendVerificationEmail(onComplete: (Boolean) -> Unit) {
        if (FirebaseManager.isFirebaseAvailable) {
            _isLoading.value = true
            val currentUser = FirebaseManager.auth?.currentUser
            if (currentUser != null) {
                currentUser.sendEmailVerification()
                    ?.addOnSuccessListener {
                        _isLoading.value = false
                        onComplete(true)
                    }
                    ?.addOnFailureListener {
                        _isLoading.value = false
                        onComplete(false)
                    } ?: run {
                        _isLoading.value = false
                        onComplete(false)
                    }
            } else {
                _isLoading.value = false
                onComplete(false)
            }
        } else {
            onComplete(true)
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        if (email.trim().isEmpty()) {
            onResult(Result.failure(Exception("Email cannot be empty.")))
            return
        }
        _isLoading.value = true
        if (FirebaseManager.isFirebaseAvailable) {
            FirebaseManager.auth?.sendPasswordResetEmail(email.trim())
                ?.addOnSuccessListener {
                    _isLoading.value = false
                    onResult(Result.success(Unit))
                }
                ?.addOnFailureListener { exception ->
                    _isLoading.value = false
                    onResult(Result.failure(exception))
                } ?: run {
                    _isLoading.value = false
                    onResult(Result.failure(Exception("Firebase Auth is not available.")))
                }
        } else {
            _isLoading.value = false
            onResult(Result.success(Unit))
        }
    }

    fun resetPasswordMock(email: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        if (email.trim().isEmpty() || newPassword.trim().isEmpty()) {
            onResult(Result.failure(Exception("Email and password cannot be empty.")))
            return
        }
        _isLoading.value = true
        ChatRepository.resetPasswordMock(email.trim(), newPassword) { result ->
            _isLoading.value = false
            onResult(result)
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

    fun updateAvatarUrl(newAvatarUrl: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        _isLoading.value = true
        ChatRepository.updateAvatarUrl(newAvatarUrl) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = {
                    _errorMessage.value = "Profile picture updated successfully!"
                    onComplete?.invoke(true, null)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Avatar update failed."
                    onComplete?.invoke(false, error.message)
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
    fun clearUnreadCount(chatId: String) {
        ChatRepository.clearUnreadCount(chatId)
    }

    fun openChat(chatId: String) {
        ChatRepository.clearUnreadCount(chatId)
        _activeChatId.value = chatId
        ChatRepository.observeChatMessages(chatId)
        _currentScreen.value = AppScreen.CHAT
    }

    fun startChatWith(friendUid: String) {
        val chatId = ChatRepository.startChatWith(friendUid)
        ChatRepository.clearUnreadCount(chatId)
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
    fun startOverlayService(context: Context): Boolean {
        val activeUser = currentUser.value ?: return false
        val canDraw = try { Settings.canDrawOverlays(context) } catch (e: Exception) { false }
        if (canDraw) {
            try {
                val intent = Intent(context, FloatingBubbleService::class.java).apply {
                    _activeChatId.value?.let { putExtra(FloatingBubbleService.EXTRA_CHAT_ID, it) }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                return true
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to start overlay service", e)
            }
        }
        return false
    }

    fun stopOverlayService(context: Context) {
        try {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "Failed to stop overlay service", e)
        }
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return try { Settings.canDrawOverlays(context) } catch (e: Exception) { false }
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
