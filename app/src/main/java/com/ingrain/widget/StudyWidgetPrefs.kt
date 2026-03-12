package com.ingrain.widget

import android.content.Context

object StudyWidgetPrefs {
    private const val PREFS_NAME = "study_widget_state"
    private const val INVALID_CARD_ID = -1L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAnswerRevealed(context: Context, widgetId: Int): Boolean =
        prefs(context).getBoolean("revealed_$widgetId", false)

    fun setAnswerRevealed(context: Context, widgetId: Int, revealed: Boolean) {
        prefs(context).edit().putBoolean("revealed_$widgetId", revealed).apply()
    }

    fun getDisplayedCardId(context: Context, widgetId: Int): Long =
        prefs(context).getLong("displayed_card_$widgetId", INVALID_CARD_ID)

    fun setDisplayedCardId(context: Context, widgetId: Int, cardId: Long?) {
        val editor = prefs(context).edit()
        val key = "displayed_card_$widgetId"
        if (cardId == null) editor.remove(key) else editor.putLong(key, cardId)
        editor.apply()
    }

    fun getSelectedDeckId(context: Context, widgetId: Int): Long? {
        val raw = prefs(context).getLong("selected_deck_$widgetId", -1L)
        return raw.takeIf { it > 0 }
    }

    fun setSelectedDeckId(context: Context, widgetId: Int, deckId: Long?) {
        val editor = prefs(context).edit()
        val key = "selected_deck_$widgetId"
        if (deckId == null) editor.remove(key) else editor.putLong(key, deckId)
        editor.apply()
    }

    fun clearForWidget(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("revealed_$widgetId")
            .remove("displayed_card_$widgetId")
            .remove("selected_deck_$widgetId")
            .apply()
    }
}
