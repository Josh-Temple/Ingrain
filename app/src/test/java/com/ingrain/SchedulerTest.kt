package com.ingrain

import com.ingrain.data.CardEntity
import com.ingrain.scheduler.Scheduler
import com.ingrain.scheduler.SchedulerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val now = 1_700_000_000_000L
    private val settings = SchedulerSettings()

    private fun baseCard() = CardEntity(
        id = 1,
        deckId = 1,
        front = "f",
        back = "b",
        createdAt = now,
        updatedAt = now,
        dueAt = now,
    )

    @Test
    fun scheduleGood_firstReview() {
        val updated = Scheduler.scheduleGood(baseCard(), now, settings)
        assertEquals(1, updated.repetitions)
        assertEquals(1.0, updated.intervalDays, 0.0001)
        assertEquals(now + 86_400_000L, updated.dueAt)
    }

    @Test
    fun scheduleAgain_resetsAndDelays() {
        val input = baseCard().copy(repetitions = 3, intervalDays = 12.0, easeFactor = 1.4)
        val updated = Scheduler.scheduleAgain(input, now, settings)
        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.lapses)
        assertEquals(0.0, updated.intervalDays, 0.0001)
        assertEquals(1.3, updated.easeFactor, 0.0001)
        assertEquals(now + 600_000L, updated.dueAt)
    }

    @Test
    fun schedulePassage_hinted_isEarlierThanGood() {
        val card = baseCard().copy(repetitions = 2, intervalDays = 8.0, easeFactor = 2.4)
        val good = Scheduler.scheduleGood(card, now, settings)
        val hinted = Scheduler.schedulePassage(card, now, settings, Scheduler.PASSAGE_GRADE_HINTED, hintLevelUsed = 1)
        assertTrue(hinted.dueAt < good.dueAt)
        assertEquals(0.5, hinted.intervalDays, 0.0001)
    }

    @Test
    fun schedulePassage_unknownGrade_fallsBackToAgain() {
        val card = baseCard().copy(repetitions = 2, intervalDays = 8.0, easeFactor = 2.4)
        val fromPassage = Scheduler.schedulePassage(card, now, settings, selfGrade = "AGAIN", hintLevelUsed = 0)
        val fromAgain = Scheduler.scheduleAgain(card, now, settings)
        assertEquals(fromAgain.dueAt, fromPassage.dueAt)
        assertEquals(fromAgain.repetitions, fromPassage.repetitions)
        assertEquals(fromAgain.lapses, fromPassage.lapses)
    }
}
