package com.ingrain.data

import com.ingrain.importing.ParseResult
import com.ingrain.scheduler.Scheduler
import com.ingrain.scheduler.SchedulerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ImportSummary(val added: Int, val skipped: Int, val failed: Int)

data class DueCardView(val deck: DeckEntity, val card: CardEntity?)

data class StudyAttempt(
    val studyMode: String,
    val promptType: String = PROMPT_TYPE_FREE_RECALL,
    val hintLevelUsed: Int = 0,
    val revealUsed: Boolean = false,
    val selfGrade: String,
    val durationMs: Long? = null,
    val errorTypes: String? = null,
)


data class ConceptMetadataInput(
    val conceptDomain: String? = null,
    val conceptOneLiner: String? = null,
    val conceptProposer: String? = null,
    val conceptYear: Int? = null,
    val canonicalExample: String? = null,
    val counterExample: String? = null,
    val commonMisuse: String? = null,
    val contrastPoints: String? = null,
    val evidenceLevel: String? = null,
    val sources: String? = null,
    val confusionCluster: String? = null,
)

class IngrainRepository(private val db: AppDatabase) {
    private val json = Json

    private fun normalizeStudyMode(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "passage_memorization" -> STUDY_MODE_PASSAGE_MEMORIZATION
            else -> STUDY_MODE_BASIC
        }
    }

    private fun normalizeStrictness(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "near_exact" -> STRICTNESS_NEAR_EXACT
            "meaning_only" -> STRICTNESS_MEANING_ONLY
            else -> STRICTNESS_EXACT
        }
    }

    private fun normalizeHintPolicy(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "disabled" -> HINT_POLICY_DISABLED
            else -> HINT_POLICY_ENABLED
        }
    }


    private fun normalizeOptionalText(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotBlank() }
    }


    private fun normalizeConceptMetadata(input: ConceptMetadataInput): ConceptMetadataInput {
        return input.copy(
            conceptDomain = normalizeOptionalText(input.conceptDomain),
            conceptOneLiner = normalizeOptionalText(input.conceptOneLiner),
            conceptProposer = normalizeOptionalText(input.conceptProposer),
            canonicalExample = normalizeOptionalText(input.canonicalExample),
            counterExample = normalizeOptionalText(input.counterExample),
            commonMisuse = normalizeOptionalText(input.commonMisuse),
            contrastPoints = normalizeOptionalText(input.contrastPoints),
            evidenceLevel = normalizeOptionalText(input.evidenceLevel),
            sources = normalizeOptionalText(input.sources),
            confusionCluster = normalizeOptionalText(input.confusionCluster),
        )
    }

    fun observeDecks(): Flow<List<DeckEntity>> = db.deckDao().observeAll()

    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>> = db.cardDao().observeByDeck(deckId)

    suspend fun createDeck(name: String): Result<Unit> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Deck name cannot be blank" }
        val now = System.currentTimeMillis()
        db.deckDao().insert(DeckEntity(name = trimmed, createdAt = now, updatedAt = now))
    }

    suspend fun renameDeck(deck: DeckEntity, newName: String): Result<Unit> = runCatching {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Deck name cannot be blank" }
        db.deckDao().update(deck.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDeck(id: Long) = db.deckDao().delete(id)

    suspend fun deleteCard(id: Long) = db.cardDao().delete(id)

    suspend fun getDeck(id: Long): DeckEntity? = db.deckDao().getById(id)

    suspend fun getCard(id: Long): CardEntity? = db.cardDao().getById(id)

    suspend fun updateCardContent(
        card: CardEntity,
        front: String,
        back: String,
        tags: List<String>,
        conceptMetadata: ConceptMetadataInput = ConceptMetadataInput(),
    ): Result<Unit> = runCatching {
        val trimmedFront = front.trim()
        val trimmedBack = back.trim()
        require(trimmedFront.isNotBlank()) { "Front is required" }
        require(trimmedBack.isNotBlank()) { "Back is required" }
        val normalizedTags = tags.map { it.trim() }.filter { it.isNotBlank() }
        val normalizedConcept = normalizeConceptMetadata(conceptMetadata)
        db.cardDao().update(
            card.copy(
                front = trimmedFront,
                back = trimmedBack,
                tagsJson = json.encodeToString(normalizedTags),
                conceptDomain = normalizedConcept.conceptDomain,
                conceptOneLiner = normalizedConcept.conceptOneLiner,
                conceptProposer = normalizedConcept.conceptProposer,
                conceptYear = normalizedConcept.conceptYear,
                canonicalExample = normalizedConcept.canonicalExample,
                counterExample = normalizedConcept.counterExample,
                commonMisuse = normalizedConcept.commonMisuse,
                contrastPoints = normalizedConcept.contrastPoints,
                evidenceLevel = normalizedConcept.evidenceLevel,
                sources = normalizedConcept.sources,
                confusionCluster = normalizedConcept.confusionCluster,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateDeckStudyOptions(deck: DeckEntity, dailyReviewLimit: Int, dailyNewCardLimit: Int): Result<Unit> = runCatching {
        require(dailyReviewLimit > 0) { "Daily review limit must be at least 1" }
        require(dailyNewCardLimit >= 0) { "Daily new card limit must be 0 or more" }
        db.deckDao().update(
            deck.copy(
                dailyReviewLimit = dailyReviewLimit,
                dailyNewCardLimit = dailyNewCardLimit,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }


    suspend fun findDeckByName(name: String): DeckEntity? = db.deckDao().getByName(name.trim())

    suspend fun getOrCreateDeck(name: String, now: Long): DeckEntity {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Deck name cannot be blank" }
        return db.deckDao().getByName(trimmed) ?: run {
            val id = db.deckDao().insert(DeckEntity(name = trimmed, createdAt = now, updatedAt = now))
            db.deckDao().getById(id)!!
        }
    }

    suspend fun cardExists(deckId: Long, front: String, back: String): Boolean {
        return db.cardDao().existsDuplicate(deckId, front, back)
    }

    suspend fun addCard(
        deckName: String,
        front: String,
        back: String,
        tags: List<String>,
        now: Long,
    ): Result<Boolean> = runCatching {
        val trimmedFront = front.trim()
        val trimmedBack = back.trim()
        require(trimmedFront.isNotBlank()) { "Front is required" }
        require(trimmedBack.isNotBlank()) { "Back is required" }
        val deck = getOrCreateDeck(name = deckName, now = now)
        if (db.cardDao().existsDuplicate(deck.id, trimmedFront, trimmedBack)) {
            false
        } else {
            db.cardDao().insert(
                CardEntity(
                    deckId = deck.id,
                    front = trimmedFront,
                    back = trimmedBack,
                    studyMode = STUDY_MODE_BASIC,
                    tagsJson = json.encodeToString(tags),
                    createdAt = now,
                    updatedAt = now,
                    dueAt = now,
                ),
            )
            true
        }
    }

    suspend fun nextDueCard(deck: DeckEntity, now: Long, dayStart: Long, dayEnd: Long): CardEntity? {
        val reviewedToday = db.reviewLogDao().countReviewedInRange(deck.id, dayStart, dayEnd)
        if (reviewedToday >= deck.dailyReviewLimit) return null

        val newCardsReviewedToday = db.reviewLogDao().countNewCardsReviewedToday(deck.id, dayStart, dayEnd)
        return if (newCardsReviewedToday < deck.dailyNewCardLimit) {
            db.cardDao().getNextDue(deck.id, now)
        } else {
            db.cardDao().getNextDueReviewedBefore(deck.id, now, dayStart)
        }
    }


    suspend fun nextWidgetDueCard(
        now: Long,
        dayStart: Long,
        dayEnd: Long,
        selectedDeckId: Long? = null,
    ): Pair<DeckEntity, CardEntity>? {
        val decks = if (selectedDeckId == null) {
            db.deckDao().getAll()
        } else {
            db.deckDao().getById(selectedDeckId)?.let { listOf(it) } ?: emptyList()
        }
        var best: Pair<DeckEntity, CardEntity>? = null
        for (deck in decks) {
            val card = nextDueCard(deck = deck, now = now, dayStart = dayStart, dayEnd = dayEnd) ?: continue
            val current = best
            if (current == null || card.dueAt < current.second.dueAt) {
                best = deck to card
            }
        }
        return best
    }

    suspend fun countDueUntil(deck: DeckEntity, dayStart: Long, dayEnd: Long, until: Long): Int {
        val reviewedToday = db.reviewLogDao().countReviewedInRange(deck.id, dayStart, dayEnd)
        val reviewCapacity = (deck.dailyReviewLimit - reviewedToday).coerceAtLeast(0)
        if (reviewCapacity == 0) return 0

        val dueCount = db.cardDao().countDueUntil(deck.id, until)
        val newCardsReviewedToday = db.reviewLogDao().countNewCardsReviewedToday(deck.id, dayStart, dayEnd)
        val hasNewCapacity = newCardsReviewedToday < deck.dailyNewCardLimit
        val eligibleCount = if (hasNewCapacity) {
            dueCount
        } else {
            db.cardDao().countDueReviewedBefore(deck.id, until, dayStart)
        }
        return minOf(eligibleCount, reviewCapacity)
    }

    suspend fun review(card: CardEntity, rating: String, settings: SchedulerSettings, now: Long): CardEntity {
        val updated = if (rating == "GOOD") Scheduler.scheduleGood(card, now, settings)
        else Scheduler.scheduleAgain(card, now, settings)
        db.cardDao().update(updated)
        db.reviewLogDao().insert(
            ReviewLogEntity(
                cardId = card.id,
                reviewedAt = now,
                rating = rating,
                prevDueAt = card.dueAt,
                newDueAt = updated.dueAt,
                prevIntervalDays = card.intervalDays,
                newIntervalDays = updated.intervalDays,
                prevEaseFactor = card.easeFactor,
                newEaseFactor = updated.easeFactor,
            ),
        )
        db.studyAttemptLogDao().insert(
            StudyAttemptLogEntity(
                cardId = card.id,
                timestamp = now,
                studyMode = STUDY_MODE_BASIC,
                selfGrade = rating,
                revealUsed = true,
            ),
        )
        return updated
    }

    suspend fun reviewPassage(card: CardEntity, attempt: StudyAttempt, settings: SchedulerSettings, now: Long): CardEntity {
        val updated = Scheduler.schedulePassage(
            card = card,
            nowMillis = now,
            s = settings,
            selfGrade = attempt.selfGrade,
            hintLevelUsed = attempt.hintLevelUsed,
        )
        db.cardDao().update(updated)
        db.reviewLogDao().insert(
            ReviewLogEntity(
                cardId = card.id,
                reviewedAt = now,
                rating = attempt.selfGrade,
                prevDueAt = card.dueAt,
                newDueAt = updated.dueAt,
                prevIntervalDays = card.intervalDays,
                newIntervalDays = updated.intervalDays,
                prevEaseFactor = card.easeFactor,
                newEaseFactor = updated.easeFactor,
            ),
        )
        db.studyAttemptLogDao().insert(
            StudyAttemptLogEntity(
                cardId = card.id,
                timestamp = now,
                studyMode = attempt.studyMode,
                promptType = attempt.promptType,
                hintLevelUsed = attempt.hintLevelUsed,
                revealUsed = attempt.revealUsed,
                selfGrade = attempt.selfGrade,
                durationMs = attempt.durationMs,
                errorTypes = attempt.errorTypes,
            ),
        )
        return updated
    }

    suspend fun importParsed(results: List<ParseResult>, now: Long): ImportSummary {
        var added = 0
        var skipped = 0
        var failed = 0
        for (result in results) {
            when (result) {
                is ParseResult.Error -> failed++
                is ParseResult.Success -> {
                    val c = result.card
                    try {
                        val deck = getOrCreateDeck(name = c.deck, now = now)
                        if (db.cardDao().existsDuplicate(deck.id, c.front, c.back)) {
                            skipped++
                            continue
                        }
                        val validInterval = c.interval_days?.takeIf { it >= 0 } ?: 0.0
                        val validEase = c.ease_factor?.takeIf { it >= 1.3 } ?: 2.5
                        val validRep = c.repetitions?.takeIf { it >= 0 } ?: 0
                        val validLapses = c.lapses?.takeIf { it >= 0 } ?: 0
                        val due = c.due_at?.takeIf { it > 0 } ?: now
                        val tagsJson = json.encodeToString(c.tags ?: emptyList<String>())
                        db.cardDao().insert(
                            CardEntity(
                                deckId = deck.id,
                                front = c.front,
                                back = c.back,
                                studyMode = normalizeStudyMode(c.study_mode),
                                strictness = normalizeStrictness(c.strictness),
                                hintPolicy = normalizeHintPolicy(c.hint_policy),
                                cloze1 = normalizeOptionalText(c.cloze1),
                                cloze2 = normalizeOptionalText(c.cloze2),
                                cloze3 = normalizeOptionalText(c.cloze3),
                                conceptDomain = normalizeOptionalText(c.concept_domain),
                                conceptOneLiner = normalizeOptionalText(c.concept_one_liner),
                                conceptProposer = normalizeOptionalText(c.concept_proposer),
                                conceptYear = c.concept_year,
                                canonicalExample = normalizeOptionalText(c.canonical_example),
                                counterExample = normalizeOptionalText(c.counter_example),
                                commonMisuse = normalizeOptionalText(c.common_misuse),
                                contrastPoints = normalizeOptionalText(c.contrast_points),
                                evidenceLevel = normalizeOptionalText(c.evidence_level),
                                sources = normalizeOptionalText(c.sources),
                                confusionCluster = normalizeOptionalText(c.confusion_cluster),
                                tagsJson = tagsJson,
                                createdAt = now,
                                updatedAt = now,
                                dueAt = due,
                                intervalDays = validInterval,
                                easeFactor = validEase,
                                repetitions = validRep,
                                lapses = validLapses,
                            ),
                        )
                        added++
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
        }
        return ImportSummary(added, skipped, failed)
    }


    suspend fun exportDeckMarkdown(deckId: Long): String {
        val deck = db.deckDao().getById(deckId) ?: return ""
        return db.cardDao().exportByDeck(deckId).joinToString("\n===\n") { card ->
            val tags = runCatching { json.decodeFromString<List<String>>(card.tagsJson) }.getOrDefault(emptyList())
            buildString {
                appendLine("---")
                appendLine("deck: ${deck.name}")
                if (tags.isEmpty()) {
                    appendLine("tags: []")
                } else {
                    appendLine("tags:")
                    tags.forEach { appendLine("  - $it") }
                }
                if (card.studyMode != STUDY_MODE_BASIC) {
                    appendLine("study_mode: \"${card.studyMode.lowercase()}\"")
                }
                if (card.strictness != STRICTNESS_EXACT) {
                    appendLine("strictness: \"${card.strictness.lowercase()}\"")
                }
                if (card.hintPolicy != HINT_POLICY_ENABLED) {
                    appendLine("hint_policy: \"${card.hintPolicy.lowercase()}\"")
                }
                card.cloze1?.takeIf { it.isNotBlank() }?.let { appendLine("cloze1: \"$it\"") }
                card.cloze2?.takeIf { it.isNotBlank() }?.let { appendLine("cloze2: \"$it\"") }
                card.cloze3?.takeIf { it.isNotBlank() }?.let { appendLine("cloze3: \"$it\"") }
                card.conceptDomain?.let { appendLine("concept_domain: \"$it\"") }
                card.conceptOneLiner?.let { appendLine("concept_one_liner: \"$it\"") }
                card.conceptProposer?.let { appendLine("concept_proposer: \"$it\"") }
                card.conceptYear?.let { appendLine("concept_year: $it") }
                card.canonicalExample?.let { appendLine("canonical_example: \"$it\"") }
                card.counterExample?.let { appendLine("counter_example: \"$it\"") }
                card.commonMisuse?.let { appendLine("common_misuse: \"$it\"") }
                card.contrastPoints?.let { appendLine("contrast_points: \"$it\"") }
                card.evidenceLevel?.let { appendLine("evidence_level: \"$it\"") }
                card.sources?.let { appendLine("sources: \"$it\"") }
                card.confusionCluster?.let { appendLine("confusion_cluster: \"$it\"") }
                appendLine("---")
                appendLine()
                appendLine("## Front")
                appendLine(card.front)
                appendLine()
                appendLine("## Back")
                append(card.back)
            }
        }
    }

    suspend fun exportDeckJsonLines(deckId: Long): String {
        val deck = db.deckDao().getById(deckId) ?: return ""
        return db.cardDao().exportByDeck(deckId).joinToString("\n") { card ->
            json.encodeToString(
                mapOf(
                    "deck" to deck.name,
                    "front" to card.front,
                    "back" to card.back,
                    "tags" to runCatching { json.decodeFromString<List<String>>(card.tagsJson) }.getOrDefault(emptyList()),
                    "study_mode" to card.studyMode.lowercase(),
                    "strictness" to card.strictness.lowercase(),
                    "hint_policy" to card.hintPolicy.lowercase(),
                    "cloze1" to card.cloze1,
                    "cloze2" to card.cloze2,
                    "cloze3" to card.cloze3,
                    "concept_domain" to card.conceptDomain,
                    "concept_one_liner" to card.conceptOneLiner,
                    "concept_proposer" to card.conceptProposer,
                    "concept_year" to card.conceptYear,
                    "canonical_example" to card.canonicalExample,
                    "counter_example" to card.counterExample,
                    "common_misuse" to card.commonMisuse,
                    "contrast_points" to card.contrastPoints,
                    "evidence_level" to card.evidenceLevel,
                    "sources" to card.sources,
                    "confusion_cluster" to card.confusionCluster,
                    "due_at" to card.dueAt,
                    "interval_days" to card.intervalDays,
                    "ease_factor" to card.easeFactor,
                    "repetitions" to card.repetitions,
                    "lapses" to card.lapses,
                ),
            )
        }
    }
}
