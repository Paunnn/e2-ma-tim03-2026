package com.tim03.slagalica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.tim03.slagalica.navigation.AppNavigation
import com.tim03.slagalica.ui.theme.SlagalicaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlagalicaTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
