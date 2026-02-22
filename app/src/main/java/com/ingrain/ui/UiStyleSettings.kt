package com.ingrain.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiStyleDataStore by preferencesDataStore("ui_style_settings")

enum class AppFontMode(val id: Int, val label: String) {
    SystemDefault(0, "System default"),
    HelveticaPreferred(1, "Helvetica preferred");

    companion object {
        fun fromId(id: Int): AppFontMode = entries.firstOrNull { it.id == id } ?: SystemDefault
    }
}

data class UiStyleSettings(
    val accentIndex: Int = 0,
    val buttonCornerRadiusDp: Int = 8,
    val backgroundColorIndex: Int = 0,
    val surfaceColorIndex: Int = 1,
    val textColorIndex: Int = 0,
    val h3SizeSp: Int = 26,
    val h4SizeSp: Int = 22,
    val bodySizeSp: Int = 18,
    val listSizeSp: Int = 18,
    val h3ColorIndex: Int = 0,
    val bodyColorIndex: Int = 0,
    val h4ColorIndex: Int = 1,
    val listColorIndex: Int = 2,
    val boldColorIndex: Int = 0,
    val italicColorIndex: Int = 1,
    val fontModeId: Int = AppFontMode.SystemDefault.id,
)

object UiStylePresets {
    val accentColors = listOf(
        Color(0xFF2C8DFF),
        Color(0xFF39C07F),
        Color(0xFFE6A23C),
        Color(0xFFB388FF),
        Color(0xFFC85C5C),
        Color(0xFFA05F63),
    )

    val accentLabels = listOf("Blue", "Green", "Amber", "Purple", "Coral", "Wine")

    val surfaceBackgroundColors = listOf(
        Color(0xFF061427),
        Color(0xFF0D1F36),
        Color(0xFF1B314E),
        Color(0xFFDDDDDD),
        Color(0xFFEEEEEE),
        Color(0xFF1F1F1F),
    )

    val surfaceBackgroundLabels = listOf("Navy", "Deep blue", "Slate", "Soft gray", "Light gray", "Charcoal")

    val textColors = listOf(
        Color(0xFFEAF1FC),
        Color(0xFF9FB4D0),
        Color(0xFF3D3D3D),
        Color(0xFF111111),
        Color(0xFFF2F7FF),
    )

    val textLabels = listOf("Ice", "Muted ice", "Graphite", "Ink", "Soft white")

    val markdownEmphasisColors = listOf(
        Color(0xFFEAF1FC),
        Color(0xFF9FB4D0),
        Color(0xFF2C8DFF),
        Color(0xFFC85C5C),
        Color(0xFFA05F63),
        Color(0xFF3D3D3D),
    )

    val markdownEmphasisLabels = listOf("Base text", "Subtle", "Primary blue", "Coral", "Wine", "Graphite")

    val markdownSizeOptions = listOf(14, 16, 18, 20, 22, 24, 26, 28)
}

class UiStyleSettingsStore(private val context: Context) {
    private object Keys {
        val accentIndex = intPreferencesKey("accent_index")
        val cornerRadius = intPreferencesKey("button_corner_radius_dp")
        val backgroundColorIndex = intPreferencesKey("background_color_index")
        val surfaceColorIndex = intPreferencesKey("surface_color_index")
        val textColorIndex = intPreferencesKey("text_color_index")
        val h3SizeSp = intPreferencesKey("h3_size_sp")
        val h4SizeSp = intPreferencesKey("h4_size_sp")
        val bodySizeSp = intPreferencesKey("body_size_sp")
        val listSizeSp = intPreferencesKey("list_size_sp")
        val h3ColorIndex = intPreferencesKey("h3_color_index")
        val bodyColorIndex = intPreferencesKey("body_color_index")
        val h4ColorIndex = intPreferencesKey("h4_color_index")
        val listColorIndex = intPreferencesKey("list_color_index")
        val boldColorIndex = intPreferencesKey("bold_color_index")
        val italicColorIndex = intPreferencesKey("italic_color_index")
        val fontModeId = intPreferencesKey("font_mode_id")
    }

    val settings: Flow<UiStyleSettings> = context.uiStyleDataStore.data.map { p ->
        UiStyleSettings(
            accentIndex = p[Keys.accentIndex] ?: 0,
            buttonCornerRadiusDp = p[Keys.cornerRadius] ?: 8,
            backgroundColorIndex = p[Keys.backgroundColorIndex] ?: 0,
            surfaceColorIndex = p[Keys.surfaceColorIndex] ?: 1,
            textColorIndex = p[Keys.textColorIndex] ?: 0,
            h3SizeSp = p[Keys.h3SizeSp] ?: 26,
            h4SizeSp = p[Keys.h4SizeSp] ?: 22,
            bodySizeSp = p[Keys.bodySizeSp] ?: 18,
            listSizeSp = p[Keys.listSizeSp] ?: 18,
            h3ColorIndex = p[Keys.h3ColorIndex] ?: 0,
            bodyColorIndex = p[Keys.bodyColorIndex] ?: 0,
            h4ColorIndex = p[Keys.h4ColorIndex] ?: 1,
            listColorIndex = p[Keys.listColorIndex] ?: 2,
            boldColorIndex = p[Keys.boldColorIndex] ?: 0,
            italicColorIndex = p[Keys.italicColorIndex] ?: 1,
            fontModeId = p[Keys.fontModeId] ?: AppFontMode.SystemDefault.id,
        )
    }

