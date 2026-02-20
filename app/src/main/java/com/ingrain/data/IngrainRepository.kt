package com.ingrain.data

import com.ingrain.importing.ParseResult
import com.ingrain.scheduler.Scheduler
import com.ingrain.scheduler.SchedulerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ImportSummary(val added: Int, val skipped: Int, val failed: Int)

data class DueCardView(val deck: DeckEntity, val card: CardEntity?)

class IngrainRepository(private val db: AppDatabase) {
    private val json = Json

    fun observeDecks(): Flow<List<DeckEntity>> = db.deckDao().observeAll()

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

    suspend fun getDeck(id: Long): DeckEntity? = db.deckDao().getById(id)

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
                    tagsJson = json.encodeToString(tags),
                    createdAt = now,
                    updatedAt = now,
                    dueAt = now,
                ),
            )
            true
        }
    }

    suspend fun nextDueCard(deckId: Long, now: Long): CardEntity? = db.cardDao().getNextDue(deckId, now)

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

    suspend fun exportDeckJsonLines(deckId: Long): String {
        val deck = db.deckDao().getById(deckId) ?: return ""
        return db.cardDao().exportByDeck(deckId).joinToString("\n") { card ->
            json.encodeToString(
                mapOf(
                    "deck" to deck.name,
                    "front" to card.front,
                    "back" to card.back,
                    "tags" to runCatching { json.decodeFromString<List<String>>(card.tagsJson) }.getOrDefault(emptyList()),
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
