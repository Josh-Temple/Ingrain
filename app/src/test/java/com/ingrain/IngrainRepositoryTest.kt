package com.ingrain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ingrain.data.AppDatabase
import com.ingrain.data.ConceptMetadataInput
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
    @Test
    fun nextWidgetDueCard_returnsEarliestDueAcrossDecks() = runBlocking {
        val now = 1_700_000_000_000L
        repo.addCard(
            deckName = "Beta",
            front = "Later",
            back = "A2",
            tags = emptyList(),
            now = now,
        ).getOrThrow()
        repo.addCard(
            deckName = "Alpha",
            front = "Sooner",
            back = "A1",
            tags = emptyList(),
            now = now,
        ).getOrThrow()

        val betaDeck = repo.findDeckByName("Beta")!!
        val alphaDeck = repo.findDeckByName("Alpha")!!
        val betaCard = repo.nextDueCard(betaDeck, now, now - 1000, now + 1000)!!
        val alphaCard = repo.nextDueCard(alphaDeck, now, now - 1000, now + 1000)!!

        db.cardDao().update(betaCard.copy(dueAt = now + 5000))
        db.cardDao().update(alphaCard.copy(dueAt = now + 1000))

        val due = repo.nextWidgetDueCard(now = now + 6000, dayStart = now - 1000, dayEnd = now + 10_000)

        assertNotNull(due)
        assertEquals("Alpha", due?.first?.name)
        assertEquals("Sooner", due?.second?.front)
    }

    @Test
    fun updateCardContent_updatesConceptMetadata() = runBlocking {
        val now = 1_700_000_000_000L
        repo.addCard(
            deckName = "Psych",
            front = "What is Zeigarnik effect?",
            back = "Remembering incomplete tasks",
            tags = listOf("memory"),
            now = now,
        ).getOrThrow()

        val deck = repo.findDeckByName("Psych")!!
        val card = repo.nextDueCard(deck, now, now - 1, now + 1)!!

        repo.updateCardContent(
            card = card,
            front = card.front,
            back = card.back,
            tags = listOf("memory", "bias"),
            conceptMetadata = ConceptMetadataInput(
                conceptDomain = "psychology",
                conceptOneLiner = "incomplete tasks are remembered better",
                conceptProposer = "Bluma Zeigarnik",
                conceptYear = 1927,
                commonMisuse = "Not equal to all recall biases",
                evidenceLevel = "textbook",
            ),
        ).getOrThrow()

        val updated = repo.getCard(card.id)!!
        assertEquals("psychology", updated.conceptDomain)
        assertEquals("Bluma Zeigarnik", updated.conceptProposer)
        assertEquals(1927, updated.conceptYear)
        assertEquals("textbook", updated.evidenceLevel)
        assertEquals("Not equal to all recall biases", updated.commonMisuse)
    }

    @Test
    fun nextWidgetDueCard_respectsSelectedDeckFilter() = runBlocking {
        val now = 1_700_000_000_000L
        repo.addCard(deckName = "Alpha", front = "A", back = "A", tags = emptyList(), now = now).getOrThrow()
        repo.addCard(deckName = "Beta", front = "B", back = "B", tags = emptyList(), now = now).getOrThrow()

        val alphaDeck = repo.findDeckByName("Alpha")!!
        val betaDeck = repo.findDeckByName("Beta")!!

        val due = repo.nextWidgetDueCard(
            now = now + 100,
            dayStart = now - 1000,
            dayEnd = now + 10_000,
            selectedDeckId = betaDeck.id,
        )

        assertNotNull(due)
        assertEquals(betaDeck.id, due?.first?.id)
        assertEquals("B", due?.second?.front)

        val missing = repo.nextWidgetDueCard(
            now = now + 100,
            dayStart = now - 1000,
            dayEnd = now + 10_000,
            selectedDeckId = betaDeck.id + alphaDeck.id + 9_999,
        )
        assertEquals(null, missing)
    }

}

