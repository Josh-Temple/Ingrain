package com.ingrain.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiStyleDataStore by preferencesDataStore("ui_style_settings")

data class UiStyleSettings(
    val accentIndex: Int = 0,
    val buttonCornerRadiusDp: Int = 8,
)

object UiStylePresets {
    val accentColors = listOf(
        Color(0xFF2C8DFF),
        Color(0xFF39C07F),
        Color(0xFFE6A23C),
        Color(0xFFB388FF),
    )

    val accentLabels = listOf("Blue", "Green", "Amber", "Purple")
}

class UiStyleSettingsStore(private val context: Context) {
    private object Keys {
        val accentIndex = intPreferencesKey("accent_index")
        val cornerRadius = intPreferencesKey("button_corner_radius_dp")
    }

    val settings: Flow<UiStyleSettings> = context.uiStyleDataStore.data.map { p ->
        UiStyleSettings(
            accentIndex = p[Keys.accentIndex] ?: 0,
            buttonCornerRadiusDp = p[Keys.cornerRadius] ?: 8,
        )
    }

    suspend fun save(settings: UiStyleSettings) {
        context.uiStyleDataStore.edit { p ->
            p[Keys.accentIndex] = settings.accentIndex
            p[Keys.cornerRadius] = settings.buttonCornerRadiusDp
        }
    }
}

fun applyUiStyle(base: ColorScheme, settings: UiStyleSettings): ColorScheme {
    val accent = UiStylePresets.accentColors.getOrElse(settings.accentIndex) { UiStylePresets.accentColors.first() }
    return base.copy(primary = accent)
}

fun appButtonShape(settings: UiStyleSettings) = RoundedCornerShape(settings.buttonCornerRadiusDp.dp)
