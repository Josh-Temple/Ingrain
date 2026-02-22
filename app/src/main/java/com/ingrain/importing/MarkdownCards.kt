package com.ingrain.importing

private data class CardBlock(
    val startLine: Int,
    val lines: List<String>,
)

private data class FrontMatter(
    val deck: String,
    val tags: List<String>,
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
            ?: return ParseResult.Error(block.startLine, "### Front section is required", lines.joinToString("\n"))
        val back = extractSection("Back", bodyLines)
            ?: return ParseResult.Error(block.startLine, "### Back section is required", lines.joinToString("\n"))

        if (front.isBlank()) return ParseResult.Error(block.startLine, "Front content is empty", lines.joinToString("\n"))
        if (back.isBlank()) return ParseResult.Error(block.startLine, "Back content is empty", lines.joinToString("\n"))

        return ParseResult.Success(
            lineNumber = block.startLine,
            card = JsonLineCard(deck = metadata.deck, front = front, back = back, tags = metadata.tags),
        )
    }

    private fun parseFrontMatter(lines: List<String>): FrontMatter? {
        var deck: String? = null
        var tagsSeen = false
        val tags = mutableListOf<String>()
        var readingTagsList = false

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

                else -> {
                    if (!line.contains(':')) return null
                }
            }
        }

        val deckName = deck?.trim().orEmpty()
        if (deckName.isBlank() || !tagsSeen) return null
        return FrontMatter(deck = deckName, tags = tags)
    }

    private fun extractSection(name: String, body: List<String>): String? {
        val header = "### $name"
        val startIndex = body.indexOfFirst { it.trim() == header }
        if (startIndex == -1) return null

        val content = mutableListOf<String>()
        for (i in startIndex + 1 until body.size) {
            val line = body[i]
            if (line.trim().startsWith("### ")) break
            content += line
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
