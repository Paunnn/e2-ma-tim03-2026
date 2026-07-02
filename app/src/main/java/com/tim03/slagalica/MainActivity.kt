package com.tim03.slagalica

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tim03.slagalica.data.repository.FirestoreSeedRepository
import com.tim03.slagalica.data.repository.UserRepository
import com.tim03.slagalica.navigation.AppNavigation
import com.tim03.slagalica.service.SlagalicaFirebaseMessagingService
import com.tim03.slagalica.ui.theme.SlagalicaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannels()
        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            try { FirestoreSeedRepository().seedDatabase() } catch (_: Exception) {}
        }
        // Presence heartbeat lives in AppNavigation, gated on the current route:
        // it must NOT run while the app sits on the login screen with a session
        // Firebase persisted from a previous run.

        setContent {
            SlagalicaTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app counts as leaving the game: drop the "active player" flag.
        // Coming back re-asserts it right away (lifecycle-aware heartbeat in AppNavigation).
        UserRepository().markInactiveAsync()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
