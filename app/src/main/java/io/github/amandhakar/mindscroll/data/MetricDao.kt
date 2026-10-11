package io.github.amandhakar.mindscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricDao {
    @Insert
    suspend fun insert(event: MetricEvent)

    @Query("SELECT COALESCE(SUM(value), 0) FROM metric_event WHERE tracker_id = :trackerId AND day = :day")
    fun observeDayTotal(trackerId: String, day: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(value), 0) FROM metric_event WHERE tracker_id = :trackerId AND day = :day")
    suspend fun dayTotal(trackerId: String, day: Long): Long

    @Query(
        """SELECT item_key AS itemKey, SUM(value) AS total FROM metric_event
           WHERE tracker_id = :trackerId AND day = :day
           GROUP BY item_key ORDER BY total DESC""",
    )
    fun observeDayByItem(trackerId: String, day: Long): Flow<List<ItemTotal>>

    @Query(
        """SELECT day, SUM(value) AS total FROM metric_event
           WHERE tracker_id = :trackerId AND day BETWEEN :fromDay AND :toDay
           GROUP BY day ORDER BY day""",
    )
    fun observeDailyTotals(trackerId: String, fromDay: Long, toDay: Long): Flow<List<DayTotal>>

    @Query("SELECT timestamp FROM metric_event WHERE tracker_id = :trackerId AND timestamp >= :since")
    suspend fun timesSince(trackerId: String, since: Long): List<Long>

    @Query("SELECT COUNT(*) FROM metric_event WHERE tracker_id = :trackerId AND timestamp >= :from AND timestamp < :to")
    suspend fun countBetween(trackerId: String, from: Long, to: Long): Long

    @Query("SELECT MIN(day) FROM metric_event WHERE tracker_id = :trackerId")
    suspend fun firstDay(trackerId: String): Long?

    @Query(
        """SELECT day, SUM(value) AS total FROM metric_event
           WHERE tracker_id = :trackerId AND day BETWEEN :fromDay AND :toDay
           GROUP BY day ORDER BY day""",
    )
    suspend fun dailyTotals(trackerId: String, fromDay: Long, toDay: Long): List<DayTotal>

    @Query(
        """SELECT item_key AS itemKey, SUM(value) AS total FROM metric_event
           WHERE tracker_id = :trackerId AND day BETWEEN :fromDay AND :toDay
           GROUP BY item_key ORDER BY total DESC""",
    )
    suspend fun totalsByItemBetween(trackerId: String, fromDay: Long, toDay: Long): List<ItemTotal>
}