    suspend fun save(settings: UiStyleSettings) {
        context.uiStyleDataStore.edit { p ->
            p[Keys.accentIndex] = settings.accentIndex
            p[Keys.cornerRadius] = settings.buttonCornerRadiusDp
            p[Keys.backgroundColorIndex] = settings.backgroundColorIndex
            p[Keys.surfaceColorIndex] = settings.surfaceColorIndex
            p[Keys.textColorIndex] = settings.textColorIndex
            p[Keys.h3SizeSp] = settings.h3SizeSp
            p[Keys.h4SizeSp] = settings.h4SizeSp
            p[Keys.bodySizeSp] = settings.bodySizeSp
            p[Keys.listSizeSp] = settings.listSizeSp
            p[Keys.h3ColorIndex] = settings.h3ColorIndex
            p[Keys.bodyColorIndex] = settings.bodyColorIndex
            p[Keys.h4ColorIndex] = settings.h4ColorIndex
            p[Keys.listColorIndex] = settings.listColorIndex
            p[Keys.boldColorIndex] = settings.boldColorIndex
            p[Keys.italicColorIndex] = settings.italicColorIndex
            p[Keys.fontModeId] = settings.fontModeId
        }
    }
}

fun applyUiStyle(base: ColorScheme, settings: UiStyleSettings): ColorScheme {
    val primary = UiStylePresets.accentColors.getOrElse(settings.accentIndex) { UiStylePresets.accentColors.first() }
    val background = UiStylePresets.surfaceBackgroundColors.getOrElse(settings.backgroundColorIndex) { base.background }
    val surface = UiStylePresets.surfaceBackgroundColors.getOrElse(settings.surfaceColorIndex) { base.surface }
    val text = UiStylePresets.textColors.getOrElse(settings.textColorIndex) { base.onSurface }
    val isLightSurface = surface.luminance() > 0.5f
    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF8F8F8)
    val onSurface = text
    val onBackground = text
    val surfaceVariant = if (isLightSurface) surface.copy(alpha = 0.9f) else surface.copy(alpha = 0.7f)
    val onSurfaceVariant = if (isLightSurface) Color(0xFF424242) else Color(0xFFB0C0D8)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        secondary = UiStylePresets.accentColors.getOrElse((settings.accentIndex + 1) % UiStylePresets.accentColors.size) { primary },
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
    )
}

fun appButtonShape(settings: UiStyleSettings) = RoundedCornerShape(settings.buttonCornerRadiusDp.dp)

fun appTypography(settings: UiStyleSettings): Typography {
    val mode = AppFontMode.fromId(settings.fontModeId)
    val base = Typography()
    if (mode == AppFontMode.SystemDefault) {
        return base.copy(bodyLarge = base.bodyLarge.copy(fontSize = settings.bodySizeSp.sp))
    }
    val helveticaPreferred = FontFamily.SansSerif
    return base.copy(
        displayLarge = base.displayLarge.merge(TextStyle(fontFamily = helveticaPreferred)),
        displayMedium = base.displayMedium.merge(TextStyle(fontFamily = helveticaPreferred)),
        displaySmall = base.displaySmall.merge(TextStyle(fontFamily = helveticaPreferred)),
        headlineLarge = base.headlineLarge.merge(TextStyle(fontFamily = helveticaPreferred)),
        headlineMedium = base.headlineMedium.merge(TextStyle(fontFamily = helveticaPreferred)),
        headlineSmall = base.headlineSmall.merge(TextStyle(fontFamily = helveticaPreferred)),
        titleLarge = base.titleLarge.merge(TextStyle(fontFamily = helveticaPreferred)),
        titleMedium = base.titleMedium.merge(TextStyle(fontFamily = helveticaPreferred)),
        titleSmall = base.titleSmall.merge(TextStyle(fontFamily = helveticaPreferred)),
        bodyLarge = base.bodyLarge.merge(TextStyle(fontFamily = helveticaPreferred, fontSize = settings.bodySizeSp.sp)),
        bodyMedium = base.bodyMedium.merge(TextStyle(fontFamily = helveticaPreferred)),
        bodySmall = base.bodySmall.merge(TextStyle(fontFamily = helveticaPreferred)),
        labelLarge = base.labelLarge.merge(TextStyle(fontFamily = helveticaPreferred)),
        labelMedium = base.labelMedium.merge(TextStyle(fontFamily = helveticaPreferred)),
        labelSmall = base.labelSmall.merge(TextStyle(fontFamily = helveticaPreferred)),
    )
}
