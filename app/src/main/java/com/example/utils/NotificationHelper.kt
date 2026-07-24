package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "chat_messages_channel"
    private const val CHANNEL_NAME = "Chat Messages"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                var channel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (channel == null) {
                    channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications for new chat messages"
                        enableVibration(true)
                        enableLights(true)
                        setShowBadge(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }
    }

    fun showMessageNotification(
        context: Context?,
        senderName: String,
        messageText: String,
        chatId: String
    ) {
        if (context == null) return
        try {
            createNotificationChannel(context)

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("CHAT_ID", chatId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
                .setGroup("CHAT_GROUP_$chatId")
                .build()

            val notificationId = (chatId.hashCode() and 0x7FFFFFFF)
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
        }
    }
}
