package io.github.amandhakar.mindscroll.display

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalTime
import io.github.amandhakar.mindscroll.R
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.Mood
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
            val mood = Mood.forCount(total)
            val moSize = (56 * context.resources.displayMetrics.density).toInt()
            val views = RemoteViews(context.packageName, R.layout.widget_counter).apply {
                setImageViewBitmap(R.id.widget_mo, MoPainter.bitmap(moSize, mood))
                setTextViewText(R.id.widget_count, total.toString())
                setTextViewText(R.id.widget_label, "${TrackerRegistry.primary.title.lowercase()} today")
                setTextColor(R.id.widget_label, mood.color.toInt())
                setTextViewText(R.id.widget_line, Mo.line(total, LocalTime.now().hour))
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
