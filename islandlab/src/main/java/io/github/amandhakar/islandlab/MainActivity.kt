package io.github.amandhakar.islandlab

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Island Lab: posts a few kinds of notification like the one Mindscroll would show by the camera,
 * so we can see what this phone's own island or Live Update chip does with each.
 *
 * What it can and cannot tell: a standard Android 16 Live Update is tried here. A phone maker's own
 * island format (Xiaomi's needs permission from Xiaomi) is not, so "nothing showed" for a Xiaomi
 * phone does not rule that out.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var waitFirst: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        }
        setContentView(scroll)

        column.addView(text("Island Lab", 24f, bold = true))
        status = text("", 14f)
        column.addView(status)
        column.addView(text("1. Allow notifications and Live notifications for this app.", 14f))
        column.addView(button("Allow notifications") { askForNotifications() })
        column.addView(button("Open Live notification settings") { openPromotedSettings() })

        column.addView(text("2. Post a test. Tick the switch to have 8 seconds to switch to another app (for example Instagram, with a Zomato order active) first.", 14f))
        waitFirst = Switch(this).apply { text = "Wait 8 seconds first"; isChecked = true }
        column.addView(waitFirst)
        column.addView(button("A. Live Update + timer (like Mindscroll)") { post(ID_A, "A", promoted = true, chronometer = true, shortText = null) })
        column.addView(button("B. Live Update + timer + short text \"22m\"") { post(ID_B, "B", promoted = true, chronometer = true, shortText = "22m") })
        column.addView(button("C. Plain ongoing notification (to compare)") { post(ID_C, "C", promoted = false, chronometer = true, shortText = null) })
        column.addView(button("Remove all tests") { removeAll() })

        column.addView(text("3. Look at the camera area and the status bar, then copy the log and send it with a screenshot.", 14f))
        column.addView(button("Copy log") { copyLog() })
        log = text("", 12f).apply { typeface = Typeface.MONOSPACE }
        column.addView(log)
        addLog("Island Lab started")
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val manager = getSystemService(NotificationManager::class.java)
        val promoted = if (Build.VERSION.SDK_INT >= 36) manager.canPostPromotedNotifications().toString() else "n/a (needs Android 16)"
        status.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Notifications allowed: ${manager.areNotificationsEnabled()}\n" +
            "Live Updates allowed: $promoted"
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openPromotedSettings() {
        val promoted = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(promoted) }.onFailure {
            addLog("Live notification settings page not found; opening notification settings")
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun post(id: Int, label: String, promoted: Boolean, chronometer: Boolean, shortText: String?) {
        if (waitFirst.isChecked) {
            addLog("$label: posting in 8 s, switch to another app now")
            handler.postDelayed({ postNow(id, label, promoted, chronometer, shortText) }, 8_000L)
        } else {
            postNow(id, label, promoted, chronometer, shortText)
        }
    }

    private fun postNow(id: Int, label: String, promoted: Boolean, chronometer: Boolean, shortText: String?) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Island Lab", NotificationManager.IMPORTANCE_DEFAULT))
            val extras = Bundle()
            // Notification.EXTRA_REQUEST_PROMOTED_ONGOING and EXTRA_SHORT_CRITICAL_TEXT, by their keys.
            if (promoted) extras.putBoolean("android.requestPromotedOngoing", true)
            if (shortText != null) extras.putCharSequence("android.shortCriticalText", shortText)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(countIcon(187))
                .setContentTitle("Instagram · 187 reels today")
                .setContentText("22 min in Instagram")
                .setStyle(Notification.BigTextStyle().bigText("22 min in Instagram · 187 reels today"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setWhen(System.currentTimeMillis() - 22 * 60_000L)
                .setUsesChronometer(chronometer)
                .setShowWhen(chronometer)
                .addExtras(extras)
                .build()
            manager.notify(id, notification)
            val promotable = if (Build.VERSION.SDK_INT >= 36) notification.hasPromotableCharacteristics().toString() else "n/a"
            val allowed = if (Build.VERSION.SDK_INT >= 36) manager.canPostPromotedNotifications().toString() else "n/a"
            addLog("$label: posted (asked to be promoted: $promoted, promotable: $promotable, Live Updates allowed: $allowed)")
        }.onFailure { addLog("$label: failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun removeAll() {
        getSystemService(NotificationManager::class.java).cancelAll()
        addLog("removed all test notifications")
    }

    private fun copyLog() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Island Lab log", log.text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun addLog(message: String) {
        log.text = "${LocalTime.now().format(time)}  $message\n${log.text}"
    }

    /** The count as white text on transparent, like Mindscroll's status bar icon. */
    private fun countIcon(total: Int): Icon {
        val size = (24 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.55f
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(total.toString(), size / 2f, y, paint)
        return Icon.createWithBitmap(bitmap)
    }

    private fun text(value: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sp
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { _: View -> onClick() }
    }

    private companion object {
        const val CHANNEL = "lab"
        const val ID_A = 101
        const val ID_B = 102
        const val ID_C = 103
    }
}
