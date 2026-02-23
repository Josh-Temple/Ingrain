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
import com.ingrain.scheduler.Scheduler

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

fun buildPassageHint(back: String, hintStage: Int): String {
    return when (hintStage) {
        PASSAGE_HINT_STAGE_1 -> firstWordsHint(back)
        PASSAGE_HINT_STAGE_2 -> keywordsHint(back)
        PASSAGE_HINT_STAGE_3 -> clozeHint(back)
        else -> ""
    }
}

private fun firstWordsHint(text: String): String {
    return text.lineSequence()
        .map { line ->
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) "" else words.take(3).joinToString(" ") + "…"
        }
        .joinToString("\n")
        .trim()
}

private fun keywordsHint(text: String): String {
    val keywords = text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 6 }
        .distinct()
        .take(16)
    return if (keywords.isEmpty()) "No strong keywords detected. Try the next hint stage."
    else keywords.joinToString(separator = " • ")
}

private fun clozeHint(text: String): String {
    return text.split(Regex("(\\s+)"))
        .joinToString(separator = "") { token ->
            if (token.isBlank()) {
                token
            } else {
                val keep = token.length <= 4 || !token.any { it.isLetterOrDigit() }
                if (keep) token else token.take(2) + "_".repeat((token.length - 2).coerceAtMost(6))
            }
        }
}

@Composable
fun PassageGradeActions(
    hintUsed: Boolean,
    onGradeSelected: (PassageGrade) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onGradeSelected(PassageGrade.Again) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(PassageGrade.Again.label) }
            if (hintUsed) {
                Button(
                    onClick = { onGradeSelected(PassageGrade.Hinted) },
                    modifier = Modifier.weight(1f),
                ) { Text(PassageGrade.Hinted.label) }
            } else {
                Button(
                    onClick = { onGradeSelected(PassageGrade.MinorErrors) },
                    modifier = Modifier.weight(1f),
                ) { Text(PassageGrade.MinorErrors.label) }
                Button(
                    onClick = { onGradeSelected(PassageGrade.Exact) },
                    modifier = Modifier.weight(1f),
                ) { Text(PassageGrade.Exact.label) }
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
