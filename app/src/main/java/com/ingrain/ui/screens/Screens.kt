package com.ingrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ingrain.data.CardEntity
import com.ingrain.data.DeckEntity
import com.ingrain.data.IngrainRepository
import com.ingrain.importing.BulkImportParser
import com.ingrain.importing.ParseResult
import com.ingrain.scheduler.Scheduler
import com.ingrain.scheduler.SchedulerSettings
import com.ingrain.scheduler.SchedulerSettingsStore
import com.ingrain.ui.AppFontMode
import com.ingrain.ui.MarkdownTokenText
import com.ingrain.ui.UiStylePresets
import com.ingrain.ui.UiStyleSettings
import com.ingrain.ui.UiStyleSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

data class AddPreset(val name: String, val front: String, val back: String, val tags: String)

private data class FormattingExample(val label: String, val description: String, val snippet: String)

private val addTemplates = listOf(
    AddPreset("Basic", "Term or Question", "Definition or Answer", ""),
    AddPreset("Example", "Word", "Meaning + Example Sentence", "vocab"),
    AddPreset("Cloze", "Sentence with {{c1::key term}}", "Extra explanation", "cloze"),
)

private val formattingExamples = listOf(
    FormattingExample("H3", "H3 heading: start with ###", "### Photosynthesis"),
    FormattingExample("H4", "H4 heading: start with ####", "#### Light reaction"),
    FormattingExample("Bold", "Bold: wrap with **double asterisks**", "**Key term**"),
    FormattingExample("Italic", "Italic: wrap with *single asterisks*", "*Nuance*"),
    FormattingExample("List", "List: start each line with -", "- Item 1\n- Item 2"),
)

private const val RatingAgain = "AGAIN"
private const val RatingGood = "GOOD"

private data class ParsedThemeColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val primary: Color,
    val accent: Color,
)

private fun parseHexColor(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (!cleaned.matches(Regex("[0-9A-Fa-f]{6}"))) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    return Color((0xFF000000 or value).toInt())
}

private fun extractCssThemeColors(input: String): ParsedThemeColors? {
    fun findVariable(name: String): Color? {
        val regex = Regex("--$name\\s*:\\s*(#[0-9A-Fa-f]{6})")
        val hex = regex.find(input)?.groupValues?.get(1) ?: return null
        return parseHexColor(hex)
    }

    val background = findVariable("color-background") ?: return null
    val surface = findVariable("color-surface") ?: return null
    val text = findVariable("color-text") ?: return null
    val primary = findVariable("color-primary") ?: return null
    val accent = findVariable("color-accent") ?: return null
    return ParsedThemeColors(background, surface, text, primary, accent)
}

private fun extractJsonThemeColors(input: String): ParsedThemeColors? {
    val root = runCatching { Json.parseToJsonElement(input).jsonObject }.getOrNull() ?: return null
    val map = buildMap {
        root["colors"]?.jsonArray?.forEach { item ->
            val obj = item.jsonObject
            val role = obj["role"]?.jsonPrimitive?.content
            val hex = obj["hex"]?.jsonPrimitive?.content
            if (role != null && hex != null) put(role, hex)
        }
    }
    val background = map["background"]?.let(::parseHexColor) ?: return null
    val surface = map["surface"]?.let(::parseHexColor) ?: return null
    val text = map["text"]?.let(::parseHexColor) ?: return null
    val primary = map["primary"]?.let(::parseHexColor) ?: return null
    val accent = map["accent"]?.let(::parseHexColor) ?: return null
    return ParsedThemeColors(background, surface, text, primary, accent)
}

private fun parseThemeColors(input: String): ParsedThemeColors? {
    return extractCssThemeColors(input)
        ?: extractJsonThemeColors(input)
}

private val AppButtonShape
    @Composable
    get() = MaterialTheme.shapes.small

private fun startOfDayMillis(nowMillis: Long): Long {
    val zoneId = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
}

private fun endOfDayMillis(nowMillis: Long): Long {
    val zoneId = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli() - 1
}

private fun formatIntervalShort(days: Double): String {
    val roundedDays = (days * 10).roundToInt() / 10.0
    return if (roundedDays % 1.0 == 0.0) {
        "${roundedDays.toInt()}d"
    } else {
        "${roundedDays}d"
    }
}

