package io.github.amandhakar.mindscroll.display

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import io.github.amandhakar.mindscroll.R
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import io.github.amandhakar.mindscroll.ui.MainActivity

/**
 * The always-on notification. Its status bar icon is the count itself, drawn as a bitmap, so
 * today's number sits in the status bar next to the camera without opening the shade.
 */
object CounterNotification {
    private const val CHANNEL_COUNTER = "counter"
    private const val CHANNEL_STATUS = "status"
    private const val ID_COUNTER = 1
    private const val ID_OFF = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_COUNTER, "Today's count", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows today's reel count in the status bar"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_STATUS, "Tracking status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when counting has stopped"
                },
            ),
        )
    }

    fun show(context: Context, state: CounterState) {
        if (!canNotify(context)) return
        val tracker = TrackerRegistry.primary
        val breakdown = state.byItem.joinToString("  ·  ") { (label, count) -> "$label $count" }
        val line = state.line()
        val notification = Notification.Builder(context, CHANNEL_COUNTER)
            .setSmallIcon(countIcon(context, state.total))
            .setLargeIcon(moIcon(context, state))
            .setContentTitle("${state.total} ${tracker.title.lowercase()} today · ${Mo.NAME} is ${state.mood.title.lowercase()}")
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(if (breakdown.isEmpty()) line else "$line\n$breakdown"))
            .setColor(state.mood.color.toInt())
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_COUNTER, notification)
        context.getSystemService(NotificationManager::class.java).cancel(ID_OFF)
    }

    private var moIconCache: Pair<Mood, Icon>? = null

    private fun moIcon(context: Context, state: CounterState): Icon {
        moIconCache?.let { (mood, icon) -> if (mood == state.mood) return icon }
        val size = (64 * context.resources.displayMetrics.density).toInt()
        return Icon.createWithBitmap(MoPainter.bitmap(size, state.mood)).also { moIconCache = state.mood to it }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_COUNTER)
    }

    /** Posted after boot if the accessibility service isn't on, so counting doesn't silently stop. */
    fun showTrackingOff(context: Context) {
        if (!canNotify(context)) return
        val notification = Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mindscroll isn't counting")
            .setContentText("Turn its accessibility service back on to keep counting reels.")
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 1,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_OFF, notification)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The count as white text on transparent; Android tints status bar icons itself. */
    private fun countIcon(context: Context, total: Long): Icon {
        val size = (24 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val text = if (total > 999) "${total / 1000}k" else total.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * when (text.length) {
                1 -> 0.9f
                2 -> 0.75f
                3 -> 0.55f
                else -> 0.45f
            }
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(text, size / 2f, y, paint)
        return Icon.createWithBitmap(bitmap)
    }
}
