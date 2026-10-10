package io.github.amandhakar.mindscroll.display

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.amandhakar.mindscroll.R
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import io.github.amandhakar.mindscroll.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home screen widget with today's count. The accessibility service pushes every new count;
 * Android's periodic update (every 30 minutes) and boot refresh it otherwise, which also
 * resets it to 0 after midnight when nothing else is running.
 */
class CounterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, goAsync())
    }

    companion object {
        fun update(context: Context, total: Long) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CounterWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_counter).apply {
                setTextViewText(R.id.widget_count, total.toString())
                setTextViewText(R.id.widget_label, "${TrackerRegistry.primary.title.lowercase()} today")
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }

        /** Reads today's total from the database and redraws every widget. */
        fun refresh(context: Context, pending: PendingResult? = null) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    update(appContext, appContext.app.metrics.todayTotal(TrackerRegistry.primary.id))
                } finally {
                    pending?.finish()
                }
            }
        }
    }
}
