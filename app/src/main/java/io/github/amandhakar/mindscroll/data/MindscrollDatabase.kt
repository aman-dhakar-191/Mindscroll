package io.github.amandhakar.mindscroll.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adding a table later (e.g. daily limits) means bumping [version] and adding a migration or
 * an `@AutoMigration`; the schemas Room exports to `app/schemas` make that checkable.
 *
 * - 1: measurements ([MetricEvent])
 * - 2: what Mo remembers ([MemoryRecord])
 */
@Database(entities = [MetricEvent::class, MemoryRecord::class], version = 2, exportSchema = true)
abstract class MindscrollDatabase : RoomDatabase() {
    abstract fun metricDao(): MetricDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun create(context: Context, name: String = "mindscroll.db"): MindscrollDatabase =
            Room.databaseBuilder(context, MindscrollDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `mo_memory` (
                        `memory_key` TEXT NOT NULL, `type` TEXT NOT NULL, `value` TEXT NOT NULL,
                        `content` TEXT NOT NULL, `source` TEXT NOT NULL, `confidence` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL, `expires_at` INTEGER, `due_at` INTEGER,
                        `user_approved` INTEGER NOT NULL, `forgotten_at` INTEGER,
                        PRIMARY KEY(`memory_key`))""",
                )
            }
        }
    }
}
