package io.github.amandhakar.mindscroll.tracker

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last couple of thousand things the trackers noticed and what they decided, so a wrong count can
 * be explained from the phone ("Detection log" on the home screen). In memory only: it holds
 * view ids and numbers, never text from other apps, and is gone when the app process ends.
 */
object DetectionLog {
    private const val MAX = 2000
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val lines = ArrayDeque<String>()
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Synchronized
    fun add(message: String) {
        lines.addLast("${LocalTime.now().format(time)}  $message")
        while (lines.size > MAX) lines.removeFirst()
        _entries.value = lines.toList()
    }

    /** The whole log, oldest first, as text for copying or saving to a file. */
    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        _entries.value = emptyList()
    }
}
