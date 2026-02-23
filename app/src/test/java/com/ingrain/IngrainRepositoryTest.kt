package com.ingrain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ingrain.data.AppDatabase
import com.ingrain.data.IngrainRepository
import com.ingrain.scheduler.SchedulerSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    }
}
