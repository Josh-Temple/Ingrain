package com.ingrain.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(repo: IngrainRepository, onOpenDeck: (Long) -> Unit) {
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
fun ImportScreen(deckId: Long, repo: IngrainRepository) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<ParseResult>>(emptyList()) }
    var summary by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Add & Import") }) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    preview = JsonLinesParser.parse(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste JSON Lines") },
            )
            Row {
                Button(onClick = { preview = JsonLinesParser.parse(text) }) { Text("Preview") }
                Button(
                    onClick = {
                        scope.launch {
                            val result = repo.importParsed(JsonLinesParser.parse(text), System.currentTimeMillis())
                            summary = "deck=$deckId, added=${result.added}, skipped=${result.skipped}, failed=${result.failed}"
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Import") }
            }
            if (summary.isNotBlank()) Text(summary)
            preview.forEach { result ->
                when (result) {
                    is ParseResult.Success -> Text("✅ line ${result.lineNumber}: ${result.card.deck} / ${result.card.front}")
                    is ParseResult.Error -> Text(
                        "❌ line ${result.lineNumber}: ${result.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
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
