package com.ingrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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

data class AddPreset(val name: String, val front: String, val back: String, val tags: String)

private val addTemplates = listOf(
    AddPreset("基本", "質問", "回答", ""),
    AddPreset("例文", "単語", "意味 + 例文", "vocab"),
    AddPreset("穴埋め", "{{c1::重要語}} を含む文", "補足説明", "cloze"),
)

private data class ManualAddDraft(
    val deckName: String,
    val front: String,
    val back: String,
    val tags: String,
)

private fun parseTags(raw: String): List<String> = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

private fun jsonTemplate(deckName: String, front: String, back: String, tags: String): String {
    val tagList = parseTags(tags).joinToString(",") { "\"$it\"" }
    return """{"deck":"$deckName","front":"$front","back":"$back","tags":[$tagList]}"""
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

    Scaffold(topBar = { TopAppBar(title = { Text("Decks") }) }) { p ->
        Column(
            Modifier
                .padding(p)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Button(onClick = onGlobalAdd, modifier = Modifier.fillMaxWidth()) {
                Text("Add card (all decks)")
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New deck name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                scope.launch {
                    error = repo.createDeck(name).exceptionOrNull()?.message
                    if (error == null) name = ""
                }
            }) {
                Text("Create")
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            decks.forEach { deck ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(deck.name)
                        Row {
                            TextButton(onClick = { onOpenDeck(deck.id) }) { Text("Open") }
                            TextButton(onClick = { scope.launch { repo.deleteDeck(deck.id) } }) { Text("Delete") }
                        }
                    }
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
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rename,
                onValueChange = { rename = it },
                label = { Text("Deck name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val current = deck ?: return@Button
                scope.launch {
                    message = repo.renameDeck(current, rename).exceptionOrNull()?.message ?: "Renamed"
                }
            }) { Text("Rename") }
            Button(onClick = onStudy) { Text("Study") }
            Button(onClick = onImport) { Text("Add & Import") }
            Button(onClick = onSettings) { Text("Settings") }
            Button(onClick = onBackup) { Text("Export / Import file") }
            if (message != null) Text(message!!)
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
            front = ""
            back = ""
            if (!continuousMode) tags = ""
            if (!continuousMode || !keepDeckInContinuous) deckName = ""
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
            duplicateHint = "同一カードが既に存在します（保存はスキップされます）"
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
                    message = "テンプレートをクリップボードへコピーしました"
                },
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
    onSave: () -> Unit,
) {
    Text(if (fixedDeck != null) "追加先: ${fixedDeck.name}" else "全デッキ追加モード")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = simpleMode, onClick = { onSimpleModeChange(true) }, label = { Text("かんたん") })
        FilterChip(selected = !simpleMode, onClick = { onSimpleModeChange(false) }, label = { Text("詳細") })
    }

    if (fixedDeck == null) {
        OutlinedTextField(
            value = draft.deckName,
            onValueChange = { onDraftChange(draft.copy(deckName = it)) },
            label = { Text("Deck name (required)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            decks.take(3).forEach {
                TextButton(onClick = { onDraftChange(draft.copy(deckName = it.name)) }) { Text(it.name) }
            }
        }
    }

    OutlinedTextField(
        value = draft.front,
        onValueChange = { onDraftChange(draft.copy(front = it)) },
        label = { Text("Front") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    OutlinedTextField(
        value = draft.back,
        onValueChange = { onDraftChange(draft.copy(back = it)) },
        label = { Text("Back") },
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
        Text("連続追加")
        Switch(checked = continuousMode, onCheckedChange = onContinuousModeChange)
        if (fixedDeck == null) {
            Text("デッキ保持")
            Switch(checked = keepDeckInContinuous, onCheckedChange = onKeepDeckInContinuousChange)
        }
    }

    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存 (Ctrl+Enter)") }

    if (recentPresets.isNotEmpty()) {
        Text("最近使った入力")
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
    Text("テンプレート")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        addTemplates.forEach { preset ->
            TextButton(onClick = {
                val updatedTags = if (simpleMode) draft.tags else preset.tags
                onDraftChange(draft.copy(front = preset.front, back = preset.back, tags = updatedTags))
            }) { Text(preset.name) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCopyTemplate) { Text("テンプレをコピー") }
        Button(onClick = {
            val default = addTemplates.first()
            val updatedTags = if (simpleMode) draft.tags else default.tags
            onDraftChange(draft.copy(front = default.front, back = default.back, tags = updatedTags))
        }) {
            Text("フォームへ挿入")
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
    Text("JSON Lines 一括追加")
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

    suspend fun loadDueCard() {
        card = repo.nextDueCard(deckId, System.currentTimeMillis())
        showAnswer = false
        if (card == null) message = "No due cards"
    }

    LaunchedEffect(deckId) { loadDueCard() }

    Scaffold(topBar = { TopAppBar(title = { Text("Study") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentCard = card
            if (currentCard == null) {
                Text(message)
            } else {
                Text("Front: ${currentCard.front}")
                if (showAnswer) Text("Back: ${currentCard.back}")
                Button(onClick = { showAnswer = true }) { Text("Show answer") }
                if (showAnswer) {
                    Row {
                        Button(onClick = {
                            scope.launch {
                                val settings = settingsStore.settings.first()
                                repo.review(currentCard, "AGAIN", settings, System.currentTimeMillis())
                                loadDueCard()
                            }
                        }) { Text("Again") }
                        Button(
                            onClick = {
                                scope.launch {
                                    val settings = settingsStore.settings.first()
                                    repo.review(currentCard, "GOOD", settings, System.currentTimeMillis())
                                    loadDueCard()
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Good") }
                    }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(initial, { initial = it }, label = { Text("initial_interval_good_days") })
            OutlinedTextField(second, { second = it }, label = { Text("second_interval_good_days") })
            OutlinedTextField(minEase, { minEase = it }, label = { Text("min_ease_factor") })
            OutlinedTextField(stepGood, { stepGood = it }, label = { Text("ease_factor_step_good") })
            OutlinedTextField(stepAgain, { stepAgain = it }, label = { Text("ease_factor_step_again") })
            OutlinedTextField(againDelay, { againDelay = it }, label = { Text("again_delay_minutes") })
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
                        "Saved"
                    }
                }
            }) { Text("Save") }
            if (message.isNotBlank()) Text(message)
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
                        message = "export done"
                    }

                    FileOperation.IMPORT -> {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()
                        val summary = repo.importParsed(JsonLinesParser.parse(text), System.currentTimeMillis())
                        message = "import added=${summary.added} skipped=${summary.skipped} failed=${summary.failed}"
                    }
                }
            }.onFailure { error ->
                message = "file op failed: ${error.message}"
            }
            pendingUri = null
            pendingOperation = null
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Backup / Restore") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                pendingOperation = FileOperation.EXPORT
                createLauncher.launch("ingrain-backup.jsonl")
            }) {
                Text("Export deck")
            }
            Button(onClick = {
                pendingOperation = FileOperation.IMPORT
                openLauncher.launch(arrayOf("*/*"))
            }) {
                Text("Import file")
            }
            Text(message)
        }
    }
}
