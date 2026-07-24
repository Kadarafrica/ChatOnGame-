package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object BadgeUtils {
    private const val TAG = "BadgeUtils"
    private const val BADGE_NOTIFICATION_ID = 9999
    private const val BADGE_CHANNEL_ID = "unread_badge_channel"

    fun updateBadge(context: Context?, totalUnread: Int) {
        if (context == null) return
        try {
            // 1. Android Native Notification Channel Badge System (Android 8.0+)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    var channel = notificationManager.getNotificationChannel(BADGE_CHANNEL_ID)
                    if (channel == null) {
                        channel = NotificationChannel(
                            BADGE_CHANNEL_ID,
                            "Unread Message Badge",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            setShowBadge(true)
                            description = "Updates launcher icon badge for unread messages"
                        }
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                if (totalUnread > 0) {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("ChatOnGame")
                        .setContentText("$totalUnread unread message${if (totalUnread > 1) "s" else ""}")
                        .setNumber(totalUnread)
                        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setSilent(true)
                        .build()

                    notificationManager.notify(BADGE_NOTIFICATION_ID, notification)
                } else {
                    notificationManager.cancel(BADGE_NOTIFICATION_ID)
                }
            }

            // 2. Broadcast Intents for OEM Launchers (Samsung, Sony, LG, HTC, Huawei, Xiaomi)
            val packageName = context.packageName
            val className = MainActivity::class.java.name

            // Standard / Samsung / Sony / LG / Asus Broadcast
            val badgeIntent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", totalUnread)
                putExtra("badge_count_package_name", packageName)
                putExtra("badge_count_class_name", className)
            }
            context.sendBroadcast(badgeIntent)

            // Samsung Sec Broadcast
            val secIntent = Intent("com.sec.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", totalUnread)
                putExtra("badge_count_package_name", packageName)
                putExtra("badge_count_class_name", className)
            }
            context.sendBroadcast(secIntent)

            // Huawei Badge Provider
            if (Build.MANUFACTURER.equals("Huawei", ignoreCase = true) || Build.MANUFACTURER.equals("Honor", ignoreCase = true)) {
                try {
                    val extra = Bundle().apply {
                        putString("package", packageName)
                        putString("class", className)
                        putInt("badgenumber", totalUnread)
                    }
                    context.contentResolver.call(
                        Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
                        "change_badge",
                        null,
                        extra
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Huawei badge error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update app badge: ${e.message}")
        }
    }
}
