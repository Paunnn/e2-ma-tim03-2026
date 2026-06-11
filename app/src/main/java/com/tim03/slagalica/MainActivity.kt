package com.tim03.slagalica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tim03.slagalica.data.repository.FirestoreSeedRepository
import com.tim03.slagalica.navigation.AppNavigation
import com.tim03.slagalica.ui.theme.SlagalicaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // SEED: pokreni jednom da napuniš Firestore, pa ukloni ovaj blok
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
}
