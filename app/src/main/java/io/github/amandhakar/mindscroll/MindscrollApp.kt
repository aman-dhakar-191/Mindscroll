package io.github.amandhakar.mindscroll

import android.app.Application
import android.content.Context
import io.github.amandhakar.mindscroll.data.MetricsRepository
import io.github.amandhakar.mindscroll.data.MindscrollDatabase
import io.github.amandhakar.mindscroll.data.MoMemoryStore
import io.github.amandhakar.mindscroll.data.MoPrefs
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.display.CounterNotification
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MindscrollApp : Application() {
    val database by lazy { MindscrollDatabase.create(this) }
    val metrics by lazy { MetricsRepository(database.metricDao()) }
    val settings by lazy { Settings(this) }
    val moPrefs by lazy { MoPrefs(this) }
    val moMemories by lazy { MoMemoryStore(database) }

    /** For writes that must finish even if the component that started them is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val labels = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        CounterNotification.createChannels(this)
        TrackedApps.labeler = ::appLabel
        TrackedApps.set(settings.trackedApps)
        appScope.launch(Dispatchers.Main) {
            settings.observeTrackedApps().collect { TrackedApps.set(it) }
        }
    }

    /** The app's name as the phone shows it, falling back to what Mindscroll knows. */
    @Synchronized
    fun appLabel(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { ReelApps.known[packageName]?.label ?: packageName }
    }
}

val Context.app: MindscrollApp get() = applicationContext as MindscrollApp
