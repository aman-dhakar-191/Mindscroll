package io.github.amandhakar.mindscroll.data

import androidx.room.withTransaction
import io.github.amandhakar.mindscroll.mascot.Memory
import io.github.amandhakar.mindscroll.mascot.MemorySource
import io.github.amandhakar.mindscroll.mascot.MemoryType
import io.github.amandhakar.mindscroll.mascot.MoMemories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What Mo remembers, on the phone only. The rules for what to keep are in [MoMemories]; this
 * applies them to the database.
 */
class MoMemoryStore(private val database: MindscrollDatabase) {
    private val dao = database.memoryDao()

    /** Memories the user can see, newest first. Expired ones drop out the next time Mo notices. */
    fun observe(): Flow<List<Memory>> = dao.observeRemembered().map { rows -> rows.map(MemoryRecord::toMemory) }

    /** Memories Mo may use [now]: not expired, not forgotten. */
    suspend fun live(now: Long): List<Memory> = dao.all().map(MemoryRecord::toMemory).filter { it.isLive(now) }

    /** Mo noticed these patterns [now]. Also clears out what's expired or long forgotten. */
    suspend fun notice(candidates: List<Memory>, now: Long) = database.withTransaction {
        val stored = dao.all().map(MemoryRecord::toMemory)
        val done = stored.filter { MoMemories.isDone(it, now) }.map { it.key }
        if (done.isNotEmpty()) dao.deleteAll(done)
        val byKey = stored.associateBy { it.key }
        val updates = candidates.mapNotNull { MoMemories.merge(byKey[it.key], it, now) }
        if (updates.isNotEmpty()) dao.upsert(updates.map(MemoryRecord::of))
    }

    suspend fun add(memory: Memory) = dao.upsert(listOf(MemoryRecord.of(memory)))

    suspend fun keep(key: String) = database.withTransaction {
        dao.get(key)?.let { dao.upsert(listOf(MemoryRecord.of(MoMemories.keep(it.toMemory())))) }
    }

    /**
     * Forgets [key]. A check-in the user asked for is simply deleted; a noticed pattern leaves a
     * note so Mo doesn't notice it again straight away.
     */
    suspend fun forget(key: String, now: Long) = database.withTransaction {
        val memory = dao.get(key)?.toMemory() ?: return@withTransaction
        if (memory.source == MemorySource.TOLD_ME || memory.type == MemoryType.CHECK_IN) {
            dao.delete(key)
        } else {
            dao.upsert(listOf(MemoryRecord.of(MoMemories.forget(memory, now))))
        }
    }

    suspend fun forgetAll(now: Long) = database.withTransaction {
        dao.all().filter { it.forgottenAt == null }.forEach { forget(it.key, now) }
    }

    /** A check-in was said: done. */
    suspend fun done(key: String) = dao.delete(key)
}
