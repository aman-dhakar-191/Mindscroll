package io.github.amandhakar.mindscroll.data

import io.github.amandhakar.mindscroll.mascot.Fatigue
import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The only way the rest of the app reads or writes measurements. Future features build on
 * these queries: daily limits compare [observeTodayTotal] (or [observeTodayByItem]) with a
 * limit, weekly stats use [observeDailyTotals].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetricsRepository(private val dao: MetricDao) {

    suspend fun record(trackerId: String, itemKey: String, value: Long = 1) {
        dao.insert(
            MetricEvent(
                trackerId = trackerId,
                itemKey = itemKey,
                value = value,
                day = Days.today(),
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    /** Today's total, rolling over to the new day at midnight. */
    fun observeTodayTotal(trackerId: String): Flow<Long> =
        Days.todayFlow().flatMapLatest { dao.observeDayTotal(trackerId, it) }

    fun observeTodayByItem(trackerId: String): Flow<List<ItemTotal>> =
        Days.todayFlow().flatMapLatest { dao.observeDayByItem(trackerId, it) }

    suspend fun todayTotal(trackerId: String): Long = dao.dayTotal(trackerId, Days.today())

    /** Totals for the last [days] days including today, oldest first, with zeros filled in. */
    fun observeDailyTotals(trackerId: String, days: Int): Flow<List<DayTotal>> =
        Days.todayFlow().flatMapLatest { today ->
            val from = today - days + 1
            dao.observeDailyTotals(trackerId, from, today).map { rows ->
                val byDay = rows.associate { it.day to it.total }
                (from..today).map { DayTotal(it, byDay[it] ?: 0) }
            }
        }

    /**
     * Mo's mood from [Fatigue]: updated when [trackerId] records something and once a minute
     * otherwise, so a break visibly calms Mo down. Emits only when the mood changes.
     */
    fun observeMood(trackerId: String): Flow<Mood> =
        combine(observeTodayTotal(trackerId), minuteTicks()) { _, _ -> Unit }
            .map { moodNow(trackerId) }
            .distinctUntilChanged()

    suspend fun moodNow(trackerId: String): Mood {
        val now = System.currentTimeMillis()
        return Mood.forLevel(Fatigue.level(dao.timesSince(trackerId, now - Fatigue.WINDOW_MS), now))
    }

    /**
     * What Mo remembers about recent days for [trackerId]: today vs yesterday at this time,
     * the run of calm days, the busiest hour and the favourite app over the last week.
     * [label] names an item (an app package).
     */
    suspend fun memoryFacts(trackerId: String, label: (String) -> String): MemoryFacts {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = Days.today()
        val firstDay = dao.firstDay(trackerId)
        val yesterday = today - 1
        val hasYesterday = firstDay != null && firstDay <= yesterday
        val startOfYesterday = LocalDate.ofEpochDay(yesterday).atStartOfDay(zone).toInstant().toEpochMilli()

        val from = maxOf(firstDay ?: today, today - MEMORY_DAYS)
        val byDay = dao.dailyTotals(trackerId, from, yesterday).associate { it.day to it.total }
        val weekTimes = dao.timesSince(trackerId, now - MEMORY_DAYS * DAY_MS)
        val peakHour = weekTimes.takeIf { it.size >= MIN_FOR_PATTERNS }
            ?.groupingBy { Instant.ofEpochMilli(it).atZone(zone).hour }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key
        val favourite = dao.totalsByItemBetween(trackerId, today - MEMORY_DAYS, today).firstOrNull()
            ?.takeIf { it.total >= MIN_FOR_FAVOURITE }

        return MemoryFacts(
            today = dao.dayTotal(trackerId, today),
            yesterdaySoFar = if (hasYesterday) dao.countBetween(trackerId, startOfYesterday, now - DAY_MS) else null,
            yesterdayTotal = if (hasYesterday) byDay[yesterday] ?: 0L else null,
            pastDays = (from..yesterday).map { byDay[it] ?: 0L },
            peakHour = peakHour,
            favouriteApp = favourite?.let { label(it.itemKey) },
        )
    }

    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60_000L
        const val MEMORY_DAYS = 7L
        /** Patterns need some data before they mean anything. */
        const val MIN_FOR_PATTERNS = 30
        const val MIN_FOR_FAVOURITE = 10L
    }
}