@Composable
private fun SectionHeading(text: String, primary: Boolean = false) {
    Text(
        text = text,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class ManualAddDraft(
    val deckName: String,
    val front: String,
    val back: String,
    val tags: String,
)

private enum class TemplateFormat(val label: String) {
    Markdown("Markdown"),
    JsonLines("JSON Lines"),
}

private fun parseTags(raw: String): List<String> = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

private val screenJson = Json { ignoreUnknownKeys = true }

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

private fun CardEntity.primaryTag(): String {
    val tags = runCatching { screenJson.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList())
    return tags.firstOrNull()?.uppercase() ?: "STUDY"
}

private fun jsonTemplate(deckName: String, front: String, back: String, tags: String): String {
    val tagList = parseTags(tags).joinToString(",") { "\"$it\"" }
    return """{"deck":"$deckName","front":"$front","back":"$back","tags":[$tagList]}"""
}

private fun markdownTemplate(deckName: String, front: String, back: String, tags: String): String {
    val tagLines = parseTags(tags)
    val tagsBlock = if (tagLines.isEmpty()) {
        "tags: []"
    } else {
        buildString {
            appendLine("tags:")
            tagLines.forEach { appendLine("  - \"$it\"") }
        }.trimEnd()
    }
    return """
---
deck: "$deckName"
$tagsBlock
---

## Front
$front

## Back
$back
""".trimIndent()
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    repo: IngrainRepository,
    onStudyDeck: (Long) -> Unit,
    onEditDeck: (Long) -> Unit,
    onAddCard: () -> Unit,
    uiStyleStore: UiStyleSettingsStore,
) {
    val decks by produceState(initialValue = emptyList<DeckEntity>(), repo) {
        repo.observeDecks().collect { value = it }
    }
    val dueCountByDeck by produceState(initialValue = emptyMap<Long, Int>(), decks) {
        val now = System.currentTimeMillis()
        val dayStart = startOfDayMillis(now)
        val endOfDay = endOfDayMillis(now)
        value = decks.associate { deck ->
            deck.id to repo.countDueUntil(deck, dayStart, endOfDay, endOfDay)
        }
    }
    var pendingDeckDelete by remember { mutableStateOf<DeckEntity?>(null) }
    var showAddDeckDialog by remember { mutableStateOf(false) }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var newDeckName by remember { mutableStateOf("") }
    var addDeckError by remember { mutableStateOf<String?>(null) }
    var showFormattingMenu by remember { mutableStateOf(false) }
    val uiStyle by uiStyleStore.settings.collectAsState(initial = UiStyleSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                actions = {
                    TextButton(shape = AppButtonShape, onClick = { showFormattingMenu = true }) { Text("Menu") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { showAddActionDialog = true },
                    icon = { Text("+") },
                    label = { Text("ADD") },
                )
            }
        },
    ) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (decks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No decks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                decks.forEachIndexed { index, deck ->
                    val dueToday = dueCountByDeck[deck.id] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onStudyDeck(deck.id) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (dueToday > 0) "Due cards remaining today" else "No due cards remaining today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(shape = AppButtonShape, onClick = { onEditDeck(deck.id) }) {
                                Text("Edit")
                            }
                            TextButton(shape = AppButtonShape, onClick = { pendingDeckDelete = deck }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (index < decks.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    if (showFormattingMenu) {
        AppFormattingGuideDialog(
            uiStyle = uiStyle,
            onSaveStyle = { updated -> scope.launch { uiStyleStore.save(updated) } },
            onDismiss = { showFormattingMenu = false },
            onCopy = { label, snippet ->
                clipboard?.setPrimaryClip(ClipData.newPlainText(label, snippet))
            },
        )
    }

    val targetDeck = pendingDeckDelete
    if (targetDeck != null) {
        AlertDialog(
            onDismissRequest = { pendingDeckDelete = null },
            title = { Text("Delete deck") },
            text = { Text("Delete \"${targetDeck.name}\"?") },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    scope.launch { repo.deleteDeck(targetDeck.id) }
                    pendingDeckDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(shape = AppButtonShape, onClick = { pendingDeckDelete = null }) { Text("Cancel") }
            },
        )
    }


    if (showAddActionDialog) {
        AlertDialog(
            onDismissRequest = { showAddActionDialog = false },
            title = { Text("Add") },
            text = { Text("Choose what to add") },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    showAddActionDialog = false
                    showAddDeckDialog = true
                }) { Text("Add deck") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(shape = AppButtonShape, onClick = {
                        showAddActionDialog = false
                        onAddCard()
                    }) { Text("Add card") }
                    TextButton(shape = AppButtonShape, onClick = { showAddActionDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showAddDeckDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDeckDialog = false
                addDeckError = null
            },
            title = { Text("Create deck") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = newDeckName,
                        onValueChange = {
                            newDeckName = it
                            addDeckError = null
                        },
                        singleLine = true,
                        label = { Text("Deck name") },
                    )
                    if (addDeckError != null) {
                        Text(addDeckError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    scope.launch {
                        addDeckError = repo.createDeck(newDeckName).exceptionOrNull()?.message
                        if (addDeckError == null) {
                            newDeckName = ""
                            showAddDeckDialog = false
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    showAddDeckDialog = false
                    addDeckError = null
                }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: Long,
    repo: IngrainRepository,
    onStudy: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deck by produceState<DeckEntity?>(initialValue = null, deckId) { value = repo.getDeck(deckId) }
    var renameInput by remember(deck?.name) { mutableStateOf(deck?.name ?: "") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Deck details") }) }) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(deck?.name ?: "Deck", style = MaterialTheme.typography.headlineLarge)
                Text("DECK OPTIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TextButton(shape = AppButtonShape, onClick = onStudy) {
                Text("Study Now", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(shape = AppButtonShape, onClick = onImport) {
                Text("Add Cards", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(shape = AppButtonShape, onClick = onSettings) {
                Text("Deck Settings", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(shape = AppButtonShape, onClick = onBackup) {
                Text("Backup & Restore", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(shape = AppButtonShape, onClick = { showRenameDialog = true }) {
                Text("Rename", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(shape = AppButtonShape, onClick = { pendingDelete = true }) {
                Text("Delete", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(shape = AppButtonShape, 
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename deck") },
            text = {
                AppTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    val current = deck ?: return@TextButton
                    scope.launch {
                        repo.renameDeck(current, renameInput)
                        showRenameDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(shape = AppButtonShape, onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete deck") },
            text = { Text("Delete \"${deck?.name ?: "this deck"}\"?") },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    val current = deck ?: return@TextButton
                    scope.launch {
                        repo.deleteDeck(current.id)
                        pendingDelete = false
                        onClose()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(shape = AppButtonShape, onClick = { pendingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AppFormattingGuideDialog(
    uiStyle: UiStyleSettings,
    onSaveStyle: (UiStyleSettings) -> Unit,
    onDismiss: () -> Unit,
    onCopy: (label: String, snippet: String) -> Unit,
) {
    var showAccentMenu by remember { mutableStateOf(false) }
    var showSizeMenu by remember { mutableStateOf(false) }
    var showBackgroundMenu by remember { mutableStateOf(false) }
    var showSurfaceMenu by remember { mutableStateOf(false) }
    var showTextMenu by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }
    var bulkThemeInput by remember { mutableStateOf("") }
    var bulkThemeMessage by remember { mutableStateOf<String?>(null) }

    @Composable
    fun SectionTitle(title: String, description: String) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    fun LabeledSettingRow(label: String, helper: String? = null, controls: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                helper?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                controls()
            }
        }
    }

    @Composable
    fun stylePickerRow(
        title: String,
        currentSize: Int,
        currentColorIndex: Int,
        onSizeSelected: (Int) -> Unit,
        onColorSelected: (Int) -> Unit,
    ) {
        var sizeMenu by remember { mutableStateOf(false) }
        var colorMenu by remember { mutableStateOf(false) }
        LabeledSettingRow(label = title, helper = "文字サイズと色の強弱") {
            Box {
                TextButton(shape = AppButtonShape, onClick = { sizeMenu = true }) { Text("${currentSize}sp") }
                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                    UiStylePresets.markdownSizeOptions.forEach { size ->
                        DropdownMenuItem(
                            text = { Text("${size}sp") },
                            onClick = {
                                onSizeSelected(size)
                                sizeMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                TextButton(shape = AppButtonShape, onClick = { colorMenu = true }) {
                    Text(UiStylePresets.markdownEmphasisLabels.getOrElse(currentColorIndex) { "Base text" })
                }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    UiStylePresets.markdownEmphasisLabels.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onColorSelected(index)
                                colorMenu = false
                            },
                        )
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App formatting guide") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text("These Markdown styles are shared across all decks and cards.")

                SurfaceCard {
                    SectionTitle("Theme", "項目名を見やすく強調し、関連設定をまとめました")
                    LabeledSettingRow("Bulk theme input", "CSS variables または JSON を貼り付け") {
                        TextField(
                            value = bulkThemeInput,
                            onValueChange = {
                                bulkThemeInput = it
                                bulkThemeMessage = null
                            },
                            placeholder = { Text("Paste :root vars or a colors JSON block") },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(shape = AppButtonShape, onClick = {
                            val parsed = parseThemeColors(bulkThemeInput)
                            if (parsed == null) {
                                bulkThemeMessage = "Could not parse 5 colors. Required: background, surface, text, primary, accent."
                            } else {
                                onSaveStyle(
                                    uiStyle.copy(
                                        customBackgroundColorArgb = parsed.background.toArgb(),
                                        customSurfaceColorArgb = parsed.surface.toArgb(),
                                        customTextColorArgb = parsed.text.toArgb(),
                                        customPrimaryColorArgb = parsed.primary.toArgb(),
                                        customSecondaryColorArgb = parsed.accent.toArgb(),
                                    ),
                                )
                                bulkThemeMessage = "Theme colors applied."
                            }
                        }) { Text("Apply bulk colors") }
                        TextButton(shape = AppButtonShape, onClick = {
                            bulkThemeInput = ""
                            bulkThemeMessage = null
                        }) { Text("Clear") }
                    }
                    bulkThemeMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    HorizontalDivider()
                    LabeledSettingRow("Accent", "アプリ全体の強調色") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showAccentMenu = true }) {
                                Text(UiStylePresets.accentLabels.getOrElse(uiStyle.accentIndex) { "Blue" })
                            }
                            DropdownMenu(expanded = showAccentMenu, onDismissRequest = { showAccentMenu = false }) {
                                UiStylePresets.accentLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(accentIndex = index, customPrimaryColorArgb = null, customSecondaryColorArgb = null))
                                        showAccentMenu = false
                                    })
                                }
                            }
                        }
                    }
                    LabeledSettingRow("Background", "画面全体の背景色") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showBackgroundMenu = true }) {
                                Text(UiStylePresets.surfaceBackgroundLabels.getOrElse(uiStyle.backgroundColorIndex) { "Navy" })
                            }
                            DropdownMenu(expanded = showBackgroundMenu, onDismissRequest = { showBackgroundMenu = false }) {
                                UiStylePresets.surfaceBackgroundLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(backgroundColorIndex = index, customBackgroundColorArgb = null))
                                        showBackgroundMenu = false
                                    })
                                }
                            }
                        }
                    }
                    LabeledSettingRow("Surface", "カードやダイアログの面色") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showSurfaceMenu = true }) {
                                Text(UiStylePresets.surfaceBackgroundLabels.getOrElse(uiStyle.surfaceColorIndex) { "Deep blue" })
                            }
                            DropdownMenu(expanded = showSurfaceMenu, onDismissRequest = { showSurfaceMenu = false }) {
                                UiStylePresets.surfaceBackgroundLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(surfaceColorIndex = index, customSurfaceColorArgb = null))
                                        showSurfaceMenu = false
                                    })
                                }
                            }
                        }
                    }
                    LabeledSettingRow("Text", "本文の標準文字色") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showTextMenu = true }) {
                                Text(UiStylePresets.textLabels.getOrElse(uiStyle.textColorIndex) { "Ice" })
                            }
                            DropdownMenu(expanded = showTextMenu, onDismissRequest = { showTextMenu = false }) {
                                UiStylePresets.textLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(textColorIndex = index, customTextColorArgb = null))
                                        showTextMenu = false
                                    })
                                }
                            }
                        }
                    }
                }

                SurfaceCard {
                    SectionTitle("Typography", "フォントと形状をまとめて調整")
                    LabeledSettingRow("Font", "全体で使うフォントファミリー") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showFontMenu = true }) {
                                Text(AppFontMode.fromId(uiStyle.fontModeId).label)
                            }
                            DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                AppFontMode.entries.forEach { mode ->
                                    DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(fontModeId = mode.id))
                                        showFontMenu = false
                                    })
                                }
                            }
                        }
                    }
                    LabeledSettingRow("Button radius", "ボタン形状の印象を調整") {
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { showSizeMenu = true }) {
                                Text("${uiStyle.buttonCornerRadiusDp}dp")
                            }
                            DropdownMenu(expanded = showSizeMenu, onDismissRequest = { showSizeMenu = false }) {
                                listOf(0, 8, 16, 24).forEach { radius ->
                                    DropdownMenuItem(
                                        text = { Text("Radius ${radius}dp") },
                                        onClick = {
                                            onSaveStyle(uiStyle.copy(buttonCornerRadiusDp = radius))
                                            showSizeMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                SurfaceCard {
                    SectionTitle("Markdown styles", "見出し・本文・装飾の階層を設計")
                    stylePickerRow(
                        title = "H3",
                        currentSize = uiStyle.h3SizeSp,
                        currentColorIndex = uiStyle.h3ColorIndex,
                        onSizeSelected = { onSaveStyle(uiStyle.copy(h3SizeSp = it)) },
                        onColorSelected = { onSaveStyle(uiStyle.copy(h3ColorIndex = it)) },
                    )
                    stylePickerRow(
                        title = "H4",
                        currentSize = uiStyle.h4SizeSp,
                        currentColorIndex = uiStyle.h4ColorIndex,
                        onSizeSelected = { onSaveStyle(uiStyle.copy(h4SizeSp = it)) },
                        onColorSelected = { onSaveStyle(uiStyle.copy(h4ColorIndex = it)) },
                    )
                    stylePickerRow(
                        title = "Body",
                        currentSize = uiStyle.bodySizeSp,
                        currentColorIndex = uiStyle.bodyColorIndex,
                        onSizeSelected = { onSaveStyle(uiStyle.copy(bodySizeSp = it)) },
                        onColorSelected = { onSaveStyle(uiStyle.copy(bodyColorIndex = it)) },
                    )
                    stylePickerRow(
                        title = "List",
                        currentSize = uiStyle.listSizeSp,
                        currentColorIndex = uiStyle.listColorIndex,
                        onSizeSelected = { onSaveStyle(uiStyle.copy(listSizeSp = it)) },
                        onColorSelected = { onSaveStyle(uiStyle.copy(listColorIndex = it)) },
                    )
                    LabeledSettingRow("Bold color", "強調テキスト（** **）の色") {
                        var boldMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { boldMenu = true }) {
                                Text(UiStylePresets.markdownEmphasisLabels.getOrElse(uiStyle.boldColorIndex) { "Base text" })
                            }
                            DropdownMenu(expanded = boldMenu, onDismissRequest = { boldMenu = false }) {
                                UiStylePresets.markdownEmphasisLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(boldColorIndex = index))
                                        boldMenu = false
                                    })
                                }
                            }
                        }
                    }
                    LabeledSettingRow("Italic color", "斜体テキスト（* *）の色") {
                        var italicMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(shape = AppButtonShape, onClick = { italicMenu = true }) {
                                Text(UiStylePresets.markdownEmphasisLabels.getOrElse(uiStyle.italicColorIndex) { "Subtle" })
                            }
                            DropdownMenu(expanded = italicMenu, onDismissRequest = { italicMenu = false }) {
                                UiStylePresets.markdownEmphasisLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        onSaveStyle(uiStyle.copy(italicColorIndex = index))
                                        italicMenu = false
                                    })
                                }
                            }
                        }
                    }
                }

                TextButton(shape = AppButtonShape, onClick = { onSaveStyle(UiStyleSettings()) }) {
                    Text("Reset all to default")
                }

                SectionTitle("Preview", "設定後の見え方をすぐ確認")
                SurfaceCard {
                    MarkdownTokenText(
                        markdown = "### Photosynthesis\n#### Light reaction\n**Chlorophyll** captures *photon energy*\n- ATP synthesis\n- NADPH generation",
                        uiStyle = uiStyle,
                    )
                }

                formattingExamples.forEach { item ->
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(shape = AppButtonShape, onClick = { onCopy(item.label.lowercase(), item.snippet) }) {
                            Text("Copy ${item.label}")
                        }
                        Text(item.snippet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(shape = AppButtonShape, onClick = onDismiss) { Text("Close") }
        },
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(deckId: Long?, repo: IngrainRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)

    val decks by produceState(initialValue = emptyList<DeckEntity>(), repo) {
        repo.observeDecks().collect { value = it }
    }

    var simpleMode by rememberSaveable { mutableStateOf(true) }
    var continuousMode by rememberSaveable { mutableStateOf(true) }
    var keepDeckInContinuous by rememberSaveable { mutableStateOf(true) }
    var selectedDeckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckName by rememberSaveable { mutableStateOf("") }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var templateFormat by rememberSaveable { mutableStateOf(TemplateFormat.Markdown) }
    var bulkText by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<ParseResult>>(emptyList()) }
    val recentPresets = remember { mutableStateListOf<AddPreset>() }
    var duplicateHint by remember { mutableStateOf<String?>(null) }
    val selectedDeck = remember(selectedDeckId, decks) { decks.firstOrNull { it.id == selectedDeckId } }

    LaunchedEffect(deckId, decks) {
        if (decks.isEmpty()) {
            selectedDeckId = null
            return@LaunchedEffect
        }
        if (selectedDeckId != null && decks.none { it.id == selectedDeckId }) {
            selectedDeckId = null
        }
        if (deckId != null && selectedDeckId == null) {
            val deckFromRoute = decks.firstOrNull { it.id == deckId }
            if (deckFromRoute != null) {
                deckName = deckFromRoute.name
            }
        }
    }

    fun clearDraft(preserveTags: Boolean, preserveDeck: Boolean) {
        front = ""
        back = ""
        if (!preserveTags) tags = ""
        if (!preserveDeck && selectedDeckId == null) deckName = ""
    }

    suspend fun submitCard() {
        val now = System.currentTimeMillis()
        val targetDeck = selectedDeck?.name ?: deckName.trim()
        if (targetDeck.isBlank()) {
            message = "Deck is required in global add"
            return
        }

        repo.addCard(
            deckName = targetDeck,
            front = front,
            back = back,
            tags = parseTags(tags),
            now = now,
        ).onSuccess { added ->
            message = if (added) "Card added to $targetDeck" else "Duplicate skipped"
            if (added) {
                val used = AddPreset("recent", front, back, tags)
                recentPresets.removeAll { it.front == used.front && it.back == used.back && it.tags == used.tags }
                recentPresets.add(0, used)
                if (recentPresets.size > 5) recentPresets.removeAt(recentPresets.lastIndex)
            }
            clearDraft(
                preserveTags = continuousMode,
                preserveDeck = continuousMode && keepDeckInContinuous,
            )
        }.onFailure {
            message = it.message ?: "Add failed"
        }
    }

    LaunchedEffect(front, back, selectedDeckId, deckName, decks) {
        duplicateHint = null
        val targetDeck = selectedDeck?.name ?: deckName.trim()
        val trimmedFront = front.trim()
        val trimmedBack = back.trim()
        if (targetDeck.isBlank() || trimmedFront.isBlank() || trimmedBack.isBlank()) return@LaunchedEffect
        val deck = repo.findDeckByName(targetDeck) ?: return@LaunchedEffect
        if (repo.cardExists(deck.id, trimmedFront, trimmedBack)) {
            duplicateHint = "The same card already exists (save will be skipped)."
        }
    }

    val draft = ManualAddDraft(deckName = deckName, front = front, back = back, tags = tags)

    Scaffold(topBar = { TopAppBar(title = { Text("Add & Import") }) }) { p ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .onPreviewKeyEvent {
                    if (it.isCtrlPressed && it.key == Key.Enter) {
                        scope.launch { submitCard() }
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ManualAddSection(
                selectedDeckId = selectedDeckId,
                decks = decks,
                onSelectedDeckIdChange = { selectedDeckId = it },
                simpleMode = simpleMode,
                onSimpleModeChange = { simpleMode = it },
                draft = draft,
                onDraftChange = {
                    deckName = it.deckName
                    front = it.front
                    back = it.back
                    tags = it.tags
                },
                continuousMode = continuousMode,
                onContinuousModeChange = { continuousMode = it },
                keepDeckInContinuous = keepDeckInContinuous,
                onKeepDeckInContinuousChange = { keepDeckInContinuous = it },
                duplicateHint = duplicateHint,
                recentPresets = recentPresets,
                templateFormat = templateFormat,
                onTemplateFormatChange = { templateFormat = it },
                onCopyTemplate = {
                    val targetDeck = selectedDeck?.name ?: deckName.ifBlank { "<deck-name>" }
                    val template = when (templateFormat) {
                        TemplateFormat.Markdown -> markdownTemplate(
                            deckName = targetDeck,
                            front = front,
                            back = back,
                            tags = tags,
                        )
                        TemplateFormat.JsonLines -> jsonTemplate(
                            deckName = targetDeck,
                            front = front,
                            back = back,
                            tags = tags,
                        )
                    }
                    clipboard?.setPrimaryClip(ClipData.newPlainText("template", template))
                    message = "${templateFormat.label} template copied to clipboard"
                },
                onCancel = { clearDraft(preserveTags = false, preserveDeck = false) },
                onSave = { scope.launch { submitCard() } },
            )

            BulkImportSection(
                bulkText = bulkText,
                onBulkTextChange = {
                    bulkText = it
                    preview = BulkImportParser.parse(it)
                },
                preview = preview,
                onPreview = { preview = BulkImportParser.parse(bulkText) },
                onImport = {
                    scope.launch {
                        val result = repo.importParsed(BulkImportParser.parse(bulkText), System.currentTimeMillis())
                        message = "added=${result.added}, skipped=${result.skipped}, failed=${result.failed}"
                    }
                },
            )

            if (message.isNotBlank()) Text(message)
        }
    }
}

