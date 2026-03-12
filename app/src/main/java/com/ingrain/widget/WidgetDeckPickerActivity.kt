package com.ingrain.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ingrain.R
import com.ingrain.data.AppDatabase
import kotlinx.coroutines.launch

class WidgetDeckPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_deck_picker)

        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finishCanceled()
            return
        }

        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )

        val title = findViewById<TextView>(R.id.deck_picker_title)
        val spinner = findViewById<Spinner>(R.id.deck_picker_spinner)
        val save = findViewById<Button>(R.id.deck_picker_save)
        val cancel = findViewById<Button>(R.id.deck_picker_cancel)

        title.text = getString(R.string.widget_pick_deck_title)
        cancel.setOnClickListener { finishCanceled(widgetId) }

        lifecycleScope.launch {
            val decks = AppDatabase.get(this@WidgetDeckPickerActivity).deckDao().getAll()
            val selectedDeckId = StudyWidgetPrefs.getSelectedDeckId(this@WidgetDeckPickerActivity, widgetId)

            val items = mutableListOf(getString(R.string.widget_all_decks_option))
            items.addAll(decks.map { it.name })
            val adapter = ArrayAdapter(
                this@WidgetDeckPickerActivity,
                android.R.layout.simple_spinner_item,
                items,
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.adapter = adapter

            val selectedIndex = selectedDeckId
                ?.let { targetId -> decks.indexOfFirst { it.id == targetId } }
                ?.takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
            spinner.setSelection(selectedIndex)

            save.setOnClickListener {
                val selected = spinner.selectedItemPosition
                val selectedDeck = if (selected <= 0) null else decks[selected - 1]
                StudyWidgetPrefs.setSelectedDeckId(this@WidgetDeckPickerActivity, widgetId, selectedDeck?.id)

                val manager = AppWidgetManager.getInstance(this@WidgetDeckPickerActivity)
                StudyWidgetProvider.refreshSingleWidget(this@WidgetDeckPickerActivity, manager, widgetId, forceQuestionState = true)

                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                )
                finish()
            }
        }
    }

    private fun finishCanceled(widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
        val data = Intent()
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        setResult(Activity.RESULT_CANCELED, data)
        finish()
    }
}
