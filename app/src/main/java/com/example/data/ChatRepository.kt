package com.example.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.utils.BadgeUtils
import com.example.utils.NotificationHelper

object ChatRepository {
    private const val TAG = "ChatRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Current app state flows
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()

    private val _recentChats = MutableStateFlow<List<Chat>>(emptyList())
    val recentChats: StateFlow<List<Chat>> = _recentChats.asStateFlow()

    private val _activeChatMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeChatMessages: StateFlow<List<Message>> = _activeChatMessages.asStateFlow()

    // Key: userId, Value: typing target userId (or empty string/null if not typing)
    private val _typingState = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingState: StateFlow<Map<String, String>> = _typingState.asStateFlow()

    // Listeners cache for Firebase cleanup
    private var messagesListener: ValueEventListener? = null
    private var messagesReferencePath: String? = null
    private var friendsListener: ValueEventListener? = null
    private var requestsListener: ValueEventListener? = null
    private var recentChatsListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null
    private val chatUnreadListeners = mutableMapOf<String, ValueEventListener>()

    private fun updateRecentChats(chats: List<Chat>) {
        val sorted = chats.sortedByDescending { it.lastMessageTimestamp }
        _recentChats.value = sorted
        val totalUnread = sorted.sumOf { it.unreadCount }
        BadgeUtils.updateBadge(appContext, totalUnread)
    }

    fun refreshMockChats(uid: String) {
        val updatedChats = mockChats.filter { it.participantUids.contains(uid) }.map { chat ->
            val unread = mockMessages.count { it.chatId == chat.id && it.receiverId == uid && it.status != "read" }
            val lastMsg = mockMessages.filter { it.chatId == chat.id }.maxByOrNull { it.timestamp }
            val lastText = lastMsg?.text ?: chat.lastMessageText
            val lastTime = lastMsg?.timestamp ?: chat.lastMessageTimestamp
            chat.copy(
                unreadCount = unread,
                lastMessageText = lastText,
                lastMessageTimestamp = lastTime
            )
        }
        updateRecentChats(updatedChats)
    }

    // Fallback Mock database for offline/demo operation
    private val mockUsers = mutableMapOf<String, User>()
    private val mockPasswords = mutableMapOf<String, String>()
    private val mockFriends = mutableMapOf<String, MutableSet<String>>() // uid -> set of friend uids
    private val mockFriendRequests = mutableListOf<FriendRequest>()
    private val mockChats = mutableListOf<Chat>()
    private val mockMessages = mutableListOf<Message>()

    init {
        // Init mock database
    }

    private var appContext: Context? = null

    private val moshi by lazy {
        Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadLocalState()
        
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val request = android.net.NetworkRequest.Builder().build()
                cm.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        updatePresence(true)
                    }

