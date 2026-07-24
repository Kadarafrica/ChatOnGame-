package com.example.service

import android.util.Log
import com.example.utils.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "New Message"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "You have received a new message."
        val chatId = remoteMessage.data["chatId"] ?: remoteMessage.data["CHAT_ID"] ?: ""

        NotificationHelper.showMessageNotification(this, title, body, chatId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
    }
}
