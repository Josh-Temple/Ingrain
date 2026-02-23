package com.ingrain.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id=:id")
    suspend fun getById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name=:name LIMIT 1")
    suspend fun getByName(name: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE deck_id=:deckId ORDER BY created_at DESC")
    fun observeByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deck_id=:deckId AND due_at <= :now ORDER BY due_at LIMIT 1")
    suspend fun getNextDue(deckId: Long, now: Long): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE deck_id=:deckId
          AND due_at <= :now
          AND EXISTS(SELECT 1 FROM review_logs rl WHERE rl.card_id = cards.id AND rl.reviewed_at < :dayStart)
        ORDER BY due_at
        LIMIT 1
        """
    )
    suspend fun getNextDueReviewedBefore(deckId: Long, now: Long, dayStart: Long): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE deck_id=:deckId AND due_at <= :until")
    suspend fun countDueUntil(deckId: Long, until: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE deck_id=:deckId
          AND due_at <= :until
          AND EXISTS(SELECT 1 FROM review_logs rl WHERE rl.card_id = cards.id AND rl.reviewed_at < :dayStart)
        """
    )
    suspend fun countDueReviewedBefore(deckId: Long, until: Long, dayStart: Long): Int

    @Query("SELECT * FROM cards WHERE id=:id")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM cards WHERE deck_id=:deckId AND front=:front AND back=:back)")
    suspend fun existsDuplicate(deckId: Long, front: String, back: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM cards WHERE deck_id=:deckId ORDER BY id")
    suspend fun exportByDeck(deckId: Long): List<CardEntity>
}

@Dao
interface ReviewLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReviewLogEntity)

    @Query(
        """
        SELECT COUNT(*)
        FROM review_logs rl
        INNER JOIN cards c ON c.id = rl.card_id
        WHERE c.deck_id=:deckId AND rl.reviewed_at BETWEEN :startInclusive AND :endInclusive
        """
    )
    suspend fun countReviewedInRange(deckId: Long, startInclusive: Long, endInclusive: Long): Int

    @Query(
        """
        SELECT COUNT(DISTINCT rl.card_id)
        FROM review_logs rl
        INNER JOIN cards c ON c.id = rl.card_id
        WHERE c.deck_id=:deckId
          AND rl.reviewed_at BETWEEN :dayStart AND :dayEnd
          AND NOT EXISTS(
            SELECT 1 FROM review_logs prev
            WHERE prev.card_id = rl.card_id AND prev.reviewed_at < :dayStart
          )
        """
    )
    suspend fun countNewCardsReviewedToday(deckId: Long, dayStart: Long, dayEnd: Long): Int
}

@Dao
interface StudyAttemptLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: StudyAttemptLogEntity)

    @Query("SELECT * FROM study_attempt_logs WHERE card_id=:cardId ORDER BY timestamp")
    suspend fun allForCard(cardId: Long): List<StudyAttemptLogEntity>
}
