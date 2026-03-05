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
    val cloze1: String?,
    val cloze2: String?,
    val cloze3: String?,
    val conceptDomain: String?,
    val conceptOneLiner: String?,
    val conceptProposer: String?,
    val conceptYear: Int?,
    val canonicalExample: String?,
    val counterExample: String?,
    val commonMisuse: String?,
    val contrastPoints: String?,
    val evidenceLevel: String?,
    val sources: String?,
    val confusionCluster: String?,
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
                cloze1 = metadata.cloze1,
                cloze2 = metadata.cloze2,
                cloze3 = metadata.cloze3,
                concept_domain = metadata.conceptDomain,
                concept_one_liner = metadata.conceptOneLiner,
                concept_proposer = metadata.conceptProposer,
                concept_year = metadata.conceptYear,
                canonical_example = metadata.canonicalExample,
                counter_example = metadata.counterExample,
                common_misuse = metadata.commonMisuse,
                contrast_points = metadata.contrastPoints,
                evidence_level = metadata.evidenceLevel,
                sources = metadata.sources,
                confusion_cluster = metadata.confusionCluster,
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
        var cloze1: String? = null
        var cloze2: String? = null
        var cloze3: String? = null
        var conceptDomain: String? = null
        var conceptOneLiner: String? = null
        var conceptProposer: String? = null
        var conceptYear: Int? = null
        var canonicalExample: String? = null
        var counterExample: String? = null
        var commonMisuse: String? = null
        var contrastPoints: String? = null
        var evidenceLevel: String? = null
        var sources: String? = null
        var confusionCluster: String? = null

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

                line.startsWith("cloze1:") -> {
                    cloze1 = line.removePrefix("cloze1:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("cloze2:") -> {
                    cloze2 = line.removePrefix("cloze2:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("cloze3:") -> {
                    cloze3 = line.removePrefix("cloze3:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("concept_domain:") -> {
                    conceptDomain = line.removePrefix("concept_domain:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("concept_one_liner:") -> {
                    conceptOneLiner = line.removePrefix("concept_one_liner:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("concept_proposer:") -> {
                    conceptProposer = line.removePrefix("concept_proposer:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("concept_year:") -> {
                    val parsed = line.removePrefix("concept_year:").trim().trim('"', '\'')
                    conceptYear = if (parsed.isBlank()) null else parsed.toIntOrNull() ?: return null
                }

                line.startsWith("canonical_example:") -> {
                    canonicalExample = line.removePrefix("canonical_example:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("counter_example:") -> {
                    counterExample = line.removePrefix("counter_example:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("common_misuse:") -> {
                    commonMisuse = line.removePrefix("common_misuse:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("contrast_points:") -> {
                    contrastPoints = line.removePrefix("contrast_points:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("evidence_level:") -> {
                    evidenceLevel = line.removePrefix("evidence_level:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("sources:") -> {
                    sources = line.removePrefix("sources:").trim().trim('"', '\'').ifBlank { null }
                }

                line.startsWith("confusion_cluster:") -> {
                    confusionCluster = line.removePrefix("confusion_cluster:").trim().trim('"', '\'').ifBlank { null }
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
            cloze1 = cloze1,
            cloze2 = cloze2,
            cloze3 = cloze3,
            conceptDomain = conceptDomain,
            conceptOneLiner = conceptOneLiner,
            conceptProposer = conceptProposer,
            conceptYear = conceptYear,
            canonicalExample = canonicalExample,
            counterExample = counterExample,
            commonMisuse = commonMisuse,
            contrastPoints = contrastPoints,
            evidenceLevel = evidenceLevel,
            sources = sources,
            confusionCluster = confusionCluster,
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
