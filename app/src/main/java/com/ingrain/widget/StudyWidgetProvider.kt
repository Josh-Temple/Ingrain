package com.ingrain.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ingrain.MainActivity
import com.ingrain.R
import com.ingrain.data.AppDatabase
import com.ingrain.data.CardEntity
import com.ingrain.data.DeckEntity
import com.ingrain.data.IngrainRepository
import com.ingrain.scheduler.SchedulerSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { updateSingleWidget(context, appWidgetManager, it, forceQuestionState = true) }
            pendingResult.finish()
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { StudyWidgetPrefs.clearForWidget(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_REVEAL -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        val current = StudyWidgetPrefs.isAnswerRevealed(context, widgetId)
                        StudyWidgetPrefs.setAnswerRevealed(context, widgetId, !current)
                        updateSingleWidget(context, manager, widgetId, forceQuestionState = false)
                        pendingResult.finish()
                    }
                }
            }

            ACTION_REFRESH -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val ids = manager.getAppWidgetIds(ComponentName(context, StudyWidgetProvider::class.java))
                    ids.forEach { updateSingleWidget(context, manager, it, forceQuestionState = true) }
                    pendingResult.finish()
                }
            }

            ACTION_GRADE -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val cardId = intent.getLongExtra(EXTRA_CARD_ID, INVALID_CARD_ID)
                val rating = intent.getStringExtra(EXTRA_RATING)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && cardId != INVALID_CARD_ID && !rating.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        handleGradeAction(context, manager, widgetId, cardId, rating)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_REVEAL = "com.ingrain.widget.ACTION_REVEAL"
        private const val ACTION_REFRESH = "com.ingrain.widget.ACTION_REFRESH"
        private const val ACTION_GRADE = "com.ingrain.widget.ACTION_GRADE"

        private const val EXTRA_CARD_ID = "extra_card_id"
        private const val EXTRA_RATING = "extra_rating"
        private const val INVALID_CARD_ID = -1L

        private const val RATING_GOOD = "GOOD"
        private const val RATING_AGAIN = "AGAIN"

        fun refreshSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            forceQuestionState: Boolean,
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                updateSingleWidget(context, manager, widgetId, forceQuestionState)
            }
        }

        private suspend fun handleGradeAction(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            cardId: Long,
            rating: String,
        ) {
            val storedCardId = StudyWidgetPrefs.getDisplayedCardId(context, widgetId)
            if (storedCardId != cardId) {
                updateSingleWidget(context, manager, widgetId, forceQuestionState = true)
                return
            }

            val db = AppDatabase.get(context)
            val repo = IngrainRepository(db)
            val card = repo.getCard(cardId)
            if (card != null) {
                val settings = SchedulerSettingsStore(context).settings.first()
                val now = System.currentTimeMillis()
                repo.review(card = card, rating = rating, settings = settings, now = now)
            }
            StudyWidgetPrefs.setAnswerRevealed(context, widgetId, false)
            StudyWidgetPrefs.setDisplayedCardId(context, widgetId, null)
            updateSingleWidget(context, manager, widgetId, forceQuestionState = true)
        }

        private suspend fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            forceQuestionState: Boolean,
        ) {
            val views = RemoteViews(context.packageName, R.layout.study_widget)
            val db = AppDatabase.get(context)
            val repo = IngrainRepository(db)
            val now = System.currentTimeMillis()
            val dayStart = startOfDayMillis(now)
            val dayEnd = endOfDayMillis(now)

            if (forceQuestionState) {
                StudyWidgetPrefs.setAnswerRevealed(context, widgetId, false)
            }
            val revealAnswer = StudyWidgetPrefs.isAnswerRevealed(context, widgetId)
            val selectedDeckId = StudyWidgetPrefs.getSelectedDeckId(context, widgetId)

            val due = withContext(Dispatchers.IO) {
                repo.nextWidgetDueCard(now = now, dayStart = dayStart, dayEnd = dayEnd, selectedDeckId = selectedDeckId)
            }

            val displayedCardId = due?.second?.id
            StudyWidgetPrefs.setDisplayedCardId(context, widgetId, displayedCardId)

            bindContent(context, views, due?.first, due?.second, revealAnswer = revealAnswer, selectedDeckId = selectedDeckId)

            val revealIntent = Intent(context, StudyWidgetProvider::class.java).apply {
                action = ACTION_REVEAL
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val revealPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                revealIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_reveal_button, revealPendingIntent)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                due?.first?.let { putExtra(MainActivity.EXTRA_OPEN_STUDY_DECK_ID, it.id) }
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 10_000,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_open_app_button, openPendingIntent)

            val refreshIntent = Intent(context, StudyWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId + 20_000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            val selectDeckIntent = Intent(context, WidgetDeckPickerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val selectDeckPendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 25_000,
                selectDeckIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_deck_selector_button, selectDeckPendingIntent)

            val unsolvedIntent = Intent(context, StudyWidgetProvider::class.java).apply {
                action = ACTION_GRADE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_CARD_ID, displayedCardId ?: INVALID_CARD_ID)
                putExtra(EXTRA_RATING, RATING_AGAIN)
            }
            val unsolvedPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId + 30_000,
                unsolvedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_unsolved_button, unsolvedPendingIntent)

            val solvedIntent = Intent(context, StudyWidgetProvider::class.java).apply {
                action = ACTION_GRADE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_CARD_ID, displayedCardId ?: INVALID_CARD_ID)
                putExtra(EXTRA_RATING, RATING_GOOD)
            }
            val solvedPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId + 40_000,
                solvedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_solved_button, solvedPendingIntent)

            manager.updateAppWidget(widgetId, views)
        }

        private fun bindContent(
            context: Context,
            views: RemoteViews,
            deck: DeckEntity?,
            card: CardEntity?,
            revealAnswer: Boolean,
            selectedDeckId: Long?,
        ) {
            val label = when {
                deck != null -> deck.name
                selectedDeckId == null -> context.getString(R.string.widget_all_decks_option)
                else -> context.getString(R.string.widget_selected_deck_empty)
            }
            views.setTextViewText(R.id.widget_deck_selector_button, context.getString(R.string.widget_deck_selector_label, label))

            if (deck == null || card == null) {
                views.setTextViewText(R.id.widget_deck_name, context.getString(R.string.widget_no_due_title))
                views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_no_due_body))
                views.setTextViewText(R.id.widget_reveal_button, context.getString(R.string.widget_refresh))
                views.setViewVisibility(R.id.widget_grade_row, View.GONE)
                return
            }

            views.setTextViewText(R.id.widget_deck_name, deck.name)
            views.setTextViewText(R.id.widget_body, if (revealAnswer) card.back else card.front)
            views.setTextViewText(
                R.id.widget_reveal_button,
                context.getString(if (revealAnswer) R.string.widget_show_question else R.string.widget_show_answer),
            )
            views.setViewVisibility(R.id.widget_grade_row, if (revealAnswer) View.VISIBLE else View.GONE)
        }

        private fun startOfDayMillis(now: Long): Long {
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = now
            c.set(java.util.Calendar.HOUR_OF_DAY, 0)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        private fun endOfDayMillis(now: Long): Long {
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = now
            c.set(java.util.Calendar.HOUR_OF_DAY, 23)
            c.set(java.util.Calendar.MINUTE, 59)
            c.set(java.util.Calendar.SECOND, 59)
            c.set(java.util.Calendar.MILLISECOND, 999)
            return c.timeInMillis
        }
    }
}
