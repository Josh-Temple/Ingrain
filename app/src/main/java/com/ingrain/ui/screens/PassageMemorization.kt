package com.ingrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ingrain.data.PROMPT_TYPE_CLOZE_RECALL
import com.ingrain.data.PROMPT_TYPE_FIRST_WORD_CUE
import com.ingrain.data.PROMPT_TYPE_FREE_RECALL
import com.ingrain.scheduler.Scheduler
import kotlin.math.max

const val PASSAGE_HINT_STAGE_NONE = 0
const val PASSAGE_HINT_STAGE_1 = 1
const val PASSAGE_HINT_STAGE_2 = 2
const val PASSAGE_HINT_STAGE_3 = 3

enum class PassageGrade(val label: String) {
    Again("Again"),
    MinorErrors("Minor errors"),
    Exact("Exact"),
    Hinted("Hinted"),
}

enum class PassagePromptType(val storageValue: String, val label: String) {
    FreeRecall(PROMPT_TYPE_FREE_RECALL, "Free Recall"),
    FirstWordCue(PROMPT_TYPE_FIRST_WORD_CUE, "First-word Cue"),
    ClozeRecall(PROMPT_TYPE_CLOZE_RECALL, "Cloze Recall"),
}

data class ClozePromptConfig(
    val revealPrefixChars: Int = 1,
    val maskChar: Char = '_',
)


data class PassageClozeVariants(
    val cloze1: String? = null,
    val cloze2: String? = null,
    val cloze3: String? = null,
) {
    fun availableIndexes(): List<Int> = listOfNotNull(
        1.takeIf { !cloze1.isNullOrBlank() },
        2.takeIf { !cloze2.isNullOrBlank() },
        3.takeIf { !cloze3.isNullOrBlank() },
    )

    fun hasAny(): Boolean = availableIndexes().isNotEmpty()

    fun byIndex(index: Int): String? {
        val raw = when (index) {
            1 -> cloze1
            2 -> cloze2
            3 -> cloze3
            else -> null
        }
        return raw?.takeIf { it.isNotBlank() }
    }
}

fun defaultClozeVariantIndex(variants: PassageClozeVariants): Int? {
    return variants.availableIndexes().firstOrNull()
}

fun nextClozeVariantIndex(current: Int?, variants: PassageClozeVariants): Int? {
    val available = variants.availableIndexes()
    if (available.isEmpty()) return null
    if (current == null) return available.first()
    val currentPos = available.indexOf(current)
    if (currentPos == -1) return available.first()
    return available[(currentPos + 1) % available.size]
}

fun resolveClozePrompt(back: String, variants: PassageClozeVariants, selectedIndex: Int?): Pair<String, Int?> {
    val available = variants.availableIndexes()
    if (available.isEmpty()) return buildClozePrompt(back) to null
    val preferred = selectedIndex?.takeIf { it in available } ?: available.first()
    return (variants.byIndex(preferred) ?: buildClozePrompt(back)) to preferred
}

fun splitIntoSentencesOrLines(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return emptyList()

    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size > 1) return lines

    return trimmed.split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

fun buildFirstWordCue(text: String): String {
    val units = splitIntoSentencesOrLines(text)
    if (units.isEmpty()) return ""
    return units.joinToString("\n") { unit ->
        unit.split(Regex("\\s+"))
            .firstOrNull { it.isNotBlank() }
            ?.let { "$it…" }
            ?: "…"
    }
}

fun buildKeywordCue(text: String): String {
    val tokens = text.lowercase().split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 5 }
    if (tokens.isEmpty()) return "No strong keywords yet. Try H3."

    val ranked = tokens.groupingBy { it }.eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenByDescending { it.first.length }.thenBy { it.first })
        .take(14)
        .map { it.first }
    return ranked.joinToString(separator = " • ")
}

fun buildClozePrompt(text: String, config: ClozePromptConfig = ClozePromptConfig()): String {
    return text.split(Regex("(\\s+)"))
        .joinToString(separator = "") { token ->
            if (token.isBlank()) return@joinToString token
            val lettersOnly = token.filter { it.isLetterOrDigit() }
            if (lettersOnly.length < 4) return@joinToString token

            val prefixLength = max(1, config.revealPrefixChars)
            val visible = token.take(prefixLength)
            val hiddenCount = (token.length - prefixLength).coerceAtLeast(1)
            visible + config.maskChar.toString().repeat(hiddenCount.coerceAtMost(8))
        }
}

fun buildPassagePrompt(back: String, promptType: PassagePromptType, clozeVariants: PassageClozeVariants = PassageClozeVariants(), selectedClozeVariantIndex: Int? = null): String {
    return when (promptType) {
        PassagePromptType.FreeRecall -> "Recall the full passage from memory."
        PassagePromptType.FirstWordCue -> buildFirstWordCue(back)
        PassagePromptType.ClozeRecall -> resolveClozePrompt(
            back = back,
            variants = clozeVariants,
            selectedIndex = selectedClozeVariantIndex,
        ).first
    }
}

fun buildPassageHint(back: String, hintStage: Int): String {
    return when (hintStage) {
        PASSAGE_HINT_STAGE_1 -> buildFirstWordCue(back)
        PASSAGE_HINT_STAGE_2 -> buildKeywordCue(back)
        PASSAGE_HINT_STAGE_3 -> buildClozePrompt(back, ClozePromptConfig(revealPrefixChars = 2))
        else -> ""
    }
}


fun allowedPassageGrades(hintUsed: Boolean): List<PassageGrade> {
    return if (hintUsed) {
        listOf(PassageGrade.Again, PassageGrade.Hinted)
    } else {
        listOf(PassageGrade.Again, PassageGrade.MinorErrors, PassageGrade.Exact)
    }
}

@Composable
fun PassageGradeActions(
    hintUsed: Boolean,
    onGradeSelected: (PassageGrade) -> Unit,
) {
    val availableGrades = allowedPassageGrades(hintUsed)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            availableGrades.forEach { grade ->
                Button(
                    onClick = { onGradeSelected(grade) },
                    modifier = Modifier.weight(1f),
                    colors = if (grade == PassageGrade.Again) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(grade.label) }
            }
        }
    }
}

fun mapPassageGradeToSchedulerValue(grade: PassageGrade): String {
    return when (grade) {
        PassageGrade.Again -> Scheduler.PASSAGE_GRADE_AGAIN
        PassageGrade.Exact -> Scheduler.PASSAGE_GRADE_EXACT
        PassageGrade.MinorErrors -> Scheduler.PASSAGE_GRADE_MINOR_ERRORS
        PassageGrade.Hinted -> Scheduler.PASSAGE_GRADE_HINTED
    }
}
