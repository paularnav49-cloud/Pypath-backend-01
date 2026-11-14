package com.pypath.app.practical

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the stored completion of a task that just passed every test stands. */
enum class CompletionStatus { SAVING, SAVED, FAILED }

/**
 * Stores "task completed" after a Check passes. The save runs on a long-lived scope and is not
 * cancelled when the screen or view model goes away, is retried a few times on failure and reports
 * every step, so a correct solution is never lost silently. One save per task at a time: repeated
 * calls while a save runs (or after it succeeded) do nothing, so rapid taps never store twice.
 *
 * The save is local (the phone's progress store). Cloud backup picks the change up afterwards and
 * retries on its own when the network is down, so no network request can undo a passed check.
 */
class CompletionSaver(
    private val scope: CoroutineScope,
    private val store: suspend (String) -> Unit,
    private val retryDelaysMs: List<Long> = listOf(400L, 1_500L),
    private val onChange: (Map<String, CompletionStatus>) -> Unit = {},
) {
    @Volatile
    var statuses: Map<String, CompletionStatus> = emptyMap()
        private set
    private val lock = Any()

    fun status(taskId: String): CompletionStatus? = statuses[taskId]

    /** Saves [taskId] unless it is already saving or saved. */
    fun save(taskId: String) {
        val start = synchronized(lock) {
            when (statuses[taskId]) {
                CompletionStatus.SAVING, CompletionStatus.SAVED -> false
                CompletionStatus.FAILED, null -> { statuses = statuses + (taskId to CompletionStatus.SAVING); true }
            }
        }
        if (!start) return
        onChange(statuses)
        scope.launch {
            withContext(NonCancellable) {
                val ok = attempt(taskId)
                set(taskId, if (ok) CompletionStatus.SAVED else CompletionStatus.FAILED)
            }
        }
    }

    /** Forgets every status (e.g. another account became active). Running saves still finish. */
    fun clear() {
        synchronized(lock) { statuses = emptyMap() }
        onChange(statuses)
    }

    private suspend fun attempt(taskId: String): Boolean {
        for (i in 0..retryDelaysMs.size) {
            try {
                store(taskId)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (i < retryDelaysMs.size) delay(retryDelaysMs[i])
            }
        }
        return false
    }

    private fun set(taskId: String, s: CompletionStatus) {
        val now = synchronized(lock) { (statuses + (taskId to s)).also { statuses = it } }
        onChange(now)
    }
}
