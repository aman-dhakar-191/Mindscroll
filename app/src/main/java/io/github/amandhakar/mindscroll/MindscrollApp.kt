package io.github.amandhakar.mindscroll

import android.app.Application
import android.content.Context
import io.github.amandhakar.mindscroll.data.MetricsRepository
import io.github.amandhakar.mindscroll.data.MindscrollDatabase
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.display.CounterNotification

class MindscrollApp : Application() {
    val database by lazy { MindscrollDatabase.create(this) }
    val metrics by lazy { MetricsRepository(database.metricDao()) }
    val settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        CounterNotification.createChannels(this)
    }
}

val Context.app: MindscrollApp get() = applicationContext as MindscrollApp
