package com.ingrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ingrain.data.CardEntity
import com.ingrain.data.DeckEntity
import com.ingrain.data.IngrainRepository
import com.ingrain.importing.JsonLinesParser
import com.ingrain.importing.ParseResult
import com.ingrain.scheduler.SchedulerSettings
import com.ingrain.scheduler.SchedulerSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class AddPreset(val name: String, val front: String, val back: String, val tags: String)

private val addTemplates = listOf(
    AddPreset("Basic", "Term or Question", "Definition or Answer", ""),
    AddPreset("Example", "Word", "Meaning + Example Sentence", "vocab"),
    AddPreset("Cloze", "Sentence with {{c1::key term}}", "Extra explanation", "cloze"),
)

private const val RatingAgain = "AGAIN"
private const val RatingGood = "GOOD"

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

private fun parseTags(raw: String): List<String> = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

private val screenJson = Json { ignoreUnknownKeys = true }

private fun CardEntity.primaryTag(): String {
    val tags = runCatching { screenJson.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList())
    return tags.firstOrNull()?.uppercase() ?: "STUDY"
}

private fun jsonTemplate(deckName: String, front: String, back: String, tags: String): String {
    val tagList = parseTags(tags).joinToString(",") { "\"$it\"" }
    return """{"deck":"$deckName","front":"$front","back":"$back","tags":[$tagList]}"""
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
fun DeckListScreen(repo: IngrainRepository, onOpenDeck: (Long) -> Unit, onGlobalAdd: () -> Unit) {
    val decks by produceState(initialValue = emptyList<DeckEntity>(), repo) {
        repo.observeDecks().collect { value = it }
    }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Ingrain") }) }) { p ->
        Column(
            Modifier
                .padding(p)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Decks", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onGlobalAdd) { Text("Add Card") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New deck") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    scope.launch {
                        error = repo.createDeck(name).exceptionOrNull()?.message
                        if (error == null) name = ""
                    }
                }) {
                    Text("Create")
                }
            }
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            if (decks.isEmpty()) {
                Text(
                    text = "No decks yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                decks.forEachIndexed { index, deck ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onOpenDeck(deck.id) }) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { scope.launch { repo.deleteDeck(deck.id) } }) {
                            Text("Delete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < decks.lastIndex) HorizontalDivider()
                }
            }
        }
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
) {
    val scope = rememberCoroutineScope()
    val deck by produceState<DeckEntity?>(initialValue = null, deckId) { value = repo.getDeck(deckId) }
    var rename by remember(deck?.name) { mutableStateOf(deck?.name ?: "") }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(deck?.name ?: "Deck") }) }) { p ->
        Column(
            Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard {
                Text("Deck setup", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = rename,
                    onValueChange = { rename = it },
                    label = { Text("Deck name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    val current = deck ?: return@Button
                    scope.launch {
                        message = repo.renameDeck(current, rename).exceptionOrNull()?.message ?: "Deck renamed"
                    }
                }) { Text("Rename") }
                if (message != null) Text(message!!)
            }

            SurfaceCard {
                Text("Actions", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onStudy, modifier = Modifier.fillMaxWidth()) { Text("Study") }
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Add Cards") }
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Scheduler Settings") }
                TextButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup & Restore") }
            }
        }
    }
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
    val fixedDeck = remember(deckId, decks) { decks.firstOrNull { it.id == deckId } }

    var simpleMode by rememberSaveable { mutableStateOf(true) }
    var continuousMode by rememberSaveable { mutableStateOf(true) }
    var keepDeckInContinuous by rememberSaveable { mutableStateOf(true) }
    var deckName by rememberSaveable { mutableStateOf("") }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var bulkText by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<ParseResult>>(emptyList()) }
    val recentPresets = remember { mutableStateListOf<AddPreset>() }
    var duplicateHint by remember { mutableStateOf<String?>(null) }

    fun clearDraft(preserveTags: Boolean, preserveDeck: Boolean) {
        front = ""
        back = ""
        if (!preserveTags) tags = ""
        if (!preserveDeck && fixedDeck == null) deckName = ""
    }

    suspend fun submitCard() {
        val now = System.currentTimeMillis()
        val targetDeck = fixedDeck?.name ?: deckName.trim()
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

    LaunchedEffect(front, back, fixedDeck?.id, deckName) {
        duplicateHint = null
        val targetDeck = fixedDeck?.name ?: deckName.trim()
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
                fixedDeck = fixedDeck,
                decks = decks,
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
                onCopyTemplate = {
                    val template = jsonTemplate(
                        deckName = fixedDeck?.name ?: deckName.ifBlank { "<deck-name>" },
                        front = front,
                        back = back,
                        tags = tags,
                    )
                    clipboard?.setPrimaryClip(ClipData.newPlainText("template", template))
                    message = "Template copied to clipboard"
                },
                onCancel = { clearDraft(preserveTags = false, preserveDeck = false) },
                onSave = { scope.launch { submitCard() } },
            )

            BulkImportSection(
                bulkText = bulkText,
                onBulkTextChange = {
                    bulkText = it
                    preview = JsonLinesParser.parse(it)
                },
                preview = preview,
                onPreview = { preview = JsonLinesParser.parse(bulkText) },
                onImport = {
                    scope.launch {
                        val result = repo.importParsed(JsonLinesParser.parse(bulkText), System.currentTimeMillis())
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
    fixedDeck: DeckEntity?,
    decks: List<DeckEntity>,
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
    onCopyTemplate: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Text(if (fixedDeck != null) "Target deck: ${fixedDeck.name}" else "Global add mode")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = simpleMode, onClick = { onSimpleModeChange(true) }, label = { Text("Simple") })
            FilterChip(selected = !simpleMode, onClick = { onSimpleModeChange(false) }, label = { Text("Detailed") })
        }
    }

    if (fixedDeck == null) {
        OutlinedTextField(
            value = draft.deckName,
            onValueChange = { onDraftChange(draft.copy(deckName = it)) },
            label = { Text("Deck") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("BIOLOGY 101") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            decks.take(3).forEach {
                TextButton(onClick = { onDraftChange(draft.copy(deckName = it.name)) }) { Text(it.name) }
            }
        }
    }

    SectionHeading("FRONT SIDE", primary = true)
    OutlinedTextField(
        value = draft.front,
        onValueChange = { onDraftChange(draft.copy(front = it)) },
        label = { Text("Term or Question") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )

    SectionHeading("BACK SIDE")
    OutlinedTextField(
        value = draft.back,
        onValueChange = { onDraftChange(draft.copy(back = it)) },
        label = { Text("Definition or Answer") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
    )

    if (!simpleMode) {
        OutlinedTextField(
            value = draft.tags,
            onValueChange = { onDraftChange(draft.copy(tags = it)) },
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { }) { Text("Add Tag") }
        Button(onClick = { }) { Text("Record Audio") }
    }

    if (duplicateHint != null) {
        Text(duplicateHint, color = MaterialTheme.colorScheme.error)
    }

    TemplateSection(
        simpleMode = simpleMode,
        draft = draft,
        onDraftChange = onDraftChange,
        onCopyTemplate = onCopyTemplate,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Continuous mode")
        Switch(checked = continuousMode, onCheckedChange = onContinuousModeChange)
        if (fixedDeck == null) {
            Text("Keep deck")
            Switch(checked = keepDeckInContinuous, onCheckedChange = onKeepDeckInContinuousChange)
        }
    }

    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save Card (Ctrl+Enter)") }

    if (recentPresets.isNotEmpty()) {
        Text("Recent inputs")
        recentPresets.forEachIndexed { idx, preset ->
            TextButton(onClick = {
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
    onCopyTemplate: () -> Unit,
) {
    Text("Templates")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        addTemplates.forEach { preset ->
            TextButton(onClick = {
                val updatedTags = if (simpleMode) draft.tags else preset.tags
                onDraftChange(draft.copy(front = preset.front, back = preset.back, tags = updatedTags))
            }) { Text(preset.name) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCopyTemplate) { Text("Copy template") }
        Button(onClick = {
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
    Text("Bulk add with JSON Lines")
    OutlinedTextField(
        value = bulkText,
        onValueChange = onBulkTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste JSON Lines") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPreview) { Text("Preview") }
        Button(onClick = onImport) { Text("Import") }
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
fun StudyScreen(deckId: Long, repo: IngrainRepository, settingsStore: SchedulerSettingsStore) {
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<CardEntity?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var remainingToday by remember { mutableStateOf(0) }

    suspend fun loadDueCard() {
        val now = System.currentTimeMillis()
        card = repo.nextDueCard(deckId, now)
        remainingToday = repo.countDueUntil(deckId, endOfDayMillis(now))
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HorizontalDivider(
                thickness = 3.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "PROGRESS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$progressText cards",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (currentCard == null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = currentCard.primaryTag(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = currentCard.front,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                    Spacer(modifier = Modifier.height(26.dp))

                    if (showAnswer) {
                        Text(
                            text = currentCard.back,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "TEXT NOTE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "画像学習は未対応。現在はテキスト回答のみで学習できます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val nextReview = java.time.Instant.ofEpochMilli(currentCard.dueAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                Text(
                                    text = "次回予定: $nextReview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    } else {
                        Button(onClick = { showAnswer = true }) { Text("Show answer") }
                    }
                }
            }

            if (currentCard != null && showAnswer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { scope.launch { submitReview(currentCard, RatingAgain) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("Again") }
                    Button(
                        onClick = { scope.launch { submitReview(currentCard, RatingGood) } },
                        modifier = Modifier.weight(2f),
                    ) { Text("Good") }
                }
                TextButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text("Edit Card", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SchedulerSettingsStore) {
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
    var message by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { p ->
        Column(
            Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard {
                Text("Scheduler tuning", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(initial, { initial = it }, label = { Text("Initial interval (days)") })
                OutlinedTextField(second, { second = it }, label = { Text("Second interval (days)") })
                OutlinedTextField(minEase, { minEase = it }, label = { Text("Minimum ease factor") })
                OutlinedTextField(stepGood, { stepGood = it }, label = { Text("Ease step (Good)") })
                OutlinedTextField(stepAgain, { stepAgain = it }, label = { Text("Ease step (Again)") })
                OutlinedTextField(againDelay, { againDelay = it }, label = { Text("Again delay (minutes)") })
                Button(onClick = {
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
                        val content = repo.exportDeckJsonLines(deckId)
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
                        val summary = repo.importParsed(JsonLinesParser.parse(text), System.currentTimeMillis())
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
                Text("Export your deck or import from a JSONL backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    pendingOperation = FileOperation.EXPORT
                    createLauncher.launch("ingrain-backup.jsonl")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export deck")
                }
                Button(onClick = {
                    pendingOperation = FileOperation.IMPORT
                    openLauncher.launch(arrayOf("*/*"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import file")
                }
                if (message.isNotBlank()) Text(message)
            }
        }
    }
}
