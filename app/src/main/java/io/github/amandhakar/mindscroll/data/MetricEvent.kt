package io.github.amandhakar.mindscroll.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing a tracker measured. Every tracker writes into this one table, so a new tracker
 * needs no schema change:
 *
 * - reel counter: `trackerId = "reels"`, `itemKey` = app package, `value` = 1 per reel
 * - screen time (future): `trackerId = "screen_time"`, `itemKey` = app package, `value` = ms
 *
 * Daily, per-item and weekly numbers are all sums over this table.
 */
@Entity(
    tableName = "metric_event",
    indices = [Index("tracker_id", "day")],
)
data class MetricEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tracker_id") val trackerId: String,
    /** What inside the tracker this is about, e.g. a package name. */
    @ColumnInfo(name = "item_key") val itemKey: String,
    val value: Long,
    /** Local calendar day ([java.time.LocalDate.toEpochDay]) when it happened. */
    val day: Long,
    /** Wall-clock time in ms, for future hour-of-day stats. */
    val timestamp: Long,
)

data class ItemTotal(val itemKey: String, val total: Long)

data class DayTotal(val day: Long, val total: Long)
