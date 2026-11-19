package io.github.amandhakar.mindscroll

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.amandhakar.mindscroll.data.MetricEvent
import io.github.amandhakar.mindscroll.data.MindscrollDatabase
import io.github.amandhakar.mindscroll.data.MoMemoryStore
import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.MoMemories
import io.github.amandhakar.mindscroll.mascot.MoMemory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MindscrollDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test.db"
    private val day = 24 * 60 * 60_000L

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun upgradingFromVersion1KeepsTheCountsAndAddsMosMemory() = runBlocking {
        // A version 1 database: today's schema without Mo's memory, as version 1 had it.
        Room.databaseBuilder(context, MindscrollDatabase::class.java, name).build().apply {
            metricDao().insert(MetricEvent(trackerId = "reels", itemKey = "app", value = 3, day = 7, timestamp = 1))
            openHelper.writableDatabase.execSQL("DROP TABLE mo_memory")
            openHelper.writableDatabase.version = 1
            close()
        }

        // Room checks the migrated schema against the entities and throws if they differ.
        val db = MindscrollDatabase.create(context, name)
        assertEquals(3L, db.metricDao().dayTotal("reels", 7))
        val store = MoMemoryStore(db)
        store.add(MoMemories.checkIn(now = 0, minutes = 30))
        assertEquals(1, store.live(0).size)
        db.close()
    }

    @Test
    fun forgettingSticksAndExpiredMemoriesAreCleared() = runBlocking {
        val db = MindscrollDatabase.create(context, name)
        val store = MoMemoryStore(db)
        val facts = MemoryFacts(0, null, null, List(7) { 10L }, peakHour = 22, favouriteApp = "Instagram")
        val now = 100 * day
        store.notice(MoMemory.candidates(facts, now), now)
        assertEquals(2, store.live(now).size)

        store.forget(MoMemories.PEAK_HOUR, now)
        store.keep(MoMemories.FAVOURITE_APP)
        store.notice(MoMemory.candidates(facts, now + day), now + day)
        assertEquals(listOf(MoMemories.FAVOURITE_APP), store.live(now + day).map { it.key })

        // A year on: the kept memory is still there, the forgotten one is gone for good.
        val later = now + 365 * day
        store.notice(emptyList(), later)
        assertEquals(listOf(MoMemories.FAVOURITE_APP), store.live(later).map { it.key })
        assertTrue(db.memoryDao().all().none { it.key == MoMemories.PEAK_HOUR })
        db.close()
    }
}
