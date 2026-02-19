package com.ingrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.ingrain.data.AppDatabase
import com.ingrain.data.IngrainRepository
import com.ingrain.scheduler.SchedulerSettingsStore
import com.ingrain.ui.IngrainApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(applicationContext)
        val repo = IngrainRepository(db)
        val settingsStore = SchedulerSettingsStore(applicationContext)

        setContent {
            MaterialTheme {
                IngrainApp(repo = repo, settingsStore = settingsStore)
            }
        }
    }
}
