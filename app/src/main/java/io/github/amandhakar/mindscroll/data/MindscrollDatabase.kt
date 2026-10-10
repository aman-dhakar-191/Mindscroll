package io.github.amandhakar.mindscroll.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Adding a table later (e.g. daily limits) means bumping [version] and adding a migration or
 * an `@AutoMigration`; the schemas Room exports to `app/schemas` make that checkable.
 */
@Database(entities = [MetricEvent::class], version = 1, exportSchema = true)
abstract class MindscrollDatabase : RoomDatabase() {
    abstract fun metricDao(): MetricDao

    companion object {
        fun create(context: Context): MindscrollDatabase =
            Room.databaseBuilder(context, MindscrollDatabase::class.java, "mindscroll.db").build()
    }
}
