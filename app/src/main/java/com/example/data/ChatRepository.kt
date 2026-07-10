package com.example.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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

    // Fallback Mock database for offline/demo operation
    private val mockUsers = mutableMapOf<String, User>()
    private val mockFriends = mutableMapOf<String, MutableSet<String>>() // uid -> set of friend uids
    private val mockFriendRequests = mutableListOf<FriendRequest>()
    private val mockChats = mutableListOf<Chat>()
    private val mockMessages = mutableListOf<Message>()

    // Preset Gaming Bot Profiles for falling back gracefully when Firebase is offline
    private val gameBots = listOf(
        User("bot_apex", "ApexLegend", "apex@chatongame.com", "FC-A7K92P", true, System.currentTimeMillis(), null, null),
        User("bot_speed", "SpeedRunner", "speed@chatongame.com", "FC-X4M81Q", true, System.currentTimeMillis(), null, null),
        User("bot_frag", "FragMaster", "frag@chatongame.com", "FC-B9R3LT", false, System.currentTimeMillis() - 120000, null, null)
    )

    private val gameBotReplies = listOf(
        "Yo! Let's party up in Valorant. Are you ready?",
        "Wait, almost finished with this speedrun! New record incoming! ⏱️",
        "GG! That clutch was absolute legendary.",
        "Add me in your squad, we're streaming on Twitch tonight 🎮",
        "My ping is kind of high right now, but I can still carry!",
        "Who's down for some CS2 or Apex? Hit me up!",
        "Check out my new gaming setup! I'll send a photo later.",
        "That boss fight was pure pain, took me 15 attempts..."
    )

    init {
        // Pre-populate mock database with game bots
        for (bot in gameBots) {
            mockUsers[bot.uid] = bot
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

    fun signUp(email: String, usernameInput: String, onResult: (Result<User>) -> Unit) {
        val username = usernameInput.trim()
        if (username.isEmpty()) {
            onResult(Result.failure(Exception("Username cannot be empty")))
            return
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val auth = FirebaseManager.auth ?: return
            val db = FirebaseManager.database?.reference ?: return

            // 1. First check username uniqueness in database
            db.child("usernames").child(username).get().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.exists()) {
                    onResult(Result.failure(Exception("Username is already taken.")))
                } else {
                    // Username unique, proceed with creating Firebase user
                    auth.createUserWithEmailAndPassword(email, "password123").addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            val uid = authTask.result.user?.uid ?: ""
                            val friendId = generateFriendId()
                            val newUser = User(uid, username, email, friendId, true, System.currentTimeMillis())

                            // Write to user lists and lookup indices
                            val updates = mapOf(
                                "/users/$uid" to newUser,
                                "/usernames/$username" to uid,
                                "/friendIds/$friendId" to uid
                            )

                            db.updateChildren(updates).addOnCompleteListener { dbTask ->
                                if (dbTask.isSuccessful) {
                                    _currentUser.value = newUser
                                    onResult(Result.success(newUser))
                                    startObservingRealtimeData(uid)
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
            _currentUser.value = newUser
            onResult(Result.success(newUser))
            startObservingMockData(uid)
        }
    }

    fun login(email: String, usernameInput: String, onResult: (Result<User>) -> Unit) {
        val username = usernameInput.trim()
        if (FirebaseManager.isFirebaseAvailable) {
            val auth = FirebaseManager.auth ?: return
            val db = FirebaseManager.database?.reference ?: return

            if (username.isNotEmpty()) {
                // Login via username
                db.child("usernames").child(username).get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result.exists()) {
                        val uid = task.result.value as String
                        db.child("users").child(uid).get().addOnCompleteListener { userTask ->
                            if (userTask.isSuccessful) {
                                val user = userTask.result.getValue(User::class.java)
                                if (user != null) {
                                    // Sign in to firebase authentication
                                    auth.signInWithEmailAndPassword(user.email, "password123").addOnCompleteListener { authTask ->
                                        if (authTask.isSuccessful) {
                                            _currentUser.value = user
                                            onResult(Result.success(user))
                                            startObservingRealtimeData(uid)
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
                        onResult(Result.failure(Exception("Username does not exist. Please sign up.")))
                    }
                }
            } else {
                // Login via email
                auth.signInWithEmailAndPassword(email, "password123").addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val uid = authTask.result.user?.uid ?: ""
                        db.child("users").child(uid).get().addOnCompleteListener { userTask ->
                            if (userTask.isSuccessful) {
                                val user = userTask.result.getValue(User::class.java)
                                if (user != null) {
                                    _currentUser.value = user
                                    onResult(Result.success(user))
                                    startObservingRealtimeData(uid)
                                } else {
                                    onResult(Result.failure(Exception("User details not found.")))
                                }
                            } else {
                                onResult(Result.failure(userTask.exception ?: Exception("Error retrieving user info.")))
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
                val updatedUser = user.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                mockUsers[user.uid] = updatedUser
                _currentUser.value = updatedUser
                onResult(Result.success(updatedUser))
                startObservingMockData(user.uid)
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
        }
    }

    // Friend requests logic
    fun sendFriendRequest(query: String, onResult: (Result<Unit>) -> Unit) {
        val user = _currentUser.value ?: return
        val searchQuery = query.trim()

        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return

            // 1. Search user by username
            db.child("usernames").child(searchQuery).get().addOnCompleteListener { userTask ->
                if (userTask.isSuccessful && userTask.result.exists()) {
                    val targetUid = userTask.result.value as String
                    createAndSendRequest(user, targetUid, onResult)
                } else {
                    // 2. Search user by Friend ID
                    db.child("friendIds").child(searchQuery).get().addOnCompleteListener { friendTask ->
                        if (friendTask.isSuccessful && friendTask.result.exists()) {
                            val targetUid = friendTask.result.value as String
                            createAndSendRequest(user, targetUid, onResult)
                        } else {
                            onResult(Result.failure(Exception("No user found with Username or Friend ID: $searchQuery")))
                        }
                    }
                }
            }
        } else {
            // Mock Friend System
            val targetBot = gameBots.find { it.username.equals(searchQuery, ignoreCase = true) || it.friendId.equals(searchQuery, ignoreCase = true) }
            val targetUser = targetBot ?: mockUsers.values.find { (it.username.equals(searchQuery, ignoreCase = true) || it.friendId.equals(searchQuery, ignoreCase = true)) && it.uid != user.uid }

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
        }
    }

    fun rejectFriendRequest(request: FriendRequest) {
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("friendRequests").child(request.toUid).child(request.id).removeValue()
        } else {
            mockFriendRequests.removeAll { it.id == request.id }
            triggerFriendRequestsUpdate(request.toUid)
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
        }
    }

    // Chat operations
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
            val exists = mockChats.any { it.id == chatId }
            if (!exists) {
                val newChat = Chat(chatId, sortedUids, "No messages yet", System.currentTimeMillis(), 0)
                mockChats.add(newChat)
                _recentChats.value = mockChats.toList()
            }
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
            _recentChats.value = mockChats.toList().sortedByDescending { it.lastMessageTimestamp }

            // Simulate Game Bot Automated Reply If receiver is a bot
            if (targetUid.startsWith("bot_")) {
                simulateBotReply(chatId, targetUid)
            }
        }
    }

    private fun simulateBotReply(chatId: String, botUid: String) {
        val handler = Handler(Looper.getMainLooper())
        
        // 1. Deliver Message immediately
        handler.postDelayed({
            updateMockMessageStatus(chatId, "delivered")
        }, 600)

        // 2. Read status + Bot starts Typing after 1.2s
        handler.postDelayed({
            updateMockMessageStatus(chatId, "read")
            _typingState.value = _typingState.value + (botUid to chatId)
        }, 1200)

        // 3. Bot sends message and stops typing after 3s
        handler.postDelayed({
            _typingState.value = _typingState.value - botUid
            
            val randomReply = gameBotReplies[java.util.Random().nextInt(gameBotReplies.size)]
            val replyId = "msg_" + UUID.randomUUID().toString().take(12)
            val botReply = Message(replyId, chatId, botUid, _currentUser.value?.uid ?: "", randomReply, System.currentTimeMillis(), "read")
            
            mockMessages.add(botReply)
            _activeChatMessages.value = mockMessages.filter { it.chatId == chatId }.sortedBy { it.timestamp }

            // Update recent chat listing
            val chatIndex = mockChats.indexOfFirst { it.id == chatId }
            if (chatIndex != -1) {
                mockChats[chatIndex] = mockChats[chatIndex].copy(
                    lastMessageText = randomReply,
                    lastMessageTimestamp = System.currentTimeMillis()
                )
            }
            _recentChats.value = mockChats.toList().sortedByDescending { it.lastMessageTimestamp }
        }, 3200)
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
        if (FirebaseManager.isFirebaseAvailable) {
            val db = FirebaseManager.database?.reference ?: return
            db.child("users").child(user.uid).child("isOnline").setValue(isOnline)
            db.child("users").child(user.uid).child("lastSeen").setValue(System.currentTimeMillis())
        } else {
            mockUsers[user.uid] = user.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
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

        // 3. Observe Recent Chats listing
        recentChatsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatsList = mutableListOf<Chat>()
                for (child in snapshot.children) {
                    val chat = child.getValue(Chat::class.java)
                    if (chat != null && chat.participantUids.contains(uid)) {
                        chatsList.add(chat)
                    }
                }
                _recentChats.value = chatsList.sortedByDescending { it.lastMessageTimestamp }
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
        _recentChats.value = mockChats.filter { it.participantUids.contains(uid) }.sortedByDescending { it.lastMessageTimestamp }
    }

    private fun triggerFriendsUpdate(uid: String) {
        val uids = mockFriends[uid] ?: emptySet()
        val list = uids.mapNotNull { mockUsers[it] }
        _friends.value = list
    }

    private fun triggerFriendRequestsUpdate(uid: String) {
        _friendRequests.value = mockFriendRequests.filter { it.toUid == uid && it.status == "pending" }
    }

    private fun cleanupListeners() {
        val db = FirebaseManager.database?.reference ?: return
        friendsListener?.let { db.child("friends").removeEventListener(it) }
        requestsListener?.let { db.child("friendRequests").removeEventListener(it) }
        recentChatsListener?.let { db.child("chats").removeEventListener(it) }
        typingListener?.let { db.child("typing").removeEventListener(it) }
        cleanupMessagesListener()

        friendsListener = null
        requestsListener = null
        recentChatsListener = null
        typingListener = null
    }
}
