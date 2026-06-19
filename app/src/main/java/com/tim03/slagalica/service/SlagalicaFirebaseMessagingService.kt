package com.tim03.slagalica.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tim03.slagalica.MainActivity
import com.tim03.slagalica.R

class SlagalicaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Slagalica"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["message"]
            ?: ""
        val channel = when (remoteMessage.data["channel"]) {
            "CHAT" -> CHANNEL_CHAT
            "RANKING" -> CHANNEL_RANKING
            "REWARD" -> CHANNEL_REWARD
            else -> CHANNEL_GENERAL
        }
        showNotification(title, body, channel)
    }

    private fun showNotification(title: String, body: String, channelId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    companion object {
        const val CHANNEL_CHAT = "chat_channel"
        const val CHANNEL_RANKING = "ranking_channel"
        const val CHANNEL_REWARD = "reward_channel"
        const val CHANNEL_GENERAL = "general_channel"
    }
}
