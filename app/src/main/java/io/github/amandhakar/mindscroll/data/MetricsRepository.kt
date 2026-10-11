package io.github.amandhakar.mindscroll.data

import io.github.amandhakar.mindscroll.mascot.Fatigue
import io.github.amandhakar.mindscroll.mascot.Mood
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

    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}
