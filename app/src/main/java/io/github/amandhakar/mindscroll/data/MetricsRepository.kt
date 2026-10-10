package io.github.amandhakar.mindscroll.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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
}
