package io.github.amandhakar.mindscroll.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import io.github.amandhakar.mindscroll.mascot.Confidence
import io.github.amandhakar.mindscroll.mascot.Memory
import io.github.amandhakar.mindscroll.mascot.MemorySource
import io.github.amandhakar.mindscroll.mascot.MemoryType
import kotlinx.coroutines.flow.Flow

/** A [Memory] as stored. Mo's memories live next to the measurements, in their own table. */
@Entity(tableName = "mo_memory")
data class MemoryRecord(
    @PrimaryKey @ColumnInfo(name = "memory_key") val key: String,
    val type: MemoryType,
    val value: String,
    val content: String,
    val source: MemorySource,
    val confidence: Confidence,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "user_approved") val userApproved: Boolean,
    @ColumnInfo(name = "forgotten_at") val forgottenAt: Long?,
) {
    fun toMemory() = Memory(key, type, value, content, source, confidence, createdAt, expiresAt, dueAt, userApproved, forgottenAt)

    companion object {
        fun of(m: Memory) = MemoryRecord(
            m.key, m.type, m.value, m.content, m.source, m.confidence, m.createdAt, m.expiresAt, m.dueAt,
            m.userApproved, m.forgottenAt,
        )
    }
}

@Dao
interface MemoryDao {
    /** Everything, forgotten ones too: they stop Mo noticing the same thing again. */
    @Query("SELECT * FROM mo_memory")
    suspend fun all(): List<MemoryRecord>

    @Query("SELECT * FROM mo_memory WHERE forgotten_at IS NULL ORDER BY created_at DESC")
    fun observeRemembered(): Flow<List<MemoryRecord>>

    @Query("SELECT * FROM mo_memory WHERE memory_key = :key")
    suspend fun get(key: String): MemoryRecord?

    @Upsert
    suspend fun upsert(records: List<MemoryRecord>)

    @Query("DELETE FROM mo_memory WHERE memory_key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM mo_memory WHERE memory_key IN (:keys)")
    suspend fun deleteAll(keys: List<String>)
}
