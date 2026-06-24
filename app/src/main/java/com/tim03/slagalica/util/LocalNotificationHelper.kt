package com.tim03.slagalica.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tim03.slagalica.MainActivity
import com.tim03.slagalica.R
import com.tim03.slagalica.service.SlagalicaFirebaseMessagingService

object LocalNotificationHelper {

    fun show(context: Context, title: String, body: String, channel: String = SlagalicaFirebaseMessagingService.CHANNEL_GENERAL) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun channelForType(type: String): String = when (type) {
        "CHAT" -> SlagalicaFirebaseMessagingService.CHANNEL_CHAT
        "RANKING" -> SlagalicaFirebaseMessagingService.CHANNEL_RANKING
        "REWARD" -> SlagalicaFirebaseMessagingService.CHANNEL_REWARD
        else -> SlagalicaFirebaseMessagingService.CHANNEL_GENERAL
    }
}