@Composable
private fun ManualAddSection(
    selectedDeckId: Long?,
    decks: List<DeckEntity>,
    onSelectedDeckIdChange: (Long?) -> Unit,
    simpleMode: Boolean,
    onSimpleModeChange: (Boolean) -> Unit,
    draft: ManualAddDraft,
    onDraftChange: (ManualAddDraft) -> Unit,
    continuousMode: Boolean,
    onContinuousModeChange: (Boolean) -> Unit,
    keepDeckInContinuous: Boolean,
    onKeepDeckInContinuousChange: (Boolean) -> Unit,
    duplicateHint: String?,
    recentPresets: List<AddPreset>,
    templateFormat: TemplateFormat,
    onTemplateFormatChange: (TemplateFormat) -> Unit,
    onCopyTemplate: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
    Text("Target deck")
    AppTextField(
        value = selectedDeck?.name ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text("Selected deck (optional)") },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Not selected") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(shape = AppButtonShape, onClick = { onSelectedDeckIdChange(null) }) { Text("None") }
        decks.forEach {
            TextButton(shape = AppButtonShape, onClick = { onSelectedDeckIdChange(it.id) }) { Text(it.name) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(shape = AppButtonShape, onClick = onCancel) { Text("Cancel") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(shape = AppButtonShape, 
                selected = simpleMode,
                onClick = { onSimpleModeChange(true) },
                label = { Text("Simple") },
                
            )
            FilterChip(shape = AppButtonShape, 
                selected = !simpleMode,
                onClick = { onSimpleModeChange(false) },
                label = { Text("Detailed") },
                
            )
        }
    }

    if (selectedDeckId == null) {
        AppTextField(
            value = draft.deckName,
            onValueChange = { onDraftChange(draft.copy(deckName = it)) },
            label = { Text("Deck") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("BIOLOGY 101") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            decks.forEach {
                TextButton(shape = AppButtonShape, onClick = { onDraftChange(draft.copy(deckName = it.name)) }) { Text(it.name) }
            }
        }
    }

    SectionHeading("FRONT SIDE", primary = true)
    AppTextField(
        value = draft.front,
        onValueChange = { onDraftChange(draft.copy(front = it)) },
        label = { Text("Term or Question") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    SectionHeading("BACK SIDE")
    AppTextField(
        value = draft.back,
        onValueChange = { onDraftChange(draft.copy(back = it)) },
        label = { Text("Definition or Answer") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
    )
    if (!simpleMode) {
        AppTextField(
            value = draft.tags,
            onValueChange = { onDraftChange(draft.copy(tags = it)) },
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }


    if (duplicateHint != null) {
        Text(duplicateHint, color = MaterialTheme.colorScheme.error)
    }

    TemplateSection(
        simpleMode = simpleMode,
        draft = draft,
        onDraftChange = onDraftChange,
        templateFormat = templateFormat,
        onTemplateFormatChange = onTemplateFormatChange,
        onCopyTemplate = onCopyTemplate,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Continuous mode")
        Switch(checked = continuousMode, onCheckedChange = onContinuousModeChange)
    }
    Text(
        text = "When enabled, front/back fields stay open after save for rapid entry.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selectedDeckId == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keep deck")
            Switch(checked = keepDeckInContinuous, onCheckedChange = onKeepDeckInContinuousChange)
        }
        Text(
            text = "Keeps the typed deck name after saving in continuous mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Button(shape = AppButtonShape, onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save Card (Ctrl+Enter)") }

    if (recentPresets.isNotEmpty()) {
        Text("Recent inputs")
        recentPresets.forEachIndexed { idx, preset ->
            TextButton(shape = AppButtonShape, onClick = {
                onDraftChange(draft.copy(front = preset.front, back = preset.back, tags = preset.tags))
            }) {
                Text("${idx + 1}. ${preset.front.take(24)}")
            }
        }
    }
}

@Composable
private fun TemplateSection(
    simpleMode: Boolean,
    draft: ManualAddDraft,
    onDraftChange: (ManualAddDraft) -> Unit,
    templateFormat: TemplateFormat,
    onTemplateFormatChange: (TemplateFormat) -> Unit,
    onCopyTemplate: () -> Unit,
) {
    Text("Templates")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        addTemplates.forEach { preset ->
            TextButton(shape = AppButtonShape, onClick = {
                val updatedTags = if (simpleMode) draft.tags else preset.tags
                onDraftChange(draft.copy(front = preset.front, back = preset.back, tags = updatedTags))
            }) { Text(preset.name) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplateFormat.entries.forEach { format ->
            FilterChip(
                shape = AppButtonShape,
                selected = templateFormat == format,
                onClick = { onTemplateFormatChange(format) },
                label = { Text(format.label) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(shape = AppButtonShape, onClick = onCopyTemplate) { Text("Copy template") }
        Button(shape = AppButtonShape, onClick = {
            val default = addTemplates.first()
            val updatedTags = if (simpleMode) draft.tags else default.tags
            onDraftChange(draft.copy(front = default.front, back = default.back, tags = updatedTags))
        }) {
            Text("Insert into form")
        }
    }
}

@Composable
private fun BulkImportSection(
    bulkText: String,
    onBulkTextChange: (String) -> Unit,
    preview: List<ParseResult>,
    onPreview: () -> Unit,
    onImport: () -> Unit,
) {
    Text("Bulk add with Markdown/YAML or JSON Lines")
    AppTextField(
        value = bulkText,
        onValueChange = onBulkTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste Markdown cards or JSON Lines") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(shape = AppButtonShape, onClick = onPreview) { Text("Preview") }
        Button(shape = AppButtonShape, onClick = onImport) { Text("Import") }
    }
    preview.take(20).forEach { result ->
        when (result) {
            is ParseResult.Success -> Text("✅ line ${result.lineNumber}: ${result.card.deck} / ${result.card.front}")
            is ParseResult.Error -> Text("❌ line ${result.lineNumber}: ${result.message}", color = MaterialTheme.colorScheme.error)
        }
    }
    if (preview.size > 20) Text("...and ${preview.size - 20} more")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    deckId: Long,
    repo: IngrainRepository,
    settingsStore: SchedulerSettingsStore,
    uiStyleStore: UiStyleSettingsStore,
    onEditCard: (Long) -> Unit,
    onBackToDecks: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val schedulerSettings by settingsStore.settings.collectAsState(initial = SchedulerSettings())
    val uiStyle by uiStyleStore.settings.collectAsState(initial = UiStyleSettings())
    var card by remember { mutableStateOf<CardEntity?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var remainingToday by remember { mutableStateOf(0) }
    var pendingCardDelete by remember { mutableStateOf<CardEntity?>(null) }
    var deckName by remember { mutableStateOf("Deck") }

    suspend fun loadDueCard() {
        val deck = repo.getDeck(deckId)
        if (deck == null) {
            card = null
            remainingToday = 0
            message = "Deck not found"
            deckName = "Deck"
            return
        }
        deckName = deck.name
        val now = System.currentTimeMillis()
        val dayStart = startOfDayMillis(now)
        val dayEnd = endOfDayMillis(now)
        card = repo.nextDueCard(deck, now, dayStart, dayEnd)
        remainingToday = repo.countDueUntil(deck, dayStart, dayEnd, dayEnd)
        showAnswer = false
        if (card == null) message = "You're done for today"
    }

    suspend fun submitReview(currentCard: CardEntity, rating: String) {
        val settings = settingsStore.settings.first()
        repo.review(currentCard, rating, settings, System.currentTimeMillis())
        loadDueCard()
    }

    LaunchedEffect(deckId) { loadDueCard() }

    Scaffold { p ->
        val currentCard = card
        val progressText = if (remainingToday <= 0) "Done" else remainingToday.toString()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .pointerInput(currentCard?.id, showAnswer) {
                    if (currentCard != null && !showAnswer) {
                        detectTapGestures { showAnswer = true }
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = deckName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (currentCard != null && !showAnswer) {
                Text(
                    text = "Tap card to reveal answer • Swipe up to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp)
                    .pointerInput(currentCard?.id) {
                        var totalDrag = 0f
                        var opened = false
                        detectVerticalDragGestures(
                            onDragEnd = {
                                totalDrag = 0f
                                opened = false
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                if (!opened && totalDrag < -120f && currentCard != null) {
                                    opened = true
                                    onEditCard(currentCard.id)
                                }
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                if (currentCard == null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    if (message == "You're done for today") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(shape = AppButtonShape, onClick = onBackToDecks) {
                            Text("Back to Decks")
                        }
                    }
                } else {
                    MarkdownTokenText(
                        markdown = currentCard.front,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        uiStyle = uiStyle,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(26.dp))

                    if (showAnswer) {
                        MarkdownTokenText(
                            markdown = currentCard.back,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            uiStyle = uiStyle,
                        )
                    }
                }
            }

            if (currentCard != null && showAnswer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(shape = AppButtonShape,
                        onClick = { scope.launch { submitReview(currentCard, RatingAgain) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "~${schedulerSettings.againDelayMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text("Again")
                        }
                    }
                    Button(shape = AppButtonShape,
                        onClick = { scope.launch { submitReview(currentCard, RatingGood) } },
                        modifier = Modifier.weight(2f),
                    ) {
                        val nextGoodInterval = Scheduler.scheduleGood(
                            currentCard,
                            System.currentTimeMillis(),
                            schedulerSettings,
                        ).intervalDays
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatIntervalShort(nextGoodInterval),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text("Good")
                        }
                    }
                }
                TextButton(shape = AppButtonShape,
                    onClick = { pendingCardDelete = currentCard },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text("Delete card", color = MaterialTheme.colorScheme.error)
                }
            }

        }
    }

    val targetCard = pendingCardDelete
    if (targetCard != null) {
        AlertDialog(
            onDismissRequest = { pendingCardDelete = null },
            title = { Text("Delete card") },
            text = { Text("Delete this card?") },
            confirmButton = {
                TextButton(shape = AppButtonShape, onClick = {
                    scope.launch {
                        repo.deleteCard(targetCard.id)
                        pendingCardDelete = null
                        loadDueCard()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(shape = AppButtonShape, onClick = { pendingCardDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(cardId: Long, repo: IngrainRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<CardEntity?>(null) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(cardId) {
        val loaded = repo.getCard(cardId)
        card = loaded
        if (loaded != null) {
            front = loaded.front
            back = loaded.back
            tags = runCatching { screenJson.decodeFromString<List<String>>(loaded.tagsJson) }
                .getOrDefault(emptyList())
                .joinToString(",")
        } else {
            message = "Card not found"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Edit Card") }) }) { p ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(value = front, onValueChange = { front = it }, label = { Text("Front") }, modifier = Modifier.fillMaxWidth())
            AppTextField(value = back, onValueChange = { back = it }, label = { Text("Back") }, modifier = Modifier.fillMaxWidth())
            AppTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = AppButtonShape, onClick = onClose) { Text("Cancel") }
                Button(shape = AppButtonShape, onClick = {
                    val target = card ?: return@Button
                    scope.launch {
                        repo.updateCardContent(target, front, back, parseTags(tags))
                            .onSuccess {
                                onClose()
                            }
                            .onFailure {
                                message = it.message ?: "Save failed"
                            }
                    }
                }) { Text("Save") }
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(deckId: Long, repo: IngrainRepository, store: SchedulerSettingsStore) {
    val scope = rememberCoroutineScope()
    val settings by produceState(initialValue = SchedulerSettings(), store) {
        store.settings.collect { value = it }
    }

    var initial by remember(settings) { mutableStateOf(settings.initialIntervalGoodDays.toString()) }
    var second by remember(settings) { mutableStateOf(settings.secondIntervalGoodDays.toString()) }
    var minEase by remember(settings) { mutableStateOf(settings.minEaseFactor.toString()) }
    var stepGood by remember(settings) { mutableStateOf(settings.easeFactorStepGood.toString()) }
    var stepAgain by remember(settings) { mutableStateOf(settings.easeFactorStepAgain.toString()) }
    var againDelay by remember(settings) { mutableStateOf(settings.againDelayMinutes.toString()) }
    var reviewLimitInput by remember { mutableStateOf("") }
    var newCardLimitInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(deckId) {
        repo.getDeck(deckId)?.let { deck ->
            reviewLimitInput = deck.dailyReviewLimit.toString()
            newCardLimitInput = deck.dailyNewCardLimit.toString()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Deck Settings") }) }) { p ->
        Column(
            Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard {
                Text("Study options", style = MaterialTheme.typography.titleMedium)
                AppTextField(
                    value = reviewLimitInput,
                    onValueChange = {
                        reviewLimitInput = it
                        message = ""
                    },
                    label = { Text("Daily review limit") },
                    singleLine = true,
                )
                AppTextField(
                    value = newCardLimitInput,
                    onValueChange = {
                        newCardLimitInput = it
                        message = ""
                    },
                    label = { Text("Daily new card limit") },
                    singleLine = true,
                )
                Button(shape = AppButtonShape, onClick = {
                    scope.launch {
                        val deck = repo.getDeck(deckId)
                        val parsedReview = reviewLimitInput.toIntOrNull()
                        val parsedNew = newCardLimitInput.toIntOrNull()
                        message = if (deck == null) {
                            "Deck not found"
                        } else if (parsedReview == null || parsedNew == null) {
                            "Invalid numeric input"
                        } else {
                            val result = repo.updateDeckStudyOptions(deck, parsedReview, parsedNew)
                            result.exceptionOrNull()?.message ?: "Study options saved"
                        }
                    }
                }) { Text("Save study options") }
            }

            SurfaceCard {
                Text("Scheduler tuning", style = MaterialTheme.typography.titleMedium)
                AppTextField(initial, { initial = it }, label = { Text("Initial interval (days)") })
                AppTextField(second, { second = it }, label = { Text("Second interval (days)") })
                AppTextField(minEase, { minEase = it }, label = { Text("Minimum ease factor") })
                AppTextField(stepGood, { stepGood = it }, label = { Text("Ease step (Good)") })
                AppTextField(stepAgain, { stepAgain = it }, label = { Text("Ease step (Again)") })
                AppTextField(againDelay, { againDelay = it }, label = { Text("Again delay (minutes)") })
                Button(shape = AppButtonShape, onClick = {
                    scope.launch {
                        val parsed = runCatching {
                            SchedulerSettings(
                                initialIntervalGoodDays = initial.toDouble(),
                                secondIntervalGoodDays = second.toDouble(),
                                minEaseFactor = minEase.toDouble(),
                                easeFactorStepGood = stepGood.toDouble(),
                                easeFactorStepAgain = stepAgain.toDouble(),
                                againDelayMinutes = againDelay.toInt(),
                            )
                        }.getOrNull()

                        message = if (parsed == null) {
                            "Invalid numeric input"
                        } else {
                            store.save(parsed)
                            "Settings saved"
                        }
                    }
                }) { Text("Save") }
                if (message.isNotBlank()) Text(message)
            }
        }
    }
}

private enum class FileOperation { EXPORT, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(deckId: Long, repo: IngrainRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var message by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOperation by remember { mutableStateOf<FileOperation?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        pendingUri = uri
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri
    }

    LaunchedEffect(pendingUri, pendingOperation) {
        val uri = pendingUri
        val operation = pendingOperation
        if (uri == null || operation == null) return@LaunchedEffect

        scope.launch {
            runCatching {
                when (operation) {
                    FileOperation.EXPORT -> {
                        val content = repo.exportDeckMarkdown(deckId)
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(content.toByteArray())
                        }
                        message = "Export complete"
                    }

                    FileOperation.IMPORT -> {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()
                        val summary = repo.importParsed(BulkImportParser.parse(text), System.currentTimeMillis())
                        message = "Import done: added=${summary.added}, skipped=${summary.skipped}, failed=${summary.failed}"
                    }
                }
            }.onFailure { error ->
                message = "File operation failed: ${error.message}"
            }
            pendingUri = null
            pendingOperation = null
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Backup & Restore") }) }) { p ->
        Column(
            Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard {
                Text("Data portability", style = MaterialTheme.typography.titleMedium)
                Text("Export your deck or import from Markdown/JSONL backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(shape = AppButtonShape, onClick = {
                    pendingOperation = FileOperation.EXPORT
                    createLauncher.launch("ingrain-backup.md")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export deck")
                }
                Button(shape = AppButtonShape, onClick = {
                    pendingOperation = FileOperation.IMPORT
                    openLauncher.launch(arrayOf("text/markdown", "application/json"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import file")
                }
                if (message.isNotBlank()) Text(message)
            }
        }
    }
}