                    override fun onLost(network: android.net.Network) {
                        updatePresence(false)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network callback setup failed: ${e.message}")
        }
    }

    fun saveLocalState() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("chatongame_local_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        try {
            // 1. Current user
            val currentUserVal = _currentUser.value
            if (currentUserVal != null) {
                val userAdapter = moshi.adapter(User::class.java)
                editor.putString("current_user_json", userAdapter.toJson(currentUserVal))
            } else {
                editor.remove("current_user_json")
            }

            // 2. Mock users
            val usersList = mockUsers.values.toList()
            val usersType = Types.newParameterizedType(List::class.java, User::class.java)
            val usersAdapter = moshi.adapter<List<User>>(usersType)
            editor.putString("mock_users_json", usersAdapter.toJson(usersList))

            // 3. Mock friends map (Map<String, Set<String>>) -> Map<String, List<String>>
            val friendsMapList = mockFriends.mapValues { it.value.toList() }
            val friendsType = Types.newParameterizedType(Map::class.java, String::class.java, List::class.java)
            val friendsAdapter = moshi.adapter<Map<String, List<String>>>(friendsType)
            editor.putString("mock_friends_json", friendsAdapter.toJson(friendsMapList))

            // 4. Mock friend requests
            val requestsType = Types.newParameterizedType(List::class.java, FriendRequest::class.java)
            val requestsAdapter = moshi.adapter<List<FriendRequest>>(requestsType)
            editor.putString("mock_friend_requests_json", requestsAdapter.toJson(mockFriendRequests))

            // 5. Mock chats
            val chatsType = Types.newParameterizedType(List::class.java, Chat::class.java)
            val chatsAdapter = moshi.adapter<List<Chat>>(chatsType)
            editor.putString("mock_chats_json", chatsAdapter.toJson(mockChats))

            // 6. Mock messages
            val messagesType = Types.newParameterizedType(List::class.java, Message::class.java)
            val messagesAdapter = moshi.adapter<List<Message>>(messagesType)
            editor.putString("mock_messages_json", messagesAdapter.toJson(mockMessages))

            // 7. Mock passwords
            val passwordsType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val passwordsAdapter = moshi.adapter<Map<String, String>>(passwordsType)
            editor.putString("mock_passwords_json", passwordsAdapter.toJson(mockPasswords))

            editor.apply()
            Log.d(TAG, "Local state successfully saved to SharedPreferences.")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local state to SharedPreferences", e)
        }
    }

    private fun loadLocalState() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("chatongame_local_prefs", Context.MODE_PRIVATE)

        try {
            // 1. Mock users
            val usersJson = prefs.getString("mock_users_json", null)
            if (!usersJson.isNullOrEmpty()) {
                val usersType = Types.newParameterizedType(List::class.java, User::class.java)
                val usersAdapter = moshi.adapter<List<User>>(usersType)
                val list = usersAdapter.fromJson(usersJson)
                if (list != null) {
                    mockUsers.clear()
                    for (user in list) {
                        if (!user.uid.startsWith("bot_")) {
                            mockUsers[user.uid] = user
                        }
                    }
                }
            }

            // 2. Mock friends
            val friendsJson = prefs.getString("mock_friends_json", null)
            if (!friendsJson.isNullOrEmpty()) {
                val friendsType = Types.newParameterizedType(Map::class.java, String::class.java, List::class.java)
                val friendsAdapter = moshi.adapter<Map<String, List<String>>>(friendsType)
                val map = friendsAdapter.fromJson(friendsJson)
                if (map != null) {
                    mockFriends.clear()
                    for ((k, v) in map) {
                        mockFriends[k] = v.filter { !it.startsWith("bot_") }.toMutableSet()
                    }
                }
            }

            // 3. Mock friend requests
            val requestsJson = prefs.getString("mock_friend_requests_json", null)
            if (!requestsJson.isNullOrEmpty()) {
                val requestsType = Types.newParameterizedType(List::class.java, FriendRequest::class.java)
                val requestsAdapter = moshi.adapter<List<FriendRequest>>(requestsType)
                val list = requestsAdapter.fromJson(requestsJson)
                if (list != null) {
                    mockFriendRequests.clear()
                    mockFriendRequests.addAll(list.filter { !it.fromUid.startsWith("bot_") && !it.toUid.startsWith("bot_") })
                }
            }

            // 4. Mock chats
            val chatsJson = prefs.getString("mock_chats_json", null)
            if (!chatsJson.isNullOrEmpty()) {
                val chatsType = Types.newParameterizedType(List::class.java, Chat::class.java)
                val chatsAdapter = moshi.adapter<List<Chat>>(chatsType)
                val list = chatsAdapter.fromJson(chatsJson)
                if (list != null) {
                    mockChats.clear()
                    mockChats.addAll(list.filter { chat -> chat.participantUids.none { it.startsWith("bot_") } })
                }
            }

            // 5. Mock messages
            val messagesJson = prefs.getString("mock_messages_json", null)
            if (!messagesJson.isNullOrEmpty()) {
                val messagesType = Types.newParameterizedType(List::class.java, Message::class.java)
                val messagesAdapter = moshi.adapter<List<Message>>(messagesType)
                val list = messagesAdapter.fromJson(messagesJson)
                if (list != null) {
                    mockMessages.clear()
                    mockMessages.addAll(list.filter { !it.senderId.startsWith("bot_") && !it.chatId.contains("bot_") })
                }
            }

            // 6. Current user
            val currentUserJson = prefs.getString("current_user_json", null)
            if (!currentUserJson.isNullOrEmpty()) {
                val userAdapter = moshi.adapter(User::class.java)
                val user = userAdapter.fromJson(currentUserJson)
                if (user != null) {
                    // Update user as online
                    val updatedUser = user.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                    mockUsers[user.uid] = updatedUser
                    _currentUser.value = updatedUser
                    
                    if (FirebaseManager.isFirebaseAvailable) {
                        startObservingRealtimeData(user.uid)
                    } else {
                        startObservingMockData(user.uid)
                    }
                    Log.d(TAG, "Restored local user session: ${user.username}")
                }
            }

            // 7. Mock passwords
            val passwordsJson = prefs.getString("mock_passwords_json", null)
            if (!passwordsJson.isNullOrEmpty()) {
                val passwordsType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                val passwordsAdapter = moshi.adapter<Map<String, String>>(passwordsType)
                val map = passwordsAdapter.fromJson(passwordsJson)
                if (map != null) {
                    mockPasswords.clear()
                    mockPasswords.putAll(map)
                }
            }

            Log.d(TAG, "Local state successfully loaded from SharedPreferences.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local state from SharedPreferences", e)
        }
    }

    // Helper: generate unique Friend ID (e.g. FC-X4M81Q)
    fun generateFriendId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomStr = (1..6)
            .map { chars[java.util.Random().nextInt(chars.length)] }
            .joinToString("")
        return "FC-$randomStr"
    }

    fun resetPasswordMock(email: String, passwordInput: String, onResult: (Result<Unit>) -> Unit) {
        val user = mockUsers.values.find { it.email.equals(email, ignoreCase = true) }
        if (user != null) {
            mockPasswords[user.uid] = passwordInput
            saveLocalState()
            onResult(Result.success(Unit))
        } else {
            onResult(Result.failure(Exception("No user found with email: $email")))
        }
    }

    fun signUp(email: String, usernameInput: String, passwordInput: String, onResult: (Result<User>) -> Unit) {
        val username = usernameInput.trim()
        if (username.isEmpty()) {
            onResult(Result.failure(Exception("Username cannot be empty")))
            return
        }
        if (passwordInput.length < 6) {
            onResult(Result.failure(Exception("Password must be at least 6 characters long.")))
            return
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val auth = FirebaseManager.auth ?: return
            val db = FirebaseManager.database?.reference ?: return

            // 1. First check username uniqueness in database
            db.child("usernames").child(username).get().withTimeout { task ->
                if (task.isSuccessful && task.result.exists()) {
                    onResult(Result.failure(Exception("Username is already taken.")))
                } else if (!task.isSuccessful) {
                    onResult(Result.failure(task.exception ?: Exception("Failed to check username uniqueness.")))
                } else {
                    // Username unique, proceed with creating Firebase user
                    auth.createUserWithEmailAndPassword(email, passwordInput).addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            val fbUser = authTask.result.user
                            fbUser?.sendEmailVerification() // Send verification email upon sign up
                            val uid = fbUser?.uid ?: ""
                            val friendId = generateFriendId()
                            val newUser = User(uid, username, email, friendId, true, System.currentTimeMillis())

                            // Write to user lists and lookup indices
                            val updates = mapOf(
                                "/users/$uid" to newUser,
                                "/usernames/$username" to uid,
                                "/friendIds/$friendId" to uid
                            )

                            db.updateChildren(updates).withTimeout { dbTask ->
                                if (dbTask.isSuccessful) {
                                    _currentUser.value = newUser
                                    onResult(Result.success(newUser))
                                    startObservingRealtimeData(uid)
                                    saveLocalState()
                                } else {
                                    onResult(Result.failure(dbTask.exception ?: Exception("Failed to write user database details.")))
                                }
                            }
                        } else {
                            onResult(Result.failure(authTask.exception ?: Exception("Firebase Sign Up failed.")))
                        }
                    }
                }
            }
        } else {
            // Local fallback sign up
            if (mockUsers.values.any { it.username.equals(username, ignoreCase = true) }) {
                onResult(Result.failure(Exception("Username is already taken.")))
                return
            }
            val uid = "user_" + UUID.randomUUID().toString().take(8)
            val friendId = generateFriendId()
            val newUser = User(uid, username, email, friendId, true, System.currentTimeMillis())

            mockUsers[uid] = newUser
            mockPasswords[uid] = passwordInput
            _currentUser.value = newUser
            onResult(Result.success(newUser))
            startObservingMockData(uid)
            saveLocalState()
        }
    }

    fun login(email: String, usernameInput: String, passwordInput: String, onResult: (Result<User>) -> Unit) {
        val username = usernameInput.trim()
        if (FirebaseManager.isFirebaseAvailable) {
            val auth = FirebaseManager.auth ?: return
            val db = FirebaseManager.database?.reference ?: return

            if (username.isNotEmpty()) {
                // Login via username - search case-insensitively in users node
                db.child("users").get().withTimeout { usersTask ->
                    var foundUid: String? = null
                    if (usersTask.isSuccessful && usersTask.result.exists()) {
                        for (child in usersTask.result.children) {
                            val u = child.getValue(User::class.java)
                            if (u != null && u.username.equals(username, ignoreCase = true)) {
                                foundUid = u.uid
                                break
                            }
                        }
                    } else if (!usersTask.isSuccessful) {
                        onResult(Result.failure(usersTask.exception ?: Exception("Error checking users database.")))
                        return@withTimeout
                    }
                    
                    if (foundUid != null) {
                        db.child("users").child(foundUid).get().withTimeout { userTask ->
                            if (userTask.isSuccessful) {
                                val user = userTask.result.getValue(User::class.java)
                                if (user != null) {
                                    auth.signInWithEmailAndPassword(user.email, passwordInput).addOnCompleteListener { authTask ->
                                        if (authTask.isSuccessful) {
                                            _currentUser.value = user
                                            onResult(Result.success(user))
                                            startObservingRealtimeData(foundUid)
                                            saveLocalState()
                                        } else {
                                            onResult(Result.failure(authTask.exception ?: Exception("Auth signin failed.")))
                                        }
                                    }
                                } else {
                                    onResult(Result.failure(Exception("User details not found.")))
                                }
                            } else {
                                onResult(Result.failure(userTask.exception ?: Exception("Error retrieving user info.")))
                            }
                        }
                    } else {
                        // Fallback to exact case usernames child if users query wasn't successful
                        db.child("usernames").child(username).get().withTimeout { task ->
                            if (task.isSuccessful && task.result.exists()) {
                                val uid = task.result.value as String
                                db.child("users").child(uid).get().withTimeout { userTask ->
                                    if (userTask.isSuccessful) {
                                        val user = userTask.result.getValue(User::class.java)
                                        if (user != null) {
                                            auth.signInWithEmailAndPassword(user.email, passwordInput).addOnCompleteListener { authTask ->
                                                if (authTask.isSuccessful) {
                                                    _currentUser.value = user
                                                    onResult(Result.success(user))
                                                    startObservingRealtimeData(uid)
                                                    saveLocalState()
                                                } else {
                                                    onResult(Result.failure(authTask.exception ?: Exception("Auth signin failed.")))
                                                }
                                            }
                                        } else {
                                            onResult(Result.failure(Exception("User details not found.")))
                                        }
                                    } else {
                                        onResult(Result.failure(userTask.exception ?: Exception("Error retrieving user info.")))
                                    }
                                }
                            } else if (!task.isSuccessful) {
                                onResult(Result.failure(task.exception ?: Exception("Error checking username database.")))
                            } else {
                                onResult(Result.failure(Exception("Username does not exist. Please sign up.")))
                            }
                        }
                    }
                }
            } else {
                // Login via email
                auth.signInWithEmailAndPassword(email, passwordInput).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val uid = authTask.result.user?.uid ?: ""
                        db.child("users").child(uid).get().withTimeout { userTask ->
                            if (userTask.isSuccessful) {
                                val user = userTask.result.getValue(User::class.java)
                                if (user != null) {
                                    _currentUser.value = user
                                    onResult(Result.success(user))
                                    startObservingRealtimeData(uid)
                                    saveLocalState()
                                } else {
                                    onResult(Result.failure(Exception("User details not found in database.")))
                                }
                            } else {
                                onResult(Result.failure(userTask.exception ?: Exception("Error retrieving user info from database.")))
                            }
                        }
                    } else {
                        onResult(Result.failure(authTask.exception ?: Exception("Email login failed.")))
                    }
                }
            }
        } else {
            // Local Mock Login
            val user = if (username.isNotEmpty()) {
                mockUsers.values.find { it.username.equals(username, ignoreCase = true) }
            } else {
                mockUsers.values.find { it.email.equals(email, ignoreCase = true) }
            }

            if (user != null) {
                if (!user.uid.startsWith("bot_")) {
                    val savedPassword = mockPasswords[user.uid]
                    if (savedPassword != null && savedPassword != passwordInput) {
                        onResult(Result.failure(Exception("Incorrect password.")))
                        return
                    } else if (savedPassword == null) {
                        mockPasswords[user.uid] = passwordInput
                    }
                }
                val updatedUser = user.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                mockUsers[user.uid] = updatedUser
                _currentUser.value = updatedUser
                onResult(Result.success(updatedUser))
                startObservingMockData(user.uid)
                saveLocalState()
            } else {
                onResult(Result.failure(Exception("Account not found. Please sign up first.")))
            }
        }
    }

    fun logout() {
        val user = _currentUser.value ?: return
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("users").child(user.uid).child("isOnline").setValue(false)
            db.child("users").child(user.uid).child("lastSeen").setValue(System.currentTimeMillis())
            FirebaseManager.auth?.signOut()
        } else {
            mockUsers[user.uid] = user.copy(isOnline = false, lastSeen = System.currentTimeMillis())
        }

        cleanupListeners()
        _currentUser.value = null
        _friends.value = emptyList()
        _friendRequests.value = emptyList()
        _recentChats.value = emptyList()
        _activeChatMessages.value = emptyList()
        saveLocalState()
    }

    fun updateUsername(newUsername: String, onResult: (Result<Unit>) -> Unit) {
        val user = _currentUser.value ?: return
        val cleanName = newUsername.trim()
        if (cleanName.isEmpty()) {
            onResult(Result.failure(Exception("Username cannot be empty.")))
            return
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            // Check uniqueness of new name
            db.child("usernames").child(cleanName).get().addOnCompleteListener { checkTask ->
                if (checkTask.isSuccessful && checkTask.result.exists()) {
                    onResult(Result.failure(Exception("Username already taken.")))
                } else {
                    // Remove old index & add new index
                    val oldName = user.username
                    val updates = mapOf(
                        "/users/${user.uid}/username" to cleanName,
                        "/usernames/$cleanName" to user.uid,
                        "/usernames/$oldName" to null
                    )
                    db.updateChildren(updates).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            val updatedUser = user.copy(username = cleanName)
                            _currentUser.value = updatedUser
                            onResult(Result.success(Unit))
                            saveLocalState()
                        } else {
                            onResult(Result.failure(updateTask.exception ?: Exception("Failed to update username.")))
                        }
                    }
                }
            }
        } else {
            // Local fallback
            if (mockUsers.values.any { it.username.equals(cleanName, ignoreCase = true) && it.uid != user.uid }) {
                onResult(Result.failure(Exception("Username already taken.")))
                return
            }
            val updatedUser = user.copy(username = cleanName)
            mockUsers[user.uid] = updatedUser
            _currentUser.value = updatedUser
            onResult(Result.success(Unit))
            saveLocalState()
        }
    }

    fun updateAvatarUrl(newAvatarUrl: String, onResult: (Result<Unit>) -> Unit) {
        val user = _currentUser.value ?: return
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("users").child(user.uid).child("avatarUrl").setValue(newAvatarUrl)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val updatedUser = user.copy(avatarUrl = newAvatarUrl)
                        _currentUser.value = updatedUser
                        mockUsers[user.uid] = updatedUser
                        saveLocalState()
                        onResult(Result.success(Unit))
                    } else {
                        onResult(Result.failure(task.exception ?: Exception("Failed to update avatar.")))
                    }
                }
        } else {
            val updatedUser = user.copy(avatarUrl = newAvatarUrl)
            _currentUser.value = updatedUser
            mockUsers[user.uid] = updatedUser
            saveLocalState()
            onResult(Result.success(Unit))
        }
    }

    // Friend requests logic
    fun sendFriendRequest(query: String, onResult: (Result<Unit>) -> Unit) {
        val user = _currentUser.value ?: return
        val searchQuery = query.trim()

        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return

            // Search users node case-insensitively for username or friendId
            db.child("users").get().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.exists()) {
                    var targetUid: String? = null
                    for (child in task.result.children) {
                        val u = child.getValue(User::class.java)
                        if (u != null) {
                            if (u.username.equals(searchQuery, ignoreCase = true) || 
                                u.friendId.equals(searchQuery, ignoreCase = true)) {
                                targetUid = u.uid
                                break
                            }
                        }
                    }
                    
                    if (targetUid != null) {
                        createAndSendRequest(user, targetUid, onResult)
                    } else {
                        onResult(Result.failure(Exception("No user found with Username or Friend ID: $searchQuery")))
                    }
                } else {
                    // Fallback to old path lookup just in case users list is empty or fails
                    db.child("usernames").child(searchQuery).get().addOnCompleteListener { userTask ->
                        if (userTask.isSuccessful && userTask.result.exists()) {
                            val uid = userTask.result.value as String
                            createAndSendRequest(user, uid, onResult)
                        } else {
                            db.child("friendIds").child(searchQuery).get().addOnCompleteListener { friendTask ->
                                if (friendTask.isSuccessful && friendTask.result.exists()) {
                                    val uid = friendTask.result.value as String
                                    createAndSendRequest(user, uid, onResult)
                                } else {
                                    onResult(Result.failure(Exception("No user found with Username or Friend ID: $searchQuery")))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Mock Friend System
            val targetUser = mockUsers.values.find { (it.username.equals(searchQuery, ignoreCase = true) || it.friendId.equals(searchQuery, ignoreCase = true)) && it.uid != user.uid }

            if (targetUser != null) {
                // Check if already friends
                if (mockFriends[user.uid]?.contains(targetUser.uid) == true) {
                    onResult(Result.failure(Exception("You are already friends with ${targetUser.username}.")))
                    return
                }

                val requestId = "req_" + UUID.randomUUID().toString().take(8)
                val newRequest = FriendRequest(requestId, user.uid, user.username, targetUser.uid, "pending", System.currentTimeMillis())
                mockFriendRequests.add(newRequest)

                // Trigger mock state update
                triggerFriendRequestsUpdate(user.uid)
                onResult(Result.success(Unit))
                saveLocalState()

                // If target is an automated game bot, automatically accept the request after a realistic delay!
                if (targetUser.uid.startsWith("bot_")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        acceptFriendRequest(newRequest)
                    }, 2000)
                }
            } else {
                onResult(Result.failure(Exception("No user found with Username or Friend ID: $searchQuery")))
            }
        }
    }

    private fun createAndSendRequest(currentUser: User, targetUid: String, onResult: (Result<Unit>) -> Unit) {
        if (currentUser.uid == targetUid) {
            onResult(Result.failure(Exception("You cannot add yourself as a friend.")))
            return
        }

        val db = FirebaseManager.database?.reference ?: return
        // Check if already friends
        db.child("friends").child(currentUser.uid).child(targetUid).get().addOnCompleteListener { checkTask ->
            if (checkTask.isSuccessful && checkTask.result.exists()) {
                onResult(Result.failure(Exception("You are already friends with this user.")))
            } else {
                val requestId = db.child("friendRequests").child(targetUid).push().key ?: UUID.randomUUID().toString()
                val request = FriendRequest(requestId, currentUser.uid, currentUser.username, targetUid, "pending", System.currentTimeMillis())

                db.child("friendRequests").child(targetUid).child(requestId).setValue(request).addOnCompleteListener { reqTask ->
                    if (reqTask.isSuccessful) {
                        onResult(Result.success(Unit))
                    } else {
                        onResult(Result.failure(reqTask.exception ?: Exception("Failed to send friend request.")))
                    }
                }
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            val updates = mapOf(
                "/friends/${request.toUid}/${request.fromUid}" to true,
                "/friends/${request.fromUid}/${request.toUid}" to true,
                "/friendRequests/${request.toUid}/${request.id}" to null // Delete processed request
            )
            db.updateChildren(updates)
        } else {
            // Local fallback
            // Add to friend index lists
            mockFriends.getOrPut(request.toUid) { mutableSetOf() }.add(request.fromUid)
            mockFriends.getOrPut(request.fromUid) { mutableSetOf() }.add(request.toUid)

            // Remove request
            mockFriendRequests.removeAll { it.id == request.id }

            // Trigger updates
            triggerFriendRequestsUpdate(request.toUid)
            triggerFriendsUpdate(request.toUid)
            saveLocalState()
        }
    }

    fun rejectFriendRequest(request: FriendRequest) {
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("friendRequests").child(request.toUid).child(request.id).removeValue()
        } else {
            mockFriendRequests.removeAll { it.id == request.id }
            triggerFriendRequestsUpdate(request.toUid)
            saveLocalState()
        }
    }

    fun removeFriend(friendUid: String) {
        val user = _currentUser.value ?: return
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            val updates = mapOf(
                "/friends/${user.uid}/$friendUid" to null,
                "/friends/$friendUid/${user.uid}" to null
            )
            db.updateChildren(updates)
        } else {
            mockFriends[user.uid]?.remove(friendUid)
            mockFriends[friendUid]?.remove(user.uid)
            triggerFriendsUpdate(user.uid)
            saveLocalState()
        }
    }

    // Chat operations
    fun clearUnreadCount(chatId: String) {
        val user = _currentUser.value ?: return
        markMessagesAsRead(chatId)
        val index = mockChats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            mockChats[index] = mockChats[index].copy(unreadCount = 0)
        }
        if (!FirebaseManager.isFirebaseAvailable) {
            refreshMockChats(user.uid)
        }
        saveLocalState()
    }

    fun startChatWith(friendUid: String): String {
        val user = _currentUser.value ?: return ""
        // Unique Chat ID is sorted participant IDs joined
        val sortedUids = listOf(user.uid, friendUid).sorted()
        val chatId = "chat_${sortedUids[0]}_${sortedUids[1]}"

        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return chatId
            val chatRef = db.child("chats").child(chatId)
            chatRef.get().addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.exists()) {
                    val newChat = Chat(chatId, sortedUids, "", System.currentTimeMillis(), 0)
                    chatRef.setValue(newChat)
                }
            }
        } else {
            // Local Mock Chats
            val index = mockChats.indexOfFirst { it.id == chatId }
            if (index == -1) {
                val newChat = Chat(chatId, sortedUids, "No messages yet", System.currentTimeMillis(), 0)
                mockChats.add(newChat)
            } else {
                mockChats[index] = mockChats[index].copy(unreadCount = 0)
            }
            refreshMockChats(user.uid)
            saveLocalState()
        }
        return chatId
    }

    fun sendMessage(chatId: String, text: String) {
        val user = _currentUser.value ?: return
        val targetUid = chatId.replace("chat_", "").split("_").firstOrNull { it != user.uid } ?: return
        val messageId = "msg_" + UUID.randomUUID().toString().take(12)
        val message = Message(messageId, chatId, user.uid, targetUid, text, System.currentTimeMillis(), "sent")

        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            val updates = mapOf(
                "/messages/$chatId/$messageId" to message,
                "/chats/$chatId/lastMessageText" to text,
                "/chats/$chatId/lastMessageTimestamp" to System.currentTimeMillis()
            )
            db.updateChildren(updates)
        } else {
            // Local Fallback Mock Messaging
            mockMessages.add(message)
            _activeChatMessages.value = mockMessages.filter { it.chatId == chatId }.sortedBy { it.timestamp }

            // Update local chat listing
            val chatIndex = mockChats.indexOfFirst { it.id == chatId }
            if (chatIndex != -1) {
                mockChats[chatIndex] = mockChats[chatIndex].copy(
                    lastMessageText = text,
                    lastMessageTimestamp = System.currentTimeMillis()
                )
            } else {
                mockChats.add(Chat(chatId, listOf(user.uid, targetUid), text, System.currentTimeMillis(), 0))
            }
            refreshMockChats(user.uid)
            saveLocalState()
        }
    }

    private fun simulateBotReply(chatId: String, botUid: String) {
        // No bot replies
    }

    private fun updateMockMessageStatus(chatId: String, newStatus: String) {
        var updated = false
        for (i in mockMessages.indices) {
            if (mockMessages[i].chatId == chatId && mockMessages[i].senderId == _currentUser.value?.uid) {
                mockMessages[i] = mockMessages[i].copy(status = newStatus)
                updated = true
            }
        }
        if (updated) {
            _activeChatMessages.value = mockMessages.filter { it.chatId == chatId }.sortedBy { it.timestamp }
            saveLocalState()
        }
    }

    fun setTypingStatus(chatId: String, isTyping: Boolean) {
        val user = _currentUser.value ?: return
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("typing").child(chatId).child(user.uid).setValue(if (isTyping) "typing" else "")
        } else {
            // Handled locally
        }
    }

    fun observeChatMessages(chatId: String) {
        cleanupMessagesListener()
        
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            messagesReferencePath = "messages/$chatId"
            messagesListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messagesList = mutableListOf<Message>()
                    for (child in snapshot.children) {
                        val msg = child.getValue(Message::class.java)
                        if (msg != null) {
                            messagesList.add(msg)
                        }
                    }
                    _activeChatMessages.value = messagesList.sortedBy { it.timestamp }

                    // Automatically mark unread messages as read
                    val currentUid = _currentUser.value?.uid ?: ""
                    snapshot.children.forEach { child ->
                        val msg = child.getValue(Message::class.java)
                        if (msg != null && msg.receiverId == currentUid && msg.status != "read") {
                            child.ref.child("status").setValue("read")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to listen to messages: ${error.message}")
                }
            }
            db.child(messagesReferencePath!!).addValueEventListener(messagesListener!!)
        } else {
            // Local Fallback
            _activeChatMessages.value = mockMessages.filter { it.chatId == chatId }.sortedBy { it.timestamp }
        }
    }

    fun cleanupMessagesListener() {
        if (FirebaseManager.isFirebaseAvailable && messagesListener != null && messagesReferencePath != null) {
            FirebaseManager.database?.reference?.child(messagesReferencePath!!)?.removeEventListener(messagesListener!!)
        }
        messagesListener = null
        messagesReferencePath = null
        _activeChatMessages.value = emptyList()
    }

    fun markMessagesAsRead(chatId: String) {
        val user = _currentUser.value ?: return
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("messages").child(chatId).get().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.exists()) {
                    task.result.children.forEach { child ->
                        val msg = child.getValue(Message::class.java)
                        if (msg != null && msg.receiverId == user.uid && msg.status != "read") {
                            child.ref.child("status").setValue("read")
                        }
                    }
                }
            }
        } else {
            // Mock read updates
            updateMockMessageStatus(chatId, "read")
        }
    }

    fun updatePresence(isOnline: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
        _currentUser.value = updatedUser
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("users").child(user.uid).child("isOnline").setValue(isOnline)
            db.child("users").child(user.uid).child("lastSeen").setValue(System.currentTimeMillis())
        } else {
            mockUsers[user.uid] = updatedUser
            triggerFriendsUpdate(user.uid)
            saveLocalState()
        }
    }

    // Initialize listeners for active sessions
    private fun startObservingRealtimeData(uid: String) {
        cleanupListeners()
        val db = FirebaseManager.database?.reference ?: return

        // 1. Observe Friends list
        friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendUids = snapshot.children.mapNotNull { child -> child.key }
                if (friendUids.isEmpty()) {
                    _friends.value = emptyList()
                    return
                }

                // Resolve each friend profile
                val friendsList = mutableListOf<User>()
                var resolvedCount = 0
                for (friendUid in friendUids) {
                    db.child("users").child(friendUid).get().addOnCompleteListener { task ->
                        resolvedCount++
                        if (task.isSuccessful) {
                            val friendProfile = task.result.getValue(User::class.java)
                            if (friendProfile != null) {
                                friendsList.add(friendProfile)
                            }
                        }
                        if (resolvedCount == friendUids.size) {
                            _friends.value = friendsList
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Friends observer canceled: ${error.message}")
            }
        }
        db.child("friends").child(uid).addValueEventListener(friendsListener!!)

        // 2. Observe Friend Requests targeting me
        requestsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = snapshot.children.mapNotNull { it.getValue(FriendRequest::class.java) }
                _friendRequests.value = requests.filter { it.status == "pending" }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Requests observer canceled: ${error.message}")
            }
        }
        db.child("friendRequests").child(uid).addValueEventListener(requestsListener!!)

        // 3. Observe Recent Chats listing & dynamic unread counts
        recentChatsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatsList = mutableListOf<Chat>()
                for (child in snapshot.children) {
                    val chat = child.getValue(Chat::class.java)
                    if (chat != null && chat.participantUids.contains(uid)) {
                        chatsList.add(chat)
                    }
                }

                if (chatsList.isEmpty()) {
                    updateRecentChats(emptyList())
                    return
                }

                // Cleanup unread listeners for removed chats
                val currentChatIds = chatsList.map { it.id }.toSet()
                val toRemove = chatUnreadListeners.keys.filter { it !in currentChatIds }
                toRemove.forEach { chatId ->
                    chatUnreadListeners[chatId]?.let { listener ->
                        db.child("messages").child(chatId).removeEventListener(listener)
                    }
                    chatUnreadListeners.remove(chatId)
                }

                val chatsMap = chatsList.associateBy { it.id }.toMutableMap()

                for (chat in chatsList) {
                    if (!chatUnreadListeners.containsKey(chat.id)) {
                        val listener = object : ValueEventListener {
                            private val notifiedMsgIds = mutableSetOf<String>()

                            override fun onDataChange(msgSnapshot: DataSnapshot) {
                                var unread = 0
                                var maxTimestamp = 0L
                                var latestText = ""
                                for (msgNode in msgSnapshot.children) {
                                    val msg = msgNode.getValue(Message::class.java)
                                    if (msg != null) {
                                        if (msg.timestamp > maxTimestamp) {
                                            maxTimestamp = msg.timestamp
                                            latestText = msg.text
                                        }
                                        if (msg.receiverId == uid && msg.status != "read") {
                                            unread++
                                            val msgKey = msgNode.key ?: msg.id
                                            if (notifiedMsgIds.add(msgKey)) {
                                                val senderName = mockUsers[msg.senderId]?.username ?: "ChatOnGame"
                                                NotificationHelper.showMessageNotification(
                                                    appContext,
                                                    senderName,
                                                    msg.text,
                                                    chat.id
                                                )
                                            }
                                        }
                                    }
                                }
                                val existing = chatsMap[chat.id] ?: chat
                                val finalTime = if (maxTimestamp > 0L) maxOf(existing.lastMessageTimestamp, maxTimestamp) else existing.lastMessageTimestamp
                                val finalText = if (maxTimestamp > 0L && latestText.isNotEmpty()) latestText else existing.lastMessageText
                                chatsMap[chat.id] = existing.copy(
                                    unreadCount = unread,
                                    lastMessageTimestamp = finalTime,
                                    lastMessageText = finalText
                                )
                                updateRecentChats(chatsMap.values.toList())
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        }
                        chatUnreadListeners[chat.id] = listener
                        db.child("messages").child(chat.id).addValueEventListener(listener)
                    }
                }
                updateRecentChats(chatsMap.values.toList())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Chats observer canceled: ${error.message}")
            }
        }
        db.child("chats").addValueEventListener(recentChatsListener!!)

        // 4. Observe Typing states
        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingMap = mutableMapOf<String, String>()
                for (chatNode in snapshot.children) {
                    val chatId = chatNode.key ?: continue
                    for (userNode in chatNode.children) {
                        val userId = userNode.key ?: continue
                        val status = userNode.value as? String ?: ""
                        if (status == "typing") {
                            typingMap[userId] = chatId
                        }
                    }
                }
                _typingState.value = typingMap
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("typing").addValueEventListener(typingListener!!)
    }

    private fun startObservingMockData(uid: String) {
        cleanupListeners()
        // Initialize mock values
        triggerFriendsUpdate(uid)
        triggerFriendRequestsUpdate(uid)
        refreshMockChats(uid)
    }

    private fun triggerFriendsUpdate(uid: String) {
        val uids = mockFriends[uid] ?: emptySet()
        val list = uids.mapNotNull { mockUsers[it] }
        _friends.value = list
    }

    private fun triggerFriendRequestsUpdate(uid: String) {
        _friendRequests.value = mockFriendRequests.filter { it.toUid == uid && it.status == "pending" }
    }

    private fun <T> com.google.android.gms.tasks.Task<T>.withTimeout(
        timeoutMillis: Long = 8000L,
        errorMessage: String = "Firebase Database connection timed out. Please verify your Realtime Database URL and rules in App Settings.",
        onComplete: (com.google.android.gms.tasks.Task<T>) -> Unit
    ) {
        var completed = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!completed) {
                completed = true
                val exception = java.util.concurrent.TimeoutException(errorMessage)
                onComplete(com.google.android.gms.tasks.Tasks.forException(exception))
            }
        }
        
        handler.postDelayed(timeoutRunnable, timeoutMillis)
        
        this.addOnCompleteListener { task ->
            handler.removeCallbacks(timeoutRunnable)
            if (!completed) {
                completed = true
                onComplete(task)
            }
        }
    }

    private fun cleanupListeners() {
        val db = FirebaseManager.database?.reference ?: return
        friendsListener?.let { db.child("friends").removeEventListener(it) }
        requestsListener?.let { db.child("friendRequests").removeEventListener(it) }
        recentChatsListener?.let { db.child("chats").removeEventListener(it) }
        typingListener?.let { db.child("typing").removeEventListener(it) }
        chatUnreadListeners.forEach { (chatId, listener) ->
            db.child("messages").child(chatId).removeEventListener(listener)
        }
        chatUnreadListeners.clear()
        cleanupMessagesListener()

        friendsListener = null
        requestsListener = null
        recentChatsListener = null
        typingListener = null
    }
}
