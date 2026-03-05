package com.ingrain.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_DAILY_REVIEW_LIMIT = 100
const val DEFAULT_DAILY_NEW_CARD_LIMIT = 1

const val STUDY_MODE_BASIC = "BASIC"
const val STUDY_MODE_PASSAGE_MEMORIZATION = "PASSAGE_MEMORIZATION"

const val STRICTNESS_EXACT = "EXACT"
const val STRICTNESS_NEAR_EXACT = "NEAR_EXACT"
const val STRICTNESS_MEANING_ONLY = "MEANING_ONLY"

const val HINT_POLICY_ENABLED = "ENABLED"
const val HINT_POLICY_DISABLED = "DISABLED"

const val PROMPT_TYPE_FREE_RECALL = "FREE_RECALL"
const val PROMPT_TYPE_FIRST_WORD_CUE = "FIRST_WORD_CUE"
const val PROMPT_TYPE_CLOZE_RECALL = "CLOZE_RECALL"

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
    @ColumnInfo(name = "study_mode") val studyMode: String = STUDY_MODE_BASIC,
    val strictness: String = STRICTNESS_EXACT,
    @ColumnInfo(name = "hint_policy") val hintPolicy: String = HINT_POLICY_ENABLED,
    @ColumnInfo(name = "cloze1") val cloze1: String? = null,
    @ColumnInfo(name = "cloze2") val cloze2: String? = null,
    @ColumnInfo(name = "cloze3") val cloze3: String? = null,
    @ColumnInfo(name = "concept_domain") val conceptDomain: String? = null,
    @ColumnInfo(name = "concept_one_liner") val conceptOneLiner: String? = null,
    @ColumnInfo(name = "concept_proposer") val conceptProposer: String? = null,
    @ColumnInfo(name = "concept_year") val conceptYear: Int? = null,
    @ColumnInfo(name = "canonical_example") val canonicalExample: String? = null,
    @ColumnInfo(name = "counter_example") val counterExample: String? = null,
    @ColumnInfo(name = "common_misuse") val commonMisuse: String? = null,
    @ColumnInfo(name = "contrast_points") val contrastPoints: String? = null,
    @ColumnInfo(name = "evidence_level") val evidenceLevel: String? = null,
    @ColumnInfo(name = "sources") val sources: String? = null,
    @ColumnInfo(name = "confusion_cluster") val confusionCluster: String? = null,
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
    tableName = "study_attempt_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["card_id"]), Index(value = ["timestamp"])],
)
data class StudyAttemptLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val timestamp: Long,
    @ColumnInfo(name = "study_mode") val studyMode: String,
    @ColumnInfo(name = "prompt_type") val promptType: String = PROMPT_TYPE_FREE_RECALL,
    @ColumnInfo(name = "hint_level_used") val hintLevelUsed: Int = 0,
    @ColumnInfo(name = "reveal_used") val revealUsed: Boolean = false,
    @ColumnInfo(name = "self_grade") val selfGrade: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "error_types") val errorTypes: String? = null,
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
