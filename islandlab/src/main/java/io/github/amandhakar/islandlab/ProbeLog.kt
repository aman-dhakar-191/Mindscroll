package io.github.amandhakar.islandlab

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the notification probe saw, newest last. Kept in memory only. */
object ProbeLog {
    private const val MAX = 300
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        lines.addLast("${LocalTime.now().format(time)}  $message")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "(nothing yet)" else lines.reversed().joinToString("\n")
}
