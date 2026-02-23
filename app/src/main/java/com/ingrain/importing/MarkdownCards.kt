package com.ingrain.importing

private data class CardBlock(
    val startLine: Int,
    val lines: List<String>,
)

private data class FrontMatter(
    val deck: String,
    val tags: List<String>,
    val studyMode: String,
    val strictness: String,
    val hintPolicy: String,
)

object MarkdownCardsParser {
    private const val CARD_SEPARATOR = "==="

    fun parse(input: String): List<ParseResult> {
        if (input.isBlank()) return listOf(ParseResult.Error(1, "Input is empty", ""))

        val sanitized = input.removePrefix("\uFEFF")
        val blocks = splitBlocks(sanitized)
        if (blocks.isEmpty()) return listOf(ParseResult.Error(1, "Input is empty", ""))
        return blocks.map { parseBlock(it) }
    }

    private fun splitBlocks(input: String): List<CardBlock> {
        val lines = input.lines()
        val blocks = mutableListOf<CardBlock>()
        var startLine = 1
        var current = mutableListOf<String>()

        fun flush() {
            if (current.any { it.isNotBlank() }) {
                blocks += CardBlock(startLine = startLine, lines = current.toList())
            }
            current = mutableListOf()
        }

        lines.forEachIndexed { index, line ->
            if (line.trim() == CARD_SEPARATOR) {
                flush()
                startLine = index + 2
            } else {
                current += line
            }
        }
        flush()
        return blocks
    }

    private fun parseBlock(block: CardBlock): ParseResult {
        val lines = block.lines
        val firstContentIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstContentIndex == -1) {
            return ParseResult.Error(block.startLine, "Card is empty", lines.joinToString("\n"))
        }

        if (lines[firstContentIndex].trim() != "---") {
            return ParseResult.Error(
                block.startLine + firstContentIndex,
                "YAML front matter must start with ---",
                lines.joinToString("\n"),
            )
        }

        val frontMatterEnd = (firstContentIndex + 1 until lines.size)
            .firstOrNull { lines[it].trim() == "---" }
            ?: return ParseResult.Error(
                block.startLine + firstContentIndex,
                "YAML front matter closing --- is missing",
                lines.joinToString("\n"),
            )

        val metadata = parseFrontMatter(lines.subList(firstContentIndex + 1, frontMatterEnd))
            ?: return ParseResult.Error(block.startLine, "Invalid YAML front matter", lines.joinToString("\n"))

        val bodyLines = lines.subList(frontMatterEnd + 1, lines.size)
        val front = extractSection("Front", bodyLines)
            ?: return ParseResult.Error(block.startLine, "## Front or ### Front section is required", lines.joinToString("\n"))
        val back = extractSection("Back", bodyLines)
            ?: return ParseResult.Error(block.startLine, "## Back or ### Back section is required", lines.joinToString("\n"))

        if (front.isBlank()) return ParseResult.Error(block.startLine, "Front content is empty", lines.joinToString("\n"))
        if (back.isBlank()) return ParseResult.Error(block.startLine, "Back content is empty", lines.joinToString("\n"))

        return ParseResult.Success(
            lineNumber = block.startLine,
            card = JsonLineCard(
                deck = metadata.deck,
                front = front,
                back = back,
                tags = metadata.tags,
                study_mode = metadata.studyMode.lowercase(),
                strictness = metadata.strictness.lowercase(),
                hint_policy = metadata.hintPolicy.lowercase(),
            ),
        )
    }

    private fun parseFrontMatter(lines: List<String>): FrontMatter? {
        var deck: String? = null
        var tagsSeen = false
        val tags = mutableListOf<String>()
        var readingTagsList = false
        var studyMode = "BASIC"
        var strictness = "EXACT"
        var hintPolicy = "ENABLED"

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach

            if (readingTagsList && line.startsWith("- ")) {
                val tag = line.removePrefix("- ").trim().trim('"', '\'')
                if (tag.isNotBlank()) tags += tag
                return@forEach
            }
            readingTagsList = false

            when {
                line.startsWith("deck:") -> {
                    deck = line.removePrefix("deck:").trim().trim('"', '\'')
                }

                line.startsWith("tags:") -> {
                    tagsSeen = true
                    val value = line.removePrefix("tags:").trim()
                    when {
                        value.isBlank() -> readingTagsList = true
                        value.startsWith("[") && value.endsWith("]") -> {
                            value.removePrefix("[").removeSuffix("]")
                                .split(',')
                                .map { it.trim().trim('"', '\'') }
                                .filter { it.isNotBlank() }
                                .forEach { tags += it }
                        }
                        else -> return null
                    }
                }

                line.startsWith("study_mode:") -> {
                    val parsed = line.removePrefix("study_mode:").trim().trim('"', '\'').lowercase()
                    studyMode = when (parsed) {
                        "basic" -> "BASIC"
                        "passage_memorization" -> "PASSAGE_MEMORIZATION"
                        else -> return null
                    }
                }

                line.startsWith("strictness:") -> {
                    val parsed = line.removePrefix("strictness:").trim().trim('"', '\'').lowercase()
                    strictness = when (parsed) {
                        "exact" -> "EXACT"
                        "near_exact" -> "NEAR_EXACT"
                        "meaning_only" -> "MEANING_ONLY"
                        else -> return null
                    }
                }

                line.startsWith("hint_policy:") -> {
                    val parsed = line.removePrefix("hint_policy:").trim().trim('"', '\'').lowercase()
                    hintPolicy = when (parsed) {
                        "enabled" -> "ENABLED"
                        "disabled" -> "DISABLED"
                        else -> return null
                    }
                }

                else -> {
                    if (!line.contains(':')) return null
                }
            }
        }

        val deckName = deck?.trim().orEmpty()
        if (deckName.isBlank() || !tagsSeen) return null
        return FrontMatter(
            deck = deckName,
            tags = tags,
            studyMode = studyMode,
            strictness = strictness,
            hintPolicy = hintPolicy,
        )
    }

    private fun extractSection(name: String, body: List<String>): String? {
        val acceptedHeaders = setOf("## $name", "### $name")
        val startIndex = body.indexOfFirst { acceptedHeaders.contains(it.trim()) }
        if (startIndex == -1) return null

        val content = mutableListOf<String>()
        for (i in startIndex + 1 until body.size) {
            val trimmed = body[i].trim()
            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) break
            content += body[i]
        }
        return content.joinToString("\n").trim()
    }
}

object BulkImportParser {
    fun parse(input: String): List<ParseResult> {
        val normalized = input.trimStart()
        if (normalized.startsWith("{")) {
            return JsonLinesParser.parse(input)
        }
        return if (normalized.startsWith("---") || normalized.contains("\n===\n")) {
            MarkdownCardsParser.parse(input)
        } else {
            JsonLinesParser.parse(input)
        }
    }
}
