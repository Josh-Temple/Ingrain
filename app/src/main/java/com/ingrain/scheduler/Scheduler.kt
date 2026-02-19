package com.ingrain.scheduler

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ingrain.data.CardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("scheduler_settings")

data class SchedulerSettings(
    val initialIntervalGoodDays: Double = 1.0,
    val secondIntervalGoodDays: Double = 6.0,
    val minEaseFactor: Double = 1.3,
    val easeFactorStepGood: Double = 0.05,
    val easeFactorStepAgain: Double = -0.20,
    val againDelayMinutes: Int = 10,
)

class SchedulerSettingsStore(private val context: Context) {
    private object Keys {
        val initial = doublePreferencesKey("initial_interval_good_days")
        val second = doublePreferencesKey("second_interval_good_days")
        val minEase = doublePreferencesKey("min_ease_factor")
        val stepGood = doublePreferencesKey("ease_factor_step_good")
        val stepAgain = doublePreferencesKey("ease_factor_step_again")
        val againDelay = intPreferencesKey("again_delay_minutes")
    }

    val settings: Flow<SchedulerSettings> = context.dataStore.data.map { p ->
        SchedulerSettings(
            initialIntervalGoodDays = p[Keys.initial] ?: 1.0,
            secondIntervalGoodDays = p[Keys.second] ?: 6.0,
            minEaseFactor = p[Keys.minEase] ?: 1.3,
            easeFactorStepGood = p[Keys.stepGood] ?: 0.05,
            easeFactorStepAgain = p[Keys.stepAgain] ?: -0.20,
            againDelayMinutes = p[Keys.againDelay] ?: 10,
        )
    }

    suspend fun save(settings: SchedulerSettings) {
        context.dataStore.edit { p ->
            p[Keys.initial] = settings.initialIntervalGoodDays
            p[Keys.second] = settings.secondIntervalGoodDays
            p[Keys.minEase] = settings.minEaseFactor
            p[Keys.stepGood] = settings.easeFactorStepGood
            p[Keys.stepAgain] = settings.easeFactorStepAgain
            p[Keys.againDelay] = settings.againDelayMinutes
        }
    }
}

object Scheduler {
    fun scheduleGood(card: CardEntity, nowMillis: Long, s: SchedulerSettings): CardEntity {
        val interval = when (card.repetitions) {
            0 -> s.initialIntervalGoodDays
            1 -> s.secondIntervalGoodDays
            else -> card.intervalDays * card.easeFactor
        }
        val due = nowMillis + (interval * 24 * 60 * 60 * 1000).toLong()
        return card.copy(
            repetitions = card.repetitions + 1,
            intervalDays = interval,
            easeFactor = card.easeFactor + s.easeFactorStepGood,
            dueAt = due,
            lastReviewedAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    fun scheduleAgain(card: CardEntity, nowMillis: Long, s: SchedulerSettings): CardEntity {
        val due = nowMillis + s.againDelayMinutes * 60 * 1000L
        return card.copy(
            lapses = card.lapses + 1,
            repetitions = 0,
            intervalDays = 0.0,
            easeFactor = maxOf(s.minEaseFactor, card.easeFactor + s.easeFactorStepAgain),
            dueAt = due,
            lastReviewedAt = nowMillis,
            updatedAt = nowMillis,
        )
    }
}
