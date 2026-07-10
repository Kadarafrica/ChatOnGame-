package com.example.data

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val friendId: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val typingTo: String? = null,
    val avatarUrl: String? = null
)

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val status: String = "pending", // "pending", "accepted", "rejected"
    val timestamp: Long = 0L
)

data class Chat(
    val id: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0
)

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val status: String = "sent" // "sent", "delivered", "read"
)
