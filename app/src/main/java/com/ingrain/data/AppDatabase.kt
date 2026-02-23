package com.ingrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeckEntity::class, CardEntity::class, ReviewLogEntity::class, StudyAttemptLogEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun studyAttemptLogDao(): StudyAttemptLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE decks ADD COLUMN daily_review_limit INTEGER NOT NULL DEFAULT $DEFAULT_DAILY_REVIEW_LIMIT"
                )
                database.execSQL(
                    "ALTER TABLE decks ADD COLUMN daily_new_card_limit INTEGER NOT NULL DEFAULT $DEFAULT_DAILY_NEW_CARD_LIMIT"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE cards ADD COLUMN study_mode TEXT NOT NULL DEFAULT '$STUDY_MODE_BASIC'"
                )
                database.execSQL(
                    "ALTER TABLE cards ADD COLUMN strictness TEXT NOT NULL DEFAULT '$STRICTNESS_EXACT'"
                )
                database.execSQL(
                    "ALTER TABLE cards ADD COLUMN hint_policy TEXT NOT NULL DEFAULT '$HINT_POLICY_ENABLED'"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_attempt_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        card_id INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        study_mode TEXT NOT NULL,
                        prompt_type TEXT NOT NULL,
                        hint_level_used INTEGER NOT NULL,
                        reveal_used INTEGER NOT NULL,
                        self_grade TEXT NOT NULL,
                        duration_ms INTEGER,
                        error_types TEXT,
                        FOREIGN KEY(card_id) REFERENCES cards(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_study_attempt_logs_card_id ON study_attempt_logs(card_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_study_attempt_logs_timestamp ON study_attempt_logs(timestamp)")
            }
        }



        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN cloze1 TEXT")
                database.execSQL("ALTER TABLE cards ADD COLUMN cloze2 TEXT")
                database.execSQL("ALTER TABLE cards ADD COLUMN cloze3 TEXT")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "ingrain.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
