package com.ingrain.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_DAILY_REVIEW_LIMIT = 100
const val DEFAULT_DAILY_NEW_CARD_LIMIT = 1

@Entity(tableName = "decks", indices = [Index(value = ["name"], unique = true)])
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "daily_review_limit") val dailyReviewLimit: Int = DEFAULT_DAILY_REVIEW_LIMIT,
    @ColumnInfo(name = "daily_new_card_limit") val dailyNewCardLimit: Int = DEFAULT_DAILY_NEW_CARD_LIMIT,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["deck_id"]), Index(value = ["deck_id", "front", "back"], unique = true)]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    val front: String,
    val back: String,
    @ColumnInfo(name = "tags_json") val tagsJson: String = "[]",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "interval_days") val intervalDays: Double = 0.0,
    @ColumnInfo(name = "ease_factor") val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long? = null,
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["card_id"])]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    val rating: String,
    @ColumnInfo(name = "prev_due_at") val prevDueAt: Long,
    @ColumnInfo(name = "new_due_at") val newDueAt: Long,
    @ColumnInfo(name = "prev_interval_days") val prevIntervalDays: Double,
    @ColumnInfo(name = "new_interval_days") val newIntervalDays: Double,
    @ColumnInfo(name = "prev_ease_factor") val prevEaseFactor: Double,
    @ColumnInfo(name = "new_ease_factor") val newEaseFactor: Double,
)
