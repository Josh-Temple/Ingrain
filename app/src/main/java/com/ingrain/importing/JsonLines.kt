package com.ingrain.importing

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class JsonLineCard(
    val deck: String,
    val front: String,
    val back: String,
    val tags: List<String>? = null,
    val study_mode: String? = null,
    val strictness: String? = null,
    val hint_policy: String? = null,
    val cloze1: String? = null,
    val cloze2: String? = null,
    val cloze3: String? = null,
    val due_at: Long? = null,
    val interval_days: Double? = null,
    val ease_factor: Double? = null,
    val repetitions: Int? = null,
    val lapses: Int? = null,
)

sealed class ParseResult {
    data class Success(val lineNumber: Int, val card: JsonLineCard) : ParseResult()
    data class Error(val lineNumber: Int, val message: String, val rawLine: String) : ParseResult()
}

object JsonLinesParser {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_INPUT_CHARS = 1_000_000

    fun parse(input: String): List<ParseResult> {
        if (input.isBlank()) return listOf(ParseResult.Error(1, "Input is empty", ""))
        if (input.length > MAX_INPUT_CHARS) {
            return listOf(ParseResult.Error(1, "Input too large (max $MAX_INPUT_CHARS chars)", ""))
        }
        return input.lineSequence().mapIndexed { idx, line ->
            val n = idx + 1
            if (line.isBlank()) {
                ParseResult.Error(n, "Empty line", line)
            } else {
                try {
                    val parsed = json.decodeFromString<JsonLineCard>(line)
                    when {
                        parsed.deck.isBlank() -> ParseResult.Error(n, "deck is required", line)
                        parsed.front.isBlank() -> ParseResult.Error(n, "front is required", line)
                        parsed.back.isBlank() -> ParseResult.Error(n, "back is required", line)
                        else -> ParseResult.Success(n, parsed)
                    }
                } catch (e: SerializationException) {
                    ParseResult.Error(n, "Invalid JSON: ${e.message ?: "parse error"}", line)
                } catch (e: Exception) {
                    ParseResult.Error(n, "Unexpected parse error: ${e.message}", line)
                }
            }
        }.toList()
    }
}
