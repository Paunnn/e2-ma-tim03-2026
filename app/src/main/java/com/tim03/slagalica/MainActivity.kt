package com.tim03.slagalica

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tim03.slagalica.data.repository.FirestoreSeedRepository
import com.tim03.slagalica.navigation.AppNavigation
import com.tim03.slagalica.service.SlagalicaFirebaseMessagingService
import com.tim03.slagalica.ui.theme.SlagalicaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannels()

        lifecycleScope.launch {
            try { FirestoreSeedRepository().seedDatabase() } catch (_: Exception) {}
        }

        setContent {
            SlagalicaTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel(
                    SlagalicaFirebaseMessagingService.CHANNEL_CHAT,
                    "Čet poruke",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifikacije za poruke u četu" },
                NotificationChannel(
                    SlagalicaFirebaseMessagingService.CHANNEL_RANKING,
                    "Rang lista",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifikacije o plasmanu na rang listama" },
                NotificationChannel(
                    SlagalicaFirebaseMessagingService.CHANNEL_REWARD,
                    "Nagrade i lige",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifikacije o nagradama i prelasku u novu ligu" },
                NotificationChannel(
                    SlagalicaFirebaseMessagingService.CHANNEL_GENERAL,
                    "Ostale notifikacije",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Ostale sistemske notifikacije" }
            ).forEach { manager.createNotificationChannel(it) }
        }
    }
}
