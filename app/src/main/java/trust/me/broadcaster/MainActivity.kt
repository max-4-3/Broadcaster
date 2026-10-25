package trust.me.broadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import trust.me.broadcaster.screens.ConfigScreen
import trust.me.broadcaster.screens.HomeScreen
import trust.me.broadcaster.ui.theme.BroadcasterTheme


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BroadcasterTheme(darkTheme = true) {
                var currentScreen by remember { mutableStateOf("home") }

                when (currentScreen.lowercase()) {
                    "home" -> HomeScreen(onBack = {}, onNavigateToConfig = { currentScreen = "config" })
                    "config" -> ConfigScreen(onBack = { currentScreen = "home" })
                }
            }
        }
    }
}

