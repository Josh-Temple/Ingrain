package com.ingrain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ingrain.data.AppDatabase
import com.ingrain.data.IngrainRepository
import com.ingrain.data.PROMPT_TYPE_CLOZE_RECALL
import com.ingrain.data.PROMPT_TYPE_FREE_RECALL
import com.ingrain.data.STUDY_MODE_PASSAGE_MEMORIZATION
import com.ingrain.data.StudyAttempt
import com.ingrain.scheduler.SchedulerSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IngrainRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: IngrainRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = IngrainRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun review_basicMode_logsAttemptWithoutCrash() = runBlocking {
        val now = 1_700_000_000_000L
        val added = repo.addCard(
            deckName = "Deck",
            front = "Front",
            back = "Back",
            tags = emptyList(),
            now = now,
        ).getOrThrow()
        assertTrue(added)

        val deck = repo.findDeckByName("Deck")!!
        val card = repo.nextDueCard(deck, now, now - 1, now + 1)!!

        val updated = repo.review(card, rating = "GOOD", settings = SchedulerSettings(), now = now + 10)
        assertNotNull(updated)

        val logged = db.studyAttemptLogDao()
            .allForCard(card.id)
            .singleOrNull()
        assertNotNull(logged)
        assertEquals(PROMPT_TYPE_FREE_RECALL, logged?.promptType)
    }

    @Test
    fun review_passageMode_logsActualPromptType() = runBlocking {
        val now = 1_700_000_000_000L
        repo.addCard(
            deckName = "Deck",
            front = "Recite",
            back = "Line one. Line two.",
            tags = emptyList(),
            now = now,
        ).getOrThrow()

        val deck = repo.findDeckByName("Deck")!!
        val baseCard = repo.nextDueCard(deck, now, now - 1, now + 1)!!
        val card = baseCard.copy(studyMode = STUDY_MODE_PASSAGE_MEMORIZATION)
        db.cardDao().update(card)

        repo.reviewPassage(
            card = card,
            attempt = StudyAttempt(
                studyMode = STUDY_MODE_PASSAGE_MEMORIZATION,
                promptType = PROMPT_TYPE_CLOZE_RECALL,
                hintLevelUsed = 2,
                revealUsed = true,
                selfGrade = com.ingrain.scheduler.Scheduler.PASSAGE_GRADE_HINTED,
                durationMs = 1234,
            ),
            settings = SchedulerSettings(),
            now = now + 50,
        )

        val logged = db.studyAttemptLogDao().allForCard(card.id).single()
        assertEquals(PROMPT_TYPE_CLOZE_RECALL, logged.promptType)
        assertEquals(2, logged.hintLevelUsed)
        assertEquals(true, logged.revealUsed)
    }
}

