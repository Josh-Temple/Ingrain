package com.ingrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ingrain.data.AppDatabase
import com.ingrain.data.IngrainRepository
import com.ingrain.scheduler.SchedulerSettingsStore
import com.ingrain.ui.IngrainApp
import com.ingrain.ui.UiStyleSettingsStore
import com.ingrain.ui.applyUiStyle
import com.ingrain.ui.appButtonShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(applicationContext)
        val repo = IngrainRepository(db)
        val settingsStore = SchedulerSettingsStore(applicationContext)
        val uiStyleStore = UiStyleSettingsStore(applicationContext)

        setContent {
            val uiStyle by uiStyleStore.settings.collectAsState(initial = com.ingrain.ui.UiStyleSettings())
            val premiumDark = darkColorScheme(
                primary = Color(0xFF2C8DFF),
                onPrimary = Color(0xFFF2F7FF),
                secondary = Color(0xFF9CB2CC),
                background = Color(0xFF061427),
                surface = Color(0xFF0D1F36),
                surfaceVariant = Color(0xFF1B314E),
                onBackground = Color(0xFFEAF1FC),
                onSurface = Color(0xFFEAF1FC),
                onSurfaceVariant = Color(0xFF9FB4D0),
            )

            val appShapes = Shapes(
                small = appButtonShape(uiStyle),
                medium = RoundedCornerShape(0.dp),
                large = RoundedCornerShape(0.dp),
            )

            MaterialTheme(
                colorScheme = applyUiStyle(premiumDark, uiStyle),
                shapes = appShapes,
            ) {
                IngrainApp(repo = repo, settingsStore = settingsStore, uiStyleStore = uiStyleStore)
            }
        }
    }
}
