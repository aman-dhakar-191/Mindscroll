package io.github.amandhakar.mindscroll.data

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Days are local calendar days, stored as [LocalDate.toEpochDay]. */
object Days {
    fun today(): Long = LocalDate.now().toEpochDay()

    /**
     * Emits today's day and again after midnight. Checks once a minute rather than sleeping
     * until midnight, because a coroutine delay doesn't advance while the phone is asleep.
     */
    fun todayFlow(): Flow<Long> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()
}
